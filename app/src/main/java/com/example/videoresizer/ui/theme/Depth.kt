package com.example.videoresizer.ui.theme

import android.graphics.BlurMaskFilter
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Batch 83: bahasa kedalaman neumorfisme untuk SEMUA tema. Dua keadaan fisik yang sungguh berbeda, bukan sekadar bayangan:
 * - **Timbul (raised)**: badan permukaan bergradien cembung (terang di kiri-atas -> gelap di kanan-bawah) + DUA bayangan
 *   buram berlawanan arah (bayangan gelap kanan-bawah, sorot terang kiri-atas).
 * - **Cekung (inset)**: badan bergradien terbalik + bayangan DALAM (gelap di tepi kiri-atas, terang di tepi kanan-bawah),
 *   dipotong ke bentuknya.
 * Nilai warna/alfa dikalibrasi per tema supaya sorot & bayangan SELALU terlihat (tema gelap memakai dasar yang lebih
 * terang dari hitam pekat, tema terang memakai dasar yang lebih gelap dari putih) — dasar yang terlalu ekstrem membuat
 * bayangan "nyatu" dengan latar.
 */
@Immutable
class DepthSpec(
    val bodyHi: Color,
    val bodyLo: Color,
    val wellDark: Color,
    val wellLight: Color,
    val shadow: Color,
    val shadowAlpha: Float,
    val highlight: Color,
    val highlightAlpha: Float,
    val insetShadowAlpha: Float,
    val insetHighlightAlpha: Float,
    val sheenAlpha: Float,
    val rimLightAlpha: Float,
    val rimDarkAlpha: Float,
    val lipDarkAlpha: Float,
    val lipLightAlpha: Float
)

/** Ukuran kedalaman: jarak offset + radius buram bayangan. LARGE = kartu, MEDIUM = panel kecil/dok, SMALL = tombol/chip, WELL = lekuk. */
enum class DepthSize(val distance: Dp, val blur: Dp) {
    LARGE(8.dp, 18.dp),
    MEDIUM(5.5.dp, 12.dp),
    SMALL(4.dp, 8.5.dp),
    WELL(5.5.dp, 10.dp)
}

/**
 * Batch 85: padding konten untuk `LazyRow` berisi [DepthChip]. Kontainer gulir MEMOTONG gambar anak di batasnya sendiri, jadi
 * tanpa ruang ini bayangan timbul chip terpotong LURUS di tepi atas/bawah baris (akar utama kesan "depth kurang nyata").
 * Fungsi (bukan val publik) supaya SpotBugs tak menandai field statis.
 */
fun depthRowPadding(): PaddingValues = PaddingValues(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 14.dp)

// Batch 85: ukuran DepthSize ~+35% dan alfa bayangan/sorot dinaikkan (user: "not enough"); jumlah lapisan gambar TIDAK ditambah (kinerja 90/120Hz).
// Batch 84: model realistis (dikalibrasi lewat simulasi gambar 5 tema): bayangan BERLAPIS (ambient lebar + kontak rapat),
// badan cembung berarah 45 derajat + kilau radial kiri-atas, tepi bevel (sorot kiri-atas / bayangan kanan-bawah), dan
// lekuk dengan "bibir" (gelap kiri-atas / terang kanan-bawah).
private val DarkDepth = DepthSpec(
    bodyHi = Color(0xFF383E4E), bodyLo = Color(0xFF222632),
    wellDark = Color(0xFF1A1D27), wellLight = Color(0xFF303544),
    shadow = Color(0xFF000000), shadowAlpha = 0.80f,
    highlight = Color(0xFFFFFFFF), highlightAlpha = 0.22f,
    insetShadowAlpha = 0.84f, insetHighlightAlpha = 0.19f,
    sheenAlpha = 0.10f, rimLightAlpha = 0.30f, rimDarkAlpha = 0.42f, lipDarkAlpha = 0.52f, lipLightAlpha = 0.15f
)
private val LightDepth = DepthSpec(
    bodyHi = Color(0xFFF1F4F9), bodyLo = Color(0xFFD2D9E5),
    wellDark = Color(0xFFC8CFDC), wellLight = Color(0xFFEAEEF4),
    shadow = Color(0xFF6F7C98), shadowAlpha = 0.74f,
    highlight = Color(0xFFFFFFFF), highlightAlpha = 1.0f,
    insetShadowAlpha = 0.80f, insetHighlightAlpha = 1.0f,
    sheenAlpha = 0.60f, rimLightAlpha = 1.0f, rimDarkAlpha = 0.36f, lipDarkAlpha = 0.44f, lipLightAlpha = 1.0f
)
private val NeonDepth = DepthSpec(
    bodyHi = Color(0xFF22304F), bodyLo = Color(0xFF111932),
    wellDark = Color(0xFF0A1022), wellLight = Color(0xFF1C2848),
    shadow = Color(0xFF000208), shadowAlpha = 0.88f,
    highlight = Color(0xFF8CEBFF), highlightAlpha = 0.22f,
    insetShadowAlpha = 0.88f, insetHighlightAlpha = 0.20f,
    sheenAlpha = 0.09f, rimLightAlpha = 0.34f, rimDarkAlpha = 0.46f, lipDarkAlpha = 0.56f, lipLightAlpha = 0.18f
)
private val PaperDepth = DepthSpec(
    bodyHi = Color(0xFFF7EEDD), bodyLo = Color(0xFFDCCBAE),
    wellDark = Color(0xFFD2C1A3), wellLight = Color(0xFFF3E9D6),
    shadow = Color(0xFF86693B), shadowAlpha = 0.70f,
    highlight = Color(0xFFFFFAEE), highlightAlpha = 1.0f,
    insetShadowAlpha = 0.74f, insetHighlightAlpha = 1.0f,
    sheenAlpha = 0.60f, rimLightAlpha = 1.0f, rimDarkAlpha = 0.38f, lipDarkAlpha = 0.44f, lipLightAlpha = 1.0f
)
private val GlassDepth = DepthSpec(
    bodyHi = Color(0xFF30408C), bodyLo = Color(0xFF18215E),
    wellDark = Color(0xFF0D1338), wellLight = Color(0xFF25327A),
    shadow = Color(0xFF010310), shadowAlpha = 0.86f,
    highlight = Color(0xFFB4C6FF), highlightAlpha = 0.27f,
    insetShadowAlpha = 0.88f, insetHighlightAlpha = 0.23f,
    sheenAlpha = 0.12f, rimLightAlpha = 0.38f, rimDarkAlpha = 0.46f, lipDarkAlpha = 0.56f, lipLightAlpha = 0.20f
)

