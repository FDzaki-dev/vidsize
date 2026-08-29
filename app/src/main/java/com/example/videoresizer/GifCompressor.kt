package com.example.videoresizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

sealed class GifCompressResult {
    data class Success(
        val outputFile: File,
        val frameCount: Int,
        val sourceBytes: Long,
        val outputBytes: Long,
        val outputDurationMs: Long,
        val outputWidth: Int
    ) : GifCompressResult()
    data class Failure(val reason: String) : GifCompressResult()
}

/** Mirrors [com.example.videoresizer.CompressionLevel]'s 2-tier RECOMMENDED/MAXIMUM pattern (VideoResizer.kt) for UI/UX consistency — same FilterChip row style, same "2 honest tiers" philosophy. */
enum class GifCompressionLevel(
    val label: String,
    val description: String,
    val dedupThreshold: Int,
    val maxWidthCapPx: Int?
) {
    RINGAN(
        label = "Ringan",
        description = "Resolusi & jumlah frame nyaris tidak berubah — hanya membuang frame yang benar-benar duplikat. Paling aman untuk kualitas (tetap HD).",
        dedupThreshold = 6,
        maxWidthCapPx = null
    ),
    MAKSIMAL(
        label = "Maksimal",
        description = "Buang frame mirip lebih agresif + batasi lebar ke 720px kalau sumbernya lebih besar. Ukuran lebih kecil, kualitas masih layak.",
        dedupThreshold = 14,
        maxWidthCapPx = 720
    );

    companion object {
        val ENTRIES: List<GifCompressionLevel> = values().toList()
    }
}

/**
 * Compresses an EXISTING GIF file the user picks — a different pipeline
 * from [GifExporter] (which builds a brand-new GIF from a VIDEO source via
 * MediaMetadataRetriever). This one decodes an already-existing raster GIF
 * via [Movie] (frame-accurate `setTime`/`draw` — deprecated as a *display*
 * widget in favor of ImageDecoder/AnimatedImageDrawable on API 28+, but
 * still the only public API available all the way back to this project's
 * own minSdk 24 that hands back individual decoded frames the way this
 * needs; ImageDecoder is API 28+ only and is callback/render-driven, not a
 * simple "give me frame N" call). GifExporter.kt itself is deliberately
 * left untouched by this addition — see PROJECT_STATE.md Batch 55 for why
 * (short version: keep the existing, working, video→GIF feature at zero
 * risk rather than share code across files for this).
 *
 * "Kompresi" here comes from two deliberately simple, easy-to-verify-by-hand
 * techniques (same philosophy as [GifExporter.buildPalette]'s own doc
 * comment: favor something a reviewer can reason about without a compiler
 * over a fancier approach that's much harder to get exactly right blind —
 * this project has no local `kotlinc`, see PROJECT_STATE.md):
 *  1. **Near-duplicate frame dedup.** A lot of real-world GIFs (screen
 *     recordings, slow pans, mostly-static UI captures) carry runs of
 *     frames that are visually identical or nearly so. A frame whose
 *     sampled pixel difference from the last KEPT frame is under
 *     [GifCompressionLevel.dedupThreshold] is dropped, and its own delay is
 *     merged onto the last kept frame instead of being thrown away — so
 *     the animation's total playback duration/speed stays the same, it
 *     just doesn't waste bytes re-storing indistinguishable frames. This
 *     needed [GifEncoder] to grow a per-frame-delay overload (see that
 *     file) since it only supported one flat delay for every frame before;
 *     GifExporter's own existing call site is untouched and behaves
 *     byte-for-byte the same as before.
 *  2. **Optional width cap** — only at [GifCompressionLevel.MAKSIMAL].
 *     [GifCompressionLevel.RINGAN] never resizes, so its output stays
 *     pixel-for-pixel the same resolution as the source ("kualitas output
 *     tetap HD" as explicitly requested — no forced downscale by default).
 *
 * Anti-crash guards (also explicitly requested):
 *  - [MAX_INPUT_BYTES] rejects an oversized source file up front, before
 *    attempting to decode it, rather than finding out mid-decode.
 *  - [MAX_SOURCE_FRAMES] bounds how many frames get sampled/decoded/held in
 *    memory at once, same ceiling role as [GifExporter.MAX_FRAMES].
 *  - [HARD_MAX_DECODE_WIDTH] is an absolute backstop independent of
 *    [GifCompressionLevel] — even RINGAN never decodes wider than this, so
 *    a pathological huge-resolution GIF can't blow up device memory.
 *    1280px is itself still "HD" width (~720p landscape), so this never
 *    visibly affects a normal HD-or-smaller GIF. Achieved by pre-scaling
 *    the decode Canvas itself (see `compressInternal`), so a full
 *    native-resolution Bitmap is never allocated even for a huge source.
 *  - The whole pipeline is wrapped so any [OutOfMemoryError] or unexpected
 *    [Exception] becomes a [GifCompressResult.Failure] with a friendly
 *    Indonesian message instead of propagating up and crashing the app.
 *
 * Call from a background dispatcher (e.g. Dispatchers.Default) — decoding,
 * dedup, quantization, and LZW encoding are all synchronous CPU/IO work,
 * same calling convention as [GifExporter.export].
 */
