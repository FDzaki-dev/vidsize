package com.example.videoresizer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * Batch 83: ornamen khas tiap tema. Semua digambar dengan Canvas (tanpa aset/PNG), deterministik (Random berbenih tetap,
 * jadi posisi tak "berkedip" saat rekomposisi) dan statis (tanpa animasi -> tanpa biaya baterai).
 *  - Latar layar penuh   : [themeBackdrop]   (aurora kaca / grid synthwave / kertas bergaris / rasi bintang / blob pastel /
 *                          damask emas + bingkai berukir di atas burgundy [Batch 94] / peta bintang astrolab + nebula + gerhana [Batch 98])
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
            DepthBakery.noteDirectDraw()
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
        AppThemeStyle.BURGUNDY_OLD_MONEY -> burgundyBackdrop()
        AppThemeStyle.COSMIC_ORANGE -> cosmicBackdrop()
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
// ---------------------------------------------------------------------------------------------------------------------
// Batch 94 — Burgundy Old Money: kemewahan "perpustakaan tua". Semua vektor Canvas (tanpa aset), statis, deterministik.
//  - Latar    : gradien anggur + cahaya lilin emas + damask berlian (kisi + bintang emas) + vignette + medali tanda air +
//               bingkai emas ganda dengan ukiran sudut/tepi + debu emas.
//  - Kartu    : bingkai emas ganda + permata sudut + fleur-de-lis mini.
//  - Hero     : bingkai tinta ganda + permata sudut + medali heraldik (sinar, karangan laurel, mutiara, fleur-de-lis).
// Geometri bersama (kelopak, sinar, berlian, bintang) dibangun ke SATU Path per elemen -> 1 panggilan gambar per motif.
// Winding semua sub-path sama (searah jarum jam) supaya isi NonZero menyatu (tanpa lubang di titik tumpang tindih).

private val BurgundyGold = Color(0xFFD9B66A)
private val BurgundyGoldBright = Color(0xFFF3DDA0)
private const val DEG_TO_RAD = 0.017453292f
private const val TWO_PI = 6.2831855f

private fun Path.addDiamond(c: Offset, rx: Float, ry: Float) {
    moveTo(c.x, c.y - ry)
    lineTo(c.x + rx, c.y)
    lineTo(c.x, c.y + ry)
    lineTo(c.x - rx, c.y)
    close()
}

private fun Path.addStar4(c: Offset, r: Float) {
    moveTo(c.x, c.y - r)
    quadraticBezierTo(c.x, c.y, c.x + r, c.y)
    quadraticBezierTo(c.x, c.y, c.x, c.y + r)
    quadraticBezierTo(c.x, c.y, c.x - r, c.y)
    quadraticBezierTo(c.x, c.y, c.x, c.y - r)
    close()
}

private fun Path.addBox(x: Float, y: Float, w: Float, h: Float) {
    moveTo(x, y)
    lineTo(x + w, y)
    lineTo(x + w, y + h)
    lineTo(x, y + h)
    close()
}

/** Kelopak tetesan: pangkal di [base], menunjuk ke atas lalu diputar [angleDeg] derajat searah jarum jam di sekitar [base]. */
private fun Path.addPetal(base: Offset, length: Float, width: Float, angleDeg: Float) {
    val a = angleDeg * DEG_TO_RAD
    val ca = cos(a)
    val sa = sin(a)
    fun pt(x: Float, y: Float) = Offset(base.x + x * ca - y * sa, base.y + x * sa + y * ca)
    val tip = pt(0f, -length)
    val r1 = pt(width, -length * 0.62f)
    val r2 = pt(width * 0.9f, -length * 0.16f)
    val l1 = pt(-width * 0.9f, -length * 0.16f)
    val l2 = pt(-width, -length * 0.62f)
    moveTo(tip.x, tip.y)
    cubicTo(r1.x, r1.y, r2.x, r2.y, base.x, base.y)
    cubicTo(l1.x, l1.y, l2.x, l2.y, tip.x, tip.y)
    close()
}

