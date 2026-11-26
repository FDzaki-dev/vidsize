package com.example.videoresizer.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

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

/** Spesifikasi kedalaman per gaya tema (tetap, TIDAK bergantung warna dinamis wallpaper -> kontras sorot/bayangan terjamin). */
fun depthSpecFor(style: AppThemeStyle): DepthSpec = when (style) {
    AppThemeStyle.LIGHT -> LightDepth
    AppThemeStyle.DARK -> DarkDepth
    AppThemeStyle.MIDNIGHT_NEON -> NeonDepth
    AppThemeStyle.WARM_PAPER -> PaperDepth
    AppThemeStyle.MIDNIGHT_BLUE_GLASS -> GlassDepth
}

/** Gaya tema aktif (diisi `VideoResizerTheme`); dibaca komponen kedalaman & ornamen. */
val LocalThemeStyle = staticCompositionLocalOf { AppThemeStyle.DARK }

/** Spesifikasi kedalaman aktif (diisi `VideoResizerTheme`). */
val LocalDepthSpec = staticCompositionLocalOf { DarkDepth }

private val NoPress: () -> Float = { 0f }

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

private fun blurPaint(blur: Float): android.graphics.Paint {
    val radius = blur.coerceAtLeast(1f)
    return blurPaintCache.getOrPut(radius) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
    }
}

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
    private val lipWidth: Float
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
    private val dark = blurPaint(blur)
    private val light = blurPaint(blur)
    private val darkTight = blurPaint(blur * CONTACT_SCALE)
    private val lightTight = blurPaint(blur * CONTACT_SCALE)

    private fun innerLayer(n: android.graphics.Canvas, paint: android.graphics.Paint, color: Color, alpha: Float, shift: Float) {
        paint.color = color.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
        n.save()
        n.translate(shift, shift)
        n.drawPath(frame, paint)
        n.restore()
    }

    fun draw(scope: DrawScope, intensity: Float) {
        with(scope) {
            clipPath(clip) {
                drawRoundRect(brush = brush, cornerRadius = CornerRadius(r, r), alpha = intensity)
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
 * Permukaan TIMBUL. `pressed` (0..1, dibaca saat menggambar) melebur ke keadaan cekung — tombol "tertekan ke dalam".
 * `style` != null menambah ornamen kartu khas tema (lihat [drawCardOrnament]).
 * Lapisan gambar: bayangan ambient + kontak (gelap kanan-bawah) dan sorot ambient + kontak (terang kiri-atas) -> badan
 * gradien 45 derajat -> kilau radial -> ornamen -> tepi bevel.
 */
fun Modifier.depthRaised(
    spec: DepthSpec,
    shape: Shape,
    depth: DepthSize = DepthSize.LARGE,
    pressed: () -> Float = NoPress,
    style: AppThemeStyle? = null,
    accent: Color = Color.Unspecified
): Modifier = this.drawWithCache {
    val w = this.size.width
    val h = this.size.height
    val r = cornerRadiusPx(shape, this.size, this)
    val d = depth.distance.toPx()
    val blur = depth.blur.toPx()
    val ambient = blurPaint(blur)
    val contact = blurPaint(blur * CONTACT_SCALE)
    val big = (w / density) * (h / density) > BIG_AREA_DP2
    val ring = d + blur * RING_SPREAD
    val shadowRing = if (big && w > 2f * ring && h > 2f * ring) ring else 0f
    val span = (w + h) / 2f
    val body = Brush.linearGradient(listOf(spec.bodyHi, spec.bodyLo), Offset.Zero, Offset(span, span))
    // Kartu besar: kilau radial dibatasi ke area dekat sudut kiri-atas (bukan separuh layar yang tak terlihat bedanya).
    val sheenR = if (big) minOf(maxOf(w, h) * 0.75f, 200.dp.toPx()) else maxOf(w, h) * 0.75f
    val sheenCx = if (big) minOf(w * 0.28f, 90.dp.toPx()) else w * 0.28f
    val sheenCy = if (big) minOf(h * 0.22f, 60.dp.toPx()) else h * 0.22f
    val sheenSize = if (big) Size(minOf(w, sheenCx + sheenR), minOf(h, sheenCy + sheenR)) else Size(w, h)
    val sheen = Brush.radialGradient(
        0f to spec.highlight.copy(alpha = spec.sheenAlpha),
        0.4f to spec.highlight.copy(alpha = spec.sheenAlpha * 0.35f),
        1f to spec.highlight.copy(alpha = 0f),
        center = Offset(sheenCx, sheenCy),
        radius = sheenR.coerceAtLeast(1f)
    )
    val rimWidth = 1.5.dp.toPx()
    val rim = bevelBrush(spec.highlight, spec.rimLightAlpha, spec.shadow, spec.rimDarkAlpha, span)
    // Batch 86: kit cekung hanya dibangun saat benar-benar ditekan (sebelumnya selalu dibangun utk SETIAP elemen).
    val well by lazy(LazyThreadSafetyMode.NONE) { WellKit(spec, w, h, r, d, blur, rimWidth) }
    val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r, r))) }
    onDrawBehind {
        val p = pressed().coerceIn(0f, 1f)
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

/** Permukaan CEKUNG (lekuk): jalur tab, ubin ikon, bak progres, keadaan terpilih. */
fun Modifier.depthInset(
    spec: DepthSpec,
    shape: Shape,
    depth: DepthSize = DepthSize.WELL
): Modifier = this.drawWithCache {
    val kit = WellKit(
        spec, this.size.width, this.size.height, cornerRadiusPx(shape, this.size, this),
        depth.distance.toPx(), depth.blur.toPx(), 1.5.dp.toPx()
    )
    onDrawBehind { kit.draw(this, 1f) }
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

/** Kartu neumorfik yang bisa diketuk: saat ditekan tampak MASUK ke dalam (timbul -> cekung). */
@Composable
fun DepthCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    depth: DepthSize = DepthSize.LARGE,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val spec = LocalDepthSpec.current
    val style = LocalThemeStyle.current
    val accent = MaterialTheme.colorScheme.primary
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (isPressed) 1f else 0f, animationSpec = tween(90), label = "depthCardPress")
    Card(
        onClick = onClick,
        modifier = modifier.depthRaised(spec, shape, depth, pressed = { press }, style = style, accent = accent),
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
    val isPressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (isPressed) 1f else 0f, animationSpec = tween(90), label = "depthChipPress")
    val sel by animateFloatAsState(if (selected) 1f else 0f, animationSpec = tween(160), label = "depthChipSel")
    val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .depthRaised(spec, shape, DepthSize.SMALL, pressed = { maxOf(press, sel) })
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
    val isPressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (isPressed) 1f else 0f, animationSpec = tween(90), label = "depthIconPress")
    Box(
        modifier = modifier
            .size(size)
            .depthRaised(spec, CircleShape, DepthSize.SMALL, pressed = { press })
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
    val isPressed by source.collectIsPressedAsState()
    val press = animateFloatAsState(if (isPressed) 1f else 0f, animationSpec = tween(90), label = "depthSwitchPress")
    val position = animateFloatAsState(if (checked) 1f else 0f, animationSpec = tween(180), label = "depthSwitchPos")
    Box(
        modifier = modifier
            .then(if (enabled) Modifier else Modifier.alpha(0.45f))
            .size(trackWidth, knob + pad * 2)
            .depthInset(spec, RoundedCornerShape(50), DepthSize.SMALL)
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
                .depthRaised(spec, CircleShape, DepthSize.SMALL, pressed = { press.value })
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
