package com.example.videoresizer

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri

/** Extracts a handful of evenly-spaced frames from a video, for use as a trim-scrubber filmstrip. */
object FilmstripExtractor {

    /**
     * Returns up to [count] downsized frame bitmaps spread evenly across the
     * video's full duration. Intended to be called from a background
     * dispatcher (e.g. `Dispatchers.IO`) since frame decoding takes tens of
     * milliseconds per frame and would jank the UI if run on the main thread.
     */
    fun extract(context: Context, uri: Uri, durationMs: Long, count: Int, targetHeightPx: Int = 160): List<Bitmap> {
        if (durationMs <= 0 || count <= 0) return emptyList()
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()
        try {
            retriever.setDataSource(context, uri)
            for (i in 0 until count) {
                val fraction = if (count == 1) 0.0 else i.toDouble() / (count - 1)
                val timeUs = (fraction * durationMs * 1000).toLong()
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                val scale = targetHeightPx.toFloat() / frame.height
                val targetWidth = (frame.width * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(frame, targetWidth, targetHeightPx, true)
                if (scaled !== frame) frame.recycle()
                frames.add(scaled)
            }
        } catch (_: Exception) {
            // Return whatever frames were successfully extracted before the failure.
        } finally {
            retriever.release()
        }
        return frames
    }
}
