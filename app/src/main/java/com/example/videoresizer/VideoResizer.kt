package com.example.videoresizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import java.io.File
import java.io.FileOutputStream

/**
 * Target aspect ratio for the exported clip.
 *
 * PERF: Kotlin/Java's generated `enum.values()` allocates a brand-new array
 * on every single call — it is not cached by the compiler. `ENTRIES` below
 * computes that array exactly once (companion `object` init happens on
 * class-load) and hands out the same immutable `List` forever after.
 * `AspectRatioOption.values()` was previously called straight from
 * Composable bodies in MainActivity.kt — including ones re-evaluated on
 * every trim-handle drag frame and every Studio list-item render — so each
 * of those recompositions was paying for a fresh array + boxing into a List
 * for no reason. Use `AspectRatioOption.ENTRIES` everywhere instead of
 * `AspectRatioOption.values()`.
 */
enum class AspectRatioOption(val label: String, val widthRatio: Int, val heightRatio: Int) {
    ORIGINAL("Original", 0, 0),
    LANDSCAPE_16_9("16:9", 16, 9),
    PORTRAIT_9_16("9:16", 9, 16),
    SQUARE_1_1("1:1", 1, 1),
    STANDARD_4_3("4:3", 4, 3),
    PORTRAIT_4_5("4:5", 4, 5);

    companion object {
        val ENTRIES: List<AspectRatioOption> = values().toList()
    }
}

/** Target output resolution (height in pixels, width derived from aspect ratio). CUSTOM uses explicit width/height instead. */
enum class ResolutionOption(val label: String, val targetHeight: Int) {
    ORIGINAL("Original", 0),
    P480("480p", 480),
    P720("720p", 720),
    P1080("1080p", 1080),
    CUSTOM("Custom", -1);

    companion object {
        val ENTRIES: List<ResolutionOption> = values().toList()
    }
}

/** How the source frame is mapped into the target width/height when the aspect ratios don't match. */
enum class ResizeMode(val label: String) {
    CROP("Crop"),
    STRETCH("Stretch");

    companion object {
        val ENTRIES: List<ResizeMode> = values().toList()
    }
}

/** Rotation to apply to the output, in degrees clockwise. */
enum class RotationOption(val label: String, val degrees: Float) {
    NONE("0°", 0f),
    CW_90("90°", 90f),
    CW_180("180°", 180f),
    CW_270("270°", 270f);

    companion object {
        val ENTRIES: List<RotationOption> = values().toList()
    }
}

/**
 * Target video bitrate preset. ORIGINAL leaves the encoder's default
 * bitrate selection untouched (same behavior as before this feature
 * existed). The others request an explicit bitrate via
 * [androidx.media3.transformer.VideoEncoderSettings] so a novice user gets
 * a predictable, much smaller file at the cost of some visual quality —
 * the same "Low/Medium/High" tradeoff CapCut/InShot-style apps expose,
 * without needing to understand what a bitrate actually is.
 *
 * Values are a bits-per-pixel-per-second multiplier applied against the
 * actual output pixel count in [VideoResizer], rather than one fixed
 * number that would over-compress large frames or waste space on small
 * ones.
 */
enum class QualityOption(val label: String, val bitsPerPixelPerSecond: Double) {
    ORIGINAL("Original", 0.0),
    LOW("Rendah", 0.045),
    MEDIUM("Sedang", 0.09),
    HIGH("Tinggi", 0.18),
    CUSTOM("Custom", -1.0);

    companion object {
        val ENTRIES: List<QualityOption> = values().toList()
    }
}

/**
 * Corner (or center) anchor for a watermark/logo overlay, expressed as
 * NDC-space anchors for [androidx.media3.effect.OverlaySettings]. Pinned
 * against media3-effect 1.3.1's anchor convention specifically — 1.6.0
 * flipped the sign convention of `setOverlayFrameAnchor`, so if this
 * project's Media3 version is ever bumped past that, the anchor pairs
 * below need re-checking against the release notes for that version.
 */
enum class WatermarkPosition(val label: String) {
    TOP_LEFT("Kiri atas"),
    TOP_RIGHT("Kanan atas"),
    BOTTOM_LEFT("Kiri bawah"),
    BOTTOM_RIGHT("Kanan bawah"),
    CENTER("Tengah");

    companion object {
        val ENTRIES: List<WatermarkPosition> = values().toList()
    }
}

/**
 * Mirrors the output horizontally/vertically. Independent of [RotationOption]
 * — both end up folded into the same [ScaleAndRotateTransformation] in
 * [VideoResizer.resizeInternal] since that builder accepts scale and
 * rotation together, rather than adding a second Effect entry.
 */
enum class FlipOption(val label: String) {
    NONE("Tidak ada"),
    HORIZONTAL("Horizontal"),
    VERTICAL("Vertikal");

