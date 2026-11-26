package com.example.videoresizer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 * Batch 83: ornamen khas tiap tema. Semua digambar dengan Canvas (tanpa aset/PNG), deterministik (Random berbenih tetap,
 * jadi posisi tak "berkedip" saat rekomposisi) dan statis (tanpa animasi -> tanpa biaya baterai).
 *  - Latar layar penuh   : [themeBackdrop]   (aurora kaca / grid synthwave / kertas bergaris / rasi bintang / blob pastel)
 *  - Motif di tiap kartu : [drawCardOrnament] (dipanggil otomatis oleh DepthCard & permukaan timbul bergaya)
 *  - Seni kartu hero     : [drawHeroArt]
 */

/** Data latar layar untuk satu tema; dibuat oleh [rememberThemeBackdrop]. */
@Immutable
class ThemeBackdrop(
    val style: AppThemeStyle,
    val bg: Color,
    val accent: Color,
    val accent2: Color
)

@Composable
fun rememberThemeBackdrop(): ThemeBackdrop {
    val style = LocalThemeStyle.current
    val cs = MaterialTheme.colorScheme
    return remember(style, cs.background, cs.primary, cs.secondary) {
        ThemeBackdrop(style = style, bg = cs.background, accent = cs.primary, accent2 = cs.secondary)
    }
}

private const val BACKDROP_MAX_BYTES = 24L * 1024L * 1024L

private data class BackdropKey(val style: AppThemeStyle, val bg: Color, val accent: Color, val accent2: Color, val w: Int, val h: Int)

private class BackdropBake(val style: AppThemeStyle, val bg: Color, val accent: Color, val accent2: Color, val w: Int, val h: Int, val slot: BakeSlot)

/** Satu entri aktif: dipakai ulang untuk semua layar selama tema sama dan ukuran yang diminta tidak lebih besar (~5%) dari hasil bake. */
private var activeBackdropBake: BackdropBake? = null

private fun obtainBackdropBake(b: ThemeBackdrop, w: Int, h: Int, density: Density): BackdropBake {
    val cur = activeBackdropBake
    if (cur != null && cur.style == b.style && cur.bg == b.bg && cur.accent == b.accent && cur.accent2 == b.accent2 &&
        w <= cur.w * 1.05f && h <= cur.h * 1.05f
    ) {
        return cur
    }
    val key = BackdropKey(b.style, b.bg, b.accent, b.accent2, w, h)
    val slot = DepthBakery.request(key, w.toLong() * h * 4L) { bakeBitmap(w, h, 0, density) { drawThemeBackdrop(b) } }
    return BackdropBake(b.style, b.bg, b.accent, b.accent2, w, h, slot).also { activeBackdropBake = it }
}

/**
 * Menggambar latar tema (warna/gradien dasar + ornamen) di belakang layar.
 * Batch 88: latar statis dirender SEKALI ke bitmap (utas latar) dan dipakai bersama semua layar -> 1 quad bertekstur per frame,
 * bukan 3 gradien radial layar-penuh + puluhan lingkaran/garis/titik yang digambar ulang tiap frame gulir/transisi.
 * Sebelum bitmap siap (atau bila terlalu besar) digambar langsung seperti sebelumnya.
 */
fun Modifier.themeBackdrop(backdrop: ThemeBackdrop): Modifier = this.drawWithCache {
    val wi = size.width.roundToInt()
    val hi = size.height.roundToInt()
    val baked = if (wi > 0 && hi > 0 && wi.toLong() * hi * 4L <= BACKDROP_MAX_BYTES) {
        obtainBackdropBake(backdrop, wi, hi, Density(this.density, this.fontScale))
    } else {
        null
    }
    onDrawBehind {
        val image = baked?.slot?.image
        if (image != null) {
            drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset.Zero, IntSize(wi, hi))
        } else {
            drawThemeBackdrop(backdrop)
        }
    }
}

private fun DrawScope.drawThemeBackdrop(b: ThemeBackdrop) {
    if (b.style == AppThemeStyle.MIDNIGHT_BLUE_GLASS) drawRect(MidnightBlueGlassGradient) else drawRect(b.bg)
    when (b.style) {
        AppThemeStyle.MIDNIGHT_BLUE_GLASS -> glassBackdrop()
        AppThemeStyle.MIDNIGHT_NEON -> neonBackdrop(b.bg, b.accent, b.accent2)
        AppThemeStyle.WARM_PAPER -> paperBackdrop(b.accent, b.accent2)
        AppThemeStyle.DARK -> darkBackdrop(b.accent, b.accent2)
        AppThemeStyle.LIGHT -> lightBackdrop(b.accent)
    }
}

