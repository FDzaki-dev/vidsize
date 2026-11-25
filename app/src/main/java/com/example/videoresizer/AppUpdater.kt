package com.example.videoresizer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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

    // Batch 81: unduhan ditulis ke `update.apk.part` lalu di-rename atomik ke `update.apk` -> `update.apk` yang ada
    // SELALU utuh (proses mati di tengah unduhan hanya meninggalkan .part, tak pernah APK separuh).
    private const val UPDATES_DIR_NAME = "updates"
    private const val APK_FILE_NAME = "update.apk"
    private const val APK_PART_NAME = "update.apk.part"

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
        CrashLogger.nonFatal<CheckResult>(
            onFailure = { e ->
                CrashLogger.logCaught(context, "AppUpdater.check", e)
                CheckResult.Error(e.message ?: "Gagal memeriksa update")
            }
        ) {
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
            } finally {
                conn?.disconnect()
            }
        }
    }

    /** Folder unduhan di cache app (dibuat bila belum ada); [IOException] bila gagal dibuat. */
    private fun updatesDir(context: Context): File {
        val dir = File(context.cacheDir, UPDATES_DIR_NAME)
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
            throw IOException("Gagal membuat folder unduhan: ${dir.absolutePath}")
        }
        return dir
    }

    /** true bila berkas ini sudah tidak ada (memang tak ada, atau berhasil dihapus); false bila masih ada setelah dicoba dihapus. */
    private fun File.deleteOrGone(): Boolean = !exists() || delete()

    /**
     * Batch 81: `update.apk` hasil unduhan sebelumnya yang MASIH sah untuk [info] — berkas ada, bisa diurai sistem
     * sebagai paket INI, dan versionCode-nya sama dgn [UpdateInfo.remoteVersionCode]. Null bila tak ada/rusak/versi lain.
     * Blocking (I/O + baca manifest APK): panggil dari [Dispatchers.IO].
     */
    private fun validCachedApk(context: Context, info: UpdateInfo): File? {
        val file = File(File(context.cacheDir, UPDATES_DIR_NAME), APK_FILE_NAME)
        if (!file.isFile || file.length() <= 0L) return null
        return CrashLogger.nonFatal<File?>(
            onFailure = { e ->
                CrashLogger.logCaught(context, "AppUpdater.cachedApk", e)
                null
            }
        ) {
            @Suppress("DEPRECATION")
            val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            if (archive != null &&
                archive.packageName == context.packageName &&
                archive.longVersionCode.toInt() == info.remoteVersionCode
            ) file else null
        }
    }

    /**
     * Batch 81: APK yang sudah selesai diunduh untuk [info] atau null. Dipakai UI supaya APK yang utuh TIDAK diunduh
     * ulang — mis. pemasang sistem dibatalkan (gestur kembali) atau layar izin "sumber tak dikenal" baru ditutup;
     * tetap sah setelah proses app mati karena berkasnya ada di cache, bukan di memori.
     */
    suspend fun cachedApk(context: Context, info: UpdateInfo): File? =
        withContext(Dispatchers.IO) { validCachedApk(context, info) }

    /**
     * Streams the APK chunk-by-chunk straight to a cache file: OkHttp
     * `ResponseBody.source()` -> Okio `sink().buffer()` di [Dispatchers.IO],
     * `emitCompleteSegments()` tiap potongan sehingga buffer tak menumpuk
     * (tidak pernah `bytes()`/`readBytes()` ke memori) — APK ~50-100MB aman di
     * perangkat low-end. [onProgress] receives 0f..1f (or -1f if the server
     * didn't send Content-Length, so a caller can fall back to an
     * indeterminate indicator); dipanggil hanya saat persen bulat berubah.
     *
     * Batch 81: (1) bila [cachedApk] sudah sah, langsung dikembalikan tanpa unduh; (2) ditulis ke `update.apk.part`
     * lalu di-rename atomik -> `update.apk` tak pernah separuh; (3) loop memeriksa pembatalan korutin tiap potongan,
     * jadi membatalkan job unduhan benar-benar menghentikan transfer (dan menghapus .part) — pembatalan bukan error,
     * tidak dicatat ke log kegagalan.
     */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val outDir = updatesDir(context)
            validCachedApk(context, info)?.let { cached ->
                onProgress(1f)
                return@withContext cached
            }
            val outFile = File(outDir, APK_FILE_NAME)
            val partFile = File(outDir, APK_PART_NAME)
            CrashLogger.nonFatal<File>(
                onFailure = { e ->
                    if (e !is CancellationException) CrashLogger.logCaught(context, "AppUpdater.download", e)
                    if (!partFile.deleteOrGone()) {
                        e.addSuppressed(IOException("Gagal menghapus berkas unduhan parsial: ${partFile.absolutePath}"))
                    }
                    throw e
                }
            ) {
                // APK lama (versi lain/rusak) dan sisa .part dibuang dulu; hasil tiap penghapusan diperiksa.
                if (!outFile.deleteOrGone()) throw IOException("Gagal menghapus APK lama: ${outFile.absolutePath}")
                if (!partFile.deleteOrGone()) throw IOException("Gagal menghapus sisa unduhan: ${partFile.absolutePath}")

                val requestBuilder = Request.Builder()
                    .url(info.apkUrl)
                    .header("Accept", "application/octet-stream")
                if (AUTH_TOKEN.isNotBlank()) requestBuilder.header("Authorization", "Bearer $AUTH_TOKEN")

                downloadClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unduh APK gagal (HTTP ${response.code})")
                    val body = response.body ?: throw IOException("Unduh APK gagal (respons kosong)")

                    val totalBytes = body.contentLength()
                    var downloaded = 0L
                    var lastPercent = -2
                    val source = body.source()
                    partFile.sink().buffer().use { sink ->
                        while (true) {
                            ensureActive()
                            val read = source.read(sink.buffer, STREAM_CHUNK_BYTES)
                            if (read == -1L) break
                            sink.emitCompleteSegments()
                            downloaded += read
                            val percent = if (totalBytes > 0) (downloaded * 100L / totalBytes).toInt() else -1
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(if (percent >= 0) percent / 100f else -1f)
                            }
                        }
                        sink.flush()
                    }
                    if (totalBytes > 0 && downloaded != totalBytes) {
                        throw IOException("Unduhan APK tidak lengkap ($downloaded dari $totalBytes byte)")
                    }
                }
                if (!partFile.renameTo(outFile)) throw IOException("Gagal menyimpan APK unduhan: ${outFile.absolutePath}")
                outFile
            }
        }

    /**
     * Batch 81: buang sisa unduhan (APK versi terpasang/lama + .part) — dipanggil saat cek update menyatakan sudah versi
     * terbaru, supaya APK puluhan MB tak menetap di cache. Kegagalan hapus dicatat mentah, tidak dilempar.
     */
    suspend fun clearCache(context: Context) {
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, UPDATES_DIR_NAME)
            for (name in listOf(APK_FILE_NAME, APK_PART_NAME)) {
                val file = File(dir, name)
                if (!file.deleteOrGone()) {
                    CrashLogger.logCaught(context, "AppUpdater.clearCache", IOException("Gagal menghapus ${file.absolutePath}"))
                }
            }
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