/** Sinar segitiga dari radius [r0] ke [r1] pada sudut [angleDeg] (0 = kanan, searah jarum jam), lebar dasar 2 * [half]. */
private fun Path.addRay(c: Offset, angleDeg: Float, r0: Float, r1: Float, half: Float) {
    val a = angleDeg * DEG_TO_RAD
    val dx = cos(a)
    val dy = sin(a)
    moveTo(c.x + dx * r0 - dy * half, c.y + dy * r0 + dx * half)
    lineTo(c.x + dx * r1, c.y + dy * r1)
    lineTo(c.x + dx * r0 + dy * half, c.y + dy * r0 - dx * half)
    close()
}

/** Fleur-de-lis (lambang bunga lili): 3 kelopak + pita pengikat + 3 ekor; [s] = setengah tinggi total. */
private fun DrawScope.fleurDeLis(center: Offset, s: Float, color: Color) {
    val y0 = center.y + 0.28f * s
    val base = Offset(center.x, y0)
    val p = Path()
    p.addPetal(base, 1.28f * s, 0.26f * s, 0f)
    p.addPetal(base, 0.92f * s, 0.34f * s, -74f)
    p.addPetal(base, 0.92f * s, 0.34f * s, 74f)
    p.addBox(center.x - 0.52f * s, y0 - 0.02f * s, 1.04f * s, 0.17f * s)
    val tail = Offset(center.x, y0 + 0.15f * s)
    p.addPetal(tail, 0.56f * s, 0.18f * s, 180f)
    p.addPetal(tail, 0.52f * s, 0.17f * s, 128f)
    p.addPetal(tail, 0.52f * s, 0.17f * s, -128f)
    drawPath(p, color, style = Fill)
}

/**
 * Medali heraldik berjari-jari [r] di [c]: mahkota sinar, cincin, karangan laurel (2 cabang, daun berpasangan), permata atas-bawah,
 * cincin mutiara, cincin dalam, fleur-de-lis. [k] = pengali alfa seluruh motif (hero ~0.8, tanda air latar ~0.13).
 */
private fun DrawScope.heraldicMedallion(c: Offset, r: Float, color: Color, k: Float) {
    val u = 1.dp.toPx()
    val rays = Path()
    for (i in 0 until 32) rays.addRay(c, i * 11.25f, r * 0.88f, r * (if (i % 2 == 0) 1.0f else 0.94f), r * 0.028f)
    drawPath(rays, color.copy(alpha = 0.30f * k), style = Fill)
    drawCircle(color.copy(alpha = 0.55f * k), r * 0.84f, c, style = Stroke(width = 1.2f * u))
    val laurel = Path()
    val lr = r * 0.66f
    val n = 9
    for (i in 0 until n) {
        val t = i / (n - 1f)
        val sc = 1f - 0.25f * t
        val len = r * 0.16f * sc
        val wid = r * 0.055f * sc
        val thR = 88f - 150f * t
        val pR = Offset(c.x + lr * cos(thR * DEG_TO_RAD), c.y + lr * sin(thR * DEG_TO_RAD))
        laurel.addPetal(pR, len, wid, thR + 38f)
        laurel.addPetal(pR, len, wid, thR - 38f)
        val thL = 92f + 150f * t
        val pL = Offset(c.x + lr * cos(thL * DEG_TO_RAD), c.y + lr * sin(thL * DEG_TO_RAD))
        laurel.addPetal(pL, len, wid, thL + 142f)
        laurel.addPetal(pL, len, wid, thL + 218f)
    }
    drawPath(laurel, color.copy(alpha = 0.50f * k), style = Fill)
    val gems = Path()
    gems.addDiamond(Offset(c.x, c.y + lr), r * 0.06f, r * 0.085f)
    gems.addDiamond(Offset(c.x, c.y - lr), r * 0.045f, r * 0.06f)
    drawPath(gems, color.copy(alpha = 0.60f * k), style = Fill)
    val pearls = List(22) { i ->
        val a = i * TWO_PI / 22f
        Offset(c.x + r * 0.47f * cos(a), c.y + r * 0.47f * sin(a))
    }
    roundDots(pearls, color.copy(alpha = 0.50f * k), r * 0.05f)
    drawCircle(color.copy(alpha = 0.40f * k), r * 0.40f, c, style = Stroke(width = u))
    fleurDeLis(c, r * 0.30f, color.copy(alpha = 0.62f * k))
}

