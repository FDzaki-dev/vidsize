package com.example.videoresizer

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Built-in crash logger. Installs a global [Thread.UncaughtExceptionHandler]
 * that writes a plain-text crash report to
 * `Documents/VideoResizer/logs/crash_<yyyyMMdd_HHmmss>_<UUID>.txt` via
 * [MediaStore] (API 29+), then re-throws to the previous handler so normal
 * crash behavior (process death, any existing handler) is unaffected.
 *
 * No legacy storage permission is required: inserting a *new* file that this
 * app itself created into the Files/Documents collection is always allowed
 * under scoped storage, regardless of whether WRITE_EXTERNAL_STORAGE is
 * granted — the same MediaStore-first approach [PublicMovieExporter] already
 * uses for published videos.
 *
 * Fail-safe by design: every write is wrapped in its own try/catch so a
 * logging failure (storage full, provider unavailable, permission revoked
 * mid-flight) can never mask, replace, or add to the original crash.
 *
 * Batch 70: [logCaught] mencatat pengecualian yang TERTANGKAP (bukan crash) secara mentah ke
 * folder yang sama sebagai `error_<ts>_<uuid>.txt` (retensi 50 berkas per awalan, terpisah dari
 * `crash_*`), supaya kegagalan ekspor/publikasi yang dulu hanya muncul sebagai pesan singkat di UI
 * atau logcat tetap terdiagnosis tanpa adb. Berkas ini daun: tak bergantung ke berkas proyek lain.
 */
object CrashLogger {

    private const val RELATIVE_DIR = "Documents/VideoResizer/logs/"
    private const val MAX_RETAINED_LOGS = 50
    private const val TAG = "VidsizeCrashLogger"
    private const val CRASH_PREFIX = "crash"
    private const val CAUGHT_PREFIX = "error"
    private const val CRASH_TITLE = "=== Vidsize crash log ==="
    private const val CAUGHT_TITLE = "=== Vidsize caught-exception log ==="

    /** Thread latar tunggal untuk [logCaught]: I/O MediaStore tak pernah berjalan di thread pemanggil (sering main). */
    private val caughtLogExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "VidsizeCaughtLogger").apply { isDaemon = true }
        }
    }

    /** Registers the crash handler. Call once, from Application.onCreate(). */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Never let the logger itself interfere with the real crash.
            // Batch 66: kegagalan penulisan log dicatat mentah ke logcat; runCatching menjaga previousHandler tetap terpanggil.
            // Batch 75: Throwable (bukan hanya Exception) tetap sengaja ditangkap lewat runCatching — handler crash tak boleh melempar.
            runCatching { writeLog(appContext, thread, throwable) }
                .onFailure { loggingFailure -> runCatching { Log.e(TAG, "install.writeLog", loggingFailure) } }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Batas kegagalan terpusat (Batch 75): menjalankan [block]; bila melempar [Exception], hasilnya diganti [onFailure].
     * Setara `try { block() } catch (e: Exception) { onFailure(e) }`: [Error] (mis. OutOfMemoryError) TETAP dilempar ulang
     * dan `return` non-lokal dari [block]/[onFailure] tetap berlaku (inline). Menggantikan blok `catch (e: Exception)`
     * di berkas lain; ditaruh di logger daun ini agar aturan ArchUnit "mandiri" tak dilanggar.
     */
    inline fun <T> nonFatal(onFailure: (Exception) -> T, block: () -> T): T =
        runCatching(block).getOrElse { failure -> if (failure is Exception) onFailure(failure) else throw failure }

    /**
     * Mencatat [throwable] yang sudah TERTANGKAP (aplikasi tetap berjalan) mentah ke Documents lewat MediaStore.
     * [tag] = nama situs/fungsi (konvensi sama dgn `Log.e(TAG, "<fungsi>", e)`), tanpa kalimat penjelasan buatan.
     * Badan log (stacktrace) dibuat di thread pemanggil; penulisan berkas di thread latar. Tidak pernah melempar.
     */
    fun logCaught(context: Context, tag: String, throwable: Throwable) {
        runCatching {
            val appContext = context.applicationContext
            val body = buildLogBody(appContext, Thread.currentThread(), throwable, CAUGHT_TITLE, tag)
            caughtLogExecutor.execute {
                runCatching { persist(appContext, CAUGHT_PREFIX, body) }
                    .onFailure { Log.e(TAG, "logCaught.persist", it) }
            }
        }.onFailure { Log.e(TAG, "logCaught", it) }
    }

    private fun writeLog(context: Context, thread: Thread, throwable: Throwable) {
        persist(context, CRASH_PREFIX, buildLogBody(context, thread, throwable, CRASH_TITLE))
    }

    /** Menulis [body] sebagai `<prefix>_<ts>_<uuid>.txt` di [RELATIVE_DIR] lewat MediaStore, lalu retensi per [prefix]. */
    private fun persist(context: Context, prefix: String, body: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "${prefix}_${timestamp}_${UUID.randomUUID()}.txt"

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
            put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain")
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, RELATIVE_DIR)
        }

        val collection = MediaStore.Files.getContentUri("external")
        val itemUri = resolver.insert(collection, values) ?: return

        nonFatal<Unit>(
            onFailure = { e ->
                Log.e(TAG, "writeLog", e)
                resolver.delete(itemUri, null, null)
                return
            }
        ) {
            resolver.openOutputStream(itemUri)?.use { out ->
                out.write(body.toByteArray(Charsets.UTF_8))
            } ?: run {
                resolver.delete(itemUri, null, null)
                return
            }
        }

        enforceRetention(context, prefix)
    }

    private fun buildLogBody(context: Context, thread: Thread, throwable: Throwable, title: String, tag: String? = null): String {
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val versionInfo = nonFatal<String>(
            onFailure = { e ->
                Log.e(TAG, "buildLogBody.versionInfo", e)
                "unknown"
            }
        ) {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            // Batch 62: longVersionCode baru ada di API 28 (minSdk 24); NoSuchMethodError bukan Exception -> log crash API 24-27 hilang.
            val code: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode
            else @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
            "${pkgInfo.versionName} ($code)"
        }

        return buildString {
            appendLine(title)
            appendLine("Timestamp: ${Date()}")
            appendLine("App version: $versionInfo")
            appendLine("OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device model: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            if (tag != null) appendLine("Tag: $tag")
            appendLine()
            appendLine("Stack trace:")
            append(stackTrace)
        }
    }

    /** FIFO retention: keeps at most [MAX_RETAINED_LOGS] logs per [prefix] (crash / error), oldest deleted first. */
    private fun enforceRetention(context: Context, prefix: String) {
        nonFatal<Unit>(
            // Retention is best-effort; a failure here must not affect the app.
            onFailure = { e -> Log.e(TAG, "enforceRetention", e) }
        ) {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.Files.FileColumns._ID)
            val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? AND " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf(RELATIVE_DIR, "${prefix}_%.txt")
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} ASC"

            resolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                var toDelete = cursor.count - MAX_RETAINED_LOGS
                while (toDelete > 0 && cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val itemUri = MediaStore.Files.getContentUri("external", id)
                    resolver.delete(itemUri, null, null)
                    toDelete--
                }
            }
        }
    }
}