// Batch 94: Burgundy Old Money — badan burgundy kaya (lebih terang dari latar), sorot = emas champagne hangat (bukan putih/biru)
// supaya tepi atas-kiri terkesan disepuh; bayangan = anggur nyaris-hitam.
private val BurgundyDepth = DepthSpec(
    bodyHi = Color(0xFF6E2336), bodyLo = Color(0xFF431120),
    wellDark = Color(0xFF260915), wellLight = Color(0xFF55192B),
    shadow = Color(0xFF14030A), shadowAlpha = 0.86f,
    highlight = Color(0xFFF2D49A), highlightAlpha = 0.24f,
    insetShadowAlpha = 0.86f, insetHighlightAlpha = 0.20f,
    sheenAlpha = 0.11f, rimLightAlpha = 0.36f, rimDarkAlpha = 0.46f, lipDarkAlpha = 0.56f, lipLightAlpha = 0.18f
)

// Batch 98: Cosmic Orange — badan ungu antariksa (lebih terang dari latar), sorot = cahaya bintang oranye-amber hangat (bukan
// putih/biru) supaya tepi atas-kiri terkesan tersinari matahari; bayangan = ungu nyaris-hitam.
private val CosmicDepth = DepthSpec(
    bodyHi = Color(0xFF47336A), bodyLo = Color(0xFF2B1E47),
    wellDark = Color(0xFF150D26), wellLight = Color(0xFF30234F),
    shadow = Color(0xFF06020E), shadowAlpha = 0.86f,
    highlight = Color(0xFFFFB674), highlightAlpha = 0.22f,
    insetShadowAlpha = 0.88f, insetHighlightAlpha = 0.20f,
    sheenAlpha = 0.11f, rimLightAlpha = 0.36f, rimDarkAlpha = 0.46f, lipDarkAlpha = 0.56f, lipLightAlpha = 0.18f
)

/** Spesifikasi kedalaman per gaya tema (tetap, TIDAK bergantung warna dinamis wallpaper -> kontras sorot/bayangan terjamin). */
fun depthSpecFor(style: AppThemeStyle): DepthSpec = when (style) {
    AppThemeStyle.LIGHT -> LightDepth
    AppThemeStyle.DARK -> DarkDepth
    AppThemeStyle.MIDNIGHT_NEON -> NeonDepth
    AppThemeStyle.WARM_PAPER -> PaperDepth
    AppThemeStyle.MIDNIGHT_BLUE_GLASS -> GlassDepth
    AppThemeStyle.BURGUNDY_OLD_MONEY -> BurgundyDepth
    AppThemeStyle.COSMIC_ORANGE -> CosmicDepth
}

/** Gaya tema aktif (diisi `VideoResizerTheme`); dibaca komponen kedalaman & ornamen. */
val LocalThemeStyle = staticCompositionLocalOf { AppThemeStyle.DARK }

/** Spesifikasi kedalaman aktif (diisi `VideoResizerTheme`). */
val LocalDepthSpec = staticCompositionLocalOf { DarkDepth }

private val NoPress: () -> Float = { 0f }

// Batch 96: umpan balik TEKAN dibuat SECEPAT tekan langsung (skala pegas). Sisa lambat Batch 95 ada di empat tempat:
// (1) sinyal tekan `clickable` di kontainer gulir ditunda ~40 md -> kini jari yang MENYENTUH dibaca langsung di pass Initial
//     (tanpa tunda, tanpa konsumsi; geser melewati touchSlop = batal, dilepas tanpa tahan);
// (2) jalur lama Flow -> state -> rekomposisi -> animateFloatAsState memakan 2-3 frame -> kini Animatable dijalankan langsung dari
//     event & dibaca saat MENGGAMBAR (tanpa rekomposisi);
// (3) frame peralihan 0<p<1 digambar langsung (4 lapis blur timbul + 4 lapis lekuk) -> berat di GPU Mali, frame jatuh tepat saat
//     cekung mestinya muncul -> kini dua bitmap bake (diam & tertekan) dilebur murah, bake tertekan disiapkan di muka;
// (4) tween 55 md FastOutSlowIn -> kini LinearOutSlowIn 40 md (lonjakan awal cepat).
// Tahan minimal PRESS_MIN_HOLD_MS HANYA untuk ketukan yang berhasil (Release); batal/geser dilepas langsung.
private const val PRESS_MIN_HOLD_MS = 150L
private const val PRESS_ATTACK_MS = 40
private const val PRESS_RELEASE_MS = 170

/** Event Press dari `InteractionSource` yang datang dalam jendela ini setelah sentuhan langsung dianggap SUDAH ditangani jalur langsung. */
private const val PRESS_FLOW_IGNORE_MS = 400L

