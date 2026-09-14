package com.example.videoresizer

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

/**
 * Publishes a locally-rendered video file into the device's public
 * "Movies" collection, so it appears in the Gallery / Google Photos
 * and other media apps — not just inside this app's private storage.
 *
 * Two code paths are required because of Android's scoped storage rules:
 *  - API 29+ (Android 10+): insert a pending row into [MediaStore.Video.Media],
 *    stream the bytes in, then clear the "pending" flag.
 *  - API 24-28: scoped storage does not apply yet; the file can be written
 *    directly under the public Movies directory, then indexed explicitly
 *    with [MediaScannerConnection] so it shows up immediately.
 */
object PublicMovieExporter {

    private const val RELATIVE_SUBFOLDER = "VideoResizer"

    /**
     * @param sourceFile the freshly-exported file produced by [VideoResizer].
     * @return the public [Uri] the video now lives at, or null if the
     *         operation failed (caller should fall back to the private
     *         copy in that case).
     */
    fun publish(context: Context, sourceFile: File, displayName: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishViaMediaStore(context, sourceFile, displayName)
        } else {
            publishViaLegacyStorage(context, sourceFile, displayName)
        }
    }

    private fun publishViaMediaStore(context: Context, sourceFile: File, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$RELATIVE_SUBFOLDER")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collection, values) ?: return null

        return try {
            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(sourceFile).use { input -> input.copyTo(out) }
            } ?: return null

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            itemUri
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            null
        }
    }

    private fun publishViaLegacyStorage(context: Context, sourceFile: File, displayName: String): Uri? {
        return try {
            val moviesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                RELATIVE_SUBFOLDER
            )
            if (!moviesDir.exists()) moviesDir.mkdirs()

            val destFile = File(moviesDir, displayName)
            sourceFile.copyTo(destFile, overwrite = true)

            // Make the file show up in Gallery/Photos immediately rather than
            // waiting for the next full media scan.
            var resultUri: Uri? = null
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("video/mp4")
            ) { _, scannedUri -> resultUri = scannedUri }

            resultUri ?: Uri.fromFile(destFile)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Same idea as [publish], but for a GIF export: publishes into the
     * device's public "Pictures" collection instead of Movies — Gallery/
     * Photos-style apps conventionally file GIFs as images, not videos.
     */
    fun publishImage(context: Context, sourceFile: File, displayName: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishImageViaMediaStore(context, sourceFile, displayName)
        } else {
            publishImageViaLegacyStorage(context, sourceFile, displayName)
        }
    }

    private fun publishImageViaMediaStore(context: Context, sourceFile: File, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$RELATIVE_SUBFOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collection, values) ?: return null

        return try {
            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(sourceFile).use { input -> input.copyTo(out) }
            } ?: return null

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            itemUri
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            null
        }
    }

    private fun publishImageViaLegacyStorage(context: Context, sourceFile: File, displayName: String): Uri? {
        return try {
            val picturesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                RELATIVE_SUBFOLDER
            )
            if (!picturesDir.exists()) picturesDir.mkdirs()

            val destFile = File(picturesDir, displayName)
            sourceFile.copyTo(destFile, overwrite = true)

            var resultUri: Uri? = null
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("image/gif")
            ) { _, scannedUri -> resultUri = scannedUri }

            resultUri ?: Uri.fromFile(destFile)
        } catch (e: Exception) {
            null
        }
    }
}
