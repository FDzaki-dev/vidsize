package com.example.videoresizer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import kotlin.math.roundToInt
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.videoresizer.ui.theme.VideoResizerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

private enum class ThemePreference { SYSTEM, LIGHT, DARK, MIDNIGHT_NEON, WARM_PAPER, MIDNIGHT_BLUE_GLASS }
private enum class Screen { MAIN, STUDIO, BATCH, GIF, COMPRESSOR }

class MainActivity : ComponentActivity() {

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var themePref by remember { mutableStateOf(ThemePreference.MIDNIGHT_BLUE_GLASS) }
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val resolvedStyle = when (themePref) {
                ThemePreference.SYSTEM -> if (systemDark) com.example.videoresizer.ui.theme.AppThemeStyle.MIDNIGHT_BLUE_GLASS else com.example.videoresizer.ui.theme.AppThemeStyle.LIGHT
                ThemePreference.LIGHT -> com.example.videoresizer.ui.theme.AppThemeStyle.LIGHT
                ThemePreference.DARK -> com.example.videoresizer.ui.theme.AppThemeStyle.DARK
                ThemePreference.MIDNIGHT_NEON -> com.example.videoresizer.ui.theme.AppThemeStyle.MIDNIGHT_NEON
                ThemePreference.WARM_PAPER -> com.example.videoresizer.ui.theme.AppThemeStyle.WARM_PAPER
                ThemePreference.MIDNIGHT_BLUE_GLASS -> com.example.videoresizer.ui.theme.AppThemeStyle.MIDNIGHT_BLUE_GLASS
            }

            VideoResizerTheme(style = resolvedStyle, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    VideoResizerApp(themePref = themePref, onThemePrefChange = { themePref = it })
                }
            }
        }
    }
}

private fun formatSeconds(ms: Long): String {
    val totalSeconds = ms / 1000.0
    return String.format(Locale.US, "%.1fs", totalSeconds)
}

/**
 * Small square preview thumbnail for a picked watermark image. Deliberately
 * hand-rolled with [BitmapFactory] instead of pulling in an image-loading
 * library (Coil/Glide) just for one 56dp thumbnail — this project has zero
 * such dependencies today, and adding one for a single use site isn't worth
 * the extra Gradle dependency surface / APK size.
 */
@Composable
private fun AsyncThumbnail(uri: Uri) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/** Human-readable file size (e.g. "12.3 MB"), used for the quality/bitrate size estimate. */
private fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

/** Holds settings that Studio's "Edit ulang" can hand back to the main screen. */
private data class PrefillSettings(
    val uri: Uri,
    val aspectRatio: AspectRatioOption,
    val resolution: ResolutionOption,
    val rotation: RotationOption,
    val muteAudio: Boolean,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val resizeMode: ResizeMode,
    val customWidth: Int?,
    val customHeight: Int?,
    val quality: QualityOption,
    val customBitrateKbps: Int?,
    val watermarkUri: Uri?,
    val watermarkPosition: WatermarkPosition,
    val watermarkOpacityPercent: Int,
    val watermarkScalePercent: Int,
    val captionText: String?,
    val captionPosition: WatermarkPosition,
    val flip: FlipOption,
    val frameRate: FrameRateOption
)

/**
 * GifScreen's counterpart to [PrefillSettings] — deliberately a separate,
 * much smaller data class rather than reusing/extending PrefillSettings:
 * GIF export has its own narrower setting set (fps/width, no
 * resolution/quality/watermark/etc.) and its own screen, so there's no
 * shared structure worth factoring out beyond the source video identity.
 */
private data class GifPrefill(
    val uri: Uri,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val fps: Int,
    val targetWidth: Int
)

/**
 * iOS-style push/pop transition constants (Batch 42). Screen switches
 * used to be an instant `if (condition) { Screen(...) }` cut — zero
 * animation, which reads as abrupt/unpolished compared to iOS's
 * UINavigationController push (new screen slides in from the right
 * over ~350ms on an ease-in-out curve, slightly faster on the way back).
 * These are shared by every full-screen overlay (Batch/GIF/Compressor/
 * Studio) so the whole app feels consistent, not just one screen.
 */