/**
 * Progres tekan 0..1 untuk permukaan timbul: 1 = cekung penuh. Dua sumber: [setPointer] (sentuhan langsung, tanpa tunda) dan
 * [setFlow] (Press/Release `InteractionSource`: papan ketik/D-pad). Hanya dipakai dari utas utama.
 */
private class DepthPressState(private val scope: CoroutineScope) {
    private val anim = Animatable(0f)
    private var pointerDown = false
    private var flowDown = false
    private var active = false
    private var downAt = 0L
    private var pointerAt = 0L
    private var job: Job? = null

    /** Dibaca di fase gambar (state Compose di balik Animatable) -> hanya menggambar ulang, tanpa rekomposisi. */
    val value: Float get() = anim.value

    fun setPointer(down: Boolean, hold: Boolean) {
        pointerAt = SystemClock.uptimeMillis()
        pointerDown = down
        update(hold)
    }

    fun setFlow(down: Boolean, hold: Boolean) {
        if (down && SystemClock.uptimeMillis() - pointerAt < PRESS_FLOW_IGNORE_MS) return
        flowDown = down
        update(hold)
    }

    private fun update(hold: Boolean) {
        val down = pointerDown || flowDown
        if (down == active) return
        active = down
        job?.cancel()
        if (down) {
            downAt = SystemClock.uptimeMillis()
            job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                anim.animateTo(1f, tween(PRESS_ATTACK_MS, easing = LinearOutSlowInEasing))
            }
        } else {
            val remain = if (hold) PRESS_MIN_HOLD_MS - (SystemClock.uptimeMillis() - downAt) else 0L
            job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (remain > 0L) delay(remain)
                anim.animateTo(0f, tween(PRESS_RELEASE_MS))
            }
        }
    }
}

@Composable
private fun rememberDepthPress(source: InteractionSource): DepthPressState {
    val scope = rememberCoroutineScope()
    val state = remember(source) { DepthPressState(scope) }
    LaunchedEffect(source, state) {
        source.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> state.setFlow(down = true, hold = false)
                is PressInteraction.Release -> state.setFlow(down = false, hold = true)
                is PressInteraction.Cancel -> state.setFlow(down = false, hold = false)
                else -> Unit
            }
        }
    }
    return state
}

/**
 * Baca sentuhan LANGSUNG di pass Initial (sebelum `clickable`, yang menunda sinyal tekan ~40 md di kontainer gulir) tanpa
 * mengonsumsi apa pun. Dilepas tanpa tahan bila jari bergeser melewati touchSlop (gulir/seret) atau pointer lenyap.
 */
private fun Modifier.depthPressInput(state: DepthPressState): Modifier = this.pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        state.setPointer(down = true, hold = false)
        var tap = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    tap = true
                    break
                }
                if ((change.position - down.position).getDistance() > slop) break
            }
        } finally {
            state.setPointer(down = false, hold = tap)
        }
    }
}

/** Skala jarak/radius lapisan KONTAK (bayangan rapat dekat tepi) relatif terhadap lapisan ambient. */
private const val CONTACT_SCALE = 0.35f

/** Batch 86: ambang luas (dp^2) elemen "besar": bayangan digambar hanya sebagai CINCIN (bagian dalam tertutup badan opak) dan kilau dibatasi. */
private const val BIG_AREA_DP2 = 40_000f

/** Batch 86: jangkauan efektif bayangan blur (~3 sigma) relatif terhadap radius blur. */
private const val RING_SPREAD = 1.8f

/**
 * Batch 86: Paint blur DIBAGI per radius (hanya segelintir nilai). Sebelumnya tiap elemen membuat 4 Paint + 4 BlurMaskFilter native
 * (plus 4 lagi utk kit cekung) pada SETIAP rekomposisi (modifier gambar dibuat ulang -> cache ditolak), ~70 chip x 8 objek native per ketukan.
 * Aman dibagi: gambar hanya di utas utama dan HWUI menyalin state Paint saat merekam tiap operasi.
 */
private val blurPaintCache = HashMap<Float, android.graphics.Paint>()

/** Paint blur SEGAR (tanpa berbagi) — dipakai utas bake latar belakang; cache bersama di bawah hanya aman di utas utama. */
private fun newBlurPaint(blur: Float): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        maskFilter = BlurMaskFilter(blur.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
    }

private fun blurPaint(blur: Float): android.graphics.Paint = blurPaintCache.getOrPut(blur.coerceAtLeast(1f)) { newBlurPaint(blur) }

private fun cornerRadiusPx(shape: Shape, size: Size, density: Density): Float {
    val radius = (shape as? CornerBasedShape)?.topStart?.toPx(size, density) ?: 0f
    return radius.coerceAtMost(minOf(size.width, size.height) / 2f)
}

/** Gradien tepi bevel berarah 45 derajat: warna pertama pekat di sudut kiri-atas memudar ke tengah; warna kedua pekat di kanan-bawah. */
private fun bevelBrush(first: Color, firstAlpha: Float, second: Color, secondAlpha: Float, span: Float): Brush =
    Brush.linearGradient(
        0f to first.copy(alpha = firstAlpha.coerceIn(0f, 1f)),
        0.5f to first.copy(alpha = 0f),
        0.5f to second.copy(alpha = 0f),
        1f to second.copy(alpha = secondAlpha.coerceIn(0f, 1f)),
        start = Offset.Zero,
        end = Offset(span, span)
    )

