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
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updater: checks GitHub's Releases API for a newer build than the
 * one currently installed, downloads the signed APK asset straight to disk
 * (streaming, never buffered fully into RAM), and hands off to the system
 * package installer via FileProvider. No new Gradle dependency — uses
 * HttpURLConnection + org.json (both already on the Android platform)
 * instead of pulling in OkHttp/Retrofit for one endpoint + one download.
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
    private const val REPO = "Video-resizer"
    private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

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
            CheckResult.Error(e.message ?: "Gagal memeriksa update")
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Streams the APK chunk-by-chunk straight to a cache file (never
     * input.readBytes() into memory) so a ~50-100MB APK can't OOM a
     * low-end device. [onProgress] receives 0f..1f (or -1f if the server
     * didn't send Content-Length, so a caller can fall back to an
     * indeterminate indicator).
     */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val outDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(outDir, "update.apk")
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                    applyCommonConfig()
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/octet-stream")
                }
                val code = conn.responseCode
                if (code !in 200..299) throw IOException("Unduh APK gagal (HTTP $code)")

                val totalBytes = conn.contentLengthLong
                var downloaded = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(if (totalBytes > 0) downloaded.toFloat() / totalBytes.toFloat() else -1f)
                        }
                        output.flush()
                    }
                }
                outFile
            } catch (e: Exception) {
                outFile.delete()
                throw e
            } finally {
                conn?.disconnect()
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