private val IosPushEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private const val IOS_PUSH_MS = 350
private const val IOS_POP_MS = 300

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoResizerApp(
    themePref: ThemePreference,
    onThemePrefChange: (ThemePreference) -> Unit
) {
    var screen by remember { mutableStateOf(Screen.MAIN) }
    var prefill by remember { mutableStateOf<PrefillSettings?>(null) }
    var gifPrefill by remember { mutableStateOf<GifPrefill?>(null) }
    var studioMessage by remember { mutableStateOf<String?>(null) }

    // BUG FIX: this used to be a `when (screen)` that fully unmounted
    // ResizerScreen whenever Studio or Batch was open. Every `remember`-held
    // piece of state in ResizerScreen (picked video, aspect ratio,
    // resolution, quality, watermark, trim range, everything) lives only as
    // long as that composable stays in the composition — removing it from
    // the tree throws all of it away, so tapping Studio/Batch by accident
    // after carefully configuring an export reset the whole screen back to
    // "nothing picked yet" on return. It also meant an in-progress export's
    // coroutine (a child of ResizerScreen's own rememberCoroutineScope) got
    // cancelled if the screen was unmounted mid-export.
    //
    // Fix: ResizerScreen now stays permanently composed underneath, and
    // Studio/Batch are drawn as opaque full-screen overlays on top of it
    // when active — same visual result, but nothing underneath ever loses
    // its state or gets its coroutines torn down.
    Box(modifier = Modifier.fillMaxSize()) {
        ResizerScreen(
            themePref = themePref,
            onThemePrefChange = onThemePrefChange,
            onOpenStudio = { screen = Screen.STUDIO },
            onOpenBatch = { screen = Screen.BATCH },
            onOpenGif = { screen = Screen.GIF },
            onOpenCompressor = { screen = Screen.COMPRESSOR },
            isForeground = screen == Screen.MAIN,
            prefill = prefill,
            onPrefillConsumed = { prefill = null },
            studioMessage = studioMessage,
            onStudioMessageShown = { studioMessage = null }
        )
        AnimatedVisibility(
            visible = screen == Screen.BATCH,
            enter = slideInHorizontally(
                animationSpec = tween(IOS_PUSH_MS, easing = IosPushEasing),
                initialOffsetX = { fullWidth -> fullWidth }
            ) + fadeIn(tween(IOS_PUSH_MS, easing = IosPushEasing)),
            exit = slideOutHorizontally(
                animationSpec = tween(IOS_POP_MS, easing = IosPushEasing),
                targetOffsetX = { fullWidth -> fullWidth }
            ) + fadeOut(tween(IOS_POP_MS, easing = IosPushEasing))
        ) {
            BatchScreen(
                onBack = { screen = Screen.MAIN }
            )
        }
        AnimatedVisibility(
            visible = screen == Screen.GIF,
            enter = slideInHorizontally(
                animationSpec = tween(IOS_PUSH_MS, easing = IosPushEasing),
                initialOffsetX = { fullWidth -> fullWidth }
            ) + fadeIn(tween(IOS_PUSH_MS, easing = IosPushEasing)),
            exit = slideOutHorizontally(
                animationSpec = tween(IOS_POP_MS, easing = IosPushEasing),
                targetOffsetX = { fullWidth -> fullWidth }
            ) + fadeOut(tween(IOS_POP_MS, easing = IosPushEasing))
        ) {
            GifScreen(
                onBack = { screen = Screen.MAIN },
                prefill = gifPrefill,
                onPrefillConsumed = { gifPrefill = null }
            )
        }
        AnimatedVisibility(
            visible = screen == Screen.COMPRESSOR,
            enter = slideInHorizontally(
                animationSpec = tween(IOS_PUSH_MS, easing = IosPushEasing),
                initialOffsetX = { fullWidth -> fullWidth }
            ) + fadeIn(tween(IOS_PUSH_MS, easing = IosPushEasing)),
            exit = slideOutHorizontally(
                animationSpec = tween(IOS_POP_MS, easing = IosPushEasing),
                targetOffsetX = { fullWidth -> fullWidth }
            ) + fadeOut(tween(IOS_POP_MS, easing = IosPushEasing))
        ) {
            CompressorScreen(
                onBack = { screen = Screen.MAIN }
            )
        }
        AnimatedVisibility(
            visible = screen == Screen.STUDIO,
            enter = slideInHorizontally(
                animationSpec = tween(IOS_PUSH_MS, easing = IosPushEasing),
                initialOffsetX = { fullWidth -> fullWidth }
            ) + fadeIn(tween(IOS_PUSH_MS, easing = IosPushEasing)),
            exit = slideOutHorizontally(
                animationSpec = tween(IOS_POP_MS, easing = IosPushEasing),
                targetOffsetX = { fullWidth -> fullWidth }
            ) + fadeOut(tween(IOS_POP_MS, easing = IosPushEasing))
        ) {
            StudioScreen(
                onBack = { screen = Screen.MAIN },
                onEditAgain = { entry ->
                    if (entry.kind == "GIF") {
                        // Separate prefill path (see GifPrefill doc comment)
                        // — a GIF entry has none of the resize-specific
                        // fields PrefillSettings below reads, so routing it
                        // through PrefillSettings/Screen.MAIN would reopen
                        // the wrong screen with mostly-default settings.
                        gifPrefill = GifPrefill(
                            uri = Uri.parse(entry.sourceUri),
                            trimStartMs = entry.trimStartMs,
                            trimEndMs = entry.trimEndMs,
                            fps = entry.gifFps.takeIf { it > 0 } ?: 10,
                            targetWidth = entry.gifWidthPx.takeIf { it > 0 } ?: 360
                        )
                        screen = Screen.GIF
                        return@StudioScreen
                    }
                    prefill = PrefillSettings(
                        uri = Uri.parse(entry.sourceUri),
                        aspectRatio = AspectRatioOption.ENTRIES.firstOrNull { it.name == entry.aspectRatioName } ?: AspectRatioOption.ORIGINAL,
                        resolution = ResolutionOption.ENTRIES.firstOrNull { it.name == entry.resolutionName } ?: ResolutionOption.ORIGINAL,
                        rotation = RotationOption.ENTRIES.firstOrNull { it.name == entry.rotationName } ?: RotationOption.NONE,
                        muteAudio = entry.muteAudio,
                        trimStartMs = entry.trimStartMs,
                        trimEndMs = entry.trimEndMs,
                        resizeMode = ResizeMode.ENTRIES.firstOrNull { it.name == entry.resizeModeName } ?: ResizeMode.CROP,
                        customWidth = entry.customWidth,
                        customHeight = entry.customHeight,
                        quality = QualityOption.ENTRIES.firstOrNull { it.name == entry.qualityName } ?: QualityOption.ORIGINAL,
                        customBitrateKbps = entry.customBitrateKbps,
                        watermarkUri = entry.watermarkUri?.let { Uri.parse(it) },
                        watermarkPosition = WatermarkPosition.ENTRIES.firstOrNull { it.name == entry.watermarkPositionName } ?: WatermarkPosition.BOTTOM_RIGHT,
                        watermarkOpacityPercent = entry.watermarkOpacityPercent,
                        watermarkScalePercent = entry.watermarkScalePercent,
                        captionText = entry.captionText,
                        captionPosition = WatermarkPosition.ENTRIES.firstOrNull { it.name == entry.captionPositionName } ?: WatermarkPosition.BOTTOM_RIGHT,
                        flip = FlipOption.ENTRIES.firstOrNull { it.name == entry.flipName } ?: FlipOption.NONE,
                        frameRate = FrameRateOption.ENTRIES.firstOrNull { it.name == entry.frameRateName } ?: FrameRateOption.ORIGINAL
                    )
                    screen = Screen.MAIN
                },
                onEditFailed = {
                    studioMessage = "Video sumber tidak lagi bisa diakses (mungkin sudah dihapus/dipindah)."
                    screen = Screen.MAIN
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ResizerScreen(
    themePref: ThemePreference,
    onThemePrefChange: (ThemePreference) -> Unit,
    onOpenStudio: () -> Unit,
    onOpenBatch: () -> Unit,
    onOpenGif: () -> Unit,
    onOpenCompressor: () -> Unit,
    isForeground: Boolean,
    prefill: PrefillSettings?,
    onPrefillConsumed: () -> Unit,
    studioMessage: String?,
    onStudioMessageShown: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // In-app updater (AppUpdater.kt / Batch 21). checkingUpdate drives the
    // top-bar icon's own tiny progress ring; updateResult drives the dialog
    // (null = nothing to show, dismissed by user or auto-cleared on
    // UpToDate). downloadProgress is null while idle, -1f for indeterminate
    // (server sent no Content-Length), 0f..1f while downloading.
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<AppUpdater.CheckResult?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    // PERF: mutableStateOf<Long/Int>() boxes every value on write. These are
    // read constantly (every OptionSection, the trim math, the resize
    // button) across a screen with high recomposition churn from dragging,
    // so the specialized mutableLongStateOf/mutableIntStateOf primitives
    // (which store an unboxed long/int under the hood) avoid that per-write
    // allocation.
    var durationMs by remember { mutableLongStateOf(0L) }
    var sourceWidth by remember { mutableIntStateOf(0) }
    var sourceHeight by remember { mutableIntStateOf(0) }
    var aspectRatio by remember { mutableStateOf(AspectRatioOption.ORIGINAL) }
    var resolution by remember { mutableStateOf(ResolutionOption.ORIGINAL) }
    var resizeMode by remember { mutableStateOf(ResizeMode.CROP) }
    var rotation by remember { mutableStateOf(RotationOption.NONE) }
    // Flip/frame-rate: independent output-transform controls alongside
    // rotation. Kept as separate state (not folded into `rotation`) since
    // flip and rotation compose freely and the UI offers them as two
    // separate chip rows.
    var flip by remember { mutableStateOf(FlipOption.NONE) }
    var frameRate by remember { mutableStateOf(FrameRateOption.ORIGINAL) }
    var muteAudio by remember { mutableStateOf(false) }
    var trimRange by remember { mutableStateOf(0f..1f) }
    var isProcessing by remember { mutableStateOf(false) }
    // UX FIX: "Processing…" used to be an indefinite spinner with no
    // feedback at all — exportProgress now drives a real percentage, and
    // activeTransformer lets the Cancel button actually stop a running
    // export instead of leaving the user stuck watching a spinner with no
    // way out.
    var exportProgress by remember { mutableIntStateOf(0) }
    var activeTransformer by remember { mutableStateOf<androidx.media3.transformer.Transformer?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    // Gallery integration: the public content:// Uri PublicMovieExporter
    // published the last export to, so "Buka di Galeri" can open it
    // directly instead of only offering the generic Share sheet.
    var galleryUri by remember { mutableStateOf<Uri?>(null) }
    // Before/after comparison: a frame from the exported result, decoded
    // from the same thumbnail file already generated for Studio history —
    // no extra extraction pass, just displayed once more here. The "before"
    // side reuses a frame already sitting in `filmstrip` (extracted from
    // the *source* video for the trim scrubber), so this whole feature
    // adds zero additional video-decoding work over what already happens.
    var resultThumbnailFile by remember { mutableStateOf<File?>(null) }
    var resultThumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // Batch 44 (Prioritas 5): this used to decodeFile() at FULL output
    // resolution (up to ~8MB as an ARGB_8888 Bitmap for a 1080p export)
    // just to show it in the small before/after preview column below —
    // now downsampled to what that preview actually needs, which alone
    // cuts the allocation by roughly an order of magnitude. Deliberately
    // NOT also calling .recycle() on the previous bitmap here (unlike
    // StudioEntryCard's onDispose below, which is safe because that row
    // has actually left composition) — this state can still be actively
    // on-screen the instant this LaunchedEffect reassigns it, and
    // recycling a Bitmap while Compose might still be mid-draw with the
    // old reference risks a hard crash for marginal benefit now that the
    // decode itself is already small.
    LaunchedEffect(resultThumbnailFile) {
        val file = resultThumbnailFile
        resultThumbnailBitmap = if (file != null && file.exists()) {
            withContext(Dispatchers.IO) {
                runCatching { decodeSampledBitmapFromFile(file.absolutePath, 480) }.getOrNull()
            }
        } else {
            null
        }
    }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showCustomResDialog by remember { mutableStateOf(false) }
    var customWidth by remember { mutableStateOf<Int?>(null) }
    var customHeight by remember { mutableStateOf<Int?>(null) }
    // Quality/bitrate control: lets the user trade file size vs visual
    // quality instead of resolution being the only size lever, plus a live
    // "Perkiraan ukuran" estimate before committing to an export.
    var quality by remember { mutableStateOf(QualityOption.ORIGINAL) }
    var showCustomBitrateDialog by remember { mutableStateOf(false) }
    var customBitrateKbps by remember { mutableStateOf<Int?>(null) }
    // "Ukuran target (MB)" — an alternate entry point into the same
    // quality=CUSTOM/customBitrateKbps fields above rather than a separate
    // ResizeRequest field: the dialog below just solves MB -> kbps via
    // VideoResizer.requiredBitrateKbpsForTargetSize and writes the result
    // into the existing customBitrateKbps state, so the export pipeline
    // needs no separate "target size" code path at all.
    var showTargetSizeDialog by remember { mutableStateOf(false) }
    var filmstrip by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    // Watermark/logo overlay — a still image, drawn in a fixed corner at a
    // fixed opacity/size for the whole clip. watermarkUri null = no watermark.
    var watermarkUri by remember { mutableStateOf<Uri?>(null) }
    var watermarkPosition by remember { mutableStateOf(WatermarkPosition.BOTTOM_RIGHT) }
    var watermarkOpacityPercent by remember { mutableFloatStateOf(70f) }
    var watermarkScalePercent by remember { mutableFloatStateOf(18f) }
    // Caption overlay — short burned-in text, same fixed-position idea as
    // the watermark above but rendered from typed text instead of a picked
    // image. captionText blank = no caption.
    var captionText by remember { mutableStateOf("") }
    var captionPosition by remember { mutableStateOf(WatermarkPosition.BOTTOM_RIGHT) }
    // One-tap "export for TikTok/IG/YouTube" preset. Selecting one just sets
    // aspect/resolution/quality to matching values below — it's a shortcut,
    // not a separate code path, so it stays in sync with manual overrides:
    // touching resolution/quality manually afterward silently clears it.
    var selectedSocialPreset by remember { mutableStateOf<SocialPreset?>(null) }
    // Progress speed/ETA: derived purely from the existing percentage feed
    // (see VideoResizer.resize's poll loop) plus a wall-clock start time, no
    // new Media3 API surface needed — elapsed/percent*(100-percent) is the
    // same rough estimator most download/export UIs use.
    var exportStartTimeMs by remember { mutableLongStateOf(0L) }
    var exportEtaSeconds by remember { mutableStateOf<Int?>(null) }
    // UX FIX: "Ganti video" used to swap the source instantly, silently
    // discarding whatever aspect ratio / resolution / trim / rotation the
    // user had already picked. This gates that action behind a confirmation
    // whenever there's actually something to lose.
    var showChangeVideoConfirm by remember { mutableStateOf(false) }
    // Batch 13: drives the custom in-app VideoPickerScreen overlay (see that
    // file) — replaces the OS Photo Picker as this screen's video-selection
    // entry point. A plain boolean overlay flag, same convention as every
    // other show*Dialog/showChangeVideoConfirm state in this composable.
    var showVideoPicker by remember { mutableStateOf(false) }
    val hasCustomizedSettings by remember {
        derivedStateOf {
            aspectRatio != AspectRatioOption.ORIGINAL ||
                resolution != ResolutionOption.ORIGINAL ||
                resizeMode != ResizeMode.CROP ||
                rotation != RotationOption.NONE ||
                flip != FlipOption.NONE ||
                frameRate != FrameRateOption.ORIGINAL ||
                muteAudio ||
                trimRange != 0f..1f ||
                quality != QualityOption.ORIGINAL ||
                watermarkUri != null
        }
    }

    // PERF: MediaMetadataRetriever.setDataSource() + extractMetadata() are
    // blocking disk/codec I/O. This used to run directly on the caller's
    // thread — fine from inside a LaunchedEffect coroutine (still Main
    // dispatcher, so still janky), but the video-picker callback below
    // called it as a *plain* synchronous function straight on the UI
    // thread, freezing the app for the full duration of metadata
    // extraction on every single video pick. Wrapping in
    // withContext(Dispatchers.IO) moves the blocking work off Main; only
    // the tiny state-assignment lambda passed to onLoaded/onFailed needs to
    // run back on Main, which callers already do implicitly by mutating
    // Compose state from a Main-dispatched coroutine.
    suspend fun loadVideoMetadata(uri: Uri, onLoaded: (Long, Int, Int) -> Unit, onFailed: () -> Unit) {
        val result = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                if (d <= 0) null else Triple(d, w, h)
            } catch (_: Exception) {
                null
            } finally {
                retriever.release()
            }
        }
        if (result != null) {
            onLoaded(result.first, result.second, result.third)
        } else {
            onFailed()
        }
    }

    // Extract filmstrip thumbnails off the main thread whenever a new video is loaded.
    LaunchedEffect(selectedUri, durationMs) {
        val uri = selectedUri
        filmstrip = emptyList()
        if (uri != null && durationMs > 0) {
            filmstrip = withContext(Dispatchers.IO) {
                FilmstripExtractor.extract(context, uri, durationMs, count = 8)
            }
        }
    }

    // Apply settings coming back from Studio's "Edit ulang", once.
    LaunchedEffect(prefill) {
        val p = prefill ?: return@LaunchedEffect
        loadVideoMetadata(
            p.uri,
            onLoaded = { d, w, h ->
                selectedUri = p.uri
                durationMs = d
                sourceWidth = w
                sourceHeight = h
                aspectRatio = p.aspectRatio
                resolution = p.resolution
                rotation = p.rotation
                flip = p.flip
                frameRate = p.frameRate
                muteAudio = p.muteAudio
                resizeMode = p.resizeMode
                customWidth = p.customWidth
                customHeight = p.customHeight
                quality = p.quality
                customBitrateKbps = p.customBitrateKbps
                watermarkUri = p.watermarkUri
                watermarkPosition = p.watermarkPosition
                watermarkOpacityPercent = p.watermarkOpacityPercent.toFloat()
                watermarkScalePercent = p.watermarkScalePercent.toFloat()
                captionText = p.captionText ?: ""
                captionPosition = p.captionPosition
                selectedSocialPreset = null
                trimRange = (p.trimStartMs.toFloat() / d).coerceIn(0f, 1f)..(p.trimEndMs.toFloat() / d).coerceIn(0f, 1f)
                resultMessage = null
                outputFile = null
                galleryUri = null
                resultThumbnailFile = null
            },
            onFailed = { resultMessage = "Video sumber tidak lagi bisa diakses (mungkin sudah dihapus/dipindah)." }
        )
        onPrefillConsumed()
    }

    LaunchedEffect(studioMessage) {
        if (studioMessage != null) {
            resultMessage = studioMessage
            onStudioMessageShown()
        }
    }

    var pendingRequest by remember { mutableStateOf<ResizeRequest?>(null) }
    var pendingThumbnailFile by remember { mutableStateOf<File?>(null) }

    // BUG FIX: this used to call notificationPermissionLauncher.launch(...)
    // on every single export, forever, as long as the permission wasn't
    // granted — so denying it once (or just not touching the system
    // dialog fast enough) meant it popped up again on every subsequent
    // export with no way to make it stop. Android's own guidance is to ask
    // at most once and respect the answer either way; the actual
    // foreground-service protection this permission enables doesn't
    // require it to be granted, so there's nothing lost by not re-asking.
    // "Asked before" is persisted in SharedPreferences so it survives
    // process death too, not just this composable's lifetime.
    val appPrefs = remember { context.getSharedPreferences("video_resizer_prefs", android.content.Context.MODE_PRIVATE) }

    // Best-effort: ask for POST_NOTIFICATIONS once so the export progress
    // notification can actually show on API 33+. Not gating anything on
    // the result — the foreground-service protection against background
    // kills works regardless of whether the notification is visible.
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* no-op either way */ }

    // Shared entry point for actually kicking off an export — used both
    // right after the button tap and after a storage-permission grant, so
    // progress/cancel wiring only lives in one place.
    fun startResize(request: ResizeRequest, thumbnailFile: File?) {
        exportProgress = 0
        exportEtaSeconds = null
        exportStartTimeMs = System.currentTimeMillis()

        // FIX: keep this process alive at foreground priority for the
        // duration of the export — see ExportForegroundService for why.
        ExportForegroundService.start(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !appPrefs.getBoolean("notif_permission_asked", false)
        ) {
            appPrefs.edit().putBoolean("notif_permission_asked", true).apply()
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        activeTransformer = runResize(
            context, scope, request, thumbnailFile,
            onProgress = { percent ->
                exportProgress = percent
                // Rough ETA from elapsed-time / percent-so-far — no extra
                // Media3 API needed, and skipped below ~5% where the ratio
                // is too noisy to be worth showing.
                if (percent >= 5) {
                    val elapsedMs = System.currentTimeMillis() - exportStartTimeMs
                    exportEtaSeconds = ((elapsedMs.toDouble() / percent) * (100 - percent) / 1000.0).roundToInt().coerceAtLeast(0)
                }
                ExportForegroundService.updateProgress(context, percent)
            }
        ) { message, resultFile, resultGalleryUri ->
            isProcessing = false
            activeTransformer = null
            exportProgress = 0
            exportEtaSeconds = null
            resultMessage = message
            outputFile = resultFile
            galleryUri = resultGalleryUri
            // Only set once the export has actually finished — the file
            // this points to doesn't exist on disk until runResize's
            // success path writes it, and resultThumbnailFile changing is
            // what triggers the decode effect, so setting it any earlier
            // would fire that decode against a file that isn't there yet.
            resultThumbnailFile = if (resultFile != null) thumbnailFile else null
            ExportForegroundService.stop(context)
        }
    }

    // UX FIX: Cancel button. Previously, once "Resize video" was tapped
    // there was no way back — a novice who picked a huge resolution by
    // mistake had to just wait it out or force-close the app. Cancelling
    // stops the Transformer and cleans up the partial output file so it
    // doesn't linger in cache taking up space.
    fun cancelResize() {
        activeTransformer?.cancel()
        activeTransformer = null
        isProcessing = false
        exportProgress = 0
        exportEtaSeconds = null
        resultMessage = "Proses dibatalkan."
        ExportForegroundService.stop(context)
    }

    // UX FIX: pressing system back while isProcessing == true used to just
    // exit the screen/app with zero warning, abandoning a running export
    // (Transformer keeps a MediaCodec session alive — quitting the Activity
    // doesn't cleanly finish it, it just orphans it). This intercepts back
    // during export and asks first instead of losing work silently.
    var showExitWhileProcessingConfirm by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = isProcessing) {
        showExitWhileProcessingConfirm = true
    }
    if (showExitWhileProcessingConfirm) {
        AlertDialog(
            onDismissRequest = { showExitWhileProcessingConfirm = false },
            title = { Text("Batalkan proses?") },
            text = { Text("Video sedang diproses. Keluar sekarang akan menghentikan dan membatalkan proses ini.") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showExitWhileProcessingConfirm = false
                    cancelResize()
                }) { Text("Batalkan proses", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); showExitWhileProcessingConfirm = false }) { Text("Tetap di sini") }
            }
        )
    }

    val storagePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingRequest
        val thumbFile = pendingThumbnailFile
        pendingRequest = null
        pendingThumbnailFile = null
        if (granted && request != null) {
            startResize(request, thumbFile)
        } else if (request != null) {
            isProcessing = false
            resultMessage = "Izin penyimpanan diperlukan untuk menyimpan video ke galeri."
        }
    }

    // Batch 13: the video Uri arrives from VideoPickerScreen (in-app,
    // MediaStore-backed) now instead of the OS Photo Picker, but everything
    // downstream of "we have a Uri" is unchanged — same metadata load, same
    // reset-on-new-video behavior. content:// Uris queried straight from
    // MediaStore are readable app-wide as long as READ_MEDIA_VIDEO /
    // READ_EXTERNAL_STORAGE is granted (which VideoPickerScreen itself
    // checks before it ever lists anything), so unlike the old Photo-Picker
    // grant this doesn't need a persistable-permission dance — the
    // runCatching call below is a harmless no-op for this Uri type, kept
    // only so a re-share of a document-provider Uri (e.g. via "Edit ulang"
    // on very old history entries) still gets the same best-effort attempt.
    fun handlePickedVideo(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        resultMessage = null
        outputFile = null
        galleryUri = null
        resultThumbnailFile = null
        customWidth = null
        customHeight = null
        scope.launch {
            loadVideoMetadata(
                uri,
                onLoaded = { d, w, h ->
                    selectedUri = uri
                    durationMs = d
                    sourceWidth = w
                    sourceHeight = h
                    trimRange = 0f..1f
                },
                onFailed = {
                    durationMs = 0L
                    selectedUri = null
                    resultMessage = "Video ini tidak bisa dibaca (mungkin format tidak didukung atau file rusak). Coba pilih video lain."
                }
            )
        }
    }

    // Watermark logo picker — same Photo Picker mechanism as the video
    // picker, scoped to images only. Subject to the same one-shot,
    // generally-non-persistable grant caveat as the source video (see
    // README): works reliably within the same app session.
    val pickWatermarkLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            watermarkUri = uri
        }
    }

    if (showChangeVideoConfirm) {
        AlertDialog(
            onDismissRequest = { showChangeVideoConfirm = false },
            title = { Text("Ganti video?") },
            text = { Text("Memilih video lain akan mereset area potong (trim) dan pengaturan resolusi custom yang sudah kamu atur untuk video ini.") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showChangeVideoConfirm = false
                    showVideoPicker = true
                }) { Text("Ganti video") }
            },
            dismissButton = {
                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); showChangeVideoConfirm = false }) { Text("Batal") }
            }
        )
    }

    if (showCustomResDialog) {
        CustomResolutionDialog(
            initialWidth = customWidth ?: sourceWidth,
            initialHeight = customHeight ?: sourceHeight,
            onDismiss = { showCustomResDialog = false },
            onSave = { w, h ->
                customWidth = w
                customHeight = h
                resolution = ResolutionOption.CUSTOM
                showCustomResDialog = false
            }
        )
    }

    if (showCustomBitrateDialog) {
        CustomBitrateDialog(
            initialKbps = customBitrateKbps,
            onDismiss = { showCustomBitrateDialog = false },
            onSave = { kbps ->
                customBitrateKbps = kbps
                quality = QualityOption.CUSTOM
                showCustomBitrateDialog = false
            }
        )
    }

    if (showTargetSizeDialog) {
        TargetSizeDialog(
            durationMs = (trimRange.endInclusive * durationMs).toLong() - (trimRange.start * durationMs).toLong(),
            muteAudio = muteAudio,
            onDismiss = { showTargetSizeDialog = false },
            onSave = { kbps ->
                customBitrateKbps = kbps
                quality = QualityOption.CUSTOM
                showTargetSizeDialog = false
            }
        )
    }

    val isGlass = com.example.videoresizer.ui.theme.LocalIsGlassTheme.current
    val screenBackground = if (isGlass) {
        com.example.videoresizer.ui.theme.MidnightBlueGlassGradient
    } else {
        androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.background)
    }
    // Batch 13: wraps the existing Scaffold so VideoPickerScreen can be
    // drawn as an opaque full-screen overlay on top of it — same "stays
    // composed underneath, overlay drawn on top" pattern VideoResizerApp
    // already uses for Studio/Batch/GIF, so nothing about ResizerScreen's
    // own state gets torn down while the picker is open.
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize().background(screenBackground),
        topBar = {
            TopAppBar(
                title = {
                    // BUG FIX (Batch 23): with 6 action icons now in this
                    // bar (update/compress/batch/gif/studio/theme, since
                    // Batch 21's update button), the title slot's available
                    // width shrank enough that this Text — which had no
                    // maxLines/overflow — wrapped "Video Resizer" onto a
                    // second line and got clipped by TopAppBar's fixed
                    // height, rendering as garbled fragments ("eo"/"Res").
                    // weight(1f, fill=false) lets the title shrink to
                    // whatever width remains instead of forcing the Row
                    // wider than it has room for; maxLines=1 + Ellipsis
                    // guarantees a single clean line (truncated with "…" on
                    // very narrow screens) instead of a wrap-then-clip.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Video Resizer",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                },
                actions = {
                    // BATCH 25: user asked to fold every feature except
                    // Studio into "More" (Compress/Batch/GIF were separate
                    // icons since before Batch 21; keeping them there was
                    // the whole reason the bar got crowded in the first
                    // place). Studio stays standalone per explicit request.
                    // Bar is now just [Studio, More] — 2 icons, title has
                    // maximum room, and every other feature lives in one
                    // dropdown instead of being split across bar+menu.
                    IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onOpenStudio() }) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = "Studio")
                    }
                    Box {
                        IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); showMoreMenu = true }) {
                            if (checkingUpdate) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Menu lainnya")
                            }
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Kompres video") },
                                leadingIcon = { Icon(Icons.Filled.Compress, contentDescription = null) },
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); showMoreMenu = false; onOpenCompressor() }
                            )
                            DropdownMenuItem(
                                text = { Text("Batch export") },
                                leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null) },
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); showMoreMenu = false; onOpenBatch() }
                            )
                            DropdownMenuItem(
                                text = { Text("Video ke GIF") },
                                leadingIcon = { Icon(Icons.Filled.Gif, contentDescription = null) },
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); showMoreMenu = false; onOpenGif() }
                            )
                            androidx.compose.material3.HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (checkingUpdate) "Mengecek update…" else "Cek update") },
                                leadingIcon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
                                enabled = !checkingUpdate,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showMoreMenu = false
                                    checkingUpdate = true
                                    scope.launch {
                                        updateResult = AppUpdater.check(context)
                                        checkingUpdate = false
                                    }
                                }
                            )
                            androidx.compose.material3.HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Tema: Dark") },
                                leadingIcon = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onThemePrefChange(ThemePreference.DARK); showMoreMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Tema: Light") },
                                leadingIcon = { Icon(Icons.Filled.LightMode, contentDescription = null) },
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onThemePrefChange(ThemePreference.LIGHT); showMoreMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Tema: Ikuti sistem") },
                                leadingIcon = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onThemePrefChange(ThemePreference.SYSTEM); showMoreMenu = false }
                            )
                            androidx.compose.material3.HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Tema: Midnight Neon") },
                                leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onThemePrefChange(ThemePreference.MIDNIGHT_NEON); showMoreMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Tema: Warm Paper") },
                                leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onThemePrefChange(ThemePreference.WARM_PAPER); showMoreMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Tema: Midnight Blue Glass") },
                                leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onThemePrefChange(ThemePreference.MIDNIGHT_BLUE_GLASS); showMoreMenu = false }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (selectedUri == null) {
                VideoPickerCard(
                    onPickClick = { showVideoPicker = true }
                )
            } else if (durationMs > 0) {
                // Local val capture instead of !!: selectedUri is Compose mutable
                // state (custom getter), so Kotlin can't smart-cast it from the
                // `selectedUri == null` check above across into this branch.
                val currentUri = selectedUri
                if (currentUri != null) {
                    VideoEditorPreview(
                        uri = currentUri,
                        sourceWidth = sourceWidth,
                        sourceHeight = sourceHeight,
                        durationMs = durationMs,
                        filmstrip = filmstrip,
                        trimRange = trimRange,
                        isForeground = isForeground,
                        onTrimRangeChange = { trimRange = it },
                        onPickDifferent = {
                            if (hasCustomizedSettings) {
                                showChangeVideoConfirm = true
                            } else {
                                showVideoPicker = true
                            }
                        }
                    )
                }
            }

            if (selectedUri != null && durationMs > 0) {
                val startMs = (trimRange.start * durationMs).toLong()
                val endMs = (trimRange.endInclusive * durationMs).toLong()

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Preset media sosial", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(
                        "Sekali tap untuk atur resolusi & bitrate persis seperti rekomendasi platform-nya.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(SocialPreset.ENTRIES) { preset ->
                            FilterChip(
                                selected = selectedSocialPreset == preset,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);                                     selectedSocialPreset = preset
                                    aspectRatio = preset.aspectRatio
                                    resolution = ResolutionOption.CUSTOM
                                    customWidth = preset.width
                                    customHeight = preset.height
                                    quality = QualityOption.CUSTOM
                                    customBitrateKbps = preset.bitrateKbps
                                },
                                label = { Text(preset.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                    selectedSocialPreset?.let { p ->
                        Text(
                            "Dipakai: ${p.width}×${p.height}, ~${p.bitrateKbps} kbps. Mengubah resolusi/kualitas manual di bawah akan melepas preset ini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OptionSection(
                    title = "Aspect ratio",
                    options = AspectRatioOption.ENTRIES,
                    labelOf = { it.label },
                    selected = aspectRatio,
                    onSelect = {
                        aspectRatio = it
                        selectedSocialPreset = null
                        if (resolution == ResolutionOption.CUSTOM) resolution = ResolutionOption.ORIGINAL
                    }
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Resolution", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(ResolutionOption.ENTRIES) { option ->
                            val isSelected = option == resolution
                            val label = if (option == ResolutionOption.CUSTOM && customWidth != null && customHeight != null) {
                                "${customWidth}×${customHeight}"
                            } else {
                                option.label
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);                                     selectedSocialPreset = null
                                    if (option == ResolutionOption.CUSTOM) {
                                        showCustomResDialog = true
                                    } else {
                                        resolution = option
                                    }
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                OptionSection(
                    title = "Mode resize",
                    options = ResizeMode.ENTRIES,
                    labelOf = { it.label },
                    selected = resizeMode,
                    onSelect = { resizeMode = it }
                )
                // UX FIX: "Crop" and "Stretch" alone don't tell a novice user
                // what actually happens to their video — in particular that
                // Stretch can visibly squash/distort it, which is easy to
                // not notice until after the export finishes.
                Text(
                    if (resizeMode == ResizeMode.CROP) {
                        "Crop: sisi video yang tidak muat akan dipotong, tanpa distorsi."
                    } else {
                        "Stretch: video ditekan/ditarik agar pas, gambar bisa terlihat gepeng."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Kualitas / bitrate", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    // UX: same reasoning as the Crop/Stretch caption above —
                    // "Rendah/Sedang/Tinggi" alone doesn't tell a novice user
                    // what they're trading off.
                    Text(
                        "Rendah = ukuran file lebih kecil, sedikit turun kualitas gambar. Tinggi = kualitas maksimal, ukuran file lebih besar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(QualityOption.ENTRIES) { option ->
                            val isSelected = option == quality
                            val label = if (option == QualityOption.CUSTOM && customBitrateKbps != null) {
                                "${customBitrateKbps} kbps"
                            } else {
                                option.label
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);                                     selectedSocialPreset = null
                                    if (option == QualityOption.CUSTOM) {
                                        showCustomBitrateDialog = true
                                    } else {
                                        quality = option
                                    }
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        // Not one of QualityOption.ENTRIES — this is a second
                        // entry point into the exact same quality=CUSTOM/
                        // customBitrateKbps state as the chips above, just
                        // driven by a target file size instead of a bitrate
                        // number. See TargetSizeDialog / requiredBitrateKbpsForTargetSize.
                        item {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);                                     selectedSocialPreset = null
                                    showTargetSizeDialog = true
                                },
                                label = { Text("Ukuran target (MB)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                    // UX: gives a novice user a concrete before-you-commit
                    // number instead of an abstract "Rendah/Sedang/Tinggi"
                    // label — "will this actually fit in my WhatsApp/Story
                    // upload limit" is exactly the question file size
                    // controls exist to answer.
                    val estimatedBytes = remember(quality, customBitrateKbps, aspectRatio, resolution, resizeMode, customWidth, customHeight, muteAudio, startMs, endMs, sourceWidth, sourceHeight) {
                        VideoResizer.estimateOutputSizeBytes(
                            ResizeRequest(
                                sourceUri = Uri.EMPTY,
                                outputFile = File(""),
                                aspectRatio = aspectRatio,
                                resolution = resolution,
                                sourceWidth = sourceWidth,
                                sourceHeight = sourceHeight,
                                muteAudio = muteAudio,
                                resizeMode = resizeMode,
                                customWidth = customWidth,
                                customHeight = customHeight,
                                quality = quality,
                                customBitrateKbps = customBitrateKbps
                            ),
                            durationMs = (endMs - startMs).coerceAtLeast(0L)
                        )
                    }
                    Text(
                        if (quality == QualityOption.ORIGINAL) {
                            "Original: ukuran mengikuti hasil encoder bawaan, tidak bisa diprediksi di sini."
                        } else if (estimatedBytes != null) {
                            "Perkiraan ukuran: ~${formatFileSize(estimatedBytes)} (kasar, hasil asli bisa sedikit berbeda)."
                        } else {
                            "Pilih video untuk melihat perkiraan ukuran."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.BrandingWatermark,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Watermark / logo", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        }
                        if (watermarkUri == null) {
                            TextButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); pickWatermarkLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) { Text("Pilih gambar") }
                        } else {
                            TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); watermarkUri = null }) {
                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hapus")
                            }
                        }
                    }
                    // Local val capture instead of !!: watermarkUri is Compose mutable
                    // state, so it can't smart-cast from the null check below.
                    val currentWatermarkUri = watermarkUri
                    if (currentWatermarkUri != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            AsyncThumbnail(uri = currentWatermarkUri)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Posisi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(WatermarkPosition.ENTRIES) { pos ->
                                        FilterChip(
                                            selected = pos == watermarkPosition,
                                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); watermarkPosition = pos },
                                            label = { Text(pos.label, style = MaterialTheme.typography.labelSmall) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = Color.White,
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        Text("Ukuran: ${watermarkScalePercent.roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = watermarkScalePercent,
                            onValueChange = { watermarkScalePercent = it },
                            valueRange = 5f..50f
                        )
                        Text("Transparansi: ${watermarkOpacityPercent.roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = watermarkOpacityPercent,
                            onValueChange = { watermarkOpacityPercent = it },
                            valueRange = 10f..100f
                        )
                    } else {
                        Text(
                            "Opsional: tempel logo/watermark PNG di salah satu sudut video hasil resize.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Caption overlay — short burned-in text (white + black
                // outline, fixed style/size), positioned with the same
                // WatermarkPosition picker the watermark section above uses.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.ClosedCaption,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Caption", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    }
                    OutlinedTextField(
                        value = captionText,
                        onValueChange = { captionText = it },
                        placeholder = { Text("Tulis caption singkat…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (captionText.isNotBlank()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(WatermarkPosition.ENTRIES) { pos ->
                                FilterChip(
                                    selected = pos == captionPosition,
                                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); captionPosition = pos },
                                    label = { Text(pos.label, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    } else {
                        Text(
                            "Opsional: teks singkat yang ikut ter-render permanen di video hasil resize.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OptionSection(
                    title = "Rotasi",
                    options = RotationOption.ENTRIES,
                    labelOf = { it.label },
                    selected = rotation,
                    onSelect = { rotation = it }
                )

                OptionSection(
                    title = "Flip / cermin",
                    options = FlipOption.ENTRIES,
                    labelOf = { it.label },
                    selected = flip,
                    onSelect = { flip = it }
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OptionSection(
                        title = "Frame rate",
                        options = FrameRateOption.ENTRIES,
                        labelOf = { it.label },
                        selected = frameRate,
                        onSelect = { frameRate = it }
                    )
                    if (frameRate != FrameRateOption.ORIGINAL) {
                        Text(
                            "Frame yang melebihi ${frameRate.fps} fps akan dibuang agar video terasa lebih halus/hemat ukuran; frame rate sumber yang lebih rendah tidak akan dipaksa naik.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bisukan audio", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Switch(checked = muteAudio, onCheckedChange = { muteAudio = it })
                }

                if (isProcessing) {
                    // UX FIX: real percentage instead of an indefinite
                    // spinner, plus a way out. Elapsed/ETA below is derived
                    // purely from the percentage feed (see startResize) —
                    // it needs no extra Media3 progress API.
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(
                            progress = { exportProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Memproses video… $exportProgress%",
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); cancelResize() }) { Text("Batalkan") }
                        }
                        val eta = exportEtaSeconds
                        Text(
                            if (eta != null) {
                                "Perkiraan sisa waktu: ~${eta}s • hardware encoder perangkat"
                            } else {
                                "Menghitung perkiraan waktu… • hardware encoder perangkat"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val canResize = endMs > startMs
                    val ctaBrush = if (canResize) {
                        Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
                    } else {
                        val dim = MaterialTheme.colorScheme.surfaceVariant
                        Brush.horizontalGradient(listOf(dim, dim))
                    }
                    val ctaContentColor = if (canResize) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        elevation = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(brush = ctaBrush, shape = RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                        enabled = canResize,
                        onClick = {
                            val uri = selectedUri ?: return@Button

                            // UX FIX: low-storage guard. Without this, a
                            // nearly-full device just lets the export run
                            // and fail late with a cryptic codec/IO error —
                            // a novice user has no idea "penyimpanan penuh"
                            // is the real cause. 250MB is a rough safety
                            // margin, not an exact requirement.
                            val freeMb = context.cacheDir.usableSpace / (1024 * 1024)
                            if (freeMb < 250) {
                                resultMessage = "Penyimpanan hampir penuh (tersisa ${freeMb}MB). Hapus beberapa file dulu sebelum memproses video."
                                return@Button
                            }

                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isProcessing = true
                            resultMessage = null
                            outputFile = null
                            galleryUri = null
                            resultThumbnailFile = null

                            val id = UUID.randomUUID().toString()
                            val thumbDir = File(context.cacheDir, "thumbs").apply { mkdirs() }
                            val request = ResizeRequest(
                                sourceUri = uri,
                                outputFile = File(context.cacheDir, "resized_$id.mp4"),
                                aspectRatio = aspectRatio,
                                resolution = resolution,
                                sourceWidth = sourceWidth,
                                sourceHeight = sourceHeight,
                                trimStartMs = startMs,
                                trimEndMs = endMs,
                                muteAudio = muteAudio,
                                rotation = rotation,
                                resizeMode = resizeMode,
                                customWidth = customWidth,
                                customHeight = customHeight,
                                quality = quality,
                                customBitrateKbps = customBitrateKbps,
                                watermarkUri = watermarkUri,
                                watermarkPosition = watermarkPosition,
                                watermarkOpacityPercent = watermarkOpacityPercent.roundToInt(),
                                watermarkScalePercent = watermarkScalePercent.roundToInt(),
                                captionText = captionText,
                                captionPosition = captionPosition,
                                flip = flip,
                                frameRate = frameRate
                            )
                            // Before/after comparison: this same thumbnail file is
                            // already generated for Studio history once export
                            // succeeds — resultThumbnailFile is set from startResize's
                            // onDone callback below, not here, since the file on disk
                            // doesn't exist yet at click time.
                            val thumbnailFile = File(thumbDir, "thumb_$id.jpg")

                            val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                            val alreadyGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                            if (needsLegacyPermission && !alreadyGranted) {
                                pendingRequest = request
                                pendingThumbnailFile = thumbnailFile
                                storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                startResize(request, thumbnailFile)
                            }
                        }
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = ctaContentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resize video", color = ctaContentColor)
                    }
                }

                resultMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(msg, color = MaterialTheme.colorScheme.onSurface)

                            // "Preview sebelum-sesudah (before/after) berdampingan":
                            // a frame from the original source (reused from
                            // `filmstrip`, already extracted for the trim scrubber —
                            // no extra decode) next to a frame from the actual
                            // exported result (reused from the thumbnail already
                            // generated for Studio history). Each keeps its own real
                            // aspect ratio rather than being forced square, so a
                            // 16:9-to-9:16 resize is immediately, visually obvious —
                            // that visual contrast is the entire point of a
                            // before/after, not just two static images.
                            if (outputFile != null && resultThumbnailBitmap != null && filmstrip.isNotEmpty() && sourceWidth > 0 && sourceHeight > 0) {
                                Spacer(Modifier.height(14.dp))
                                Text("Sebelum vs sesudah", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Image(
                                            bitmap = filmstrip[filmstrip.size / 2].asImageBitmap(),
                                            contentDescription = "Sebelum",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(sourceWidth.toFloat() / sourceHeight.toFloat())
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text("Sebelum", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(
                                        Icons.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        // Local val capture instead of !!: resultThumbnailBitmap
                                        // is Compose mutable state, so it can't smart-cast across
                                        // this nested composable scope even though the outer `if`
                                        // above already confirmed it's non-null.
                                        val resultBitmap = resultThumbnailBitmap
                                        if (resultBitmap != null) {
                                            Image(
                                                bitmap = resultBitmap.asImageBitmap(),
                                                contentDescription = "Sesudah",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(resultBitmap.width.toFloat() / resultBitmap.height.toFloat())
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text("Sesudah", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            if (outputFile != null || galleryUri != null) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    // Gallery integration: opens straight in the
                                    // user's default Gallery/video app when we
                                    // successfully published to MediaStore.
                                    galleryUri?.let { uri ->
                                        TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); openInGallery(context, uri) }) {
                                            Text("Buka di Galeri")
                                        }
                                    }
                                    outputFile?.let { file ->
                                        TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); shareVideo(context, file) }) {
                                            Text("Bagikan")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showVideoPicker) {
        VideoPickerScreen(
            onVideoSelected = { uri ->
                showVideoPicker = false
                handlePickedVideo(uri)
            },
            onCancel = { showVideoPicker = false }
        )
    }
    // In-app updater dialog (AppUpdater.kt / Batch 21) — drawn at this same
    // top level as VideoPickerScreen's overlay above, so it floats over
    // everything regardless of which screen/sub-state is active underneath.
    when (val result = updateResult) {
        is AppUpdater.CheckResult.UpToDate -> {
            LaunchedEffect(Unit) { updateResult = null }
        }
        is AppUpdater.CheckResult.Error -> {
            AlertDialog(
                onDismissRequest = { updateResult = null },
                title = { Text("Cek update gagal") },
                text = { Text(result.message) },
                confirmButton = { TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); updateResult = null }) { Text("OK") } }
            )
        }
        is AppUpdater.CheckResult.Available -> {
            val info = result.info
            val progress = downloadProgress
            AlertDialog(
                onDismissRequest = { if (progress == null) { updateResult = null } },
                title = { Text("Update tersedia: ${info.tagName}") },
                text = {
                    Column {
                        if (progress == null) {
                            if (info.releaseNotes.isNotBlank()) Text(info.releaseNotes)
                        } else if (progress < 0f) {
                            Text("Mengunduh…")
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            Text("Mengunduh… ${(progress * 100).toInt()}%")
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = progress == null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            downloadProgress = -1f
                            scope.launch {
                                try {
                                    val apk = AppUpdater.download(context, info) { p -> downloadProgress = p }
                                    downloadedApk = apk
                                    downloadProgress = null
                                    updateResult = null
                                    if (AppUpdater.canInstall(context)) {
                                        AppUpdater.install(context, apk)
                                    } else {
                                        AppUpdater.requestInstallPermission(context)
                                    }
                                } catch (e: Exception) {
                                    downloadProgress = null
                                    updateResult = AppUpdater.CheckResult.Error(e.message ?: "Unduh gagal")
                                }
                            }
                        }
                    ) { Text("Unduh & Pasang") }
                },
                dismissButton = {
                    TextButton(enabled = progress == null, onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); updateResult = null }) { Text("Nanti") }
                }
            )
        }
        null -> Unit
    }
    }
}

/**
 * A picked video's queue state within [BatchScreen]. Kept separate from
 * [ResizeRequest] because a batch item's width/height/duration aren't known
 * until it's actually about to be processed (read lazily, one at a time,
 * rather than probing every picked file up front with MediaMetadataRetriever
 * before the user even taps Start — that cost scales with how many videos
 * they picked and buys nothing if they cancel before starting).
 */
private data class BatchItem(
    val uri: Uri,
    val displayName: String,
    val status: BatchStatus = BatchStatus.Waiting
)

private sealed class BatchStatus {
    data object Waiting : BatchStatus()
    data class Processing(val percent: Int) : BatchStatus()
    data object Done : BatchStatus()
    data class Failed(val message: String) : BatchStatus()
    data object Cancelled : BatchStatus()
}

/** Best-effort content display name for a picked Uri, for the queue list. Falls back to the Uri's last path segment if the provider doesn't expose OpenableColumns. */
private fun queryDisplayName(context: android.content.Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "video"
}

/**
 * Batch export: same resize/quality/watermark settings applied to several
 * videos in one queued run, one at a time (Media3's Transformer is not
 * designed to run more than one export concurrently on a single device —
 * queuing sequentially is both simpler and the actually-supported way to
 * do this, matching how CapCut/InShot-style "batch export" works too).
 *
 * Deliberately **not** included here: per-video trimming. Each picked video
 * has its own length, so a single shared trim range wouldn't mean the same
 * thing across all of them — this exports each video in full. Everything
 * else (aspect ratio, resolution/custom, resize mode, rotation, mute,
 * quality/bitrate, watermark, and the one-tap social presets) is shared
 * across the whole batch, same as picking those once for all selected
 * clips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun BatchScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var items by remember { mutableStateOf<List<BatchItem>>(emptyList()) }
    // Per-item preview thumbnail, keyed by Uri rather than added as a field
    // on BatchItem itself — BatchItem is a plain data class copied a lot
    // (status updates during processing), and a Bitmap field would mean
    // re-copying/re-comparing a Bitmap on every single one of those status
    // updates for no reason. A side map keyed by Uri stays populated across
    // those copies with zero extra cost.
    var thumbnails by remember { mutableStateOf<Map<Uri, Bitmap>>(emptyMap()) }
    var isProcessing by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var activeTransformer by remember { mutableStateOf<androidx.media3.transformer.Transformer?>(null) }
    // BUG FIX: see cancelBatch() below — Transformer.cancel() never invokes
    // the onDone callback the loop below is suspended waiting on, so
    // without capturing (and actually cancelling) the loop's own
    // coroutine Job, "Batalkan batch" only ever reset UI state while the
    // real background loop kept silently hanging forever, able to race
    // with a fresh loop if the user pressed "Proses semua" again.
    var batchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var summaryMessage by remember { mutableStateOf<String?>(null) }

    var aspectRatio by remember { mutableStateOf(AspectRatioOption.ORIGINAL) }
    var resolution by remember { mutableStateOf(ResolutionOption.ORIGINAL) }
    var resizeMode by remember { mutableStateOf(ResizeMode.CROP) }
    var rotation by remember { mutableStateOf(RotationOption.NONE) }
    // Batch 12: same controls ResizerScreen already has, extended here so
    // batch jobs aren't limited to a narrower option set than a single
    // export — see CHANGELOG's Batch 9 "Not done this batch" for why this
    // was deferred originally.
    var flip by remember { mutableStateOf(FlipOption.NONE) }
    var frameRate by remember { mutableStateOf(FrameRateOption.ORIGINAL) }
    var muteAudio by remember { mutableStateOf(false) }
    var showCustomResDialog by remember { mutableStateOf(false) }
    var customWidth by remember { mutableStateOf<Int?>(null) }
    var customHeight by remember { mutableStateOf<Int?>(null) }
    var quality by remember { mutableStateOf(QualityOption.ORIGINAL) }
    var showCustomBitrateDialog by remember { mutableStateOf(false) }
    var customBitrateKbps by remember { mutableStateOf<Int?>(null) }
    // "Ukuran target (MB)" for batch: unlike ResizerScreen's TargetSizeDialog
    // (one video, one known duration, so MB->kbps can be shown live),
    // batch items can each have a different duration — so this just stores
    // the target size itself, and startBatch() below solves MB->kbps
    // separately for *each* item against that item's own probed duration
    // right before building its ResizeRequest.
    var targetSizeMb by remember { mutableStateOf<Double?>(null) }
    var showTargetSizeDialog by remember { mutableStateOf(false) }
    var watermarkUri by remember { mutableStateOf<Uri?>(null) }
    var watermarkPosition by remember { mutableStateOf(WatermarkPosition.BOTTOM_RIGHT) }
    var watermarkOpacityPercent by remember { mutableFloatStateOf(70f) }
    var watermarkScalePercent by remember { mutableFloatStateOf(18f) }
    var captionText by remember { mutableStateOf("") }
    var captionPosition by remember { mutableStateOf(WatermarkPosition.BOTTOM_RIGHT) }
    var selectedSocialPreset by remember { mutableStateOf<SocialPreset?>(null) }

    // Shared with ResizerScreen via the same SharedPreferences key, so the
    // permission is asked at most once across the whole app — not once per
    // screen. See the fix note on ResizerScreen's identical block.
    val appPrefs = remember { context.getSharedPreferences("video_resizer_prefs", android.content.Context.MODE_PRIVATE) }
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* no-op either way */ }

    val pickVideosLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            items = uris.map { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                BatchItem(uri = uri, displayName = queryDisplayName(context, uri))
            }
            thumbnails = emptyMap()
            summaryMessage = null
        }
    }

    // Per-item preview thumbnail for the queue list below. count=1 makes
    // FilmstripExtractor grab a single frame at time 0 regardless of the
    // durationMs argument (see its fraction-for-count==1 branch), so a
    // real duration isn't needed here — the dummy 1L is never actually
    // used by that code path. Skips any Uri already in the map so picking
    // more videos on top of an existing queue doesn't re-decode ones
    // already thumbnailed.
    LaunchedEffect(items) {
        val missing = items.map { it.uri }.filter { it !in thumbnails }
        if (missing.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            for (uri in missing) {
                val frame = FilmstripExtractor.extract(context, uri, durationMs = 1L, count = 1, targetHeightPx = 96).firstOrNull()
                if (frame != null) {
                    thumbnails = thumbnails + (uri to frame)
                }
            }
        }
    }

    val pickWatermarkLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            watermarkUri = uri
        }
    }

    // Same single-queue-slot pattern as the single-video screen's
    // startResize/cancelResize: only one Transformer is ever active at a
    // time, so there's only ever one thing to cancel.
    fun cancelBatch() {
        activeTransformer?.cancel()
        activeTransformer = null
        // BUG FIX (root cause): Transformer.cancel() above stops the
        // encoder, but it does NOT invoke the onDone callback the loop
        // coroutine is suspended on via `done.await()` — so without this,
        // that coroutine would hang forever, invisible, still holding a
        // reference into this screen's state and able to race with a
        // fresh startBatch() call if the user pressed "Proses semua"
        // again right after cancelling. Cancelling the Job directly is
        // what actually unblocks/terminates that suspended await.
        batchJob?.cancel()
        batchJob = null
        isProcessing = false
        items = items.mapIndexed { idx, item ->
            if (idx == currentIndex || item.status is BatchStatus.Waiting) item.copy(status = BatchStatus.Cancelled) else item
        }
        currentIndex = -1
        ExportForegroundService.stop(context)
        summaryMessage = "Batch dibatalkan."
    }

    fun startBatch() {
        if (items.isEmpty() || isProcessing) return
        val freeMb = context.cacheDir.usableSpace / (1024 * 1024)
        if (freeMb < 250) {
            summaryMessage = "Penyimpanan hampir penuh (tersisa ${freeMb}MB). Hapus beberapa file dulu sebelum memproses batch."
            return
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        isProcessing = true
        summaryMessage = null
        items = items.map { it.copy(status = BatchStatus.Waiting) }
        ExportForegroundService.start(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !appPrefs.getBoolean("notif_permission_asked", false)
        ) {
            appPrefs.edit().putBoolean("notif_permission_asked", true).apply()
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        batchJob = scope.launch {
            var successCount = 0
            var failCount = 0
            for (i in items.indices) {
                currentIndex = i
                items = items.toMutableList().also { it[i] = it[i].copy(status = BatchStatus.Processing(0)) }
                val item = items[i]

                val (w, h, dur) = withContext(Dispatchers.IO) {
                    val retriever = MediaMetadataRetriever()
                    runCatching {
                        retriever.setDataSource(context, item.uri)
                        val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        val vw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        val vh = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        Triple(vw, vh, d)
                    }.getOrElse { Triple(0, 0, 0L) }.also { retriever.release() }
                }

                if (dur <= 0) {
                    items = items.toMutableList().also {
                        it[i] = it[i].copy(status = BatchStatus.Failed("Tidak bisa membaca video ini"))
                    }
                    failCount++
                    continue
                }

                val id = UUID.randomUUID().toString()
                val thumbDir = File(context.cacheDir, "thumbs").apply { mkdirs() }
                // Target-size mode resolves per item here, against this
                // item's own probed `dur` — see targetSizeMb's doc comment
                // above for why this can't be solved once for the whole
                // queue. Falls back to MIN_BITRATE_KBPS (best effort) in the
                // rare case the requested size is impossible for this one
                // item's duration, rather than failing that item outright.
                val tsMb = targetSizeMb
                val effectiveQuality = if (tsMb != null) QualityOption.CUSTOM else quality
                val effectiveCustomBitrateKbps = if (tsMb != null) {
                    VideoResizer.requiredBitrateKbpsForTargetSize(tsMb, dur, muteAudio) ?: VideoResizer.MIN_BITRATE_KBPS
                } else {
                    customBitrateKbps
                }
                val request = ResizeRequest(
                    sourceUri = item.uri,
                    outputFile = File(context.cacheDir, "resized_$id.mp4"),
                    aspectRatio = aspectRatio,
                    resolution = resolution,
                    sourceWidth = w,
                    sourceHeight = h,
                    trimStartMs = 0L,
                    trimEndMs = dur,
                    muteAudio = muteAudio,
                    rotation = rotation,
                    flip = flip,
                    frameRate = frameRate,
                    resizeMode = resizeMode,
                    customWidth = customWidth,
                    customHeight = customHeight,
                    quality = effectiveQuality,
                    customBitrateKbps = effectiveCustomBitrateKbps,
                    watermarkUri = watermarkUri,
                    watermarkPosition = watermarkPosition,
                    watermarkOpacityPercent = watermarkOpacityPercent.roundToInt(),
                    watermarkScalePercent = watermarkScalePercent.roundToInt(),
                    captionText = captionText,
                    captionPosition = captionPosition
                )
                val thumbnailFile = File(thumbDir, "thumb_$id.jpg")

                val done = kotlinx.coroutines.CompletableDeferred<Boolean>()
                activeTransformer = runResize(
                    context, scope, request, thumbnailFile,
                    onProgress = { percent ->
                        items = items.toMutableList().also { it[i] = it[i].copy(status = BatchStatus.Processing(percent)) }
                        ExportForegroundService.updateProgress(context, percent)
                    }
                ) { message, resultFile, _ ->
                    items = items.toMutableList().also {
                        it[i] = it[i].copy(
                            status = if (resultFile != null) BatchStatus.Done else BatchStatus.Failed(message)
                        )
                    }
                    done.complete(resultFile != null)
                }
                val ok = done.await()
                if (ok) successCount++ else failCount++
                activeTransformer = null
            }
            ExportForegroundService.stop(context)
            isProcessing = false
            currentIndex = -1
            batchJob = null
            summaryMessage = "Selesai: $successCount berhasil" + if (failCount > 0) ", $failCount gagal." else "."
        }
    }

    if (showCustomResDialog) {
        CustomResolutionDialog(
            initialWidth = customWidth ?: 1080,
            initialHeight = customHeight ?: 1920,
            onDismiss = { showCustomResDialog = false },
            onSave = { w, h ->
                customWidth = w
                customHeight = h
                resolution = ResolutionOption.CUSTOM
                showCustomResDialog = false
            }
        )
    }
    if (showCustomBitrateDialog) {
        CustomBitrateDialog(
            initialKbps = customBitrateKbps,
            onDismiss = { showCustomBitrateDialog = false },
            onSave = { kbps ->
                customBitrateKbps = kbps
                quality = QualityOption.CUSTOM
                showCustomBitrateDialog = false
            }
        )
    }

    if (showTargetSizeDialog) {
        BatchTargetSizeDialog(
            initialMb = targetSizeMb,
            onDismiss = { showTargetSizeDialog = false },
            onSave = { mb ->
                targetSizeMb = mb
                showTargetSizeDialog = false
            }
        )
    }

    // UX FIX (Batch 43, audit "back/cancel belum merata"): both the
    // system back gesture and the toolbar arrow used to silently call
    // cancelBatch() + onBack() with zero warning while a batch export
    // was actively running — the same gap ResizerScreen/CompressorScreen
    // already closed for themselves via showExitWhileProcessingConfirm.
    // Only asks when isProcessing; back while idle behaves exactly as
    // before (immediate onBack(), no dialog).
    var showExitWhileProcessingConfirm by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = true) {
        if (isProcessing) showExitWhileProcessingConfirm = true else onBack()
    }
    if (showExitWhileProcessingConfirm) {
        AlertDialog(
            onDismissRequest = { showExitWhileProcessingConfirm = false },
            title = { Text("Batalkan proses?") },
            text = { Text("Batch sedang diproses. Keluar sekarang akan menghentikan dan membatalkan seluruh antrean ini.") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showExitWhileProcessingConfirm = false
                    cancelBatch()
                    onBack()
                }) { Text("Batalkan proses", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); showExitWhileProcessingConfirm = false }) { Text("Tetap di sini") }
            }
        )
    }

    val isGlass = com.example.videoresizer.ui.theme.LocalIsGlassTheme.current
    val screenBackground = if (isGlass) {
        com.example.videoresizer.ui.theme.MidnightBlueGlassGradient
    } else {
        androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.background)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().background(screenBackground),
        topBar = {
            TopAppBar(
                title = { Text("Ekspor Batch", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isProcessing) showExitWhileProcessingConfirm = true else onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                "Terapkan pengaturan yang sama ke beberapa video sekaligus, diproses satu per satu. Trim per-video tidak tersedia di sini — setiap video diproses utuh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); pickVideosLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (items.isEmpty()) "Pilih beberapa video" else "Ganti pilihan video (${items.size} dipilih)")
            }

            if (items.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                val thumb = thumbnails[item.uri]
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Movie,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    item.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            val statusText = when (val s = item.status) {
                                is BatchStatus.Waiting -> if (isProcessing) "Menunggu" else ""
                                is BatchStatus.Processing -> "${s.percent}%"
                                is BatchStatus.Done -> "Selesai"
                                is BatchStatus.Failed -> "Gagal"
                                is BatchStatus.Cancelled -> "Dibatalkan"
                            }
                            val statusColor = when (item.status) {
                                is BatchStatus.Done -> MaterialTheme.colorScheme.primary
                                is BatchStatus.Failed -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            if (statusText.isNotEmpty()) {
                                Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
                            }
                        }
                        if (idx == currentIndex && item.status is BatchStatus.Processing) {
                            LinearProgressIndicator(
                                progress = { (item.status as BatchStatus.Processing).percent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // BUG FIX (illogical leftover): selectedSocialPreset was already
            // being declared and defensively reset to null in several
            // places here, as if a preset could be active — but there was
            // never actually a picker UI in this screen to set it to
            // anything in the first place, so it was permanently dead
            // state. Completing it properly here instead of deleting it,
            // since batch export sharing the same one-tap presets as
            // single-video export is the more useful outcome — same
            // behavior as the main screen's version.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Public,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Preset media sosial", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    "Sekali tap untuk atur resolusi & bitrate persis seperti rekomendasi platform-nya — berlaku untuk semua video di antrian ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(SocialPreset.ENTRIES) { preset ->
                        FilterChip(
                            selected = selectedSocialPreset == preset,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);                                 selectedSocialPreset = preset
                                aspectRatio = preset.aspectRatio
                                resolution = ResolutionOption.CUSTOM
                                customWidth = preset.width
                                customHeight = preset.height
                                quality = QualityOption.CUSTOM
                                customBitrateKbps = preset.bitrateKbps
                                targetSizeMb = null
                            },
                            label = { Text(preset.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                selectedSocialPreset?.let { p ->
                    Text(
                        "Dipakai: ${p.width}×${p.height}, ~${p.bitrateKbps} kbps. Mengubah resolusi/kualitas manual di bawah akan melepas preset ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OptionSection(
                title = "Aspect ratio",
                options = AspectRatioOption.ENTRIES,
                labelOf = { it.label },
                selected = aspectRatio,
                onSelect = {
                    aspectRatio = it
                    selectedSocialPreset = null
                    if (resolution == ResolutionOption.CUSTOM) resolution = ResolutionOption.ORIGINAL
                }
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Resolusi", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ResolutionOption.ENTRIES) { option ->
                        val isSelected = option == resolution
                        val label = if (option == ResolutionOption.CUSTOM && customWidth != null && customHeight != null) {
                            "${customWidth}×${customHeight}"
                        } else option.label
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);                                 selectedSocialPreset = null
                                if (option == ResolutionOption.CUSTOM) showCustomResDialog = true else resolution = option
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            OptionSection(
                title = "Mode resize",
                options = ResizeMode.ENTRIES,
                labelOf = { it.label },
                selected = resizeMode,
                onSelect = { resizeMode = it }
            )
            Text(
                if (resizeMode == ResizeMode.CROP) {
                    "Crop: sisi video yang tidak muat akan dipotong, tanpa distorsi."
                } else {
                    "Stretch: video ditekan/ditarik agar pas, gambar bisa terlihat gepeng."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Kualitas / bitrate", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Rendah = ukuran file lebih kecil, sedikit turun kualitas gambar. Tinggi = kualitas maksimal, ukuran file lebih besar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(QualityOption.ENTRIES) { option ->
                        val isSelected = option == quality && targetSizeMb == null
                        val label = if (option == QualityOption.CUSTOM && customBitrateKbps != null) {
                            "${customBitrateKbps} kbps"
                        } else option.label
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);                                 selectedSocialPreset = null
                                targetSizeMb = null
                                if (option == QualityOption.CUSTOM) showCustomBitrateDialog = true else quality = option
                            },
                            label = { Text(label) }
                        )
                    }
                    // Not one of QualityOption.ENTRIES, same "second entry
                    // point into CUSTOM" idea as ResizerScreen's identical
                    // chip — see targetSizeMb's doc comment above for how
                    // batch resolves this per item instead of once.
                    item {
                        FilterChip(
                            selected = targetSizeMb != null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);                                 selectedSocialPreset = null
                                showTargetSizeDialog = true
                            },
                            label = { Text(targetSizeMb?.let { "${it} MB" } ?: "Ukuran target (MB)") }
                        )
                    }
                }
                if (targetSizeMb != null) {
                    Text(
                        "Bitrate akan dihitung per video sesuai durasi masing-masing saat batch diproses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OptionSection(
                title = "Rotasi",
                options = RotationOption.ENTRIES,
                labelOf = { it.label },
                selected = rotation,
                onSelect = { rotation = it }
            )

            OptionSection(
                title = "Flip / cermin",
                options = FlipOption.ENTRIES,
                labelOf = { it.label },
                selected = flip,
                onSelect = { flip = it }
            )

            OptionSection(
                title = "Frame rate",
                options = FrameRateOption.ENTRIES,
                labelOf = { it.label },
                selected = frameRate,
                onSelect = { frameRate = it }
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Mute audio", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
                Switch(checked = muteAudio, onCheckedChange = { muteAudio = it })
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Watermark / logo", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    if (watermarkUri == null) {
                        TextButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); pickWatermarkLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text("Pilih gambar") }
                    } else {
                        TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); watermarkUri = null }) { Text("Hapus") }
                    }
                }
                if (watermarkUri != null) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(WatermarkPosition.ENTRIES) { pos ->
                            FilterChip(
                                selected = pos == watermarkPosition,
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); watermarkPosition = pos },
                                label = { Text(pos.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Caption", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                OutlinedTextField(
                    value = captionText,
                    onValueChange = { captionText = it },
                    placeholder = { Text("Tulis caption singkat…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (captionText.isNotBlank()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(WatermarkPosition.ENTRIES) { pos ->
                            FilterChip(
                                selected = pos == captionPosition,
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); captionPosition = pos },
                                label = { Text(pos.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            if (isProcessing) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Memproses video ${(currentIndex + 1).coerceAtLeast(1)} dari ${items.size}…",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    OutlinedButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); cancelBatch() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Batalkan batch")
                    }
                }
            } else {
                Button(
                    onClick = { startBatch() },
                    enabled = items.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Proses semua (${items.size})")
                }
            }

            summaryMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
            }

            Text(
                "Hasil setiap video otomatis tersimpan ke Galeri > Movies > VideoResizer dan muncul di Studio, sama seperti export satu-per-satu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/**
 * Unified video editor preview: the ExoPlayer preview, the filmstrip, the
 * trim range handles, and a live playhead all live in one component so the
 * "video's own timeline" and the "trim scrubber" are the same thing, not two
 * separate controls.
 */
@Composable
private fun VideoEditorPreview(
    uri: Uri,
    sourceWidth: Int,
    sourceHeight: Int,
    durationMs: Long,
    filmstrip: List<Bitmap>,
    trimRange: ClosedFloatingPointRange<Float>,
    isForeground: Boolean,
    onTrimRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onPickDifferent: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    var isPlaying by remember { mutableStateOf(false) }

    // BUG FIX: since ResizerScreen (and this preview inside it) now stays
    // permanently composed even while Studio/Batch is open on top of it
    // (see VideoResizerApp's overlay fix), this player is no longer
    // destroyed just by navigating away — so without this, a video that
    // was playing would keep playing its audio in the background behind
    // the Studio/Batch screen. This pauses it the moment ResizerScreen
    // stops being the visible screen, and leaves it paused (not resumed)
    // when coming back — matching normal "you left, it stopped" behavior.
    LaunchedEffect(isForeground) {
        if (!isForeground) {
            exoPlayer.pause()
            isPlaying = false
        }
    }

    // FIX: Ghost Audio Background.
    // Releasing only onDispose isn't enough: launching the system Photo
    // Picker (via "Ganti video") backgrounds this Activity with
    // onPause()/onStop() but does NOT recompose this node away, so the
    // player never learns it should stop. This lifecycle observer forces
    // an explicit pause the moment the Activity loses foreground.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
                isPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    // PERF: updated every 100ms while playing — unboxed Float avoids an
    // allocation on every one of those ticks.
    var playheadFraction by remember { mutableFloatStateOf(0f) }

    // Poll playback position while playing, to animate the filmstrip playhead.
    LaunchedEffect(exoPlayer, isPlaying) {
        while (isPlaying) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0)
            playheadFraction = if (durationMs > 0) (pos.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            kotlinx.coroutines.delay(100)
        }
    }

    val startMs = (trimRange.start * durationMs).toLong()
    val endMs = (trimRange.endInclusive * durationMs).toLong()

    // BUG FIX (reported: play/pause button never fades, whether playing or
    // paused/static): there used to be no auto-hide logic at all — the
    // button sat permanently opaque on top of the video, which is both
    // visually noisy over a static (paused) frame and, more importantly,
    // stays in the way *during* playback when a standard player's controls
    // are expected to step aside. `controlsVisible` drives a fade; it's
    // reset to true (and the hide timer restarted) on every play/pause tap
    // and on every direct tap on the video itself, but only actually
    // *counts down* to hidden while playing — while paused/static the
    // button stays visible, since there's nothing to watch unobstructed.
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(isPlaying, controlsVisible) {
        if (isPlaying && controlsVisible) {
            kotlinx.coroutines.delay(2500)
            controlsVisible = false
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { controlsVisible = true }
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false // the filmstrip below is the real scrubber
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                // Fully-qualified call (not the bare `AnimatedVisibility(...)`
                // import): this Box sits inside the Card's outer Column, so
                // Kotlin's implicit-receiver search also finds the
                // ColumnScope.AnimatedVisibility extension from that
                // enclosing scope and prefers it over the plain top-level
                // composable — which then fails to compile because we're
                // not directly in a ColumnScope here. Qualifying the call
                // sidesteps the ambiguity entirely.
                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.seekTo(startMs)
                                exoPlayer.play()
                            }
                            isPlaying = !isPlaying
                            controlsVisible = true
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.45f), shape = androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatSeconds(0), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(formatSeconds(durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }

            // Unified trim control: the filmstrip IS the scrubber. A purple
            // frame sits directly on top of the thumbnail row, its left/right
            // edges are the draggable trim handles — no separate slider
            // underneath. This replaces the old design where the filmstrip
            // was just a read-only preview and a normal Material3 RangeSlider
            // below it was the actual (visually disconnected) control; that
            // mismatch between "where you look" and "where you drag" was the
            // whole complaint.
            val trackHeight = 56.dp
            val handleWidth = 16.dp
            val density = LocalDensity.current
            var trackWidthPx by remember { mutableFloatStateOf(0f) }
            val handleWidthPx = with(density) { handleWidth.toPx() }
            // Minimum gap between the two handles, in fraction-of-duration —
            // stops them crossing over or collapsing to a zero-length clip.
            val minGapFraction = remember(durationMs) {
                if (durationMs <= 0) 0.05f else (500f / durationMs).coerceIn(0.02f, 0.2f)
            }

            fun pauseIfPlaying() {
                if (isPlaying) {
                    exoPlayer.pause()
                    isPlaying = false
                }
            }

            // FIX (asymmetric edges, reported by user): the trim handles
            // below are drawn as SIBLINGS of the filmstrip Row in this Box,
            // not children of it — so the filmstrip's own `.clip(...)` on
            // its Row never applied to them. Each handle's invisible 48dp
            // touch target is wider than its visible bar, and at the two
            // extremes (fraction 0f / 1f) that made the *visible* bar hang
            // ~8dp past the filmstrip's left/right edge — a lopsided-looking
            // sliver outside the rounded corners on both sides, worse
            // whenever the trim wasn't already at the very start/end.
            // Clipping this whole Box (filmstrip + dim overlays + selection
            // frame + both handles together) to one shared rounded shape
            // makes every layer stop flush at the exact same edge, so nothing
            // in the stack can overhang past what's visually the track.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                if (filmstrip.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(trackHeight)
                            .clip(RoundedCornerShape(10.dp))
                            .onSizeChanged { trackWidthPx = it.width.toFloat() },
                    ) {
                        filmstrip.forEach { frame ->
                            Image(
                                bitmap = frame.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(trackHeight)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .onSizeChanged { trackWidthPx = it.width.toFloat() }
                    )
                }

                // Dim the parts of the filmstrip that fall outside the selected trim range.
                if (trimRange.start > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(trimRange.start.coerceIn(0f, 1f))
                            .height(trackHeight)
                            .align(Alignment.CenterStart)
                            .background(Color.Black.copy(alpha = 0.55f))
                    )
                }
                if (trimRange.endInclusive < 1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(1f - trimRange.endInclusive.coerceIn(0f, 1f))
                            .height(trackHeight)
                            .align(Alignment.CenterEnd)
                            .background(Color.Black.copy(alpha = 0.55f))
                    )
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((playheadFraction * trackWidthPx).roundToInt(), 0) }
                            .width(2.dp)
                            .height(trackHeight)
                            .background(Color.White)
                    )
                }

                if (trackWidthPx > 0f) {
                    val accent = MaterialTheme.colorScheme.primary
                    val startX = trimRange.start * trackWidthPx
                    val endX = trimRange.endInclusive * trackWidthPx

                    // Frame around the current selection, flush with the filmstrip.
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(startX.roundToInt(), 0) }
                            .width(with(density) { (endX - startX).coerceAtLeast(0f).toDp() })
                            .height(trackHeight)
                            .border(2.dp, accent, RoundedCornerShape(10.dp))
                    )

                    TrimHandle(
                        fraction = trimRange.start,
                        validRange = 0f..(trimRange.endInclusive - minGapFraction).coerceAtLeast(0f),
                        trackWidthPx = trackWidthPx,
                        handleWidth = handleWidth,
                        handleWidthPx = handleWidthPx,
                        trackHeight = trackHeight,
                        color = accent,
                        label = "Batas awal potongan, ${formatSeconds(startMs)}",
                        onDragStart = { pauseIfPlaying() },
                        onFractionChange = { newStart ->
                            onTrimRangeChange(newStart..trimRange.endInclusive)
                        }
                    )
                    TrimHandle(
                        fraction = trimRange.endInclusive,
                        validRange = (trimRange.start + minGapFraction).coerceAtMost(1f)..1f,
                        trackWidthPx = trackWidthPx,
                        handleWidth = handleWidth,
                        handleWidthPx = handleWidthPx,
                        trackHeight = trackHeight,
                        color = accent,
                        label = "Batas akhir potongan, ${formatSeconds(endMs)}",
                        onDragStart = { pauseIfPlaying() },
                        onFractionChange = { newEnd ->
                            onTrimRangeChange(trimRange.start..newEnd)
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val details = buildList {
                    if (sourceWidth > 0 && sourceHeight > 0) add("${sourceWidth}×${sourceHeight}")
                    add("Potong: ${formatSeconds(startMs)} — ${formatSeconds(endMs)} (${formatSeconds(endMs - startMs)})")
                }
                Text(
                    details.joinToString(" • "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPickDifferent() }) { Text("Ganti video") }
            }
        }
    }
}