private fun drawShadowLayer(
    n: android.graphics.Canvas,
    paint: android.graphics.Paint,
    color: Color,
    alpha: Float,
    shift: Float,
    w: Float,
    h: Float,
    r: Float,
    ring: Float
) {
    paint.color = color.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
    if (ring <= 0f) {
        n.drawRoundRect(shift, shift, w + shift, h + shift, r, r, paint)
        return
    }
    // Elemen besar: 4 pita TANPA tumpang-tindih (atas, bawah, kiri, kanan) membentuk cincin; interior (tertutup badan opak) dilewati
    // lewat clipRect (scissor GPU) -> fragmen blur di interior kartu raksasa tak lagi dihitung tiap frame.
    drawShadowBand(n, paint, shift, w, h, r, -ring, -ring, w + ring, ring)
    drawShadowBand(n, paint, shift, w, h, r, -ring, h - ring, w + ring, h + ring)
    drawShadowBand(n, paint, shift, w, h, r, -ring, ring, ring, h - ring)
    drawShadowBand(n, paint, shift, w, h, r, w - ring, ring, w + ring, h - ring)
}

private fun drawShadowBand(
    n: android.graphics.Canvas,
    paint: android.graphics.Paint,
    shift: Float,
    w: Float,
    h: Float,
    r: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float
) {
    n.save()
    if (n.clipRect(left, top, right, bottom)) n.drawRoundRect(shift, shift, w + shift, h + shift, r, r, paint)
    n.restore()
}

/** Peralatan gambar lekuk (cekung) yang di-cache per ukuran: jalur potong, gradien badan, bingkai bayangan dalam, bibir. */
private class WellKit(
    private val spec: DepthSpec,
    private val w: Float,
    private val h: Float,
    private val r: Float,
    private val d: Float,
    blur: Float,
    private val lipWidth: Float,
    paint: (Float) -> android.graphics.Paint
) {
    private val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r, r))) }
    private val span = (w + h) / 2f
    private val brush = Brush.linearGradient(listOf(spec.wellDark, spec.wellLight), Offset.Zero, Offset(span, span))
    private val lip = bevelBrush(spec.shadow, spec.lipDarkAlpha, spec.highlight, spec.lipLightAlpha, span)
    private val frame = android.graphics.Path().apply {
        fillType = android.graphics.Path.FillType.EVEN_ODD
        val m = blur * 3f
        addRect(-m, -m, w + m, h + m, android.graphics.Path.Direction.CW)
        addRoundRect(0f, 0f, w, h, r, r, android.graphics.Path.Direction.CW)
    }
    private val dark = paint(blur)
    private val light = dark
    private val darkTight = paint(blur * CONTACT_SCALE)
    private val lightTight = darkTight

    private fun innerLayer(n: android.graphics.Canvas, paint: android.graphics.Paint, color: Color, alpha: Float, shift: Float) {
        paint.color = color.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
        n.save()
        n.translate(shift, shift)
        n.drawPath(frame, paint)
        n.restore()
    }

    /** `fill = false` -> hanya bayangan dalam + bibir (tanpa badan sumur), untuk dilapiskan di atas isi yang sudah punya latar sendiri. */
    fun draw(scope: DrawScope, intensity: Float, fill: Boolean = true) {
        with(scope) {
            clipPath(clip) {
                if (fill) drawRoundRect(brush = brush, cornerRadius = CornerRadius(r, r), alpha = intensity)
                drawIntoCanvas { c ->
                    val n = c.nativeCanvas
                    innerLayer(n, dark, spec.shadow, spec.insetShadowAlpha * 0.80f * intensity, d)
                    innerLayer(n, light, spec.highlight, spec.insetHighlightAlpha * 0.80f * intensity, -d)
                    innerLayer(n, darkTight, spec.shadow, spec.insetShadowAlpha * 0.90f * intensity, d * 0.4f)
                    innerLayer(n, lightTight, spec.highlight, spec.insetHighlightAlpha * 0.80f * intensity, -d * 0.4f)
                }
                drawRoundRect(
                    brush = lip,
                    topLeft = Offset(lipWidth / 2f, lipWidth / 2f),
                    size = Size((w - lipWidth).coerceAtLeast(1f), (h - lipWidth).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius((r - lipWidth / 2f).coerceAtLeast(0f)),
                    style = Stroke(width = lipWidth),
                    alpha = intensity
                )
            }
        }
    }
}

/**
 * Kit gambar TIMBUL: semua objek (paint, brush, path) dibangun SEKALI per ukuran. `draw` identik untuk gambar langsung (GPU,
 * dipakai saat animasi tekan / sebelum bake siap / elemen raksasa) maupun untuk bake ke bitmap (CPU, utas latar).
 */