private fun DrawScope.orb(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = center, radius = radius),
        radius = radius,
        center = center
    )
}

private fun DrawScope.sparkle(center: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticBezierTo(center.x, center.y, center.x + r, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y + r)
        quadraticBezierTo(center.x, center.y, center.x - r, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(p, color, style = Fill)
}

private fun DrawScope.bracket(origin: Offset, sx: Float, sy: Float, len: Float, color: Color, strokeWidth: Float) {
    drawLine(color, origin, Offset(origin.x + sx * len, origin.y), strokeWidth)
    drawLine(color, origin, Offset(origin.x, origin.y + sy * len), strokeWidth)
}

private fun DrawScope.roundDots(points: List<Offset>, color: Color, width: Float) =
    drawPoints(points, PointMode.Points, color, width, StrokeCap.Round)

private fun DrawScope.scatter(rnd: Random, count: Int, w: Float, h: Float): List<Offset> =
    List(count) { Offset(rnd.nextFloat() * w, rnd.nextFloat() * h) }

// ---------------------------------------------------------------------------------------------------------------------
// Midnight Blue Glass: aurora, gelembung kaca, kilau diagonal.
private fun DrawScope.glassBackdrop() {
    val w = size.width
    val h = size.height
    val u = 1.dp.toPx()
    orb(Offset(w * 0.88f, h * 0.10f), w * 0.70f, Color(0xFF4C8DFF), 0.42f)
    orb(Offset(w * 0.02f, h * 0.48f), w * 0.60f, Color(0xFF64D2FF), 0.22f)
    orb(Offset(w * 0.92f, h * 0.88f), w * 0.75f, Color(0xFF8B6CFF), 0.30f)
    val rnd = Random(7)
    repeat(18) {
        val rad = (6f + rnd.nextFloat() * 26f) * u
        val c = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
        drawCircle(Color.White.copy(alpha = 0.04f), rad, c)
        drawCircle(Color.White.copy(alpha = 0.16f), rad, c, style = Stroke(width = u))
        drawCircle(Color.White.copy(alpha = 0.30f), rad * 0.16f, Offset(c.x - rad * 0.38f, c.y - rad * 0.38f))
    }
    rotate(-16f, pivot = Offset(w / 2f, h / 2f)) {
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent)),
            topLeft = Offset(-w * 0.2f, h * 0.30f),
            size = Size(w * 1.4f, h * 0.10f)
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Midnight Neon: matahari synthwave, grid perspektif, garis pindai, braket sudut, bintang.
private fun DrawScope.neonBackdrop(bg: Color, cyan: Color, magenta: Color) {
    val w = size.width
    val h = size.height
    val u = 1.dp.toPx()
    val horizon = h * 0.68f
    val sunRadius = w * 0.22f
    clipRect(0f, 0f, w, horizon) {
        drawCircle(
            brush = Brush.verticalGradient(
                listOf(magenta.copy(alpha = 0.34f), Color(0xFFFFB347).copy(alpha = 0.18f)),
                startY = horizon - sunRadius * 2f,
                endY = horizon
            ),
            radius = sunRadius,
            center = Offset(w * 0.70f, horizon)
        )
        for (i in 1..5) {
            drawRect(bg, topLeft = Offset(w * 0.70f - sunRadius, horizon - i * 7f * u), size = Size(sunRadius * 2f, i * 0.9f * u))
        }
    }
    val gridColor = cyan.copy(alpha = 0.16f)
    for (i in 1..9) {
        val t = i / 9f
        val y = horizon + (h - horizon) * t * t
        drawLine(gridColor, Offset(0f, y), Offset(w, y), u)
    }
    for (j in -12..12) {
        drawLine(gridColor, Offset(w / 2f + j * w * 0.035f, horizon), Offset(w / 2f + j * w * 0.22f, h), u)
    }
    drawRect(
        brush = Brush.verticalGradient(listOf(magenta.copy(alpha = 0.16f), Color.Transparent), startY = horizon, endY = horizon + h * 0.08f),
        topLeft = Offset(0f, horizon),
        size = Size(w, h * 0.08f)
    )
    drawLine(magenta.copy(alpha = 0.55f), Offset(0f, horizon), Offset(w, horizon), 1.5f * u)
    val scan = Path()
    var y = 0f
    while (y < h) {
        scan.moveTo(0f, y)
        scan.lineTo(w, y)
        y += 5f * u
    }
    drawPath(scan, cyan.copy(alpha = 0.035f), style = Stroke(width = u))
    val rnd = Random(11)
    repeat(26) {
        val c = if (it % 3 == 0) magenta else cyan
        drawCircle(c.copy(alpha = 0.55f), (0.6f + rnd.nextFloat() * 1.2f) * u, Offset(rnd.nextFloat() * w, rnd.nextFloat() * horizon * 0.9f))
    }
    val bracketColor = cyan.copy(alpha = 0.60f)
    bracket(Offset(16f * u, 16f * u), 1f, 1f, 26f * u, bracketColor, 1.5f * u)
    bracket(Offset(w - 16f * u, 16f * u), -1f, 1f, 26f * u, bracketColor, 1.5f * u)
    bracket(Offset(16f * u, h - 16f * u), 1f, -1f, 26f * u, bracketColor, 1.5f * u)
    bracket(Offset(w - 16f * u, h - 16f * u), -1f, -1f, 26f * u, bracketColor, 1.5f * u)
}

// ---------------------------------------------------------------------------------------------------------------------
// Warm Paper: serat kertas, garis buku tulis + garis margin, lubang kertas, selotip washi, cap pos.
private fun DrawScope.paperBackdrop(terracotta: Color, olive: Color) {
    val w = size.width
    val h = size.height
    val u = 1.dp.toPx()
    val ink = Color(0xFF8A7860)
    val rnd = Random(3)
    roundDots(scatter(rnd, 260, w, h), ink.copy(alpha = 0.07f), 1.4f * u)
    roundDots(scatter(rnd, 90, w, h), ink.copy(alpha = 0.12f), 2.2f * u)
    var y = 96f * u
    while (y < h) {
        drawLine(Color(0xFF7A9BB8).copy(alpha = 0.16f), Offset(0f, y), Offset(w, y), u)
        y += 30f * u
    }
    drawLine(terracotta.copy(alpha = 0.22f), Offset(42f * u, 0f), Offset(42f * u, h), 1.5f * u)
    for (k in 1..3) drawCircle(ink.copy(alpha = 0.16f), 5f * u, Offset(16f * u, h * k / 4f))
    rotate(-7f, pivot = Offset(60f * u, 22f * u)) { tape(Offset(-14f * u, 8f * u), 130f * u, 26f * u, terracotta) }
    rotate(9f, pivot = Offset(w - 60f * u, 22f * u)) { tape(Offset(w - 126f * u, 8f * u), 130f * u, 26f * u, olive) }
    stamp(Offset(w - 62f * u, h * 0.80f), 40f * u, terracotta.copy(alpha = 0.20f))
}

private fun DrawScope.tape(topLeft: Offset, length: Float, width: Float, color: Color) {
    drawRect(color.copy(alpha = 0.30f), topLeft, Size(length, width))
    var x = 6f
    while (x < length) {
        drawLine(Color.White.copy(alpha = 0.30f), Offset(topLeft.x + x, topLeft.y), Offset(topLeft.x + x - width * 0.6f, topLeft.y + width), 2f)
        x += 12f
    }
}

private fun DrawScope.stamp(center: Offset, radius: Float, color: Color) {
    val u = 1.dp.toPx()
    drawCircle(color, radius, center, style = Stroke(width = 2f * u))
    drawCircle(color, radius * 0.82f, center, style = Stroke(width = 1.2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * u, 3f * u))))
    sparkle(center, radius * 0.42f, color)
}

