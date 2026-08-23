package com.example.videoresizer

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

sealed class GifExportResult {
    data class Success(val outputFile: File, val frameCount: Int) : GifExportResult()
    data class Failure(val reason: String) : GifExportResult()
}

/**
 * Converts a trimmed range of a source video into an animated GIF: extracts
 * frames at a fixed rate via [MediaMetadataRetriever.getFrameAtTime] (the
 * same technique [FilmstripExtractor] already uses for the trim-scrubber
 * thumbnails, just at a much higher frame count and resolution), builds one
 * shared color palette for the whole clip, then hands indexed-pixel frames
 * to [GifEncoder].
 *
 * Deliberately independent of the VideoResizer/Transformer pipeline: GIF
 * isn't a video codec Transformer can target, so this reads/quantizes/
 * writes everything itself rather than bending Transformer's video-export
 * path to fit an image format it was never meant to produce.
 *
 * Call from a background dispatcher (e.g. Dispatchers.Default) — frame
 * decoding, quantization, and LZW encoding are all synchronous CPU/IO work.
 */
object GifExporter {

    /**
     * Hard ceiling on frame count so a long clip / high fps combination
     * can't run for minutes or balloon memory use — the caller's UI is
     * expected to estimate frame count up front and warn/disable well
     * before hitting this, but this is the backstop.
     */
    const val MAX_FRAMES = 200