/**
 * One draggable trim boundary, rendered directly on top of the filmstrip at
 * [fraction] of its width — this and its sibling (for the other edge) ARE
 * the trim control now, not a decoration next to a separate slider.
 *
 * Drag accumulation happens in fraction-space via `currentFraction`, which
 * is deliberately re-seeded from the authoritative [fraction] parameter on
 * every `onDragStart` — that keeps this handle honest if the OTHER handle's
 * clamping logic nudged this one's value from outside the gesture (e.g. the
 * user drags the right handle past the left one's minimum-gap limit).
 *
 * BUG FIX (root cause of the reported "gesture feels off" defect):
 * `currentFraction` used to only be clamped to 0f..1f here, while the
 * caller separately re-clamped the value it actually applied to a tighter
 * range (leaving room for the other handle's minimum gap). Those two
 * clamps disagreeing meant the accumulator could keep climbing past what
 * was actually being shown/applied whenever a drag pushed past that
 * tighter limit — so reversing direction required "catching up" through
 * the overshoot before the handle visually moved again. Fix: the caller
 * now passes the *exact* range this handle is allowed to reach
 * ([validRange]), and every drag step clamps `currentFraction` to that same
 * range before it's ever used — the accumulator can no longer disagree
 * with what's applied, so there's no overshoot left to catch up from.
 */
