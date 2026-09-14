package com.example.videoresizer

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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
 */
object CrashLogger {

    private const val RELATIVE_DIR = "Documents/VideoResizer/logs/"
    private const val MAX_RETAINED_LOGS = 50

    /** Registers the crash handler. Call once, from Application.onCreate(). */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeLog(appContext, thread, throwable)
            } catch (loggingFailure: Throwable) {
                // Never let the logger itself interfere with the real crash.
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeLog(context: Context, thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "crash_${timestamp}_${UUID.randomUUID()}.txt"
        val body = buildLogBody(context, thread, throwable)

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
            put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain")
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, RELATIVE_DIR)
        }

        val collection = MediaStore.Files.getContentUri("external")
        val itemUri = resolver.insert(collection, values) ?: return

        try {
            resolver.openOutputStream(itemUri)?.use { out ->
                out.write(body.toByteArray(Charsets.UTF_8))
            } ?: run {
                resolver.delete(itemUri, null, null)
                return
            }
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            return
        }

        enforceRetention(context)
    }

    private fun buildLogBody(context: Context, thread: Thread, throwable: Throwable): String {
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val versionInfo = try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pkgInfo.versionName} (${pkgInfo.longVersionCode})"
        } catch (e: Exception) {
            "unknown"
        }

        return buildString {
            appendLine("=== Vidsize crash log ===")
            appendLine("Timestamp: ${Date()}")
            appendLine("App version: $versionInfo")
            appendLine("OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device model: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendLine("Stack trace:")
            append(stackTrace)
        }
    }

    /** FIFO retention: keeps at most [MAX_RETAINED_LOGS] logs, oldest deleted first. */
    private fun enforceRetention(context: Context) {
        try {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.Files.FileColumns._ID)
            val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? AND " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf(RELATIVE_DIR, "crash_%.txt")
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
        } catch (e: Exception) {
            // Retention is best-effort; a failure here must not affect the app.
        }
    }
}