@Suppress("DEPRECATION")
object GifCompressor {

    const val MAX_SOURCE_FRAMES = 300
    private const val HARD_MAX_DECODE_WIDTH = 1280
    private const val MAX_INPUT_BYTES = 60L * 1024 * 1024

    /**
     * Sampling cadence used to walk [Movie]'s timeline — Movie only exposes
     * total duration(), not per-frame timestamps, so this mirrors
     * GifExporter's own "sample at a fixed interval" approach (see its
     * frameInterval/actualIntervalMs). ~25fps is fast enough to catch
     * virtually every distinct frame a typical GIF encoder produces (most
     * real-world GIFs run well under 20fps, since GIF delays are only
     * precise to 1/100s and few tools bother going faster).
     */
    private const val SAMPLE_INTERVAL_MS = 40.0

    fun compress(
        context: Context,
        sourceUri: Uri,
        level: GifCompressionLevel,
        outputFile: File,
        onProgress: (Int) -> Unit
    ): GifCompressResult {
        return try {
            compressInternal(context, sourceUri, level, outputFile, onProgress)
        } catch (e: OutOfMemoryError) {
            GifCompressResult.Failure("Memori tidak cukup untuk memproses GIF ini. Coba GIF dengan resolusi/durasi lebih kecil, atau pakai tingkat kompresi Maksimal.")
        } catch (e: Exception) {
            GifCompressResult.Failure(e.message ?: "Gagal mengompres GIF.")
        }
    }