private class RaisedKit(
    private val spec: DepthSpec,
    private val w: Float,
    private val h: Float,
    private val r: Float,
    private val d: Float,
    private val blur: Float,
    private val style: AppThemeStyle?,
    private val accent: Color,
    density: Density,
    paint: (Float) -> android.graphics.Paint
) {
    private val ambient = paint(blur)
    private val contact = paint(blur * CONTACT_SCALE)
    val big = (w / density.density) * (h / density.density) > BIG_AREA_DP2
    private val ring = d + blur * RING_SPREAD
    private val shadowRing = if (big && w > 2f * ring && h > 2f * ring) ring else 0f
    private val span = (w + h) / 2f
    private val body = Brush.linearGradient(listOf(spec.bodyHi, spec.bodyLo), Offset.Zero, Offset(span, span))
    // Kartu besar: kilau radial dibatasi ke area dekat sudut kiri-atas (bukan separuh layar yang tak terlihat bedanya).
    private val sheenR = if (big) minOf(maxOf(w, h) * 0.75f, with(density) { 200.dp.toPx() }) else maxOf(w, h) * 0.75f
    private val sheenCx = if (big) minOf(w * 0.28f, with(density) { 90.dp.toPx() }) else w * 0.28f
    private val sheenCy = if (big) minOf(h * 0.22f, with(density) { 60.dp.toPx() }) else h * 0.22f
    private val sheenSize = if (big) Size(minOf(w, sheenCx + sheenR), minOf(h, sheenCy + sheenR)) else Size(w, h)
    private val sheen = Brush.radialGradient(
        0f to spec.highlight.copy(alpha = spec.sheenAlpha),
        0.4f to spec.highlight.copy(alpha = spec.sheenAlpha * 0.35f),
        1f to spec.highlight.copy(alpha = 0f),
        center = Offset(sheenCx, sheenCy),
        radius = sheenR.coerceAtLeast(1f)
    )
    private val rimWidth = with(density) { 1.5.dp.toPx() }
    private val rim = bevelBrush(spec.highlight, spec.rimLightAlpha, spec.shadow, spec.rimDarkAlpha, span)
    // Kit cekung hanya dibangun saat benar-benar ditekan.
    private val well by lazy(LazyThreadSafetyMode.NONE) { WellKit(spec, w, h, r, d, blur, rimWidth, paint) }
    private val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r, r))) }

    fun draw(scope: DrawScope, p: Float) {
        with(scope) {
            val k = 1f - p
            if (k > 0.01f) {
                drawIntoCanvas { c ->
                    val n = c.nativeCanvas
                    drawShadowLayer(n, ambient, spec.shadow, spec.shadowAlpha * 0.72f * k, d, w, h, r, shadowRing)
                    drawShadowLayer(n, ambient, spec.highlight, spec.highlightAlpha * 0.80f * k, -d, w, h, r, shadowRing)
                    drawShadowLayer(n, contact, spec.shadow, spec.shadowAlpha * 0.85f * k, d * CONTACT_SCALE, w, h, r, shadowRing)
                    drawShadowLayer(n, contact, spec.highlight, spec.highlightAlpha * 0.90f * k, -d * CONTACT_SCALE, w, h, r, shadowRing)
                }
            }
            drawRoundRect(brush = body, cornerRadius = CornerRadius(r, r))
            drawRoundRect(brush = sheen, size = sheenSize, cornerRadius = CornerRadius(r, r))
            if (style != null) drawCardOrnament(style, accent, r, clip)
            if (k > 0.01f) {
                drawRoundRect(
                    brush = rim,
                    topLeft = Offset(rimWidth / 2f, rimWidth / 2f),
                    size = Size((w - rimWidth).coerceAtLeast(1f), (h - rimWidth).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius((r - rimWidth / 2f).coerceAtLeast(0f)),
                    style = Stroke(width = rimWidth),
                    alpha = k
                )
            }
            if (p > 0.01f) well.draw(this, p)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Batch 88: BAKE. Elemen statis (chip, kartu sedang, sumur) dirender SEKALI ke bitmap di utas latar belakang (CPU), lalu tiap frame
// cukup 1 `drawImage` (quad bertekstur) -- bukan 4 draw blur + gradien + kilau + bevel (~8 draw bershader berat) per elemen.
// GPU kelas menengah (Infinix/Mali) kewalahan oleh overdraw blur itu di 90/120Hz. Sebelum bake siap, saat animasi tekan, dan untuk
// elemen raksasa, gambar langsung (kit) tetap dipakai -> tampilan identik, hanya jalurnya yang lebih murah.

private const val BAKE_MAX_BYTES = 6_000_000L
private const val BAKE_BUDGET_BYTES = 48L * 1024L * 1024L

/** Batch 96: batas ukuran bitmap agar bake tertekan disiapkan di muka (ubin/tombol ikon/kenop; bukan kartu raksasa). */
private const val PRESS_PREWARM_MAX_BYTES = 2_500_000L

internal data class BakeKey(
    val spec: DepthSpec,
    val kind: Int,
    val variant: Int,
    val w: Int,
    val h: Int,
    val r: Int,
    val depth: DepthSize,
    val style: AppThemeStyle?,
    val accentBits: Long,
    val density: Float
)

/** Wadah hasil bake; `image` adalah state Compose sehingga gambar elemen otomatis beralih begitu bitmap siap. */
internal class BakeSlot(val bytes: Long) {
    var image: ImageBitmap? by mutableStateOf(null)
}

internal object DepthBakery {
    private val slots = LinkedHashMap<Any, BakeSlot>(64, 0.75f, true)
    private var totalBytes = 0L
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "depth-bake").apply {
            priority = Thread.NORM_PRIORITY - 1
            isDaemon = true
        }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + worker)

    /** Hanya dipanggil dari utas utama. `render` berjalan di utas latar; kegagalan/OOM -> `image` tetap null (gambar langsung). */
    fun request(key: Any, bytes: Long, render: () -> ImageBitmap): BakeSlot {
        slots[key]?.let { return it }
        val oldest = slots.entries.iterator()
        while (totalBytes + bytes > BAKE_BUDGET_BYTES && oldest.hasNext()) {
            val evicted = oldest.next().value
            totalBytes -= evicted.bytes
            evicted.image = null
            oldest.remove()
        }
        val slot = BakeSlot(bytes)
        slots[key] = slot
        totalBytes += bytes
        scope.launch { slot.image = runCatching(render).getOrNull() }
        return slot
    }
}

/** Menggambar `block` (koordinat lokal 0..w, 0..h) ke bitmap berukuran (w + 2*margin) x (h + 2*margin). */
internal fun bakeBitmap(width: Int, height: Int, margin: Int, density: Density, block: DrawScope.() -> Unit): ImageBitmap {
    val image = ImageBitmap(width + 2 * margin, height + 2 * margin)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(image.width.toFloat(), image.height.toFloat())) {
        // `inset` menggeser asal DAN mengecilkan `size` ke ukuran elemen: kode gambar yang memakai size default (badan, kilau, ornamen,
        // sumur) tetap menggambar tepat seukuran elemen, bukan seukuran bitmap + margin (bug v88: "bingkai" persegi di tiap ubin).
        inset(margin.toFloat()) { block() }
    }
    return image
}