    companion object {
        val ENTRIES: List<FlipOption> = values().toList()
    }
}

/**
 * Target output frame rate. ORIGINAL leaves the source's own frame rate
 * untouched (identical behavior to before this feature existed). Backed by
 * [androidx.media3.effect.FrameDropEffect.createDefaultFrameDropEffect],
 * which decides per-frame whether to keep or drop it purely from
 * presentation timestamps — it does not need to be told the *source*'s
 * frame rate up front.
 */
enum class FrameRateOption(val label: String, val fps: Int) {
    ORIGINAL("Original", 0),
    FPS_24("24 fps", 24),
    FPS_30("30 fps", 30),
    FPS_60("60 fps", 60);

    companion object {
        val ENTRIES: List<FrameRateOption> = values().toList()
    }
}

/**
 * Quality target for [VideoResizer.compress] — the dedicated Compressor tab,
 * as opposed to [QualityOption] which backs the Resizer screen's own
 * bitrate slider. Both presets force the H.265/HEVC codec instead of
 * whatever the source used: HEVC needs roughly half the bitrate of H.264
 * for the same perceived quality, which is *where the size reduction
 * actually comes from*. Re-encoding is inherently lossy — there is no such
 * thing as a truly zero-quality-loss way to shrink an already-encoded
 * video — so both presets are tuned to be visually transparent (no
 * perceptible difference on a phone screen) rather than literally lossless,
 * and [VideoResizer.compress] additionally never requests a bitrate higher
 * than the source's own, so an already-efficient source is left alone
 * instead of being blown back up.
 */
enum class CompressionLevel(val label: String, val description: String, val targetBitsPerPixelPerFrame: Double) {
    RECOMMENDED("Rekomendasi", "Ukuran lebih kecil, kualitas nyaris tak terlihat beda", 0.06),
    MAXIMUM("Maksimal", "Ukuran paling kecil, kualitas masih layak", 0.035);

    companion object {
        val ENTRIES: List<CompressionLevel> = values().toList()
        /**
         * Fallback fps used by the bpp→bitrate formula ONLY when the
         * source's real frame rate couldn't be probed (see
         * [CompressRequest.sourceFps] — Batch 32 fix). Previously this was
         * used unconditionally for every source regardless of its actual
         * fps, which meant a 60fps source got a bitrate sized for half its
         * real frame rate (visibly worse quality than the preset promised)
         * and a 24fps source got more bitrate than it needed (bigger file
         * than necessary). Kept as a named fallback, not deleted, since a
         * source whose fps can't be read still needs *some* assumption.
         */
        const val ASSUMED_FPS = 30
    }
}

/** Request for [VideoResizer.compress]. Deliberately separate from [ResizeRequest]: no aspect/resolution/watermark/caption knobs, just the source identity + an optional trim + a [CompressionLevel]. */
data class CompressRequest(
    val sourceUri: Uri,
    val outputFile: File,
    val sourceWidth: Int,
    val sourceHeight: Int,
    /** Duration (ms) and file size (bytes) of the *whole original source file* — used only to estimate its current bitrate so compression never re-encodes above it. Not affected by trimStartMs/trimEndMs below. */
    val sourceDurationMs: Long,
    val sourceFileSizeBytes: Long,
    /**
     * Source video track's real frame rate (Batch 32), probed by
     * [MainActivity]'s CompressorScreen via `MediaExtractor` +
     * `MediaFormat.KEY_FRAME_RATE`. 0 or negative means "couldn't be
     * probed" — [computeCompressTargetBitrateBps] falls back to
     * [CompressionLevel.ASSUMED_FPS] in that case, same as the old
     * unconditional behavior.
     */
    val sourceFps: Int = 0,
    /**
     * Whether the source has an audio track at all (Batch 33), probed by
     * [MainActivity]'s CompressorScreen via `MediaExtractor`. Defaults to
     * `true` (old assumed-audio-present behavior) so a caller that hasn't
     * been updated to probe this still behaves exactly as before.
     */
    val sourceHasAudio: Boolean = true,
    /**
     * Source's real audio bitrate in bits/sec (Batch 33), probed via
     * `MediaFormat.KEY_BIT_RATE` on the audio track if present. 0 or
     * negative means "couldn't be probed" — [estimateSourceBitrateBps]
     * falls back to the old flat 128kbps assumption in that case (only
     * when [sourceHasAudio] is true; a source with no audio track at all
     * correctly gets 0 regardless of this field).
     */
    val sourceAudioBitrateBps: Int = 0,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val muteAudio: Boolean = false,
    val level: CompressionLevel = CompressionLevel.RECOMMENDED
)