    private fun compressInternal(
        context: Context,
        sourceUri: Uri,
        level: GifCompressionLevel,
        outputFile: File,
        onProgress: (Int) -> Unit
    ): GifCompressResult {
        val sourceBytes = runCatching {
            context.contentResolver.openAssetFileDescriptor(sourceUri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        if (sourceBytes <= 0) {
            return GifCompressResult.Failure("File GIF tidak bisa dibaca.")
        }
        if (sourceBytes > MAX_INPUT_BYTES) {
            return GifCompressResult.Failure("File GIF terlalu besar (maks ${MAX_INPUT_BYTES / (1024 * 1024)}MB) untuk dikompres di perangkat ini.")
        }

        // Batch 55b (bugfix): read the whole file into memory FIRST, then
        // decode via decodeByteArray — NOT decodeStream(inputStream) closed
        // via `.use{}` right after the call. Movie.draw() is called many
        // times AFTER decode (once per sampled frame, further down) — if
        // decodeStream() keeps any lazy reference into the stream rather
        // than fully self-contained data, closing that stream immediately
        // (as `.use{}` does) risks every later draw() call silently
        // rendering nothing, which is exactly the "blank white output"
        // failure mode reported against the first version of this file.
        // decodeByteArray has no such risk — the byte array is fully
        // materialized and owns its own data, independent of any stream.
        // This readBytes() is bounded by MAX_INPUT_BYTES (already checked
        // above, ≤60MB) — a different risk profile from the Release
        // Downloader's "DILARANG readBytes()" rule, which is about an
        // a-priori-unbounded network download, not a pre-size-checked
        // local file.
        val sourceBytesArray = runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
        }.getOrNull() ?: return GifCompressResult.Failure("File GIF tidak bisa dibaca.")

        val movie = Movie.decodeByteArray(sourceBytesArray, 0, sourceBytesArray.size)
            ?: return GifCompressResult.Failure("File yang dipilih bukan GIF yang valid.")

        val srcWidth = movie.width()
        val srcHeight = movie.height()
        if (srcWidth <= 0 || srcHeight <= 0) {
            return GifCompressResult.Failure("File yang dipilih bukan GIF yang valid.")
        }

        // Absolute crash-prevention backstop, independent of `level` — see
        // HARD_MAX_DECODE_WIDTH's doc comment above.
        val decodeScale = min(1f, HARD_MAX_DECODE_WIDTH.toFloat() / srcWidth)
        // MAKSIMAL's own (much gentler) resize preference — only applies if
        // it would shrink further than the safety backstop already did.
        val capPx = level.maxWidthCapPx
        val levelScale = if (capPx != null && srcWidth * decodeScale > capPx) {
            capPx.toFloat() / (srcWidth * decodeScale)
        } else 1f
        val finalScale = decodeScale * levelScale
        val outWidth = max(1, (srcWidth * finalScale).toInt())
        val outHeight = max(1, (srcHeight * finalScale).toInt())

        val totalDurationMs = movie.duration().let { if (it > 0) it else 1 }
        val frameCount = (totalDurationMs / SAMPLE_INTERVAL_MS).toInt().coerceIn(1, MAX_SOURCE_FRAMES)
        val actualIntervalMs = totalDurationMs.toDouble() / frameCount

        // Batch 55c (bugfix): ONE persistent bitmap+canvas reused across
        // every sampled frame — NOT a fresh blank one per sample (the
        // previous version of this loop). A real GIF player's drawing
        // surface PERSISTS between frames: plenty of real-world GIFs
        // (especially ones already run through an optimizer, but also
        // plenty of ordinary ones) only redraw the region that actually
        // changed at a given timestamp, relying on whatever was already on
        // the canvas for everything else. Handing Movie a brand-new blank
        // canvas for every single sample discarded that assumption —
        // anything outside whatever region got redrawn at that exact
        // timestamp stayed at the canvas's blank/transparent init state
        // (rendered as white by most viewers/galleries), which matches
        // exactly the "top strip has real content, everything below it is
        // blank" result reported against Batch 55b. This fix is safe
        // either way: if Movie actually already composites every frame
        // fully internally (i.e. this wasn't the bug), reusing the canvas
        // changes nothing observable, since each draw() would repaint the
        // same full content it always did.
        val canvasBmp = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBmp)
        if (finalScale != 1f) canvas.scale(finalScale, finalScale)

        // --- Sample `frameCount` evenly-spaced points in time, snapshotting the persistent canvas after each ---
        val sampled = ArrayList<Bitmap>(frameCount)
        for (i in 0 until frameCount) {
            val t = min((i * actualIntervalMs).toInt(), (totalDurationMs - 1).coerceAtLeast(0))
            movie.setTime(t)
            movie.draw(canvas, 0f, 0f)
            // A genuine copy, not a reference — canvasBmp keeps getting
            // drawn on for every later sample, so each kept snapshot needs
            // to own its own pixel data.
            sampled.add(canvasBmp.copy(Bitmap.Config.ARGB_8888, false))
            // Decoding is roughly the first 30% of total work; dedup is a
            // quick pass (30-35%), quantizing (35-90%) and LZW encoding
            // (90-100%) make up the rest — mirrors GifExporter's own
            // stage-weighted progress reporting.
            onProgress((((i + 1) * 30) / frameCount).coerceIn(0, 30))
        }
        canvasBmp.recycle()

        if (sampled.isEmpty()) {
            return GifCompressResult.Failure("Tidak ada frame yang berhasil dibaca dari GIF ini.")
        }

        // Anti-corrupt/blank-output safety net: if EVERY sampled frame came
        // back a single uniform color, that's not a real animated-GIF
        // frame (even simple GIFs have some pixel variation) — it's the
        // signature of Movie.draw() silently failing to render anything on
        // this device/file combo. Rather than silently encoding that as a
        // "successful" but blank/white-looking GIF, fail loudly here with
        // a clear message instead.
        if (sampled.all { isUniformColor(it) }) {
            sampled.forEach { it.recycle() }
            return GifCompressResult.Failure("Gagal membaca isi GIF ini di perangkat ini (frame yang terbaca kosong/polos). Coba file GIF lain.")
        }

        // --- Dedup near-identical consecutive frames, merging delay onto the kept one ---
        val keptBitmaps = ArrayList<Bitmap>()
        val keptDelaysMs = ArrayList<Double>()
        for (bmp in sampled) {
            val lastIdx = keptBitmaps.size - 1
            if (lastIdx >= 0 && bitmapDiffScore(keptBitmaps[lastIdx], bmp) < level.dedupThreshold) {
                keptDelaysMs[lastIdx] = keptDelaysMs[lastIdx] + actualIntervalMs
                bmp.recycle()
            } else {
                keptBitmaps.add(bmp)
                keptDelaysMs.add(actualIntervalMs)
            }
        }
        onProgress(35)

        val palette = buildPaletteLocal(keptBitmaps)
        val paletteR = IntArray(palette.size) { (palette[it] shr 16) and 0xFF }
        val paletteG = IntArray(palette.size) { (palette[it] shr 8) and 0xFF }
        val paletteB = IntArray(palette.size) { palette[it] and 0xFF }

        val indexedFrames = ArrayList<ByteArray>(keptBitmaps.size)
        for (i in keptBitmaps.indices) {
            indexedFrames.add(quantizeFrameLocal(keptBitmaps[i], paletteR, paletteG, paletteB, outWidth, outHeight))
            keptBitmaps[i].recycle()
            onProgress((35 + ((i + 1) * 55) / keptBitmaps.size).coerceIn(35, 90))
        }

        // GIF delay unit is centiseconds; floor of 2 matches GifExporter's
        // own floor (0/1 reads as "no delay" on many real-world viewers).
        val delaysCs = keptDelaysMs.map { max(2, (it / 10.0).toInt()) }
        val outputDurationMs = keptDelaysMs.sum().toLong()

        return try {
            FileOutputStream(outputFile).use { out ->
                GifEncoder.encode(
                    out = out,
                    width = outWidth,
                    height = outHeight,
                    palette = palette,
                    frames = indexedFrames,
                    delays = delaysCs,
                    loopForever = true
                )
            }
            onProgress(100)
            GifCompressResult.Success(
                outputFile = outputFile,
                frameCount = indexedFrames.size,
                sourceBytes = sourceBytes,
                outputBytes = outputFile.length(),
                outputDurationMs = outputDurationMs,
                outputWidth = outWidth
            )
        } catch (e: Exception) {
            runCatching { outputFile.delete() }
            GifCompressResult.Failure(e.message ?: "Gagal menulis file GIF.")
        }
    }