/**
 * Permukaan TIMBUL. `pressed` (0..1, dibaca saat menggambar) melebur ke keadaan cekung — tombol "tertekan ke dalam".
 * `style` != null menambah ornamen kartu khas tema (lihat [drawCardOrnament]).
 * Lapisan gambar: bayangan ambient + kontak (gelap kanan-bawah) dan sorot ambient + kontak (terang kiri-atas) -> badan
 * gradien 45 derajat -> kilau radial -> ornamen -> tepi bevel. Diam (p=0) atau tertekan penuh (p=1) -> bitmap hasil bake.
 */
fun Modifier.depthRaised(
    spec: DepthSpec,
    shape: Shape,
    depth: DepthSize = DepthSize.LARGE,
    pressed: () -> Float = NoPress,
    style: AppThemeStyle? = null,
    accent: Color = Color.Unspecified,
    prewarmPress: Boolean = false
): Modifier = this.drawWithCache {
    val w = this.size.width
    val h = this.size.height
    val r = cornerRadiusPx(shape, this.size, this)
    val d = depth.distance.toPx()
    val blur = depth.blur.toPx()
    val kit = RaisedKit(spec, w, h, r, d, blur, style, accent, this, ::blurPaint)
    val wi = w.roundToInt()
    val hi = h.roundToInt()
    val margin = ceil(d + blur * RING_SPREAD).toInt() + 2
    val bytes = (wi + 2L * margin) * (hi + 2L * margin) * 4L
    val bakeable = wi > 0 && hi > 0 && bytes <= BAKE_MAX_BYTES
    val snapshot = Density(this.density, this.fontScale)
    val key = BakeKey(spec, 0, 0, wi, hi, r.roundToInt(), depth, style, accent.value.toLong(), snapshot.density)
    fun slotFor(variant: Int, pressedValue: Float): BakeSlot = DepthBakery.request(key.copy(variant = variant), bytes) {
        bakeBitmap(wi, hi, margin, snapshot) {
            RaisedKit(spec, w, h, r, d, blur, style, accent, snapshot, ::newBlurPaint).draw(this, pressedValue)
        }
    }
    val restSlot by lazy(LazyThreadSafetyMode.NONE) { slotFor(0, 0f) }
    val pressSlot by lazy(LazyThreadSafetyMode.NONE) { slotFor(1, 1f) }
    // Batch 96: bake tertekan disiapkan di muka (hanya setelah bake diam siap, jadi antreannya di belakang semua bake diam layar ini)
    // untuk elemen tak-terlalu-besar yang sering diketuk; chip (puluhan, ringan) cukup lazy agar anggaran memori tak jebol.
    val prewarm = prewarmPress && bytes <= PRESS_PREWARM_MAX_BYTES
    onDrawBehind {
        val p = pressed().coerceIn(0f, 1f)
        if (!bakeable) {
            kit.draw(this, p)
        } else {
            val restImage = restSlot.image
            val pressImage = if (p > 0.01f || (prewarm && restImage != null)) pressSlot.image else null
            val at = Offset(-margin.toFloat(), -margin.toFloat())
            when {
                p <= 0.01f && restImage != null -> drawImage(restImage, at)
                p >= 0.99f && pressImage != null -> drawImage(pressImage, at)
                // Peralihan MURAH: lebur dua bitmap (bukan 8 lapis blur langsung) -> tak ada frame jatuh saat cekung muncul.
                restImage != null && pressImage != null -> {
                    drawImage(restImage, at, alpha = 1f - p)
                    drawImage(pressImage, at, alpha = p)
                }
                else -> kit.draw(this, p)
            }
        }
    }
}

/** Batch 97: pudar gelap tipis di atas isi saat tertekan (kartu bergradien tampak masuk meski bayangan timbulnya tertutup isi). */
private const val OVERLAY_WASH_ALPHA = 0.14f

private fun DrawScope.drawPressOverlay(kit: WellKit, spec: DepthSpec, r: Float, intensity: Float) {
    kit.draw(this, intensity, fill = false)
    drawRoundRect(color = spec.shadow.copy(alpha = OVERLAY_WASH_ALPHA * intensity), cornerRadius = CornerRadius(r, r))
}

/**
 * Batch 97: lapisan CEKUNG di ATAS isi. Kartu yang isinya menutup penuh badan timbul (hero Beranda: gradien aksen opak) tak
 * memperlihatkan cekung yang digambar di BELAKANG isi -> hanya riak. Di sini bayangan dalam + bibir + pudar gelap digambar SETELAH
 * isi, intensitas = progres tekan. Dibake sekali (bitmap seukuran elemen), diminta sejak gambar pertama supaya siap saat ditekan.
 */
