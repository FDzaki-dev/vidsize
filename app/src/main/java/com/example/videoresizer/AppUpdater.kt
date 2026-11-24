package com.example.videoresizer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink

/**
 * In-app updater: checks GitHub's Releases API for a newer build than the
 * one currently installed, downloads the signed APK asset straight to disk
 * (streaming, never buffered fully into RAM), and hands off to the system
 * package installer via FileProvider. [check] tetap memakai HttpURLConnection +
 * org.json (platform Android; respons JSON kecil). [download] memakai OkHttp
 * `ResponseBody.source()` yang dialirkan lewat Okio `sink().buffer()` (aturan
 * guard streaming; dependency OkHttp + Okio sejak Batch 69).
 *
 * Repo identity is hardcoded to this project's actual GitHub repo (matches
 * .github/workflows/build.yml, which is what publishes the releases this
 * reads). If the repo is ever renamed/forked, update OWNER/REPO here — nothing
 * else in the app needs to change.
 *
 * Version comparison reuses the *exact* scheme build.yml + app/build.gradle.kts
 * already establish: every CI release tag is "v<semanticVersionName>-build<N>"
 * where N = GITHUB_RUN_NUMBER, and versionCode on-device is always 1000+N
 * (see app/build.gradle.kts). So the remote build number parsed out of the
 * release tag converts straight to a versionCode-comparable int with no
 * separate "latest version" field needed from the API at all.
 */
object AppUpdater {

    private const val OWNER = "FDzaki-dev"
    // Batch 61: repo di-rename Video-resizer -> vidsize (api.github.com/.../Video-resizer masih 301 ke repo ini,
    // tetapi redirect hilang bila nama lama dipakai repo lain) -> pakai nama sekarang langsung.
    private const val REPO = "vidsize"
    private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val STREAM_CHUNK_BYTES = 8L * 1024L

    /** Klien OkHttp tunggal untuk unduhan APK; timeout & redirect (GitHub 302 -> CDN) setara [applyCommonConfig]. */
    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** Optional PAT for private repos / higher rate limits. Blank = unauthenticated (fine for a public repo). */
    private const val AUTH_TOKEN = ""

    data class UpdateInfo(
        val tagName: String,
        val apkUrl: String,
        val apkName: String,
        val remoteVersionCode: Int,
        val releaseNotes: String
    )

    sealed class CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult()
        object UpToDate : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    private fun HttpURLConnection.applyCommonConfig() {
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        instanceFollowRedirects = true // GitHub release assets 302 to an S3/CDN URL
        if (AUTH_TOKEN.isNotBlank()) setRequestProperty("Authorization", "Bearer $AUTH_TOKEN")
    }

    private fun installedVersionCode(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    }

    suspend fun check(context: Context): CheckResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                applyCommonConfig()
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val code = conn.responseCode
            if (code !in 200..299) return@withContext CheckResult.Error("Cek update gagal (HTTP $code)")

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name")
            val assets: JSONArray = json.optJSONArray("assets") ?: JSONArray()

            var apkUrl: String? = null
            var apkName = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url")
                    apkName = name
                    break
                }
            }
            if (apkUrl.isNullOrBlank()) return@withContext CheckResult.Error("Release terbaru tidak memiliki file APK")

            val remoteBuild = Regex("build(\\d+)").find(tagName)?.groupValues?.get(1)?.toIntOrNull()
            val remoteVersionCode = if (remoteBuild != null) 1000 + remoteBuild else 0
            val current = installedVersionCode(context)

            if (remoteVersionCode == 0 || remoteVersionCode <= current) {
                CheckResult.UpToDate
            } else {
                CheckResult.Available(
                    UpdateInfo(
                        tagName = tagName,
                        apkUrl = apkUrl,
                        apkName = apkName,
                        remoteVersionCode = remoteVersionCode,
                        releaseNotes = json.optString("body").take(600)
                    )
                )
            }
        } catch (e: IOException) {
            CheckResult.Error(e.message ?: "Tidak bisa terhubung ke GitHub")
        } catch (e: Exception) {
            CrashLogger.logCaught(context, "AppUpdater.check", e)
            CheckResult.Error(e.message ?: "Gagal memeriksa update")
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Streams the APK chunk-by-chunk straight to a cache file: OkHttp
     * `ResponseBody.source()` -> Okio `sink().buffer()` di [Dispatchers.IO],
     * `emitCompleteSegments()` tiap potongan sehingga buffer tak menumpuk
     * (tidak pernah `bytes()`/`readBytes()` ke memori) — APK ~50-100MB aman di
     * perangkat low-end. [onProgress] receives 0f..1f (or -1f if the server
     * didn't send Content-Length, so a caller can fall back to an
     * indeterminate indicator).
     */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val outDir = File(context.cacheDir, "updates")
            if (!outDir.isDirectory && !outDir.mkdirs() && !outDir.isDirectory) {
                throw IOException("Gagal membuat folder unduhan: ${outDir.absolutePath}")
            }
            val outFile = File(outDir, "update.apk")
            try {
                val requestBuilder = Request.Builder()
                    .url(info.apkUrl)
                    .header("Accept", "application/octet-stream")
                if (AUTH_TOKEN.isNotBlank()) requestBuilder.header("Authorization", "Bearer $AUTH_TOKEN")

                downloadClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unduh APK gagal (HTTP ${response.code})")
                    val body = response.body ?: throw IOException("Unduh APK gagal (respons kosong)")

                    val totalBytes = body.contentLength()
                    var downloaded = 0L
                    val source = body.source()
                    outFile.sink().buffer().use { sink ->
                        while (true) {
                            val read = source.read(sink.buffer, STREAM_CHUNK_BYTES)
                            if (read == -1L) break
                            sink.emitCompleteSegments()
                            downloaded += read
                            onProgress(if (totalBytes > 0) downloaded.toFloat() / totalBytes.toFloat() else -1f)
                        }
                        sink.flush()
                    }
                }
                outFile
            } catch (e: Exception) {
                CrashLogger.logCaught(context, "AppUpdater.download", e)
                if (!outFile.delete() && outFile.exists()) {
                    e.addSuppressed(IOException("Gagal menghapus berkas unduhan parsial: ${outFile.absolutePath}"))
                }
                throw e
            }
        }

    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 26) context.packageManager.canRequestPackageInstalls() else true

    /** Opens the OS "allow install from this app" settings screen (API 26+ AppOps gate). */
    fun requestInstallPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }

    /** Hands the downloaded APK to the system installer via FileProvider (matches the existing provider authority/file_paths.xml — cache-path already covers cacheDir). */
    fun install(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