    /**
     * True if every sampled pixel in [bmp] is the exact same color. Used
     * only as a "did rendering actually happen" signal (see the check right
     * after the decode loop above) — a real GIF frame, even a simple one,
     * essentially never comes back perfectly uniform across a whole strided
     * sample; a canvas that Movie.draw() failed to render onto does.
     */
    private fun isUniformColor(bmp: Bitmap): Boolean {
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return true
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val first = pixels[0]
        val stride = max(1, pixels.size / 2000)
        var i = 0
        while (i < pixels.size) {
            if (pixels[i] != first) return false
            i += stride
        }
        return true
    }

    /**
     * Average per-channel pixel difference between two same-size bitmaps,
     * sampled with a stride (same reasoning as GifExporter.buildPalette's
     * own stride sampling — a full per-pixel compare on every consecutive
     * frame pair would be needless work for what's just a duplicate/
     * not-duplicate signal). 0 = identical, 255 = maximally different.
     */
    private fun bitmapDiffScore(a: Bitmap, b: Bitmap): Int {
        val w = a.width
        val h = a.height
        if (w != b.width || h != b.height) return 255
        val pixelsA = IntArray(w * h)
        val pixelsB = IntArray(w * h)
        a.getPixels(pixelsA, 0, w, 0, 0, w, h)
        b.getPixels(pixelsB, 0, w, 0, 0, w, h)
        val stride = max(1, pixelsA.size / 4000)
        var totalDiff = 0L
        var samples = 0
        var i = 0
        while (i < pixelsA.size) {
            val pa = pixelsA[i]
            val pb = pixelsB[i]
            val dr = ((pa shr 16) and 0xFF) - ((pb shr 16) and 0xFF)
            val dg = ((pa shr 8) and 0xFF) - ((pb shr 8) and 0xFF)
            val db = (pa and 0xFF) - (pb and 0xFF)
            totalDiff += (abs(dr) + abs(dg) + abs(db)) / 3
            samples++
            i += stride
        }
        return if (samples > 0) (totalDiff / samples).toInt() else 255
    }

    /**
     * Same frequency-bucket palette algorithm as GifExporter.buildPalette —
     * intentionally duplicated rather than shared (see this file's doc
     * comment on GifExporter.kt being left untouched) since it's a small,
     * already-proven, fully self-contained function.
     */
    private fun buildPaletteLocal(bitmaps: List<Bitmap>): IntArray {
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

    /** Same nearest-color mapping as GifExporter.quantizeFrame — duplicated for the same reason as buildPaletteLocal above. */
    private fun quantizeFrameLocal(bmp: Bitmap, paletteR: IntArray, paletteG: IntArray, paletteB: IntArray, width: Int, height: Int): ByteArray {
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