private fun Modifier.depthPressOverlay(spec: DepthSpec, shape: Shape, depth: DepthSize, pressed: () -> Float): Modifier = this.drawWithCache {
    val w = this.size.width
    val h = this.size.height
    val r = cornerRadiusPx(shape, this.size, this)
    val d = depth.distance.toPx()
    val blur = depth.blur.toPx()
    val lip = 1.5.dp.toPx()
    val kit = WellKit(spec, w, h, r, d, blur, lip, ::blurPaint)
    val wi = w.roundToInt()
    val hi = h.roundToInt()
    val bytes = wi.toLong() * hi * 4L
    val snapshot = Density(this.density, this.fontScale)
    val slot = if (wi > 0 && hi > 0 && bytes <= BAKE_MAX_BYTES) {
        DepthBakery.request(BakeKey(spec, 2, 0, wi, hi, r.roundToInt(), depth, null, 0L, snapshot.density), bytes) {
            bakeBitmap(wi, hi, 0, snapshot) {
                drawPressOverlay(WellKit(spec, w, h, r, d, blur, lip, ::newBlurPaint), spec, r, 1f)
            }
        }
    } else {
        null
    }
    onDrawWithContent {
        drawContent()
        val baked = slot?.image
        val p = pressed().coerceIn(0f, 1f)
        if (p > 0.01f) {
            if (baked != null) drawImage(baked, alpha = p) else drawPressOverlay(kit, spec, r, p)
        }
    }
}

/** Permukaan CEKUNG (lekuk): jalur tab, ubin ikon, bak progres, keadaan terpilih. Dibake seperti [depthRaised]. */
fun Modifier.depthInset(
    spec: DepthSpec,
    shape: Shape,
    depth: DepthSize = DepthSize.WELL
): Modifier = this.drawWithCache {
    val w = this.size.width
    val h = this.size.height
    val r = cornerRadiusPx(shape, this.size, this)
    val d = depth.distance.toPx()
    val blur = depth.blur.toPx()
    val lip = 1.5.dp.toPx()
    val kit = WellKit(spec, w, h, r, d, blur, lip, ::blurPaint)
    val wi = w.roundToInt()
    val hi = h.roundToInt()
    val bytes = wi.toLong() * hi * 4L
    val snapshot = Density(this.density, this.fontScale)
    val slot = if (wi > 0 && hi > 0 && bytes <= BAKE_MAX_BYTES) {
        DepthBakery.request(BakeKey(spec, 1, 0, wi, hi, r.roundToInt(), depth, null, 0L, snapshot.density), bytes) {
            bakeBitmap(wi, hi, 0, snapshot) { WellKit(spec, w, h, r, d, blur, lip, ::newBlurPaint).draw(this, 1f) }
        }
    } else {
        null
    }
    onDrawBehind {
        val baked = slot?.image
        if (baked != null) drawImage(baked) else kit.draw(this, 1f)
    }
}

/** Kartu neumorfik statis. `inset = true` -> cekung (mis. entri terpilih). Pengganti `Card` (tanpa warna/bayangan Material). */
@Composable
fun DepthCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    depth: DepthSize = DepthSize.LARGE,
    inset: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val spec = LocalDepthSpec.current
    val style = LocalThemeStyle.current
    val accent = MaterialTheme.colorScheme.primary
    val surface = if (inset) modifier.depthInset(spec, shape, depth) else modifier.depthRaised(spec, shape, depth, style = style, accent = accent)
    Card(
        modifier = surface,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

/**
 * Kartu neumorfik yang bisa diketuk: saat ditekan tampak MASUK ke dalam (timbul -> cekung). `pressOverlay = true` utk kartu yang isinya
 * menutup penuh badan timbul (mis. gradien hero): cekung digambar juga DI ATAS isi.
 */
@Composable
fun DepthCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    depth: DepthSize = DepthSize.LARGE,
    interactionSource: MutableInteractionSource? = null,
    pressOverlay: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val spec = LocalDepthSpec.current
    val style = LocalThemeStyle.current
    val accent = MaterialTheme.colorScheme.primary
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val press = rememberDepthPress(source)
    Card(
        onClick = onClick,
        modifier = modifier
            .depthPressInput(press)
            .depthRaised(spec, shape, depth, pressed = { press.value }, style = style, accent = accent, prewarmPress = true)
            .then(if (pressOverlay) Modifier.depthPressOverlay(spec, shape, depth) { press.value } else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        interactionSource = source,
        content = content
    )
}

/**
 * Chip pilihan neumorfik (pengganti `FilterChip`): TIDAK terpilih = timbul; terpilih = cekung + teks aksen. Ditekan = masuk ke dalam.
 */
@Composable
fun DepthChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val spec = LocalDepthSpec.current
    val shape = RoundedCornerShape(50)
    val source = remember { MutableInteractionSource() }
    val press = rememberDepthPress(source)
    val sel by animateFloatAsState(if (selected) 1f else 0f, animationSpec = tween(160), label = "depthChipSel")
    val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .depthPressInput(press)
            .depthRaised(spec, shape, DepthSize.SMALL, pressed = { maxOf(press.value, sel) })
            .clip(shape)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    leadingIcon?.invoke()
                    label()
                }
            }
        }
    }
}

/** Tombol ikon bulat timbul (aksi header/kembali); ditekan = cekung. */
@Composable
fun DepthIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    content: @Composable () -> Unit
) {
    val spec = LocalDepthSpec.current
    val source = remember { MutableInteractionSource() }
    val press = rememberDepthPress(source)
    Box(
        modifier = modifier
            .size(size)
            .depthPressInput(press)
            .depthRaised(spec, CircleShape, DepthSize.SMALL, pressed = { press.value }, prewarmPress = true)
            .clip(CircleShape)
            .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { content() }
    }
}

/** Bar progres neumorfik: bak CEKUNG + isi gradien aksen. `progress` dibaca saat menggambar (tanpa rekomposisi). */
@Composable
fun DepthProgressBar(progress: () -> Float, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val spec = LocalDepthSpec.current
    val shape = RoundedCornerShape(50)
    val fill = accentGradient()
    Box(modifier = modifier.height(height).depthInset(spec, shape, DepthSize.SMALL).padding(2.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val fraction = progress().coerceIn(0f, 1f)
                    if (fraction > 0f) {
                        val barWidth = (size.width * fraction).coerceAtLeast(size.height)
                        drawRoundRect(
                            brush = fill,
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                        )
                    }
                }
        )
    }
}