data class ResizeRequest(
    val sourceUri: Uri,
    val outputFile: File,
    val aspectRatio: AspectRatioOption,
    val resolution: ResolutionOption,
    val sourceWidth: Int,
    val sourceHeight: Int,
    /** Trim range, in ms, relative to the source video. Both 0 means "no trim". */
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val muteAudio: Boolean = false,
    val rotation: RotationOption = RotationOption.NONE,
    val resizeMode: ResizeMode = ResizeMode.CROP,
    /** Used only when resolution == CUSTOM: exact output pixel dimensions. */
    val customWidth: Int? = null,
    val customHeight: Int? = null,
    /** Bitrate/quality preset. ORIGINAL = let the encoder pick (old behavior). */
    val quality: QualityOption = QualityOption.ORIGINAL,
    /** Used only when quality == CUSTOM: exact target video bitrate in kbps. */
    val customBitrateKbps: Int? = null,
    /** Logo/watermark image to overlay on the output, or null for none. */
    val watermarkUri: Uri? = null,
    val watermarkPosition: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    /** 0-100. How opaque the watermark is (100 = fully solid). */
    val watermarkOpacityPercent: Int = 70,
    /** 0-100. Watermark width as a rough percentage of the frame width. */
    val watermarkScalePercent: Int = 18,
    /** Optional short caption burned into the output as a static text overlay (white text, black outline). Null/blank = no caption. */
    val captionText: String? = null,
    /** Reuses [WatermarkPosition] rather than a separate enum — same five anchor points work fine for a caption too. */
    val captionPosition: WatermarkPosition = WatermarkPosition.BOTTOM_RIGHT,
    /** Mirror the output on this axis. NONE = no flip (old behavior). */
    val flip: FlipOption = FlipOption.NONE,
    /** Target output frame rate. ORIGINAL = keep the source's frame rate (old behavior). */
    val frameRate: FrameRateOption = FrameRateOption.ORIGINAL
)

sealed class ResizeResult {
    data class Success(val outputFile: File) : ResizeResult()
    data class Failure(val message: String) : ResizeResult()
}

/**
 * Wraps androidx.media3 Transformer to re-encode a video into a new
 * aspect ratio/resolution, optionally trimmed, muted, and/or rotated.
 */
@UnstableApi
class VideoResizer(private val context: Context) {

    /**
     * Starts the export and returns the underlying [Transformer] so the
     * caller can cancel a running export (`transformer.cancel()`).
     *
     * UX FIX: previously `onProgress` was wired up but never actually
     * called — [androidx.media3.transformer.Transformer] doesn't push
     * progress on its own, you have to poll `getProgress()`. Without that,
     * "Processing…" was an indefinite spinner with zero feedback: a novice
     * user has no way to tell a slow export from a frozen app. This adds a
     * Main-thread poll loop (Transformer must be polled from its own
     * thread) that reports percentage until the export finishes or errors,
     * and stops cleanly either way so it doesn't keep polling forever.
     *
     * Progress is capped at 99 on purpose: media3 1.3.1's progress
     * reporting is known to be a little inaccurate near the end (it can
     * jump straight from ~70% to done), so treating "100%" as meaning
     * "finished" would be a lie the UI can't back up. The actual finish
     * signal is always `onCompleted`/`onError`, never the percentage.
     */
    fun resize(request: ResizeRequest, onProgress: (Int) -> Unit, onDone: (ResizeResult) -> Unit): Transformer? {
        // HARDENING: everything below — building effects, resolving the
        // watermark bitmap, and Transformer.start() itself — used to run
        // completely unguarded. Any synchronous exception here (revoked URI
        // permission on the watermark image, an invalid/zero target size,
        // a device rejecting the encoder config before the async pipeline
        // even begins) crashed the whole app instead of surfacing as a
        // normal failed export. Transformer.Listener.onError below only
        // catches errors *after* the pipeline is running, so it can't help
        // with this. Wrapping the setup and routing failures through the
        // exact same ResizeResult.Failure path onError already uses keeps
        // failure handling in exactly one place, with identical UX: the
        // caller just sees "export failed" instead of the app dying.
        return try {
            resizeInternal(request, onProgress, onDone)
        } catch (e: Exception) {
            onDone(ResizeResult.Failure(e.message ?: "Gagal memulai export"))
            null
        }
    }