/** Satu gulungan (ikal) ukiran sudut; [pt] memetakan koordinat satuan (0..1) ke piksel — dipanggil normal & ditukar (x,y) utk cermin diagonal. */
private fun scrollCurl(pt: (Float, Float) -> Offset): Path = Path().apply {
    val p0 = pt(0.34f, 0.14f)
    moveTo(p0.x, p0.y)
    val a1 = pt(0.66f, 0.10f)
    val a2 = pt(0.86f, 0.30f)
    val a3 = pt(0.70f, 0.50f)
    cubicTo(a1.x, a1.y, a2.x, a2.y, a3.x, a3.y)
    val b1 = pt(0.58f, 0.64f)
    val b2 = pt(0.38f, 0.54f)
    val b3 = pt(0.46f, 0.40f)
    cubicTo(b1.x, b1.y, b2.x, b2.y, b3.x, b3.y)
}

/** Ukiran sudut bingkai: dua garis tepi, permata berlian, dan dua gulungan simetris diagonal. [sx]/[sy] = arah ke dalam (+1/-1). */
private fun DrawScope.cornerFlourish(o: Offset, sx: Float, sy: Float, size: Float, color: Color, u: Float) {
    fun pt(x: Float, y: Float) = Offset(o.x + sx * size * x, o.y + sy * size * y)
    drawLine(color, pt(0f, 0f), pt(1f, 0f), 1.4f * u, StrokeCap.Round)
    drawLine(color, pt(0f, 0f), pt(0f, 1f), 1.4f * u, StrokeCap.Round)
    val curlStroke = Stroke(width = 1.15f * u, cap = StrokeCap.Round)
    drawPath(scrollCurl { x, y -> pt(x, y) }, color, style = curlStroke)
    drawPath(scrollCurl { x, y -> pt(y, x) }, color, style = curlStroke)
    val gem = Path().apply { addDiamond(pt(0.17f, 0.17f), size * 0.085f, size * 0.085f) }
    drawPath(gem, color, style = Fill)
}

/** Gulungan horizontal simetris di tengah sisi bingkai; [dy] = +1 (tepi atas, melengkung ke bawah) atau -1 (tepi bawah). */
private fun DrawScope.edgeFlourish(cx: Float, y: Float, dy: Float, color: Color, u: Float) {
    for (sx in floatArrayOf(-1f, 1f)) {
        fun px(v: Float) = cx + sx * v * u
        fun py(v: Float) = y + dy * v * u
        val p = Path().apply {
            moveTo(px(10f), py(0f))
            lineTo(px(36f), py(0f))
            cubicTo(px(50f), py(0f), px(56f), py(12f), px(46f), py(15f))
            cubicTo(px(38f), py(17f), px(34f), py(8f), px(40f), py(6f))
        }
        drawPath(p, color, style = Stroke(width = 1.1f * u, cap = StrokeCap.Round))
        roundDots(listOf(Offset(px(60f), py(0f)), Offset(px(70f), py(0f))), color, 2.2f * u)
    }
    val gem = Path().apply { addDiamond(Offset(cx, y), 4.2f * u, 4.2f * u) }
    drawPath(gem, color.copy(alpha = 0.65f), style = Fill)
}