/** Ubin lekuk (cekung) untuk membungkus ikon di dalam kartu timbul. */
@Composable
fun DepthWell(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    content: @Composable () -> Unit
) {
    val spec = LocalDepthSpec.current
    Box(modifier = modifier.depthInset(spec, shape), contentAlignment = Alignment.Center) { content() }
}

/** Gradien aksen app (hero, CTA, ikon pilih video) — ujungnya disesuaikan agar teks [accentContent] selalu terbaca. */
@Composable
fun accentGradient(): Brush {
    val cs = MaterialTheme.colorScheme
    val darkText = cs.onPrimary.luminance() < 0.5f
    val end = if (darkText) lerp(cs.primary, Color.White, 0.28f) else lerp(cs.primary, Color.Black, 0.32f)
    return Brush.linearGradient(listOf(cs.primary, end))
}

/** Warna konten di atas [accentGradient] (`onPrimary` tema: gelap di aksen terang seperti cyan Neon, terang di aksen gelap). */
@Composable
fun accentContent(): Color = MaterialTheme.colorScheme.onPrimary

/**
 * Batch 85: saklar neumorfik (pengganti `Switch`). Jalur CEKUNG; isi aksen "menyala" dari kiri sampai kenop; kenop TIMBUL yang
 * bergeser dan masuk ke dalam saat ditekan. Memakai `absoluteOffset` + gambar fisik (sama seperti gagang trim: tak ikut cermin RTL).
 */
@Composable
fun DepthSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val spec = LocalDepthSpec.current
    val fill = accentGradient()
    val density = LocalDensity.current
    val knob = 26.dp
    val pad = 4.dp
    val trackWidth = 60.dp
    val travelPx = with(density) { (trackWidth - knob - pad * 2).toPx() }
    val knobPx = with(density) { knob.toPx() }
    val source = remember { MutableInteractionSource() }
    val press = rememberDepthPress(source)
    val position = animateFloatAsState(if (checked) 1f else 0f, animationSpec = tween(180), label = "depthSwitchPos")
    Box(
        modifier = modifier
            .then(if (enabled) Modifier else Modifier.alpha(0.45f))
            .size(trackWidth, knob + pad * 2)
            .depthInset(spec, RoundedCornerShape(50), DepthSize.SMALL)
            .depthPressInput(press)
            .toggleable(
                value = checked,
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(pad)
                .drawBehind {
                    val reach = (travelPx * position.value + knobPx).coerceAtMost(size.width)
                    drawRoundRect(
                        brush = fill,
                        size = Size(reach, size.height),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                        alpha = position.value
                    )
                }
        )
        Box(
            modifier = Modifier
                .padding(pad)
                .absoluteOffset { IntOffset((travelPx * position.value).roundToInt(), 0) }
                .size(knob)
                .depthRaised(spec, CircleShape, DepthSize.SMALL, pressed = { press.value }, prewarmPress = true)
        )
    }
}

/**
 * Batch 85: slider neumorfik (pengganti `Slider`): bak CEKUNG berisi isi aksen sampai kenop, kenop TIMBUL (masuk ke dalam saat
 * disentuh) dengan titik aksen. Sentuh = langsung melompat ke posisi itu; geser horizontal = mengikuti jari (gerak vertikal tetap
 * diteruskan ke gulir induk). Semantik `progressBarRangeInfo` + `setProgress` untuk aksesibilitas.
 */
@Composable
fun DepthSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true
) {
    val spec = LocalDepthSpec.current
    val fill = accentGradient()
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val knob = 28.dp
    val knobPx = with(density) { knob.toPx() }
    val edgePx = with(density) { 2.dp.toPx() }
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f
    val latestChange by rememberUpdatedState(onValueChange)
    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val press = animateFloatAsState(if (dragging) 1f else 0f, animationSpec = tween(90), label = "depthSliderPress")
    Box(
        modifier = modifier
            .then(if (enabled) Modifier else Modifier.alpha(0.45f))
            .fillMaxWidth()
            .height(knob + 12.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange)
                setProgress { target ->
                    if (enabled) latestChange(target.coerceIn(valueRange.start, valueRange.endInclusive))
                    enabled
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                val pick: (Float) -> Unit = { x ->
                    val usable = size.width - knobPx
                    if (usable > 0f) {
                        val f = ((x - knobPx / 2f) / usable).coerceIn(0f, 1f)
                        latestChange(valueRange.start + f * (valueRange.endInclusive - valueRange.start))
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    try {
                        pick(down.position.x)
                        val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (slop != null) {
                            horizontalDrag(slop.id) { change ->
                                pick(change.position.x)
                                change.consume()
                            }
                        }
                    } finally {
                        dragging = false
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .depthInset(spec, RoundedCornerShape(50), DepthSize.SMALL)
                .padding(2.dp)
                .drawBehind {
                    val center = knobPx / 2f + (widthPx - knobPx) * fraction
                    val reach = (center - edgePx).coerceAtLeast(size.height).coerceAtMost(size.width)
                    drawRoundRect(
                        brush = fill,
                        size = Size(reach, size.height),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }
        )
        Box(
            modifier = Modifier
                .absoluteOffset { IntOffset(((widthPx - knobPx) * fraction).roundToInt(), 0) }
                .size(knob)
                .depthRaised(spec, CircleShape, DepthSize.SMALL, pressed = { press.value }),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(8.dp).background(accent, CircleShape))
        }
    }
}