    private fun resizeInternal(request: ResizeRequest, onProgress: (Int) -> Unit, onDone: (ResizeResult) -> Unit): Transformer {
        val (targetWidth, targetHeight) = computeTargetDimensions(request)

        val videoEffects = mutableListOf<androidx.media3.common.Effect>()

        val presentation = if (targetWidth > 0 && targetHeight > 0) {
            val layout = when (request.resizeMode) {
                ResizeMode.CROP -> Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                ResizeMode.STRETCH -> Presentation.LAYOUT_STRETCH_TO_FIT
            }
            Presentation.createForWidthAndHeight(targetWidth, targetHeight, layout)
        } else {
            null
        }
        presentation?.let { videoEffects.add(it) }

        // Rotation + flip share a single GL transform matrix, so both fold
        // into one ScaleAndRotateTransformation instead of two separate
        // Effect entries — same one-pass idea as Presentation above handling
        // crop/stretch together. scaleX/scaleY of -1 mirrors that axis; a
        // flip is applied even when rotation is NONE, so the condition below
        // checks both instead of gating flip on rotation being set.
        if (request.rotation != RotationOption.NONE || request.flip != FlipOption.NONE) {
            val scaleX = if (request.flip == FlipOption.HORIZONTAL) -1f else 1f
            val scaleY = if (request.flip == FlipOption.VERTICAL) -1f else 1f
            videoEffects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(request.rotation.degrees)
                    .setScale(scaleX, scaleY)
                    .build()
            )
        }

        // Watermark/logo overlay — same OverlayEffect + BitmapOverlay pipeline
        // Google's own Transformer demo app uses for picture-in-picture/logo
        // overlays. Applied AFTER presentation/rotation so the watermark sits
        // on top of the already-cropped/rotated frame, not the raw source.
        val watermarkUri = request.watermarkUri
        if (watermarkUri != null) {
            videoEffects.add(buildWatermarkOverlay(request, watermarkUri))
        }

        // Caption overlay — reuses the exact same OverlayEffect/BitmapOverlay
        // pipeline as the watermark above, just fed a bitmap this class
        // renders itself (white text + black outline for legibility over any
        // footage) instead of a picked image. Stacking this as its own
        // OverlayEffect entry alongside the watermark one, rather than
        // trying to merge them into a single overlay, mirrors how
        // presentation/rotation/watermark already chain as independent
        // Effect entries in this same list.
        val captionText = request.captionText?.trim()
        if (!captionText.isNullOrEmpty()) {
            videoEffects.add(buildCaptionOverlay(request, captionText))
        }

        // Frame rate: FrameDropEffect.createDefaultFrameDropEffect only
        // needs the *target* fps — it decides per-frame whether to keep or
        // drop based on presentation timestamps, so no separate "read the
        // source's frame rate first" step is needed here. Added last so it
        // operates on the same already-cropped/rotated/watermarked stream as
        // every other effect above.
        if (request.frameRate != FrameRateOption.ORIGINAL) {
            videoEffects.add(FrameDropEffect.createDefaultFrameDropEffect(request.frameRate.fps.toFloat()))
        }