private fun DrawScope.burgundyBackdrop() {
    val w = size.width
    val h = size.height
    val u = 1.dp.toPx()
    val gold = BurgundyGold
    drawRect(
        Brush.verticalGradient(0f to Color(0xFF55182B), 0.45f to Color(0xFF3A0F1B), 1f to Color(0xFF260811))
    )
    orb(Offset(w * 0.5f, -h * 0.02f), w * 1.05f, Color(0xFFE6B965), 0.20f)
    orb(Offset(w * 0.95f, h * 0.62f), w * 0.70f, Color(0xFF8C1E34), 0.30f)
    // Damask: kisi berlian (lereng dx/dy = 2/3) + bintang empat-arah di pusat tiap berlian (pola half-drop).
    val cell = 52f * u
    val slope = 2f / 3f
    val trellis = Path()
    var x0 = -slope * h
    while (x0 <= w) {
        trellis.moveTo(x0, 0f)
        trellis.lineTo(x0 + slope * h, h)
        x0 += cell
    }
    x0 = 0f
    while (x0 <= w + slope * h) {
        trellis.moveTo(x0, 0f)
        trellis.lineTo(x0 - slope * h, h)
        x0 += cell
    }
    drawPath(trellis, gold.copy(alpha = 0.09f), style = Stroke(width = u))
    val stars = Path()
    var q = 0
    while (q * 0.75f * cell < h + cell) {
        val y = 0.75f * cell * q
        val shift = 0.5f * cell * (q - 1)
        var x = (shift % cell) - cell
        while (x <= w + cell) {
            if (x >= -cell) stars.addStar4(Offset(x, y), 5.5f * u)
            x += cell
        }
        q++
    }
    drawPath(stars, gold.copy(alpha = 0.16f), style = Fill)
    drawRect(
        Brush.radialGradient(
            0f to Color.Transparent,
            0.30f to Color.Transparent,
            1f to Color(0xFF12030A).copy(alpha = 0.60f),
            center = Offset(w / 2f, h * 0.38f),
            radius = maxOf(w, h) * 0.85f
        )
    )
    heraldicMedallion(Offset(w * 0.5f, h * 0.95f), w * 0.38f, gold, 0.13f)
    // Bingkai emas ganda + ukiran.
    val inset = 9f * u
    drawRect(gold.copy(alpha = 0.34f), Offset(inset, inset), Size(w - 2f * inset, h - 2f * inset), style = Stroke(width = 1.2f * u))
    drawRect(
        gold.copy(alpha = 0.16f), Offset(inset + 4f * u, inset + 4f * u),
        Size(w - 2f * inset - 8f * u, h - 2f * inset - 8f * u), style = Stroke(width = 0.7f * u)
    )
    val corner = gold.copy(alpha = 0.62f)
    cornerFlourish(Offset(inset, inset), 1f, 1f, 40f * u, corner, u)
    cornerFlourish(Offset(w - inset, inset), -1f, 1f, 40f * u, corner, u)
    cornerFlourish(Offset(inset, h - inset), 1f, -1f, 40f * u, corner, u)
    cornerFlourish(Offset(w - inset, h - inset), -1f, -1f, 40f * u, corner, u)
    edgeFlourish(w / 2f, inset, 1f, gold.copy(alpha = 0.55f), u)
    edgeFlourish(w / 2f, h - inset, -1f, gold.copy(alpha = 0.55f), u)
    roundDots(scatter(Random(13), 34, w, h * 0.70f), BurgundyGoldBright.copy(alpha = 0.30f), 1.5f * u)
}

// ---------------------------------------------------------------------------------------------------------------------
// Batch 98 — Cosmic Orange: "peta bintang & astrolab". Semua vektor Canvas (tanpa aset), statis, deterministik (Random berbenih).
//  - Latar : gradien antariksa + tiga kabut nebula (oranye surya, ungu, bara) + medan bintang berkilau + astrolab (cincin
//            konsentris dengan skala derajat) + orbit miring berplanet/berbulan + komet + cincin gerhana + penggaris peta bintang.
//  - Kartu : orbit dua busur dengan satelit di sudut kanan-bawah + kilau bintang kanan-atas.
//  - Hero  : planet garis-tinta bercincin (cincin belakang/depan), orbit putus-putus dengan bulan, kilau bintang.

private val CosmicOrange = Color(0xFFFF8A2A)
private val CosmicAmber = Color(0xFFFFC56E)
private val CosmicStar = Color(0xFFFFE9CF)

/** Astrolab: lingkaran konsentris [radii] di sekitar [c] + skala derajat (tik tiap 6 derajat, tik panjang tiap 30 derajat) pada cincin [tickRing]. */
private fun DrawScope.astrolabe(c: Offset, radii: FloatArray, tickRing: Int, color: Color, u: Float) {
    for (i in radii.indices) {
        drawCircle(color.copy(alpha = 0.20f - i * 0.04f), radii[i], c, style = Stroke(width = u))
    }
    val ring = radii[tickRing]
    val ticks = Path()
    for (i in 0 until 60) {
        val a = i * 6f * DEG_TO_RAD
        val len = if (i % 5 == 0) 9f * u else 4.5f * u
        ticks.moveTo(c.x + cos(a) * ring, c.y + sin(a) * ring)
        ticks.lineTo(c.x + cos(a) * (ring + len), c.y + sin(a) * (ring + len))
    }
    drawPath(ticks, color.copy(alpha = 0.24f), style = Stroke(width = u))
}