// ---------------------------------------------------------------------------------------------------------------------
// Dark: rasi bintang berhubung garis + cahaya aksen di sudut.
private fun DrawScope.darkBackdrop(accent: Color, accent2: Color) {
    val w = size.width
    val h = size.height
    val u = 1.dp.toPx()
    orb(Offset(w * 0.90f, h * 0.05f), w * 0.70f, accent, 0.20f)
    orb(Offset(0f, h * 0.90f), w * 0.60f, accent2, 0.12f)
    val rnd = Random(5)
    val pts = scatter(rnd, 9, w, h * 0.62f)
    for (i in 0 until pts.size - 1) drawLine(accent.copy(alpha = 0.14f), pts[i], pts[i + 1], u)
    pts.forEach {
        drawCircle(accent2.copy(alpha = 0.55f), 2f * u, it)
        drawCircle(accent2.copy(alpha = 0.12f), 7f * u, it)
    }
    roundDots(scatter(rnd, 40, w, h), Color.White.copy(alpha = 0.22f), 1.6f * u)
    roundDots(scatter(rnd, 14, w, h), Color.White.copy(alpha = 0.40f), 2.6f * u)
}

// ---------------------------------------------------------------------------------------------------------------------
// Light: blob pastel, kisi titik, cincin konsentris.
private fun DrawScope.lightBackdrop(accent: Color) {
    val w = size.width
    val h = size.height
    val u = 1.dp.toPx()
    orb(Offset(w * 0.92f, h * 0.06f), w * 0.75f, Color(0xFFB9A6FF), 0.55f)
    orb(Offset(0f, h * 0.50f), w * 0.60f, Color(0xFFFFC7A8), 0.45f)
    orb(Offset(w, h * 0.92f), w * 0.70f, Color(0xFFA6F0DC), 0.45f)
    val dots = ArrayList<Offset>()
    var gy = 14f * u
    while (gy < h) {
        var gx = 14f * u
        while (gx < w) {
            dots.add(Offset(gx, gy))
            gx += 22f * u
        }
        gy += 22f * u
    }
    roundDots(dots, Color(0xFF6B7488).copy(alpha = 0.16f), 1.6f * u)
    for (k in 1..3) drawCircle(accent.copy(alpha = 0.10f), (90f + 40f * k) * u, Offset(0f, 0f), style = Stroke(width = 1.5f * u))
}