@Composable
private fun TrimHandle(
    fraction: Float,
    validRange: ClosedFloatingPointRange<Float>,
    trackWidthPx: Float,
    handleWidth: Dp,
    handleWidthPx: Float,
    trackHeight: Dp,
    color: Color,
    /** Batch 46 (Prioritas 7 — Accessibility): TalkBack label for this handle, e.g. "Batas awal potongan, 0:03". Was previously unreachable/nameless to a screen reader — this Box only had a raw pointerInput drag detector, no semantics node at all. */
    label: String,
    onDragStart: () -> Unit,
    onFractionChange: (Float) -> Unit
) {
    // PERF: this is the single hottest piece of state in the whole
    // screen — it's written on every raw onDrag pointer-move event, easily
    // dozens of times a second while a user's finger is moving. A boxed
    // Float here means an allocation per pointer-move; mutableFloatStateOf
    // stores the primitive directly.
    var currentFraction by remember { mutableFloatStateOf(fraction) }

    // BUG FIX: trackWidthPx and validRange both change over the handle's
    // lifetime (track width settles after first layout; validRange shifts
    // whenever the other handle moves) without a drag necessarily being in
    // progress. Reading them through rememberUpdatedState instead of
    // capturing them directly in the pointerInput key means the active
    // gesture detector coroutine always sees the latest values on its next
    // onDrag callback, without needing to restart — restarting mid-drag
    // would silently drop whatever finger movement was already in
    // progress, which is its own separate "gesture just stops working"
    // defect this also closes off.
    val latestTrackWidthPx = rememberUpdatedState(trackWidthPx)
    val latestValidRange = rememberUpdatedState(validRange)

    // BUG FIX: touch target used to be exactly the visible bar's width
    // (16dp) — well under Android's 48dp minimum recommended touch target,
    // making the handle genuinely hard to grab precisely (easy to miss,
    // which reads as "the drag doesn't work right" even though the drag
    // logic itself was fine in that instance). The invisible outer hit box
    // is widened to 48dp while the visible bar inside stays the original
    // width, so the control doesn't look any different.
    val touchTargetWidth = 48.dp
    val touchTargetWidthPx = with(LocalDensity.current) { touchTargetWidth.toPx() }
    val handleWidthPxLocal = handleWidthPx

    // BUG FIX (reported: handle looks asymmetric, gets thinner toward the
    // ends): the visible bar used to just be centered on `fraction *
    // trackWidthPx` with no clamp of its own. Near the two extremes
    // (fraction close to 0f or 1f) that center point puts up to half the
    // 16dp bar outside [0, trackWidthPx] — and since the parent Box clips
    // to the track's rounded shape (needed so the bar never overhangs past
    // the filmstrip's rounded corners), that overhanging half gets sliced
    // off. The bar doesn't move, but less and less of it is left
    // *unclipped* as it nears an edge, which reads as it thinning out.
    // Fix: compute how far the visible bar would want to sit outside
    // [0, trackWidthPx - handleWidthPx] and cancel exactly that with an
    // inner offset, so the full-width bar itself never crosses the track
    // boundary — nothing left for the clip to cut into, at any fraction.
    val barCorrectionPx = if (trackWidthPx > 0f) {
        val desiredBarLeft = fraction * trackWidthPx - handleWidthPxLocal / 2f
        val clampedBarLeft = desiredBarLeft.coerceIn(0f, (trackWidthPx - handleWidthPxLocal).coerceAtLeast(0f))
        clampedBarLeft - desiredBarLeft
    } else 0f

    Box(
        modifier = Modifier
            // BUG FIX: Modifier.offset() mirrors its x offset in RTL
            // layouts (this app declares android:supportsRtl="true"), but
            // detectDragGestures reports dragAmount in raw, un-mirrored
            // physical-pixel space. Mixing the two meant a right-to-left
            // locale would have dragged handles backwards relative to the
            // finger. absoluteOffset never mirrors, matching the
            // coordinate space the drag delta actually arrives in.
            .absoluteOffset {
                IntOffset((fraction * trackWidthPx - touchTargetWidthPx / 2).roundToInt(), 0)
            }
            .width(touchTargetWidth)
            .height(trackHeight)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        currentFraction = fraction
                        onDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val range = latestValidRange.value
                        currentFraction = (currentFraction + dragAmount.x / latestTrackWidthPx.value)
                            .coerceIn(range.start, range.endInclusive)
                        onFractionChange(currentFraction)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // The actually-visible handle — same size/appearance as before,
        // centered inside the touch target by default, then nudged back
        // inward by barCorrectionPx whenever centering it would have
        // pushed part of it past the track's edge.
        Box(
            Modifier
                .absoluteOffset { IntOffset(barCorrectionPx.roundToInt(), 0) }
                .width(handleWidth)
                .height(trackHeight)
                .background(color, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Small grip mark so the handle doesn't read as just a plain bar.
            Box(
                Modifier
                    .width(2.dp)
                    .height(20.dp)
                    .background(Color.White, RoundedCornerShape(1.dp))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPickerCard(onPickClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Card(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPickClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text("Tap to choose a video", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text("Dari Galeri langsung", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CustomResolutionDialog(
    initialWidth: Int,
    initialHeight: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // FIX: No Input Guard. 4K UHD is a hard ceiling — past this, the
    // hardware encoder on low-end devices becomes crash-prone.
    val maxWidth = 3840
    val maxHeight = 2160
    val minDimension = 16

    // UX FIX: previously this seeded the fields with initialWidth/Height
    // even when they were 0 or already outside the 16..4K range (e.g. the
    // very first time Custom is opened, before sourceWidth/Height finished
    // loading, or on a source that's technically bigger than 4K) — the
    // dialog would then open already showing a red error the user hasn't
    // caused anything to trigger. Only pre-fill when the value is actually
    // valid; otherwise start blank and let the user type.
    var widthText by remember { mutableStateOf(if (initialWidth in minDimension..maxWidth) initialWidth.toString() else "") }
    var heightText by remember { mutableStateOf(if (initialHeight in minDimension..maxHeight) initialHeight.toString() else "") }

    val widthValue = widthText.toIntOrNull()
    val heightValue = heightText.toIntOrNull()

    val widthError = when {
        widthText.isEmpty() -> null
        widthValue == null -> "Tidak valid"
        widthValue < minDimension -> "Min ${minDimension}px"
        widthValue > maxWidth -> "Maks ${maxWidth}px (4K)"
        else -> null
    }
    val heightError = when {
        heightText.isEmpty() -> null
        heightValue == null -> "Tidak valid"
        heightValue < minDimension -> "Min ${minDimension}px"
        heightValue > maxHeight -> "Maks ${maxHeight}px (4K)"
        else -> null
    }

    val isValid = widthValue != null && heightValue != null &&
        widthValue in minDimension..maxWidth &&
        heightValue in minDimension..maxHeight

    AlertDialog(
        onDismissRequest = {
            // FIX: Accidental Dismiss. Intentionally left empty — tapping
            // outside must NOT close this dialog and silently discard
            // whatever the user has typed. Only Save/Cancel below can.
        },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text("Resolusi Kustom") },
        text = {
            // FIX: Keyboard Blocks Dialog. imePadding() pushes this content
            // (and the Save/Cancel buttons) up above the keyboard so they
            // never end up hidden on small screens.
            Column(
                modifier = Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { input -> widthText = input.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Lebar") },
                        singleLine = true,
                        isError = widthError != null,
                        supportingText = { widthError?.let { Text(it) } },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { input -> heightText = input.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Tinggi") },
                        singleLine = true,
                        isError = heightError != null,
                        supportingText = { heightError?.let { Text(it) } },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "Maksimal 3840×2160 (4K)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val w = widthValue
                    val h = heightValue
                    if (isValid && w != null && h != null) onSave(w, h)
                },
                enabled = isValid
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDismiss() }) { Text("Batal") }
        }
    )
}

@Composable
private fun CustomBitrateDialog(
    initialKbps: Int?,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var kbpsText by remember { mutableStateOf(initialKbps?.toString() ?: "") }
    val kbpsValue = kbpsText.toIntOrNull()
    val error = when {
        kbpsText.isEmpty() -> null
        kbpsValue == null -> "Tidak valid"
        kbpsValue < VideoResizer.MIN_BITRATE_KBPS -> "Min ${VideoResizer.MIN_BITRATE_KBPS} kbps"
        kbpsValue > VideoResizer.MAX_BITRATE_KBPS -> "Maks ${VideoResizer.MAX_BITRATE_KBPS} kbps"
        else -> null
    }
    val isValid = kbpsValue != null && kbpsValue in VideoResizer.MIN_BITRATE_KBPS..VideoResizer.MAX_BITRATE_KBPS

    AlertDialog(
        onDismissRequest = {
            // Same "accidental dismiss" guard as CustomResolutionDialog:
            // tapping outside must not silently discard a typed value.
        },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text("Bitrate Kustom") },
        text = {
            Column(
                modifier = Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = kbpsText,
                    onValueChange = { input -> kbpsText = input.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Bitrate video (kbps)") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Contoh acuan: 2000–4000 kbps untuk 720p, 4000–8000 kbps untuk 1080p. Semakin rendah, semakin kecil ukuran file tapi semakin turun kualitas gambar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); kbpsValue?.let { if (isValid) onSave(it) } },
                enabled = isValid
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDismiss() }) { Text("Batal") }
        }
    )
}

/**
 * Alternate entry point into the same quality=CUSTOM/customBitrateKbps
 * state CustomBitrateDialog writes to, just driven by a target file size in
 * MB instead of a raw kbps number — see
 * VideoResizer.requiredBitrateKbpsForTargetSize for the MB -> kbps math.
 */
@Composable
private fun TargetSizeDialog(
    durationMs: Long,
    muteAudio: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var sizeText by remember { mutableStateOf("") }
    val sizeValue = sizeText.toDoubleOrNull()
    val computedKbps = if (sizeValue != null && sizeValue > 0.0 && durationMs > 0) {
        VideoResizer.requiredBitrateKbpsForTargetSize(sizeValue, durationMs, muteAudio)
    } else {
        null
    }
    // requiredBitrateKbpsForTargetSize clamps its result to
    // MIN_BITRATE_KBPS..MAX_BITRATE_KBPS (same floor/ceiling
    // estimateOutputSizeBytes uses) so a bad target can't produce an
    // unusable file — but that means an extreme target (e.g. 1MB for a
    // 10-minute clip) silently returns a *bigger* bitrate than requested,
    // so the real output won't actually land near the size the user typed.
    // Surface that instead of quietly showing a number that looks precise.
    val isClamped = computedKbps == VideoResizer.MIN_BITRATE_KBPS || computedKbps == VideoResizer.MAX_BITRATE_KBPS
    val error = when {
        sizeText.isEmpty() -> null
        sizeValue == null || sizeValue <= 0.0 -> "Tidak valid"
        durationMs <= 0 -> "Pilih video & rentang trim dulu"
        computedKbps == null -> "Ukuran ini terlalu kecil untuk durasi klip ini"
        else -> null
    }

    AlertDialog(
        onDismissRequest = {
            // Same "accidental dismiss" guard as CustomBitrateDialog: tapping
            // outside must not silently discard a typed value.
        },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text("Ukuran Target (MB)") },
        text = {
            Column(
                modifier = Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = sizeText,
                    onValueChange = { input -> sizeText = input.filter { c -> c.isDigit() || c == '.' }.take(6) },
                    label = { Text("Ukuran file target (MB)") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        when {
                            error != null -> Text(error)
                            isClamped && computedKbps == VideoResizer.MIN_BITRATE_KBPS ->
                                Text("≈ $computedKbps kbps (minimum) — ukuran target terlalu kecil untuk durasi klip ini, hasil akhir akan lebih besar dari target.")
                            isClamped ->
                                Text("≈ $computedKbps kbps (maksimum) — ukuran target ini sudah di atas kualitas tertinggi yang didukung.")
                            computedKbps != null -> Text("≈ $computedKbps kbps untuk durasi klip yang dipilih saat ini.")
                            else -> Text("Contoh: 16 untuk target upload WhatsApp Status/Story.")
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Perkiraan kasar (asumsi audio ~128 kbps jika tidak dibisukan). Bitrate video dihitung mundur dari ukuran target dibagi durasi klip yang sedang dipilih.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); computedKbps?.let(onSave) },
                enabled = computedKbps != null
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDismiss() }) { Text("Batal") }
        }
    )
}

/**
 * BatchScreen's counterpart to [TargetSizeDialog] — deliberately simpler:
 * no live "≈ X kbps" preview, since a batch queue can hold videos of
 * different durations and there's no single duration to compute one
 * against here. Just collects the target MB; startBatch() solves MB->kbps
 * separately for each item against that item's own duration.
 */
@Composable
private fun BatchTargetSizeDialog(
    initialMb: Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var sizeText by remember { mutableStateOf(initialMb?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "") }
    val sizeValue = sizeText.toDoubleOrNull()
    val isValid = sizeValue != null && sizeValue > 0.0

    AlertDialog(
        onDismissRequest = { /* accidental-dismiss guard, same as the other dialogs in this file */ },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text("Ukuran Target (MB) — Batch") },
        text = {
            Column(
                modifier = Modifier
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = sizeText,
                    onValueChange = { input -> sizeText = input.filter { c -> c.isDigit() || c == '.' }.take(6) },
                    label = { Text("Ukuran file target per video (MB)") },
                    singleLine = true,
                    isError = sizeText.isNotEmpty() && !isValid,
                    supportingText = {
                        if (sizeText.isNotEmpty() && !isValid) Text("Tidak valid") else Text("Contoh: 16 untuk target upload WhatsApp Status/Story.")
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Berlaku untuk semua video di antrian — bitrate dihitung ulang per video sesuai durasinya masing-masing saat batch diproses, bukan satu bitrate tetap untuk semua.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); sizeValue?.let(onSave) },
                enabled = isValid
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDismiss() }) { Text("Batal") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionSection(
    title: String,
    options: List<T>,
    labelOf: (T) -> String,
    selected: T,
    onSelect: (T) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(options) { option ->
                val isSelected = option == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(option) },
                    label = { Text(labelOf(option)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioScreen(
    onBack: () -> Unit,
    onEditAgain: (VideoHistoryEntry) -> Unit,
    onEditFailed: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // PERF: VideoHistoryStore.getAll() parses a JSON array out of
    // SharedPreferences and sorts it — real, if small, CPU + I/O work.
    // Seeding this via remember{} ran it synchronously during Studio's
    // first composition, on Main. Starting from an empty list and loading
    // via LaunchedEffect(Unit) on Dispatchers.IO keeps first frame instant;
    // the (very brief) empty-state flash is a fair trade for never
    // blocking a frame on disk I/O.
    var entries by remember { mutableStateOf<List<VideoHistoryEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        entries = withContext(Dispatchers.IO) { VideoHistoryStore.getAll(context) }
    }

    // Sweep-select (Batch 41): long-press any card to enter multi-select
    // mode, tap more cards to add/remove, then bulk-delete via the top
    // bar. selectedIds keyed by VideoHistoryEntry.id (String).
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    fun exitSelection() { selectionMode = false; selectedIds = emptySet() }
    fun toggleSelected(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        if (selectedIds.isEmpty()) selectionMode = false
    }

    // UX FIX: Accidental Delete. Tapping the trash icon used to delete the
    // video + thumbnail file immediately and permanently — a single
    // mis-tap in a scrolling list had no way back. Now it just marks a
    // pending entry and a confirmation dialog does the actual delete.
    var pendingDeleteEntry by remember { mutableStateOf<VideoHistoryEntry?>(null) }
    pendingDeleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeleteEntry = null },
            title = { Text("Hapus video ini?") },
            text = { Text("Video dan file hasil resize akan dihapus permanen dan tidak bisa dikembalikan.") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    pendingDeleteEntry = null
                    scope.launch {
                        withContext(Dispatchers.IO) { VideoHistoryStore.deleteWithFiles(context, entry) }
                        entries = withContext(Dispatchers.IO) { VideoHistoryStore.getAll(context) }
                    }
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); pendingDeleteEntry = null }) { Text("Batal") }
            }
        )
    }

    // Bulk delete (sweep-select, Batch 41) — same pending+confirm pattern
    // as single delete above, so a mis-tap on the bulk delete icon can't
    // wipe the selection permanently either.
    var pendingBulkDelete by remember { mutableStateOf(false) }
    if (pendingBulkDelete) {
        AlertDialog(
            onDismissRequest = { pendingBulkDelete = false },
            title = { Text("Hapus ${selectedIds.size} video ini?") },
            text = { Text("Video dan file hasil resize yang dipilih akan dihapus permanen dan tidak bisa dikembalikan.") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val toDelete = entries.filter { it.id in selectedIds }
                    pendingBulkDelete = false
                    exitSelection()
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            toDelete.forEach { VideoHistoryStore.deleteWithFiles(context, it) }
                        }
                        entries = withContext(Dispatchers.IO) { VideoHistoryStore.getAll(context) }
                    }
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); pendingBulkDelete = false }) { Text("Batal") }
            }
        )
    }

    // FIX: Broken System Back Navigation.
    // Screen.STUDIO has no real back-stack entry, so a system back gesture
    // falls through to the Activity and finishes it. This intercepts that
    // gesture and routes it back to Screen.MAIN instead.
    androidx.activity.compose.BackHandler(enabled = true) {
        if (selectionMode) exitSelection() else onBack()
    }

    val isGlass = com.example.videoresizer.ui.theme.LocalIsGlassTheme.current
    val screenBackground = if (isGlass) {
        com.example.videoresizer.ui.theme.MidnightBlueGlassGradient
    } else {
        androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.background)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().background(screenBackground),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionMode) "${selectedIds.size} dipilih" else "Studio",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (selectionMode) exitSelection() else onBack()
                    }) {
                        Icon(
                            if (selectionMode) Icons.Filled.Close else Icons.Filled.ArrowBack,
                            contentDescription = if (selectionMode) "Batal pilih" else "Kembali"
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedIds = if (selectedIds.size == entries.size) emptySet() else entries.map { it.id }.toSet()
                        }) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Pilih semua")
                        }
                        IconButton(
                            enabled = selectedIds.isNotEmpty(),
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); pendingBulkDelete = true }
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Hapus yang dipilih", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Belum ada video yang diproses. Hasil resize akan muncul di sini.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    StudioEntryCard(
                        entry = entry,
                        selectionMode = selectionMode,
                        isSelected = entry.id in selectedIds,
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectionMode = true
                            toggleSelected(entry.id)
                        },
                        onToggleSelect = { toggleSelected(entry.id) },
                        onEditAgain = {
                            // PERF: setDataSource()/extractMetadata() are
                            // blocking I/O — hopping to Dispatchers.IO keeps
                            // this off Main even though it's just a quick
                            // probe to confirm the source file still exists.
                            scope.launch {
                                val stillPlayable = withContext(Dispatchers.IO) {
                                    val retriever = MediaMetadataRetriever()
                                    try {
                                        retriever.setDataSource(context, Uri.parse(entry.sourceUri))
                                        (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0
                                    } catch (_: Exception) {
                                        false
                                    } finally {
                                        retriever.release()
                                    }
                                }
                                if (stillPlayable) onEditAgain(entry) else onEditFailed()
                            }
                        },
                        onShare = {
                            val publicUri = entry.publicUri
                            if (entry.kind == "GIF") {
                                if (publicUri != null) {
                                    shareGifUri(context, Uri.parse(publicUri))
                                } else {
                                    shareGifFile(context, File(entry.outputFilePath))
                                }
                            } else if (publicUri != null) {
                                shareVideoUri(context, Uri.parse(publicUri))
                            } else {
                                shareVideo(context, File(entry.outputFilePath))
                            }
                        },
                        onOpenInGallery = entry.publicUri?.let { uriString ->
                            {
                                if (entry.kind == "GIF") {
                                    openGifInGallery(context, Uri.parse(uriString))
                                } else {
                                    openInGallery(context, Uri.parse(uriString))
                                }
                            }
                        },
                        onDelete = { pendingDeleteEntry = entry }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudioEntryCard(
    entry: VideoHistoryEntry,
    selectionMode: Boolean,
    isSelected: Boolean,
    onLongPress: () -> Unit,
    onToggleSelect: () -> Unit,
    onEditAgain: () -> Unit,
    onShare: () -> Unit,
    onOpenInGallery: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var thumb by remember(entry.id) { mutableStateOf<Bitmap?>(null) }
    // Batch 44 (Prioritas 5): was decodeFile() at FULL output resolution
    // (up to ~8MB as an ARGB_8888 Bitmap for a 1080p export) for every
    // single row, just to show it at 72dp — LazyColumn compounds this
    // across however many rows are alive at once while scrolling. Now
    // downsampled to what this thumbnail actually needs. Deliberately NOT
    // adding a manual .recycle() on dispose here: with the decode already
    // this small the memory-pressure problem is solved, and recycling a
    // Bitmap Compose might still hold a lingering reference to during a
    // LazyColumn item transition/ripple isn't a risk worth taking for the
    // little it would still save.
    LaunchedEffect(entry.id) {
        thumb = withContext(Dispatchers.IO) {
            runCatching { decodeSampledBitmapFromFile(entry.thumbnailPath, 240) }.getOrNull()
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() },
                onLongClick = onLongPress
            )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Icon(
                    if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Terpilih" else "Belum dipilih",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val bmp = thumb
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        if (entry.kind == "GIF") Icons.Filled.Gif else Icons.Filled.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (entry.kind == "GIF") {
                    // GIF entries carry none of the resize-specific fields
                    // above (aspect/resolution/rotation/quality/watermark/
                    // caption/flip/frame-rate) — they're a completely
                    // different export pipeline (GifExporter, not
                    // VideoResizer/Transformer), so this branch shows the
                    // settings that actually apply to a GIF instead.
                    val gifDetails = listOfNotNull(
                        "GIF",
                        if (entry.gifFps > 0) "${entry.gifFps} fps" else null,
                        if (entry.gifWidthPx > 0) "${entry.gifWidthPx}px" else null
                    )
                    Text(
                        gifDetails.joinToString(" • "),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    val aspectLabel = AspectRatioOption.ENTRIES.firstOrNull { it.name == entry.aspectRatioName }?.label ?: entry.aspectRatioName
                    val resLabel = ResolutionOption.ENTRIES.firstOrNull { it.name == entry.resolutionName }?.label ?: entry.resolutionName
                    val rotLabel = RotationOption.ENTRIES.firstOrNull { it.name == entry.rotationName }?.label ?: entry.rotationName
                    val qualityOpt = QualityOption.ENTRIES.firstOrNull { it.name == entry.qualityName } ?: QualityOption.ORIGINAL
                    val qualityLabel = if (qualityOpt == QualityOption.CUSTOM && entry.customBitrateKbps != null) {
                        "${entry.customBitrateKbps} kbps"
                    } else {
                        qualityOpt.label
                    }
                    Text(
                        "$aspectLabel • $resLabel" + if (rotLabel != "0°") " • $rotLabel" else "",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (qualityOpt != QualityOption.ORIGINAL) {
                        Text(
                            "Kualitas: $qualityLabel",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!entry.watermarkUri.isNullOrEmpty()) {
                        Text(
                            "Watermark aktif",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (!entry.captionText.isNullOrBlank()) {
                        Text(
                            "Caption aktif",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    run {
                        // Polish (Batch 11): flip/frame-rate weren't shown anywhere
                        // in this history card before, even though they're saved
                        // per-entry since Batch 9 — matches how rotation/quality/
                        // watermark/caption already surface here.
                        val flipOpt = FlipOption.ENTRIES.firstOrNull { it.name == entry.flipName } ?: FlipOption.NONE
                        val frameRateOpt = FrameRateOption.ENTRIES.firstOrNull { it.name == entry.frameRateName } ?: FrameRateOption.ORIGINAL
                        val extras = listOfNotNull(
                            if (flipOpt != FlipOption.NONE) "Flip ${flipOpt.label}" else null,
                            if (frameRateOpt != FrameRateOption.ORIGINAL) frameRateOpt.label else null
                        )
                        if (extras.isNotEmpty()) {
                            Text(
                                extras.joinToString(" • "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Text(
                    "Durasi: ${formatSeconds(entry.trimEndMs - entry.trimStartMs)}" + if (entry.muteAudio) " • Tanpa audio" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState())
                    ) {
                        TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onEditAgain() }, contentPadding = PaddingValues(horizontal = 8.dp), enabled = !selectionMode) { Text("Edit ulang") }
                        if (onOpenInGallery != null) {
                            TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onOpenInGallery() }, contentPadding = PaddingValues(horizontal = 8.dp), enabled = !selectionMode) { Text("Galeri") }
                        }
                        TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onShare() }, contentPadding = PaddingValues(horizontal = 8.dp), enabled = !selectionMode) { Text("Bagikan") }
                    }
                    // FIX (Batch 41): delete used to live inside the
                    // horizontalScroll row above, so on narrow screens
                    // (3 text buttons already fill the width) it was
                    // scrolled off and effectively invisible/undiscoverable.
                    // Pinned outside the scroll now so it's always visible.
                    if (!selectionMode) {
                        IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDelete() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Runs the resize, then handles its result.
 *
 * IMPORTANT: [androidx.media3.transformer.Transformer.Listener.onCompleted]
 * fires on the Main thread (Transformer requires a Looper-backed thread to
 * build on, which in practice is always the UI thread here). Everything
 * downstream of a successful export — copying the output file into the
 * public Movies collection, decoding+compressing a JPEG thumbnail, and
 * writing to history — used to run directly inside that Main-thread
 * callback. For a large 1080p export those are each real, unbounded I/O
 * work; stacked together on the UI thread, a big enough file was enough to
 * blow past Android's input-dispatch timeout and trigger an ANR, every
 * single time an export finished. `scope` (the caller's
 * `rememberCoroutineScope()`, tied to that composable's lifecycle) is what
 * lets this hop to [Dispatchers.IO] for that work and only return to Main
 * to call [onDone], which touches Compose state.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun runResize(
    context: android.content.Context,
    scope: CoroutineScope,
    request: ResizeRequest,
    thumbnailFile: File? = null,
    onProgress: (Int) -> Unit = {},
    onDone: (message: String, resultFile: File?, galleryUri: Uri?) -> Unit
): androidx.media3.transformer.Transformer? {
    return VideoResizer(context).resize(
        request,
        onProgress = onProgress,
        onDone = { result ->
            when (result) {
                is ResizeResult.Success -> {
                    // Everything here is file I/O / CPU-bound work with no
                    // suspension points of its own — the whole block moves
                    // to Dispatchers.IO as one unit rather than hopping
                    // dispatchers between each step.
                    scope.launch {
                        val (message, resultFile, galleryUri) = withContext(Dispatchers.IO) {
                            val displayName = result.outputFile.name
                            val publicUri = PublicMovieExporter.publish(context, result.outputFile, displayName)
                            val message = if (publicUri != null) {
                                "Selesai. Video tersimpan di Galeri > Movies > VideoResizer ($displayName)."
                            } else {
                                "Selesai, tetapi gagal menyalin ke galeri publik. Video tetap tersedia lewat tombol Share di bawah."
                            }

                            if (thumbnailFile != null) {
                                val extracted = extractVideoThumbnail(result.outputFile, thumbnailFile)
                                if (extracted) {
                                    VideoHistoryStore.add(
                                        context,
                                        VideoHistoryEntry(
                                            id = UUID.randomUUID().toString(),
                                            createdAt = System.currentTimeMillis(),
                                            outputFilePath = result.outputFile.absolutePath,
                                            thumbnailPath = thumbnailFile.absolutePath,
                                            sourceUri = request.sourceUri.toString(),
                                            aspectRatioName = request.aspectRatio.name,
                                            resolutionName = request.resolution.name,
                                            rotationName = request.rotation.name,
                                            muteAudio = request.muteAudio,
                                            trimStartMs = request.trimStartMs,
                                            trimEndMs = request.trimEndMs,
                                            resizeModeName = request.resizeMode.name,
                                            customWidth = request.customWidth,
                                            customHeight = request.customHeight,
                                            qualityName = request.quality.name,
                                            customBitrateKbps = request.customBitrateKbps,
                                            publicUri = publicUri?.toString(),
                                            watermarkUri = request.watermarkUri?.toString(),
                                            watermarkPositionName = request.watermarkPosition.name,
                                            watermarkOpacityPercent = request.watermarkOpacityPercent,
                                            watermarkScalePercent = request.watermarkScalePercent,
                                            captionText = request.captionText,
                                            captionPositionName = request.captionPosition.name,
                                            flipName = request.flip.name,
                                            frameRateName = request.frameRate.name
                                        )
                                    )
                                }
                            }

                            Triple(message, result.outputFile, publicUri)
                        }
                        // Back on Main (scope's default context) — safe to
                        // touch Compose state here.
                        onDone(message, resultFile, galleryUri)
                    }
                }
                is ResizeResult.Failure -> {
                    // UX FIX: raw ExportException messages (codec names,
                    // stack-trace-ish text) mean nothing to a novice user.
                    // Lead with a plain-language sentence and actionable
                    // suggestion, keep the technical detail available but
                    // secondary for anyone who wants to report a bug.
                    onDone(
                        "Gagal memproses video. Coba lagi, atau pilih resolusi/durasi yang lebih kecil. " +
                            "(Detail teknis: ${result.message})",
                        null,
                        null
                    )
                }
            }
        }
    )
}

/**
 * Gallery integration: opens [uri] directly in whatever app the user has
 * set as their default video/gallery viewer (ACTION_VIEW), instead of
 * always routing through the generic Share sheet. Falls back to a plain
 * message if no such app is installed, rather than crashing.
 */
private fun openInGallery(context: android.content.Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
        }
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "Tidak ada aplikasi Galeri yang bisa membuka video ini.", android.widget.Toast.LENGTH_LONG).show()
    } catch (e: SecurityException) {
        android.widget.Toast.makeText(context, "Tidak bisa membuka video ini di Galeri.", android.widget.Toast.LENGTH_LONG).show()
    }
}

/**
 * Decodes [path] downsampled to roughly [reqHeightPx] tall instead of full
 * resolution (Batch 44 — MICRO_POLISH_GUIDE Prioritas 5). Both call sites
 * that use this (StudioEntryCard's 72dp list thumbnail, ResizerScreen's
 * before/after result preview) only ever display the result at a few
 * hundred px at most, but were decoding the underlying JPEG — which is
 * saved at the FULL output resolution by [extractVideoThumbnail], e.g.
 * ~8MB as an ARGB_8888 Bitmap for a 1080p export — at full size every
 * single time. `inJustDecodeBounds` reads just the header first (no pixel
 * data) to compute a power-of-two `inSampleSize`, so the real decode below
 * never allocates more than it needs to actually show on screen.
 */
private fun decodeSampledBitmapFromFile(path: String, reqHeightPx: Int): Bitmap? {
    return try {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOptions)
        var sampleSize = 1
        val rawHeight = boundsOptions.outHeight
        if (rawHeight > reqHeightPx && reqHeightPx > 0) {
            while (rawHeight / (sampleSize * 2) >= reqHeightPx) sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        BitmapFactory.decodeFile(path, decodeOptions)
    } catch (e: Exception) {
        null
    }
}

/** Grabs the first frame of [videoFile] and saves it as a JPEG at [thumbnailFile]. Returns success. */
private fun extractVideoThumbnail(videoFile: File, thumbnailFile: File): Boolean {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(videoFile.absolutePath)
        val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return false
        java.io.FileOutputStream(thumbnailFile).use { out ->
            frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        frame.recycle()
        true
    } catch (e: Exception) {
        false
    } finally {
        retriever.release()
    }
}

private fun shareVideo(context: android.content.Context, file: File) {
    if (!file.exists()) {
        android.widget.Toast.makeText(context, "File video tidak ditemukan (mungkin cache sudah dibersihkan).", android.widget.Toast.LENGTH_LONG).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    shareVideoUri(context, uri)
}

/**
 * Gallery integration: shares the durable public MediaStore copy directly
 * by its content:// Uri (no FileProvider wrapping needed — it's already a
 * content Uri). Prefer this over [shareVideo] wherever a [VideoHistoryEntry]
 * has [VideoHistoryEntry.publicUri] set, since the private cache file it
 * also tracks can be evicted by Android at any time under storage
 * pressure, while the public Gallery copy sticks around.
 */
private fun shareVideoUri(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share resized video"))
}

/** GIF counterpart of [shareVideo] — same FileProvider-wrapping approach, just "image/gif" mime. */
private fun shareGifFile(context: android.content.Context, file: File) {
    if (!file.exists()) {
        android.widget.Toast.makeText(context, "File GIF tidak ditemukan (mungkin cache sudah dibersihkan).", android.widget.Toast.LENGTH_LONG).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    shareGifUri(context, uri)
}

/**
 * GIF counterpart of [shareVideoUri] — shares the durable public MediaStore
 * copy directly by its content:// Uri. Prefer this over [shareGifFile]
 * wherever a [VideoHistoryEntry] has a non-null publicUri, same reasoning
 * as the video-share helpers above.
 */
private fun shareGifUri(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/gif"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share GIF"))
}

/** GIF counterpart of [openInGallery] — same ACTION_VIEW approach, just "image/gif" mime. */
private fun openGifInGallery(context: android.content.Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/gif")
        }
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "Tidak ada aplikasi Galeri yang bisa membuka GIF ini.", android.widget.Toast.LENGTH_LONG).show()
    } catch (e: SecurityException) {
        android.widget.Toast.makeText(context, "Tidak bisa membuka GIF ini di Galeri.", android.widget.Toast.LENGTH_LONG).show()
    }
}

/**
 * Standalone "Video ke GIF" screen. Deliberately not folded into
 * ResizerScreen: GIF export is a completely different pipeline
 * (GifExporter, no Transformer/Media3 involved) with its own narrower
 * settings (fps/width instead of the full resize option set), so a separate
 * screen keeps both simpler than one screen branching on output format.
 *
 * Reuses VideoPickerCard/VideoEditorPreview/FilmstripExtractor from the
 * main resizer flow for a consistent picking/trimming experience.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GifScreen(
    onBack: () -> Unit,
    prefill: GifPrefill? = null,
    onPrefillConsumed: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var sourceWidth by remember { mutableIntStateOf(0) }
    var sourceHeight by remember { mutableIntStateOf(0) }
    var trimRange by remember { mutableStateOf(0f..1f) }
    var filmstrip by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var fps by remember { mutableIntStateOf(10) }
    var targetWidth by remember { mutableIntStateOf(360) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var resultFile by remember { mutableStateOf<File?>(null) }
    var galleryUri by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var activeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Shared by the picker launcher below and the prefill effect further
    // down — both need "duration/width/height of this uri, or null if it
    // can't be read", so this factors out the duplicate MediaMetadataRetriever
    // probe rather than having two near-identical copies of it in one screen.
    suspend fun loadSourceMetadata(uri: Uri): Triple<Long, Int, Int>? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            if (d > 0) Triple(d, w, h) else null
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    // Batch 13: video Uri now comes from the in-app VideoPickerScreen
    // overlay instead of the OS Photo Picker — see the identical comment on
    // ResizerScreen's handlePickedVideo for why takePersistableUriPermission
    // is still attempted (harmless no-op for a plain MediaStore Uri).
    var showVideoPicker by remember { mutableStateOf(false) }
    fun handlePickedVideo(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        resultFile = null
        galleryUri = null
        message = null
        scope.launch {
            val loaded = loadSourceMetadata(uri)
            if (loaded != null) {
                selectedUri = uri
                durationMs = loaded.first
                sourceWidth = loaded.second
                sourceHeight = loaded.third
                trimRange = 0f..1f
            } else {
                message = "Video ini tidak bisa dibaca."
            }
        }
    }

    // "Edit ulang" from a saved GIF history entry — see GifPrefill's doc
    // comment. Re-probes duration/width/height rather than trusting a
    // possibly-stale saved value, same caution StudioScreen's onEditAgain
    // already applies before reopening ResizerScreen with a video prefill.
    LaunchedEffect(prefill) {
        val p = prefill ?: return@LaunchedEffect
        val loaded = loadSourceMetadata(p.uri)
        if (loaded != null) {
            val (d, w, h) = loaded
            selectedUri = p.uri
            durationMs = d
            sourceWidth = w
            sourceHeight = h
            fps = p.fps
            targetWidth = p.targetWidth
            trimRange = (p.trimStartMs.toFloat() / d).coerceIn(0f, 1f)..(p.trimEndMs.toFloat() / d).coerceIn(0f, 1f)
            resultFile = null
            galleryUri = null
            message = null
        } else {
            message = "Video sumber tidak lagi bisa diakses (mungkin sudah dihapus/dipindah)."
        }
        onPrefillConsumed()
    }

    LaunchedEffect(selectedUri, durationMs) {
        val uri = selectedUri
        filmstrip = if (uri != null && durationMs > 0) {
            withContext(Dispatchers.IO) { FilmstripExtractor.extract(context, uri, durationMs, count = 8) }
        } else {
            emptyList()
        }
    }

    val startMs = (trimRange.start * durationMs).toLong()
    val endMs = (trimRange.endInclusive * durationMs).toLong()
    val clipSeconds = (endMs - startMs).coerceAtLeast(0L) / 1000.0
    val estimatedFrames = (clipSeconds * fps).toInt().coerceAtLeast(0)
    // Mirrors GifExporter.MAX_FRAMES: keeps the button's own warning in sync
    // with the backstop the exporter itself enforces, rather than letting
    // the UI promise something the exporter would silently cap anyway.
    val framesTooMany = estimatedFrames > GifExporter.MAX_FRAMES

    // Same reasoning as BatchScreen's BackHandler fix (see its comment):
    // no Navigation-Compose back stack here, screens are manual state, so
    // without this a system back press/gesture falls through to the
    // default platform behavior and exits the whole app instead of
    // returning to the Resizer screen underneath.
    // UX FIX (Batch 43, audit "back/cancel belum merata"): both the
    // system back gesture and the toolbar arrow used to silently cancel
    // activeJob + exit with zero warning while a GIF export was actively
    // running — the on-screen "Batalkan" button (during progress) was the
    // only path that ever asked/confirmed anything. Same gap
    // ResizerScreen/CompressorScreen/BatchScreen already closed for
    // themselves via showExitWhileProcessingConfirm. Only asks when
    // isProcessing; back while idle behaves exactly as before (immediate
    // onBack(), no dialog).
    var showExitWhileProcessingConfirm by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = true) {
        if (isProcessing) showExitWhileProcessingConfirm = true else onBack()
    }
    if (showExitWhileProcessingConfirm) {
        AlertDialog(
            onDismissRequest = { showExitWhileProcessingConfirm = false },
            title = { Text("Batalkan proses?") },
            text = { Text("GIF sedang dibuat. Keluar sekarang akan menghentikan dan membatalkan proses ini.") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showExitWhileProcessingConfirm = false
                    activeJob?.cancel()
                    isProcessing = false
                    onBack()
                }) { Text("Batalkan proses", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); showExitWhileProcessingConfirm = false }) { Text("Tetap di sini") }
            }
        )
    }

    // Batch 13: same Box-wrap-Scaffold overlay pattern as ResizerScreen, so
    // VideoPickerScreen can render on top without unmounting this screen.
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video ke GIF") },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isProcessing) showExitWhileProcessingConfirm = true else onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (selectedUri == null || durationMs <= 0) {
                VideoPickerCard(
                    onPickClick = { showVideoPicker = true }
                )
            } else {
                // Local val capture instead of !!, same reasoning as
                // ResizerScreen's identical comment above: selectedUri is
                // Compose mutable state (custom getter), so Kotlin can't
                // smart-cast it from the null-check in the `if` above.
                val currentUri = selectedUri
                if (currentUri != null) {
                VideoEditorPreview(
                    uri = currentUri,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight,
                    durationMs = durationMs,
                    filmstrip = filmstrip,
                    trimRange = trimRange,
                    isForeground = true,
                    onTrimRangeChange = { trimRange = it },
                    onPickDifferent = {
                        showVideoPicker = true
                    }
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Frame rate GIF", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(listOf(5, 10, 15)) { f ->
                            FilterChip(
                                selected = fps == f,
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); fps = f },
                                label = { Text("$f fps") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Lebar GIF", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(listOf(240, 360, 480)) { w ->
                            FilterChip(
                                selected = targetWidth == w,
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); targetWidth = w },
                                label = { Text("${w}px") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                Text(
                    if (framesTooMany) {
                        "Perkiraan: ~$estimatedFrames frame — terlalu banyak untuk fps/lebar ini. Persingkat rentang trim, atau turunkan fps/lebar."
                    } else {
                        "Perkiraan: ~$estimatedFrames frame dari ${String.format(java.util.Locale.US, "%.1f", clipSeconds)} detik klip. Klip lebih panjang / fps & lebar lebih besar = proses lebih lama & file lebih besar."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isProcessing) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Membuat GIF… $progress%", color = MaterialTheme.colorScheme.onBackground)
                            OutlinedButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                activeJob?.cancel()
                                isProcessing = false
                                message = "Dibatalkan."
                            }) { Text("Batalkan") }
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            val uri = selectedUri ?: return@Button
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isProcessing = true
                            progress = 0
                            message = null
                            resultFile = null
                            galleryUri = null
                            val outFile = File(context.cacheDir, "gif_${UUID.randomUUID()}.gif")
                            activeJob = scope.launch {
                                val result = withContext(Dispatchers.Default) {
                                    GifExporter.export(
                                        context = context,
                                        sourceUri = uri,
                                        startMs = startMs,
                                        endMs = endMs,
                                        fps = fps,
                                        targetWidth = targetWidth,
                                        outputFile = outFile,
                                        onProgress = { p ->
                                            // GifExporter.export runs entirely on
                                            // Dispatchers.Default and calls this
                                            // synchronously from that background
                                            // thread — hop back to Main before
                                            // touching Compose state, rather than
                                            // relying on the snapshot system to
                                            // paper over a cross-thread write.
                                            scope.launch(Dispatchers.Main) { progress = p }
                                        }
                                    )
                                }
                                isProcessing = false
                                when (result) {
                                    is GifExportResult.Success -> {
                                        val publicUri = withContext(Dispatchers.IO) {
                                            PublicMovieExporter.publishImage(context, outFile, outFile.name)
                                        }
                                        resultFile = outFile
                                        galleryUri = publicUri
                                        message = if (publicUri != null) {
                                            "Selesai. GIF tersimpan di Galeri > Pictures > VideoResizer (${result.frameCount} frame)."
                                        } else {
                                            "GIF selesai dibuat (${result.frameCount} frame), tapi gagal disalin ke galeri publik. Tetap tersedia lewat tombol Share di bawah."
                                        }
                                        // Studio history entry (Batch 12).
                                        // thumbnailPath deliberately points
                                        // at the GIF file itself rather than
                                        // a separately-extracted static
                                        // frame: BitmapFactory.decodeFile
                                        // (what StudioEntryCard already
                                        // uses for the thumbnail) happily
                                        // reads a GIF's first frame as a
                                        // plain Bitmap, so no extra
                                        // thumbnail-generation step is
                                        // needed the way the video path
                                        // needs extractVideoThumbnail.
                                        withContext(Dispatchers.IO) {
                                            VideoHistoryStore.add(
                                                context,
                                                VideoHistoryEntry(
                                                    id = UUID.randomUUID().toString(),
                                                    createdAt = System.currentTimeMillis(),
                                                    outputFilePath = outFile.absolutePath,
                                                    thumbnailPath = outFile.absolutePath,
                                                    sourceUri = uri.toString(),
                                                    aspectRatioName = AspectRatioOption.ORIGINAL.name,
                                                    resolutionName = ResolutionOption.ORIGINAL.name,
                                                    rotationName = RotationOption.NONE.name,
                                                    muteAudio = false,
                                                    trimStartMs = startMs,
                                                    trimEndMs = endMs,
                                                    publicUri = publicUri?.toString(),
                                                    kind = "GIF",
                                                    gifFps = fps,
                                                    gifWidthPx = targetWidth
                                                )
                                            )
                                        }
                                    }
                                    is GifExportResult.Failure -> {
                                        message = "Gagal membuat GIF: ${result.reason}"
                                    }
                                }
                            }
                        },
                        enabled = endMs > startMs && estimatedFrames > 0 && !framesTooMany,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Buat GIF")
                    }
                }

                message?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                }

                if (resultFile != null) {
                    // Local val capture instead of !!, same convention as
                    // the rest of this file (see the comment on
                    // `currentUri` above).
                    val savedGifFile = resultFile
                    val savedGalleryUri = galleryUri
                    if (savedGifFile != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); shareGifFile(context, savedGifFile) }) { Text("Bagikan") }
                            if (savedGalleryUri != null) {
                                OutlinedButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); openGifInGallery(context, savedGalleryUri) }) { Text("Buka di Galeri") }
                            }
                        }
                    }
                }
                }
            }
        }
    }
    if (showVideoPicker) {
        VideoPickerScreen(
            onVideoSelected = { uri ->
                showVideoPicker = false
                handlePickedVideo(uri)
            },
            onCancel = { showVideoPicker = false }
        )
    }
    }
}

/** Metadata probed for the picked source video: duration/dimensions (same as every other screen) plus its file size in bytes, real fps, and audio-track presence/bitrate (Batch 33), needed only here to estimate/cap the compressed output size. */
private data class CompressSourceInfo(val durationMs: Long, val width: Int, val height: Int, val sizeBytes: Long, val fps: Int, val hasAudio: Boolean, val audioBitrateBps: Int)

/**
 * Compressor tab: re-encodes a whole video (optionally trimmed) as H.265 at
 * a much lower bitrate than the source needed for the same visual quality,
 * shrinking the file with no visible quality loss for normal viewing — see
 * [CompressionLevel]'s doc comment in VideoResizer.kt for the honest
 * framing ("visually transparent", not literally lossless — no re-encode
 * of an already-lossy video can be). Deliberately its own screen rather
 * than folded into ResizerScreen's quality slider: this is a one-tap
 * "just make it smaller" tool with no aspect/resolution/watermark/caption
 * knobs to configure, closer in spirit to GifScreen than to ResizerScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompressorScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var sourceWidth by remember { mutableIntStateOf(0) }
    var sourceHeight by remember { mutableIntStateOf(0) }
    var sourceSizeBytes by remember { mutableLongStateOf(0L) }
    var sourceFps by remember { mutableIntStateOf(0) }
    var sourceHasAudio by remember { mutableStateOf(true) }
    var sourceAudioBitrateBps by remember { mutableIntStateOf(0) }
    var trimRange by remember { mutableStateOf(0f..1f) }
    var filmstrip by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var level by remember { mutableStateOf(CompressionLevel.RECOMMENDED) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var resultFile by remember { mutableStateOf<File?>(null) }
    var resultSizeBytes by remember { mutableLongStateOf(0L) }
    var galleryUri by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    // BUG FIX (Batch 31): sebelumnya screen ini hanya menyimpan coroutine
    // Job pembungkus (yang langsung selesai begitu compress() memulai
    // pipeline async-nya, sebelum sempat menunggu apa pun) — jadi tombol
    // "Batalkan" & back-saat-proses TIDAK pernah benar-benar menghentikan
    // Transformer. Encoder tetap jalan diam-diam di background (buang
    // baterai/CPU) dan callback onDone masih bisa nyelonong menulis hasil
    // ke galeri/history setelah UI sudah "dibatalkan". Sama seperti
    // activeTransformer di ResizerScreen (baris ~634) & BatchScreen (baris
    // ~1821) — cancel yang benar adalah Transformer.cancel(), bukan Job.
    var activeTransformer by remember { mutableStateOf<androidx.media3.transformer.Transformer?>(null) }
    var showVideoPicker by remember { mutableStateOf(false) }

    // Same duration/width/height probe every other screen uses, plus the
    // source file's own size (via its AssetFileDescriptor length) and real
    // fps (Batch 32 — via MediaExtractor's video track MediaFormat, since
    // MediaMetadataRetriever has no reliable "playback fps" key; its
    // CAPTURE_FRAMERATE key is for slow-mo capture rate, not this), which
    // only this screen needs, to estimate/cap the compressed output size.
    suspend fun loadSourceInfo(uri: Uri): CompressSourceInfo? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val size = runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull() ?: -1L
            // 0 = "couldn't probe" — computeCompressTargetBitrateBps /
            // estimateCompressedSizeBytes both fall back to ASSUMED_FPS.
            // Same MediaExtractor pass also checks for an audio track
            // (Batch 33) — hasAudio defaults to true (old assumed-present
            // behavior) only if the probe itself fails; a probe that
            // succeeds but finds zero audio tracks correctly reports false.
            var fps = 0
            var hasAudio = true
            var audioBitrateBps = 0
            val probed = runCatching {
                val extractor = android.media.MediaExtractor()
                try {
                    extractor.setDataSource(context, uri, null)
                    var foundAudio = false
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                        if (mime.startsWith("video/") && format.containsKey(android.media.MediaFormat.KEY_FRAME_RATE)) {
                            fps = format.getInteger(android.media.MediaFormat.KEY_FRAME_RATE)
                        } else if (mime.startsWith("audio/")) {
                            foundAudio = true
                            if (format.containsKey(android.media.MediaFormat.KEY_BIT_RATE)) {
                                audioBitrateBps = format.getInteger(android.media.MediaFormat.KEY_BIT_RATE)
                            }
                        }
                    }
                    foundAudio
                } finally {
                    extractor.release()
                }
            }
            probed.onSuccess { hasAudio = it }
            if (d > 0 && size > 0) CompressSourceInfo(d, w, h, size, fps, hasAudio, audioBitrateBps) else null
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    fun handlePickedVideo(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        resultFile = null
        galleryUri = null
        message = null
        scope.launch {
            val info = loadSourceInfo(uri)
            if (info != null) {
                selectedUri = uri
                durationMs = info.durationMs
                sourceWidth = info.width
                sourceHeight = info.height
                sourceSizeBytes = info.sizeBytes
                sourceFps = info.fps
                sourceHasAudio = info.hasAudio
                sourceAudioBitrateBps = info.audioBitrateBps
                trimRange = 0f..1f
            } else {
                message = "Video ini tidak bisa dibaca."
            }
        }
    }

    LaunchedEffect(selectedUri, durationMs) {
        val uri = selectedUri
        filmstrip = if (uri != null && durationMs > 0) {
            withContext(Dispatchers.IO) { FilmstripExtractor.extract(context, uri, durationMs, count = 8) }
        } else {
            emptyList()
        }
    }

    val startMs = (trimRange.start * durationMs).toLong()
    val endMs = (trimRange.endInclusive * durationMs).toLong()
    val clipDurationMs = (endMs - startMs).coerceAtLeast(0L)
    val estimatedNewSizeBytes = if (selectedUri != null && clipDurationMs > 0) {
        VideoResizer.estimateCompressedSizeBytes(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            sourceDurationMs = durationMs,
            sourceFileSizeBytes = sourceSizeBytes,
            clipDurationMs = clipDurationMs,
            muteAudio = false,
            level = level,
            sourceFps = sourceFps,
            sourceHasAudio = sourceHasAudio,
            sourceAudioBitrateBps = sourceAudioBitrateBps
        )
    } else null
    // Same reasoning as source size: proportional slice of the whole
    // file's bytes for the trimmed range, so the "before" side of the
    // before/after comparison reflects the clip being exported, not the
    // whole source file, when a trim is applied.
    val originalClipSizeBytes = if (durationMs > 0 && sourceSizeBytes > 0) {
        (sourceSizeBytes * (clipDurationMs.toDouble() / durationMs.toDouble())).toLong()
    } else 0L

    // Batch 34: factored out so the "Batalkan" button (during progress),
    // the confirm dialog's own button, and nothing else duplicate this —
    // exact same real-cancel logic Batch 31 fixed (Transformer.cancel() +
    // ExportForegroundService.stop(), not just resetting UI state).
    fun cancelCompress() {
        activeTransformer?.cancel()
        activeTransformer = null
        ExportForegroundService.stop(context)
        isProcessing = false
        message = "Dibatalkan."
    }

    // UX FIX (Batch 34, Pending Queue item): previously back (both the
    // toolbar arrow and the system gesture/button) silently cancelled and
    // exited mid-export with zero warning — same gap ResizerScreen already
    // closed for itself via showExitWhileProcessingConfirm (baris ~648).
    // Only asks when isProcessing; back when idle behaves exactly as
    // before (immediate onBack(), no dialog).
    var showExitWhileProcessingConfirm by remember { mutableStateOf(false) }

    // Same BackHandler reasoning as every other manual-state screen here.
    androidx.activity.compose.BackHandler(enabled = true) {
        if (isProcessing) showExitWhileProcessingConfirm = true else onBack()
    }
    if (showExitWhileProcessingConfirm) {
        AlertDialog(
            onDismissRequest = { showExitWhileProcessingConfirm = false },
            title = { Text("Batalkan proses?") },
            text = { Text("Video sedang dikompres. Keluar sekarang akan menghentikan dan membatalkan proses ini.") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showExitWhileProcessingConfirm = false
                    cancelCompress()
                    onBack()
                }) { Text("Batalkan proses", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); showExitWhileProcessingConfirm = false }) { Text("Tetap di sini") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kompres Video") },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isProcessing) showExitWhileProcessingConfirm = true else onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (selectedUri == null || durationMs <= 0) {
                VideoPickerCard(
                    onPickClick = { showVideoPicker = true }
                )
            } else {
                val currentUri = selectedUri
                if (currentUri != null) {
                VideoEditorPreview(
                    uri = currentUri,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight,
                    durationMs = durationMs,
                    filmstrip = filmstrip,
                    trimRange = trimRange,
                    isForeground = true,
                    onTrimRangeChange = { trimRange = it },
                    onPickDifferent = { showVideoPicker = true }
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tingkat kompresi", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(CompressionLevel.ENTRIES) { opt ->
                            FilterChip(
                                selected = level == opt,
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); level = opt },
                                label = { Text(opt.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                    Text(
                        level.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (estimatedNewSizeBytes != null && originalClipSizeBytes > 0) {
                    val savedPercent = ((1.0 - estimatedNewSizeBytes.toDouble() / originalClipSizeBytes.toDouble()) * 100.0)
                        .coerceIn(0.0, 99.0)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ukuran asli", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatFileSize(originalClipSizeBytes), color = MaterialTheme.colorScheme.onBackground)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Perkiraan hasil", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("~${formatFileSize(estimatedNewSizeBytes)}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        }
                        Text(
                            "Hemat sekitar ${savedPercent.roundToInt()}% — encode H.265, resolusi & kualitas tampilan tetap sama. Perkiraan kasar, ukuran hasil akhir bisa sedikit berbeda.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (isProcessing) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mengompres… $progress%", color = MaterialTheme.colorScheme.onBackground)
                            OutlinedButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                cancelCompress()
                            }) { Text("Batalkan") }
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            val uri = selectedUri ?: return@Button
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isProcessing = true
                            progress = 0
                            message = null
                            resultFile = null
                            galleryUri = null
                            val id = UUID.randomUUID().toString()
                            val outFile = File(context.cacheDir, "compressed_$id.mp4")
                            val request = CompressRequest(
                                sourceUri = uri,
                                outputFile = outFile,
                                sourceWidth = sourceWidth,
                                sourceHeight = sourceHeight,
                                sourceDurationMs = durationMs,
                                sourceFileSizeBytes = sourceSizeBytes,
                                sourceFps = sourceFps,
                                sourceHasAudio = sourceHasAudio,
                                sourceAudioBitrateBps = sourceAudioBitrateBps,
                                trimStartMs = startMs,
                                trimEndMs = endMs,
                                level = level
                            )
                            // BUG FIX (Batch 31): jalankan lewat foreground
                            // service yang sama dengan ResizerScreen/BatchScreen
                            // agar proses kompres video besar tidak dibunuh OS
                            // saat app di-background, dan progresnya terlihat
                            // di notifikasi — screen ini sebelumnya tidak
                            // memakainya sama sekali.
                            ExportForegroundService.start(context)
                            activeTransformer = VideoResizer(context).compress(
                                request,
                                onProgress = { p ->
                                    progress = p
                                    ExportForegroundService.updateProgress(context, p)
                                },
                                onDone = { result ->
                                    isProcessing = false
                                    activeTransformer = null
                                    ExportForegroundService.stop(context)
                                    when (result) {
                                            is ResizeResult.Success -> {
                                                scope.launch {
                                                    val (msg, pubUri, outSize) = withContext(Dispatchers.IO) {
                                                        val displayName = result.outputFile.name
                                                        val publicUri = PublicMovieExporter.publish(context, result.outputFile, displayName)
                                                        val size = result.outputFile.length()
                                                        val thumbDir = File(context.cacheDir, "thumbs").apply { mkdirs() }
                                                        val thumbnailFile = File(thumbDir, "thumb_$id.jpg")
                                                        if (extractVideoThumbnail(result.outputFile, thumbnailFile)) {
                                                            VideoHistoryStore.add(
                                                                context,
                                                                VideoHistoryEntry(
                                                                    id = id,
                                                                    createdAt = System.currentTimeMillis(),
                                                                    outputFilePath = result.outputFile.absolutePath,
                                                                    thumbnailPath = thumbnailFile.absolutePath,
                                                                    sourceUri = uri.toString(),
                                                                    aspectRatioName = AspectRatioOption.ORIGINAL.name,
                                                                    resolutionName = ResolutionOption.ORIGINAL.name,
                                                                    rotationName = RotationOption.NONE.name,
                                                                    muteAudio = false,
                                                                    trimStartMs = startMs,
                                                                    trimEndMs = endMs,
                                                                    publicUri = publicUri?.toString(),
                                                                    kind = "COMPRESS"
                                                                )
                                                            )
                                                        }
                                                        val savedPercent = if (originalClipSizeBytes > 0) {
                                                            ((1.0 - size.toDouble() / originalClipSizeBytes.toDouble()) * 100.0).coerceIn(0.0, 99.0).roundToInt()
                                                        } else 0
                                                        val message = if (publicUri != null) {
                                                            "Selesai. Tersimpan di Galeri > Movies > VideoResizer. Ukuran turun $savedPercent% (${formatFileSize(size)})."
                                                        } else {
                                                            "Selesai (${formatFileSize(size)}, turun $savedPercent%), tapi gagal disalin ke galeri publik. Tetap tersedia lewat tombol Share di bawah."
                                                        }
                                                        Triple(message, publicUri, size)
                                                    }
                                                    resultFile = result.outputFile
                                                    resultSizeBytes = outSize
                                                    galleryUri = pubUri
                                                    message = msg
                                                }
                                            }
                                            is ResizeResult.Failure -> {
                                                message = "Gagal mengompres video. (Detail teknis: ${result.message})"
                                            }
                                        }
                                    }
                                )
                        },
                        enabled = clipDurationMs > 0,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Kompres Video")
                    }
                }

                message?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                }

                if (resultFile != null) {
                    val savedFile = resultFile
                    val savedGalleryUri = galleryUri
                    if (savedFile != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); shareVideo(context, savedFile) }) { Text("Bagikan") }
                            if (savedGalleryUri != null) {
                                OutlinedButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); openInGallery(context, savedGalleryUri) }) { Text("Buka di Galeri") }
                            }
                        }
                    }
                }
                }
            }
        }
    }
    if (showVideoPicker) {
        VideoPickerScreen(
            onVideoSelected = { uri ->
                showVideoPicker = false
                handlePickedVideo(uri)
            },
            onCancel = { showVideoPicker = false }
        )
    }
    }
}