private fun DrawScope.cosmicBackdrop() {
    val w = size.width
    val h = size.height
    val u = 1.dp.toPx()
    drawRect(Brush.verticalGradient(0f to Color(0xFF2E1B4D), 0.45f to Color(0xFF1E1433), 1f to Color(0xFF0E0720)))
    // Nebula: oranye surya kanan-atas, ungu kiri-tengah, bara oranye kanan-bawah.
    orb(Offset(w * 0.86f, h * 0.06f), w * 0.95f, CosmicOrange, 0.24f)
    orb(Offset(w * 0.02f, h * 0.44f), w * 0.75f, Color(0xFFA24DFF), 0.20f)
    orb(Offset(w * 0.90f, h * 0.94f), w * 0.95f, Color(0xFFFF5A1F), 0.26f)
    // Medan bintang: titik redup/terang/amber + kilau empat-arah (satu Path).
    val rnd = Random(21)
    roundDots(scatter(rnd, 70, w, h), CosmicStar.copy(alpha = 0.34f), 1.3f * u)
    roundDots(scatter(rnd, 26, w, h), CosmicStar.copy(alpha = 0.62f), 2f * u)
    roundDots(scatter(rnd, 16, w, h), CosmicAmber.copy(alpha = 0.55f), 2.2f * u)
    val glints = Path()
    repeat(9) { glints.addStar4(Offset(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.85f), (4f + rnd.nextFloat() * 4f) * u) }
    drawPath(glints, CosmicStar.copy(alpha = 0.55f), style = Fill)
    // Gerhana (hanya cincin + sabit terang, tanpa piringan gelap supaya tombol header tetap jelas) di kanan-atas.
    val ec = Offset(w * 0.90f, h * 0.05f)
    val er = w * 0.13f
    orb(ec, er * 2.4f, CosmicOrange, 0.20f)
    drawCircle(CosmicOrange.copy(alpha = 0.40f), er, ec, style = Stroke(width = 1.2f * u))
    drawCircle(CosmicOrange.copy(alpha = 0.14f), er * 1.35f, ec, style = Stroke(width = u))
    drawArc(
        CosmicAmber.copy(alpha = 0.85f), -155f, 95f, false, Offset(ec.x - er, ec.y - er), Size(2f * er, 2f * er),
        style = Stroke(width = 3f * u, cap = StrokeCap.Round)
    )
    // Astrolab raksasa di kanan-bawah + orbit miring berplanet dan berbulan.
    val ac = Offset(w * 0.88f, h * 0.90f)
    astrolabe(ac, floatArrayOf(w * 0.22f, w * 0.34f, w * 0.50f, w * 0.70f), 2, CosmicOrange, u)
    rotate(-24f, pivot = ac) {
        val orx = w * 0.62f
        val ory = w * 0.20f
        drawOval(CosmicOrange.copy(alpha = 0.26f), Offset(ac.x - orx, ac.y - ory), Size(2f * orx, 2f * ory), style = Stroke(width = u))
        val t1 = 215f * DEG_TO_RAD
        val planet = Offset(ac.x + orx * cos(t1), ac.y + ory * sin(t1))
        orb(planet, 18f * u, CosmicOrange, 0.55f)
        drawCircle(CosmicOrange, 5f * u, planet)
        val t2 = 250f * DEG_TO_RAD
        drawCircle(CosmicStar.copy(alpha = 0.85f), 2.4f * u, Offset(ac.x + orx * cos(t2), ac.y + ory * sin(t2)))
    }
    // Komet: ekor memudar ke kepala amber di kiri-bawah.
    val tail = Offset(w * 0.05f, h * 0.70f)
    val head = Offset(w * 0.34f, h * 0.79f)
    drawLine(
        Brush.linearGradient(listOf(Color.Transparent, CosmicAmber.copy(alpha = 0.75f)), start = tail, end = head),
        tail, head, 2f * u, StrokeCap.Round
    )
    orb(head, 14f * u, CosmicAmber, 0.50f)
    drawCircle(CosmicStar, 2.6f * u, head)
    drawRect(
        Brush.radialGradient(
            0f to Color.Transparent,
            0.35f to Color.Transparent,
            1f to Color(0xFF07030F).copy(alpha = 0.55f),
            center = Offset(w / 2f, h * 0.40f),
            radius = maxOf(w, h) * 0.85f
        )
    )
    // Penggaris peta bintang di tepi atas/bawah (tik panjang tiap 5).
    val ruler = Path()
    var px = 0f
    var i = 0
    while (px <= w) {
        val len = if (i % 5 == 0) 7f * u else 3.5f * u
        ruler.moveTo(px, 6f * u)
        ruler.lineTo(px, 6f * u + len)
        ruler.moveTo(px, h - 6f * u)
        ruler.lineTo(px, h - 6f * u - len)
        px += 12f * u
        i++
    }
    drawPath(ruler, CosmicOrange.copy(alpha = 0.16f), style = Stroke(width = u))
}

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
            AppThemeStyle.BURGUNDY_OLD_MONEY -> {
                // Bingkai emas ganda + permata di tengah busur tiap sudut + fleur-de-lis mini kanan-atas.
                val gold = if (accent == Color.Unspecified) BurgundyGold else accent
                val i1 = 5.5f * u
                drawRoundRect(
                    gold.copy(alpha = 0.50f), Offset(i1, i1), Size(w - 2f * i1, h - 2f * i1),
                    CornerRadius((r - i1).coerceAtLeast(2f * u)), style = Stroke(width = 0.9f * u)
                )
                val i2 = 8.5f * u
                drawRoundRect(
                    gold.copy(alpha = 0.22f), Offset(i2, i2), Size(w - 2f * i2, h - 2f * i2),
                    CornerRadius((r - i2).coerceAtLeast(2f * u)), style = Stroke(width = 0.6f * u)
                )
                val k = (r - i1).coerceAtLeast(0f) * 0.7071f
                val gems = Path()
                gems.addDiamond(Offset(r - k, r - k), 2.8f * u, 2.8f * u)
                gems.addDiamond(Offset(w - r + k, r - k), 2.8f * u, 2.8f * u)
                gems.addDiamond(Offset(r - k, h - r + k), 2.8f * u, 2.8f * u)
                gems.addDiamond(Offset(w - r + k, h - r + k), 2.8f * u, 2.8f * u)
                drawPath(gems, gold.copy(alpha = 0.80f), style = Fill)
                fleurDeLis(Offset(w - 26f * u, 28f * u), 8f * u, gold.copy(alpha = 0.40f))
            }
            AppThemeStyle.COSMIC_ORANGE -> {
                // Orbit dua busur + satelit di sudut kanan-bawah; kilau bintang kecil di kanan-atas.
                val orange = if (accent == Color.Unspecified) CosmicOrange else accent
                val oc = Offset(w, h)
                drawCircle(orange.copy(alpha = 0.18f), 26f * u, oc, style = Stroke(width = u))
                drawCircle(orange.copy(alpha = 0.11f), 44f * u, oc, style = Stroke(width = u))
                val sa = -132f * DEG_TO_RAD
                val sat = Offset(oc.x + cos(sa) * 44f * u, oc.y + sin(sa) * 44f * u)
                drawCircle(orange.copy(alpha = 0.22f), 6f * u, sat)
                drawCircle(orange.copy(alpha = 0.85f), 2.4f * u, sat)
                val glints = Path()
                glints.addStar4(Offset(w - 22f * u, 22f * u), 6f * u)
                glints.addStar4(Offset(w - 40f * u, 36f * u), 3f * u)
                drawPath(glints, CosmicStar.copy(alpha = 0.55f), style = Fill)
                roundDots(listOf(Offset(w - 14f * u, 40f * u), Offset(w - 52f * u, 18f * u)), CosmicStar.copy(alpha = 0.35f), 1.6f * u)
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
        AppThemeStyle.BURGUNDY_OLD_MONEY -> {
            // Hero = emas (gradien aksen) -> seni digambar dgn TINTA burgundy (bukan putih): bingkai ganda, permata sudut, medali heraldik.
            val ink = BurgundyInk
            drawRoundRect(
                ink.copy(alpha = 0.34f), Offset(8f * u, 8f * u), Size(w - 16f * u, h - 16f * u),
                CornerRadius(18f * u), style = Stroke(width = 1.2f * u)
            )
            drawRoundRect(
                ink.copy(alpha = 0.18f), Offset(12f * u, 12f * u), Size(w - 24f * u, h - 24f * u),
                CornerRadius(14f * u), style = Stroke(width = 0.7f * u)
            )
            val k = 18f * u * 0.7071f
            val gems = Path()
            gems.addDiamond(Offset(26f * u - k, 26f * u - k), 3f * u, 3f * u)
            gems.addDiamond(Offset(w - 26f * u + k, 26f * u - k), 3f * u, 3f * u)
            gems.addDiamond(Offset(26f * u - k, h - 26f * u + k), 3f * u, 3f * u)
            gems.addDiamond(Offset(w - 26f * u + k, h - 26f * u + k), 3f * u, 3f * u)
            drawPath(gems, ink.copy(alpha = 0.45f), style = Fill)
            heraldicMedallion(Offset(w - 66f * u, 66f * u), 50f * u, ink, 0.80f)
            val glints = Path()
            glints.addStar4(Offset(w - 24f * u, h - 40f * u), 7f * u)
            glints.addStar4(Offset(w * 0.60f, 26f * u), 5f * u)
            drawPath(glints, ink.copy(alpha = 0.40f), style = Fill)
        }
        AppThemeStyle.COSMIC_ORANGE -> {
            // Hero = oranye (gradien aksen) -> seni garis dgn TINTA cokelat-ungu: planet bercincin, orbit putus-putus + bulan, kilau bintang.
            val ink = CosmicInk
            val pc = Offset(w - 64f * u, 54f * u)
            val pr = 24f * u
            rotate(-22f, pivot = pc) {
                drawOval(
                    ink.copy(alpha = 0.28f), Offset(pc.x - 66f * u, pc.y - 20f * u), Size(132f * u, 40f * u),
                    style = Stroke(width = u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * u, 4f * u)))
                )
                val moon = 20f * DEG_TO_RAD
                drawCircle(ink.copy(alpha = 0.75f), 3.6f * u, Offset(pc.x + 66f * u * cos(moon), pc.y + 20f * u * sin(moon)))
                // Cincin belakang (setengah atas), lalu planet, lalu cincin depan (setengah bawah).
                drawArc(ink.copy(alpha = 0.80f), 180f, 180f, false, Offset(pc.x - 46f * u, pc.y - 11f * u), Size(92f * u, 22f * u), style = Stroke(width = 2.4f * u))
                drawCircle(ink.copy(alpha = 0.16f), pr, pc)
                clipPath(Path().apply { addOval(Rect(pc, pr)) }) {
                    for (k in -2..2) drawLine(ink.copy(alpha = 0.26f), Offset(pc.x - pr, pc.y + k * 8f * u), Offset(pc.x + pr, pc.y + k * 8f * u), 1.2f * u)
                }
                drawCircle(ink.copy(alpha = 0.85f), pr, pc, style = Stroke(width = 1.6f * u))
                drawArc(ink.copy(alpha = 0.85f), 0f, 180f, false, Offset(pc.x - 46f * u, pc.y - 11f * u), Size(92f * u, 22f * u), style = Stroke(width = 2.4f * u))
            }
            val glints = Path()
            glints.addStar4(Offset(w * 0.58f, 26f * u), 5f * u)
            glints.addStar4(Offset(w - 22f * u, h - 38f * u), 7f * u)
            glints.addStar4(Offset(w * 0.46f, h - 30f * u), 4f * u)
            drawPath(glints, ink.copy(alpha = 0.42f), style = Fill)
            roundDots(listOf(Offset(w * 0.70f, h - 24f * u), Offset(w - 52f * u, h - 56f * u), Offset(w * 0.52f, 46f * u)), ink.copy(alpha = 0.40f), 2f * u)
        }
    }
}
