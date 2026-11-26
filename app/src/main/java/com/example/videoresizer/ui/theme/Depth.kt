package com.example.videoresizer.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    val insetHighlightAlpha: Float
)

/** Ukuran kedalaman: jarak offset + radius buram bayangan. LARGE = kartu, MEDIUM = panel kecil/dok, SMALL = tombol/chip, WELL = lekuk. */
enum class DepthSize(val distance: Dp, val blur: Dp) {
    LARGE(6.dp, 13.dp),
    MEDIUM(4.dp, 9.dp),
    SMALL(2.5.dp, 6.dp),
    WELL(4.dp, 8.dp)
}

private val DarkDepth = DepthSpec(
    bodyHi = Color(0xFF363B4A), bodyLo = Color(0xFF272B37),
    wellDark = Color(0xFF212530), wellLight = Color(0xFF2F3341),
    shadow = Color(0xFF000000), shadowAlpha = 0.62f,
    highlight = Color(0xFFFFFFFF), highlightAlpha = 0.13f,
    insetShadowAlpha = 0.70f, insetHighlightAlpha = 0.11f
)
private val LightDepth = DepthSpec(
    bodyHi = Color(0xFFEEF1F6), bodyLo = Color(0xFFD9DEE8),
    wellDark = Color(0xFFD3D9E4), wellLight = Color(0xFFE8ECF3),
    shadow = Color(0xFF8F9BB3), shadowAlpha = 0.60f,
    highlight = Color(0xFFFFFFFF), highlightAlpha = 1.0f,
    insetShadowAlpha = 0.65f, insetHighlightAlpha = 0.95f
)
private val NeonDepth = DepthSpec(
    bodyHi = Color(0xFF202C4C), bodyLo = Color(0xFF141D36),
    wellDark = Color(0xFF0F172B), wellLight = Color(0xFF1B2644),
    shadow = Color(0xFF01030A), shadowAlpha = 0.72f,
    highlight = Color(0xFF8CEBFF), highlightAlpha = 0.13f,
    insetShadowAlpha = 0.75f, insetHighlightAlpha = 0.12f
)
private val PaperDepth = DepthSpec(
    bodyHi = Color(0xFFF4EAD8), bodyLo = Color(0xFFE2D3BA),
    wellDark = Color(0xFFDFD0B5), wellLight = Color(0xFFF1E7D4),
    shadow = Color(0xFF9A8156), shadowAlpha = 0.55f,
    highlight = Color(0xFFFFF9EC), highlightAlpha = 1.0f,
    insetShadowAlpha = 0.55f, insetHighlightAlpha = 0.95f
)
private val GlassDepth = DepthSpec(
    bodyHi = Color(0xFF2D3C86), bodyLo = Color(0xFF1D2866),
    wellDark = Color(0xFF131B47), wellLight = Color(0xFF222F70),
    shadow = Color(0xFF020512), shadowAlpha = 0.70f,
    highlight = Color(0xFFB4C6FF), highlightAlpha = 0.16f,
    insetShadowAlpha = 0.75f, insetHighlightAlpha = 0.14f
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

private fun blurPaint(blur: Float): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        maskFilter = BlurMaskFilter(blur.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
    }

private fun cornerRadiusPx(shape: Shape, size: Size, density: Density): Float {
    val radius = (shape as? CornerBasedShape)?.topStart?.toPx(size, density) ?: 0f
    return radius.coerceAtMost(minOf(size.width, size.height) / 2f)
}

/** Peralatan gambar lekuk (cekung) yang di-cache per ukuran: jalur potong, gradien badan, bingkai bayangan dalam. */
private class WellKit(
    private val spec: DepthSpec,
    w: Float,
    h: Float,
    private val r: Float,
    private val d: Float,
    blur: Float
) {
    private val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r, r))) }
    private val brush = Brush.linearGradient(listOf(spec.wellDark, spec.wellLight), Offset.Zero, Offset(w, h))
    private val frame = android.graphics.Path().apply {
        fillType = android.graphics.Path.FillType.EVEN_ODD
        val m = blur * 3f
        addRect(-m, -m, w + m, h + m, android.graphics.Path.Direction.CW)
        addRoundRect(0f, 0f, w, h, r, r, android.graphics.Path.Direction.CW)
    }
    private val dark = blurPaint(blur)
    private val light = blurPaint(blur)

    fun draw(scope: DrawScope, intensity: Float) {
        with(scope) {
            clipPath(clip) {
                drawRoundRect(brush = brush, cornerRadius = CornerRadius(r, r), alpha = intensity)
                drawIntoCanvas { c ->
                    val n = c.nativeCanvas
                    dark.color = spec.shadow.copy(alpha = spec.insetShadowAlpha * intensity).toArgb()
                    n.save()
                    n.translate(d, d)
                    n.drawPath(frame, dark)
                    n.restore()
                    light.color = spec.highlight.copy(alpha = spec.insetHighlightAlpha * intensity).toArgb()
                    n.save()
                    n.translate(-d, -d)
                    n.drawPath(frame, light)
                    n.restore()
                }
            }
        }
    }
}

/**
 * Permukaan TIMBUL. `pressed` (0..1, dibaca saat menggambar) melebur ke keadaan cekung — tombol "tertekan ke dalam".
 * `style` != null menambah ornamen kartu khas tema (lihat [drawCardOrnament]).
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
    val dark = blurPaint(depth.blur.toPx())
    val light = blurPaint(depth.blur.toPx())
    val body = Brush.linearGradient(listOf(spec.bodyHi, spec.bodyLo), Offset.Zero, Offset(w, h))
    val well = WellKit(spec, w, h, r, d, depth.blur.toPx())
    val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r, r))) }
    onDrawBehind {
        val p = pressed().coerceIn(0f, 1f)
        val k = 1f - p
        if (k > 0.01f) {
            drawIntoCanvas { c ->
                val n = c.nativeCanvas
                dark.color = spec.shadow.copy(alpha = spec.shadowAlpha * k).toArgb()
                n.drawRoundRect(d, d, w + d, h + d, r, r, dark)
                light.color = spec.highlight.copy(alpha = spec.highlightAlpha * k).toArgb()
                n.drawRoundRect(-d, -d, w - d, h - d, r, r, light)
            }
        }
        drawRoundRect(brush = body, cornerRadius = CornerRadius(r, r))
        if (style != null) drawCardOrnament(style, accent, r, clip)
        if (p > 0.01f) well.draw(this, p)
    }
}

/** Permukaan CEKUNG (lekuk): jalur tab, ubin ikon, bak progres, keadaan terpilih. */
fun Modifier.depthInset(
    spec: DepthSpec,
    shape: Shape,
    depth: DepthSize = DepthSize.WELL
): Modifier = this.drawWithCache {
    val kit = WellKit(spec, this.size.width, this.size.height, cornerRadiusPx(shape, this.size, this), depth.distance.toPx(), depth.blur.toPx())
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