        var mediaItemBuilder = MediaItem.Builder().setUri(request.sourceUri)
        if (request.trimEndMs > request.trimStartMs) {
            mediaItemBuilder = mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(request.trimStartMs)
                    .setEndPositionMs(request.trimEndMs)
                    .build()
            )
        }

        val editedMediaItemBuilder = EditedMediaItem.Builder(mediaItemBuilder.build())
            .setRemoveAudio(request.muteAudio)
        if (videoEffects.isNotEmpty()) {
            editedMediaItemBuilder.setEffects(Effects(emptyList(), videoEffects))
        }
        val editedMediaItem = editedMediaItemBuilder.build()

        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        lateinit var transformer: Transformer
        val progressHolder = androidx.media3.transformer.ProgressHolder()
        val pollProgress = object : Runnable {
            override fun run() {
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress.coerceIn(0, 99))
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    mainHandler.postDelayed(this, 400)
                }
            }
        }

        // Bitrate/quality: ORIGINAL leaves Transformer's default encoder
        // selection untouched (exact same behavior as before this feature
        // existed). Otherwise we hand it an explicit target bitrate via
        // DefaultEncoderFactory/VideoEncoderSettings. setEnableFallback(true)
        // is important here: not every device's hardware encoder accepts an
        // arbitrary bitrate, and without fallback enabled a device that
        // rejects the exact requested value fails the whole export instead
        // of quietly picking the closest value it can actually do.
        val targetBitrateBps = requestedBitrateBps(request, targetWidth, targetHeight)
        val transformerBuilder = Transformer.Builder(context)
        if (targetBitrateBps != null) {
            transformerBuilder.setEncoderFactory(
                androidx.media3.transformer.DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        androidx.media3.transformer.VideoEncoderSettings.Builder()
                            .setBitrate(targetBitrateBps)
                            .build()
                    )
                    .setEnableFallback(true)
                    .build()
            )
        }

        transformer = transformerBuilder
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                    mainHandler.removeCallbacksAndMessages(null)
                    onDone(ResizeResult.Success(request.outputFile))
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    mainHandler.removeCallbacksAndMessages(null)
                    onDone(ResizeResult.Failure(exportException.message ?: "Export failed"))
                }
            })
            .build()

        transformer.start(editedMediaItem, request.outputFile.absolutePath)
        mainHandler.post(pollProgress)
        return transformer
    }

    /**
     * Compressor tab entry point — re-encodes [request.sourceUri] as H.265
     * at the same resolution (no crop/aspect/watermark/caption pipeline),
     * targeting a much smaller bitrate than H.264 needs for the same
     * visual quality. See [CompressionLevel]'s doc comment for the honest
     * "visually transparent, not literally lossless" framing. Same
     * try/catch-wrapped pattern as [resize] so a synchronous setup failure
     * (bad Uri, encoder rejects config before the async pipeline starts)
     * surfaces as an ordinary [ResizeResult.Failure] instead of crashing.
     */
    fun compress(request: CompressRequest, onProgress: (Int) -> Unit, onDone: (ResizeResult) -> Unit): Transformer? {
        return try {
            compressInternal(request, onProgress, onDone)
        } catch (e: Exception) {
            onDone(ResizeResult.Failure(e.message ?: "Gagal memulai kompresi"))
            null
        }
    }

    private fun compressInternal(request: CompressRequest, onProgress: (Int) -> Unit, onDone: (ResizeResult) -> Unit): Transformer {
        // Encoders generally require even dimensions — only reached for
        // odd source dimensions, since no crop/resize is otherwise applied.
        val w = if (request.sourceWidth % 2 != 0) request.sourceWidth + 1 else request.sourceWidth
        val h = if (request.sourceHeight % 2 != 0) request.sourceHeight + 1 else request.sourceHeight
        val videoEffects = mutableListOf<androidx.media3.common.Effect>()
        if ((w != request.sourceWidth || h != request.sourceHeight) && w > 0 && h > 0) {
            videoEffects.add(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT))
        }

        val targetBitrateBps = computeCompressTargetBitrateBps(request, w, h)
        val transformerBuilder = Transformer.Builder(context)
            .setTransformationRequest(
                androidx.media3.transformer.TransformationRequest.Builder()
                    .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H265)
                    .build()
            )
            .setEncoderFactory(
                androidx.media3.transformer.DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        androidx.media3.transformer.VideoEncoderSettings.Builder()
                            .setBitrate(targetBitrateBps)
                            .build()
                    )
                    // A device without an H.265 hardware encoder falls back to
                    // whatever DefaultEncoderFactory picks instead, same
                    // safety net as [resize]'s bitrate path.
                    .setEnableFallback(true)
                    .build()
            )

        var mediaItemBuilder = MediaItem.Builder().setUri(request.sourceUri)
        if (request.trimEndMs > request.trimStartMs) {
            mediaItemBuilder = mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(request.trimStartMs)
                    .setEndPositionMs(request.trimEndMs)
                    .build()
            )
        }
        val editedMediaItemBuilder = EditedMediaItem.Builder(mediaItemBuilder.build())
            .setRemoveAudio(request.muteAudio)
        if (videoEffects.isNotEmpty()) {
            editedMediaItemBuilder.setEffects(Effects(emptyList(), videoEffects))
        }
        val editedMediaItem = editedMediaItemBuilder.build()

        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        lateinit var transformer: Transformer
        val progressHolder = androidx.media3.transformer.ProgressHolder()
        val pollProgress = object : Runnable {
            override fun run() {
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress.coerceIn(0, 99))
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    mainHandler.postDelayed(this, 400)
                }
            }
        }

        transformer = transformerBuilder
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                    mainHandler.removeCallbacksAndMessages(null)
                    onDone(ResizeResult.Success(request.outputFile))
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    mainHandler.removeCallbacksAndMessages(null)
                    onDone(ResizeResult.Failure(exportException.message ?: "Export failed"))
                }
            })
            .build()

        transformer.start(editedMediaItem, request.outputFile.absolutePath)
        mainHandler.post(pollProgress)
        return transformer
    }

    /**
     * Builds the [OverlayEffect] that draws a static image (a watermark, or
     * a caption rendered to a bitmap by [buildCaptionOverlay]) on top of
     * every output frame at a fixed corner/position, scale, and opacity for
     * the whole clip duration.
     *
     * NDC anchor pairs below follow the pattern from Google's own Media3
     * Transformer sample code: `setOverlayFrameAnchor` picks a point *within
     * the overlay image itself*, `setBackgroundFrameAnchor` picks the
     * matching point on the video frame that the overlay's anchor point
     * gets pinned to. A small inset (0.86 instead of 1.0) keeps the overlay
     * from being flush against the very edge of the frame.
     */
    private fun buildImageOverlay(uri: Uri, position: WatermarkPosition, scalePercent: Int, opacityPercent: Int): OverlayEffect {
        val inset = 0.86f
        val (overlayAnchorX, overlayAnchorY, bgAnchorX, bgAnchorY) = when (position) {
            WatermarkPosition.TOP_LEFT -> Quad(-1f, 1f, -inset, inset)
            WatermarkPosition.TOP_RIGHT -> Quad(1f, 1f, inset, inset)
            WatermarkPosition.BOTTOM_LEFT -> Quad(-1f, -1f, -inset, -inset)
            WatermarkPosition.BOTTOM_RIGHT -> Quad(1f, -1f, inset, -inset)
            WatermarkPosition.CENTER -> Quad(0f, 0f, 0f, 0f)
        }
        val scale = (scalePercent.coerceIn(5, 60)) / 100f
        val alpha = (opacityPercent.coerceIn(5, 100)) / 100f

        val overlaySettings = OverlaySettings.Builder()
            .setOverlayFrameAnchor(overlayAnchorX, overlayAnchorY)
            .setBackgroundFrameAnchor(bgAnchorX, bgAnchorY)
            .setScale(scale, scale)
            .setAlphaScale(alpha)
            .build()

        val overlay = BitmapOverlay.createStaticBitmapOverlay(context, uri, overlaySettings)
        return OverlayEffect(ImmutableList.of(overlay))
    }

    private fun buildWatermarkOverlay(request: ResizeRequest, watermarkUri: Uri): OverlayEffect =
        buildImageOverlay(watermarkUri, request.watermarkPosition, request.watermarkScalePercent, request.watermarkOpacityPercent)

    /**
     * Renders [text] to a small transparent-background PNG in the app's
     * cache dir, then hands it to [buildImageOverlay] exactly like a picked
     * watermark image — reusing the same already-working overlay pipeline
     * rather than a separate Media3 text-rendering API. Fixed white-text/
     * black-outline style (readable over both light and dark footage) and a
     * fixed 40% frame-width scale, matching how the watermark feature
     * itself started as a simple on/off toggle before scale/opacity
     * sliders were added — a caption can grow the same way later if it
     * turns out to need per-caption sizing.
     */
    private fun buildCaptionOverlay(request: ResizeRequest, text: String): OverlayEffect {
        val bitmap = renderCaptionBitmap(text)
        val file = File(context.cacheDir, "caption_overlay_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        val captionUri = Uri.fromFile(file)
        return buildImageOverlay(captionUri, request.captionPosition, scalePercent = 40, opacityPercent = 100)
    }

    private fun renderCaptionBitmap(text: String): Bitmap {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 64f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }
        val strokePaint = Paint(fillPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            color = android.graphics.Color.BLACK
        }
        val padding = 24f
        val textWidth = fillPaint.measureText(text)
        val fm = fillPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val bmpWidth = (textWidth + padding * 2).toInt().coerceAtLeast(1)
        val bmpHeight = (textHeight + padding * 2).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val baseline = padding - fm.ascent
        canvas.drawText(text, padding, baseline, strokePaint)
        canvas.drawText(text, padding, baseline, fillPaint)
        return bitmap
    }

    /** Small tuple purely to keep [buildWatermarkOverlay]'s anchor math on one readable line each. */
    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)

    /**
     * Resolves the H.265 target bitrate for [compressInternal]: the
     * chosen [CompressionLevel]'s own bits-per-pixel-per-frame target
     * multiplied by the source's REAL fps (Batch 32 — [request.sourceFps],
     * falling back to [CompressionLevel.ASSUMED_FPS] only if it couldn't be
     * probed), capped so it never exceeds ~85% of the source's own
     * estimated bitrate (see [estimateSourceBitrateBps]) — the actual
     * guarantee that compressing an already-efficient source doesn't grow
     * it.
     */
    private fun computeCompressTargetBitrateBps(request: CompressRequest, w: Int, h: Int): Int {
        val fps = if (request.sourceFps > 0) request.sourceFps else CompressionLevel.ASSUMED_FPS
        val levelTargetBps = (w.toDouble() * h.toDouble() * fps * request.level.targetBitsPerPixelPerFrame).toLong()
        val sourceBps = estimateSourceBitrateBps(request)
        val cappedBps = if (sourceBps != null && sourceBps > 0) minOf(levelTargetBps, (sourceBps * 0.85).toLong()) else levelTargetBps
        return cappedBps.coerceIn(MIN_BITRATE_KBPS.toLong() * 1000, MAX_BITRATE_KBPS.toLong() * 1000).toInt()
    }

    /**
     * Rough estimate of the *source* file's own video bitrate (bits/sec),
     * from its total file size and duration minus its audio track's
     * bitrate (Batch 33 — real probed value via [CompressRequest.sourceHasAudio]/
     * [CompressRequest.sourceAudioBitrateBps] when available; a source
     * with no audio track at all correctly subtracts 0 instead of the old
     * flat 128kbps assumption, which used to over-tighten the cap on
     * silent/audio-less sources). Not exact (container overhead/VBR
     * variance ignored) — it only needs to be good enough for a one-sided
     * safety cap, not a precise figure.
     */
    private fun estimateSourceBitrateBps(request: CompressRequest): Long? {
        if (request.sourceDurationMs <= 0 || request.sourceFileSizeBytes <= 0) return null
        val totalBps = (request.sourceFileSizeBytes * 8.0) / (request.sourceDurationMs / 1000.0)
        val audioBps = when {
            request.muteAudio || !request.sourceHasAudio -> 0.0
            request.sourceAudioBitrateBps > 0 -> request.sourceAudioBitrateBps.toDouble()
            else -> 128_000.0
        }
        return (totalBps - audioBps).toLong().coerceAtLeast(0L).takeIf { it > 0 }
    }

    /**
     * Resolves the requested video-encoder bitrate in bits per second for
     * this request, or null to leave the encoder's own default alone
     * (ORIGINAL preset — identical to pre-quality-feature behavior).
     */
    private fun requestedBitrateBps(request: ResizeRequest, targetWidth: Int, targetHeight: Int): Int? {
        if (request.quality == QualityOption.ORIGINAL) return null
        if (request.quality == QualityOption.CUSTOM) {
            val kbps = request.customBitrateKbps ?: return null
            return (kbps.coerceIn(MIN_BITRATE_KBPS, MAX_BITRATE_KBPS)) * 1000
        }
        val w = if (targetWidth > 0) targetWidth else request.sourceWidth
        val h = if (targetHeight > 0) targetHeight else request.sourceHeight
        if (w <= 0 || h <= 0) return null
        val bps = (w.toLong() * h.toLong() * request.quality.bitsPerPixelPerSecond).toInt()
        return bps.coerceIn(MIN_BITRATE_KBPS * 1000, MAX_BITRATE_KBPS * 1000)
    }

    companion object {
        /** Hard floor/ceiling so a bad custom value can't produce an unusable file or an encoder crash. */
        const val MIN_BITRATE_KBPS = 250
        const val MAX_BITRATE_KBPS = 50_000

        /** Resolves the final output width/height (0,0 means "keep original"). */
        fun computeTargetDimensions(request: ResizeRequest): Pair<Int, Int> {
            if (request.resolution == ResolutionOption.CUSTOM) {
                val w = (request.customWidth ?: request.sourceWidth).let { if (it % 2 != 0) it + 1 else it }
                val h = (request.customHeight ?: request.sourceHeight).let { if (it % 2 != 0) it + 1 else it }
                return w.coerceAtLeast(2) to h.coerceAtLeast(2)
            }

            if (request.aspectRatio == AspectRatioOption.ORIGINAL && request.resolution == ResolutionOption.ORIGINAL) {
                return 0 to 0
            }

            val ratioW: Int
            val ratioH: Int
            if (request.aspectRatio == AspectRatioOption.ORIGINAL) {
                ratioW = request.sourceWidth
                ratioH = request.sourceHeight
            } else {
                ratioW = request.aspectRatio.widthRatio
                ratioH = request.aspectRatio.heightRatio
            }

            val height = if (request.resolution == ResolutionOption.ORIGINAL) {
                request.sourceHeight
            } else {
                request.resolution.targetHeight
            }

            val width = (height * ratioW.toDouble() / ratioH.toDouble()).toInt().let {
                // Encoders generally require even dimensions.
                if (it % 2 != 0) it + 1 else it
            }
            val evenHeight = if (height % 2 != 0) height + 1 else height

            return width to evenHeight
        }

        /**
         * Rough estimated output file size in bytes, for the "Perkiraan
         * ukuran" label in the UI. This is intentionally approximate — real
         * encoders vary output somewhat around a requested bitrate — but
         * gives a novice user a ballpark figure *before* they commit to a
         * multi-minute export, which is the whole point of exposing a
         * quality control at all.
         *
         * durationMs should already reflect the trim range, not the full
         * source clip.
         */
        fun estimateOutputSizeBytes(
            request: ResizeRequest,
            durationMs: Long
        ): Long? {
            if (durationMs <= 0) return null
            val (targetWidth, targetHeight) = computeTargetDimensions(request)
            val videoBitrateBps = when {
                request.quality == QualityOption.ORIGINAL -> return null // unknown — encoder default
                request.quality == QualityOption.CUSTOM ->
                    (request.customBitrateKbps ?: return null).coerceIn(MIN_BITRATE_KBPS, MAX_BITRATE_KBPS) * 1000
                else -> {
                    val w = if (targetWidth > 0) targetWidth else request.sourceWidth
                    val h = if (targetHeight > 0) targetHeight else request.sourceHeight
                    if (w <= 0 || h <= 0) return null
                    (w.toLong() * h.toLong() * request.quality.bitsPerPixelPerSecond).toInt()
                        .coerceIn(MIN_BITRATE_KBPS * 1000, MAX_BITRATE_KBPS * 1000)
                }
            }
            // Rough constant audio bitrate assumption (AAC), 0 if muted.
            val audioBitrateBps = if (request.muteAudio) 0 else 128_000
            val totalBitsPerSecond = (videoBitrateBps + audioBitrateBps).toLong()
            val seconds = durationMs / 1000.0
            return (totalBitsPerSecond * seconds / 8.0).toLong()
        }

        /**
         * Inverse of [estimateOutputSizeBytes]: given a desired total output
         * file size, solves for the video bitrate (kbps) that would roughly
         * produce it over [durationMs]. Backs the "Ukuran target (MB)"
         * quality mode in the UI — the caller computes this once and feeds
         * the result back in as an ordinary QualityOption.CUSTOM +
         * customBitrateKbps request, so the export pipeline itself needs no
         * separate "target size" code path at all.
         *
         * Returns null when there's no valid video bitrate that fits: a
         * non-positive size/duration, or a target so small the assumed
         * 128kbps audio track alone would already exceed it.
         */
        fun requiredBitrateKbpsForTargetSize(targetSizeMb: Double, durationMs: Long, muteAudio: Boolean): Int? {
            if (targetSizeMb <= 0.0 || durationMs <= 0) return null
            val audioBitrateBps = if (muteAudio) 0 else 128_000
            val seconds = durationMs / 1000.0
            val totalBitsPerSecond = (targetSizeMb * 1024.0 * 1024.0 * 8.0) / seconds
            val videoBitrateBps = totalBitsPerSecond - audioBitrateBps
            if (videoBitrateBps <= 0) return null
            return (videoBitrateBps / 1000.0).toInt().coerceIn(MIN_BITRATE_KBPS, MAX_BITRATE_KBPS)
        }

        /**
         * Estimated output size (bytes) for the Compressor tab's
         * before/after preview — same formula [VideoResizer.compressInternal]
         * itself uses to pick the real target bitrate, kept in the
         * companion so CompressorScreen can call it before an export even
         * starts (no VideoResizer instance/Context needed).
         */
        fun estimateCompressedSizeBytes(
            sourceWidth: Int,
            sourceHeight: Int,
            sourceDurationMs: Long,
            sourceFileSizeBytes: Long,
            clipDurationMs: Long,
            muteAudio: Boolean,
            level: CompressionLevel,
            /** Real source fps (Batch 32) — see [CompressRequest.sourceFps]; 0/negative falls back to [CompressionLevel.ASSUMED_FPS], same as [computeCompressTargetBitrateBps]. */
            sourceFps: Int = 0,
            /** See [CompressRequest.sourceHasAudio] (Batch 33). */
            sourceHasAudio: Boolean = true,
            /** See [CompressRequest.sourceAudioBitrateBps] (Batch 33). 0/negative falls back to 128kbps, only when [sourceHasAudio] is true. */
            sourceAudioBitrateBps: Int = 0
        ): Long? {
            if (clipDurationMs <= 0) return null
            val w = if (sourceWidth % 2 != 0) sourceWidth + 1 else sourceWidth
            val h = if (sourceHeight % 2 != 0) sourceHeight + 1 else sourceHeight
            if (w <= 0 || h <= 0) return null
            val fps = if (sourceFps > 0) sourceFps else CompressionLevel.ASSUMED_FPS
            val levelTargetBps = (w.toDouble() * h.toDouble() * fps * level.targetBitsPerPixelPerFrame)
            val audioBps = when {
                muteAudio || !sourceHasAudio -> 0.0
                sourceAudioBitrateBps > 0 -> sourceAudioBitrateBps.toDouble()
                else -> 128_000.0
            }
            val sourceVideoBps = if (sourceDurationMs > 0 && sourceFileSizeBytes > 0) {
                (((sourceFileSizeBytes * 8.0) / (sourceDurationMs / 1000.0)) - audioBps).coerceAtLeast(0.0)
            } else null
            val cappedVideoBps = if (sourceVideoBps != null && sourceVideoBps > 0) minOf(levelTargetBps, sourceVideoBps * 0.85) else levelTargetBps
            val finalVideoBps = cappedVideoBps.coerceIn(MIN_BITRATE_KBPS * 1000.0, MAX_BITRATE_KBPS * 1000.0)
            val seconds = clipDurationMs / 1000.0
            return ((finalVideoBps + audioBps) * seconds / 8.0).toLong()
        }
    }
}