    // BUG FIX (audit, Batch 50): cancellation was cosmetic-only. This is
    // now `suspend` + calls `coroutineContext.ensureActive()` at the top
    // of both hot loops below, so `activeJob?.cancel()` in GifScreen
    // actually halts the CPU work at the next frame boundary instead of
    // silently running to completion after the UI already said
    // "Dibatalkan." — the same class of bug Batch 31 fixed for
    // BatchScreen's Transformer path, but this one's root cause is the
    // opposite: no suspension point existed anywhere in this function for
    // a cancelled Job to actually interrupt.
    suspend fun export(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        fps: Int,
        targetWidth: Int,
        outputFile: File,
        onProgress: (Int) -> Unit
    ): GifExportResult {
        if (endMs <= startMs || fps <= 0 || targetWidth <= 0) {
            return GifExportResult.Failure("Rentang waktu atau pengaturan tidak valid.")
        }

        val clipMs = endMs - startMs
        val frameInterval = 1000.0 / fps
        val frameCount = (clipMs / frameInterval).toInt().coerceIn(1, MAX_FRAMES)
        // Real spacing between extracted frames — matches `frameInterval`
        // exactly unless MAX_FRAMES capped frameCount below what `fps`
        // alone would have asked for (a very long clip / high fps
        // combination). The UI disables the export button before this can
        // happen in practice, but computing delayCentiseconds from this
        // instead of the raw `fps` keeps GifExporter itself correct even
        // if called with a frameCount-capping combination some other way.
        val actualIntervalMs = clipMs.toDouble() / frameCount

        val bitmaps = ArrayList<Bitmap>(frameCount)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, sourceUri)
            for (i in 0 until frameCount) {
                coroutineContext.ensureActive()
                val timeMs = startMs + (i * actualIntervalMs).toLong()
                val frame = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: continue
                if (frame.width <= 0 || frame.height <= 0) {
                    frame.recycle()
                    continue
                }
                val scale = targetWidth.toFloat() / frame.width
                val targetHeight = (frame.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(frame, targetWidth, targetHeight, true)
                if (scaled !== frame) frame.recycle()
                bitmaps.add(scaled)
                // Frame extraction is roughly the first 40% of total work;
                // quantizing (40-90%) and LZW encoding (90-100%) make up the rest.
                onProgress((((i + 1) * 40) / frameCount).coerceIn(0, 40))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // MUST re-throw, never swallow — catching this as a generic
            // Exception below would turn a real cancellation into a fake
            // "Failure" result and break structured concurrency (the
            // cancelling coroutine would never actually finish cancelling).
            bitmaps.forEach { it.recycle() }
            throw e
        } catch (e: Exception) {
            bitmaps.forEach { it.recycle() }
            return GifExportResult.Failure(e.message ?: "Gagal membaca video sumber")
        } finally {
            retriever.release()
        }

        if (bitmaps.isEmpty()) {
            return GifExportResult.Failure("Tidak ada frame yang berhasil diambil dari video ini.")
        }

        val width = bitmaps[0].width
        val height = bitmaps[0].height
        // Every frame was scaled from the same targetWidth against the same
        // source, so dimensions matching is expected — guarded anyway since
        // a mismatched frame would corrupt the shared-dimensions GIF output.
        val consistentBitmaps = bitmaps.filter { it.width == width && it.height == height }
        if (consistentBitmaps.isEmpty()) {
            bitmaps.forEach { it.recycle() }
            return GifExportResult.Failure("Ukuran frame video tidak konsisten.")
        }

        val palette = buildPalette(consistentBitmaps)
        // Split once, shared across every frame — quantizeFrame no longer
        // rebuilds these three arrays per call (previously up to
        // MAX_FRAMES=200 redundant IntArray(≤256) allocations for data
        // that's identical every time, since the palette itself is fixed
        // for the whole clip).
        val paletteR = IntArray(palette.size) { (palette[it] shr 16) and 0xFF }
        val paletteG = IntArray(palette.size) { (palette[it] shr 8) and 0xFF }
        val paletteB = IntArray(palette.size) { palette[it] and 0xFF }

        val indexedFrames = ArrayList<ByteArray>(consistentBitmaps.size)
        try {
            for ((i, bmp) in consistentBitmaps.withIndex()) {
                coroutineContext.ensureActive()
                indexedFrames.add(quantizeFrame(bmp, paletteR, paletteG, paletteB, width, height))
                bmp.recycle()
                onProgress((40 + ((i + 1) * 50) / consistentBitmaps.size).coerceIn(40, 90))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Bitmaps already quantized above are recycled in-loop; this
            // cleans up the ones still pending when cancellation hit, so a
            // cancelled GIF export doesn't leak native bitmap memory while
            // it unwinds. Re-throw is mandatory — see comment on the
            // frame-extraction catch block above.
            consistentBitmaps.drop(indexedFrames.size).forEach { if (!it.isRecycled) it.recycle() }
            throw e
        }

        val delayCentiseconds = max(2, (actualIntervalMs / 10.0).toInt())

        return try {
            FileOutputStream(outputFile).use { out ->
                GifEncoder.encode(
                    out = out,
                    width = width,
                    height = height,
                    palette = palette,
                    frames = indexedFrames,
                    delayCentiseconds = delayCentiseconds,
                    loopForever = true
                )
            }
            onProgress(100)
            GifExportResult.Success(outputFile, indexedFrames.size)
        } catch (e: Exception) {
            runCatching { outputFile.delete() }
            GifExportResult.Failure(e.message ?: "Gagal menulis file GIF")
        }
    }

    /**
     * Builds one shared up-to-256-color palette for the whole clip using a
     * frequency/popularity algorithm: pixels are bucketed by their top 5
     * bits per channel (32,768 possible buckets — coarser than full 24-bit
     * color, close enough to group near-identical shades together), sampled
     * with a stride rather than reading every single pixel to keep this
     * fast, and the most frequent buckets become the palette — each
     * expanded back out to full 8-bit RGB as that bucket's average color.
     *
     * This is deliberately simpler than a median-cut/NeuQuant quantizer: a
     * first working version of GIF export favors a well-understood,
     * easy-to-verify-by-hand algorithm over a more sophisticated one that's
     * much harder to get exactly right without a way to locally compile and
     * run it. Visual quality is a reasonable "GIF-typical" tradeoff, not
     * perfect photographic color reproduction — a fine target for a future
     * batch if it turns out to matter.
     */
    private fun buildPalette(bitmaps: List<Bitmap>): IntArray {
        // bucket key -> [rSum, gSum, bSum, count]
        val buckets = HashMap<Int, LongArray>()
        for (bmp in bitmaps) {
            val w = bmp.width
            val h = bmp.height
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            val stride = max(1, pixels.size / 4000)
            var i = 0
            while (i < pixels.size) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val bucket = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                val sums = buckets.getOrPut(bucket) { LongArray(4) }
                // FIX (CI Batch 9 build failure): LongArray element += Int
                // doesn't compile — Kotlin has no implicit Int->Long
                // widening, so `sums[0] += r` fails indexed-assignment
                // overload resolution ("No set method providing array
                // access"). Explicit .toLong() on the RHS resolves it.
                sums[0] += r.toLong(); sums[1] += g.toLong(); sums[2] += b.toLong(); sums[3] += 1L
                i += stride
            }
        }

        val top = buckets.entries.sortedByDescending { it.value[3] }.take(256)
        if (top.isEmpty()) return intArrayOf(0x000000, 0xFFFFFF)

        return IntArray(top.size) { idx ->
            val sums = top[idx].value
            val count = sums[3].coerceAtLeast(1)
            val r = (sums[0] / count).toInt().coerceIn(0, 255)
            val g = (sums[1] / count).toInt().coerceIn(0, 255)
            val b = (sums[2] / count).toInt().coerceIn(0, 255)
            (r shl 16) or (g shl 8) or b
        }
    }

    /** Maps every pixel of [bmp] to the nearest color in the (pre-split) palette by squared RGB distance. */
    private fun quantizeFrame(bmp: Bitmap, paletteR: IntArray, paletteG: IntArray, paletteB: IntArray, width: Int, height: Int): ByteArray {
        val pixels = IntArray(width * height)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)
        val result = ByteArray(pixels.size)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            var bestIdx = 0
            var bestDist = Int.MAX_VALUE
            for (j in paletteR.indices) {
                val dr = r - paletteR[j]
                val dg = g - paletteG[j]
                val db = b - paletteB[j]
                val dist = dr * dr + dg * dg + db * db
                if (dist < bestDist) {
                    bestDist = dist
                    bestIdx = j
                    if (dist == 0) break
                }
            }
            result[i] = bestIdx.toByte()
        }
        return result
    }
}