// ---------------------------------------------------------------------------------------------------------------------
/**
 * Motif kecil di permukaan kartu timbul (dipotong ke bentuk kartu): kilau kaca / braket neon / jahitan kertas /
 * titik bintang / cincin pastel.
 */
internal fun DrawScope.drawCardOrnament(style: AppThemeStyle, accent: Color, r: Float, clip: Path) {
    val w = size.width
    val h = size.height
    val u = 1.dp.toPx()
    clipPath(clip) {
        when (style) {
            AppThemeStyle.MIDNIGHT_BLUE_GLASS -> {
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), startY = 0f, endY = h * 0.5f),
                    size = Size(w, h * 0.5f)
                )
                drawCircle(Color.White.copy(alpha = 0.22f), 14f * u, Offset(w - 26f * u, 24f * u), style = Stroke(width = u))
                drawCircle(Color.White.copy(alpha = 0.18f), 7f * u, Offset(w - 52f * u, 40f * u), style = Stroke(width = u))
                drawCircle(Color.White.copy(alpha = 0.20f), 3.5f * u, Offset(w - 40f * u, 12f * u))
            }
            AppThemeStyle.MIDNIGHT_NEON -> {
                val c = accent.copy(alpha = 0.70f)
                val inset = 8f * u
                bracket(Offset(inset, inset), 1f, 1f, 10f * u, c, 1.5f * u)
                bracket(Offset(w - inset, inset), -1f, 1f, 10f * u, c, 1.5f * u)
                bracket(Offset(inset, h - inset), 1f, -1f, 10f * u, c, 1.5f * u)
                bracket(Offset(w - inset, h - inset), -1f, -1f, 10f * u, c, 1.5f * u)
                drawLine(accent.copy(alpha = 0.55f), Offset(w * 0.12f, h - 1.5f * u), Offset(w * 0.88f, h - 1.5f * u), 1.5f * u)
                val scan = Path()
                var y = 0f
                while (y < h) {
                    scan.moveTo(0f, y)
                    scan.lineTo(w, y)
                    y += 6f * u
                }
                drawPath(scan, accent.copy(alpha = 0.035f), style = Stroke(width = u))
            }
            AppThemeStyle.WARM_PAPER -> {
                drawRoundRect(
                    color = Color(0xFF8A7860).copy(alpha = 0.32f),
                    topLeft = Offset(6f * u, 6f * u),
                    size = Size(w - 12f * u, h - 12f * u),
                    cornerRadius = CornerRadius((r - 6f * u).coerceAtLeast(2f * u)),
                    style = Stroke(width = 1.2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * u, 4f * u)))
                )
            }
            AppThemeStyle.DARK -> {
                val dots = ArrayList<Offset>()
                for (i in 0..2) for (j in 0..2) dots.add(Offset(w - 14f * u - i * 8f * u, 14f * u + j * 8f * u))
                roundDots(dots, Color.White.copy(alpha = 0.22f), 2f * u)
                drawLine(Color.White.copy(alpha = 0.07f), Offset(r, 1f * u), Offset(w - r, 1f * u), u)
            }
            AppThemeStyle.LIGHT -> {
                drawCircle(accent.copy(alpha = 0.12f), 34f * u, Offset(w, 0f), style = Stroke(width = 1.5f * u))
                drawCircle(accent.copy(alpha = 0.10f), 54f * u, Offset(w, 0f), style = Stroke(width = 1.5f * u))
                drawCircle(accent.copy(alpha = 0.14f), 2.5f * u, Offset(w - 22f * u, h - 16f * u))
                drawCircle(accent.copy(alpha = 0.10f), 2.5f * u, Offset(w - 34f * u, h - 16f * u))
            }
        }
    }
}

/** Seni dekoratif besar untuk kartu hero (digambar di atas gradien aksen dengan putih/alfa rendah). */
fun DrawScope.drawHeroArt(style: AppThemeStyle) {
    val w = size.width
    val h = size.height
    val u = 1.dp.toPx()
    val c = Color.White
    when (style) {
        AppThemeStyle.MIDNIGHT_BLUE_GLASS -> {
            drawCircle(c.copy(alpha = 0.10f), 62f * u, Offset(w * 0.84f, h * 0.30f))
            drawCircle(c.copy(alpha = 0.28f), 62f * u, Offset(w * 0.84f, h * 0.30f), style = Stroke(width = u))
            drawCircle(c.copy(alpha = 0.10f), 40f * u, Offset(w * 0.97f, h * 0.78f))
            drawCircle(c.copy(alpha = 0.24f), 24f * u, Offset(w * 0.66f, h * 0.90f), style = Stroke(width = u))
            sparkle(Offset(w * 0.62f, h * 0.16f), 9f * u, c.copy(alpha = 0.55f))
            sparkle(Offset(w * 0.92f, h * 0.60f), 6f * u, c.copy(alpha = 0.45f))
        }
        AppThemeStyle.MIDNIGHT_NEON -> {
            for (k in 1..4) drawCircle(c.copy(alpha = 0.20f), (30f + k * 22f) * u, Offset(w * 0.90f, h * 0.45f), style = Stroke(width = u))
            for (k in 0..2) drawLine(c.copy(alpha = 0.16f), Offset(w * 0.55f + k * 22f * u, h), Offset(w * 0.75f + k * 22f * u, 0f), 2f * u)
            bracket(Offset(w - 14f * u, 14f * u), -1f, 1f, 18f * u, c.copy(alpha = 0.6f), 1.5f * u)
        }
        AppThemeStyle.WARM_PAPER -> {
            drawCircle(c.copy(alpha = 0.34f), 46f * u, Offset(w * 0.86f, h * 0.42f), style = Stroke(width = 2f * u))
            drawCircle(
                c.copy(alpha = 0.30f), 37f * u, Offset(w * 0.86f, h * 0.42f),
                style = Stroke(width = 1.2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * u, 3f * u)))
            )
            sparkle(Offset(w * 0.86f, h * 0.42f), 16f * u, c.copy(alpha = 0.40f))
            for (k in 0..5) drawLine(c.copy(alpha = 0.14f), Offset(w * 0.60f + k * 12f * u, h), Offset(w * 0.60f + k * 12f * u + 24f * u, h - 24f * u), 1.5f * u)
        }
        AppThemeStyle.DARK -> {
            val pts = listOf(
                Offset(w * 0.58f, h * 0.22f), Offset(w * 0.70f, h * 0.55f), Offset(w * 0.82f, h * 0.28f),
                Offset(w * 0.92f, h * 0.62f), Offset(w * 0.76f, h * 0.86f)
            )
            for (i in 0 until pts.size - 1) drawLine(c.copy(alpha = 0.28f), pts[i], pts[i + 1], u)
            pts.forEach {
                drawCircle(c.copy(alpha = 0.60f), 2.5f * u, it)
                drawCircle(c.copy(alpha = 0.14f), 8f * u, it)
            }
        }
        AppThemeStyle.LIGHT -> {
            for (k in 1..4) drawCircle(c.copy(alpha = 0.22f), (24f + k * 20f) * u, Offset(w * 0.92f, h * 0.18f), style = Stroke(width = 1.5f * u))
            val dots = ArrayList<Offset>()
            for (i in 0..4) for (j in 0..3) dots.add(Offset(w * 0.60f + i * 14f * u, h * 0.62f + j * 14f * u))
            roundDots(dots, c.copy(alpha = 0.35f), 2.4f * u)
        }
    }
}
