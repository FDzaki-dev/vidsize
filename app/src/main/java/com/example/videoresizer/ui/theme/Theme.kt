package com.example.videoresizer.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

// Scrim navigation bar (navigasi 3-tombol) untuk edge-to-edge; nilai standar rekomendasi AndroidX (terang 90% putih, gelap 50% hitam).
private val EdgeToEdgeLightScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val EdgeToEdgeDarkScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x16)

/**
 * Whether Midnight Blue Glass is the currently active theme. Read by each
 * screen (ResizerScreen/StudioScreen/BatchScreen) to decide whether to
 * paint [MidnightBlueGlassGradient] or a plain flat background behind
 * itself — a CompositionLocal instead of a parameter so it doesn't need
 * threading through every screen's function signature.
 */
val LocalIsGlassTheme = compositionLocalOf { false }

// Batch 83: nada kontainer Material (dialog/menu/lembar bawah) dibuat OPAK & seirama dgn badan kartu neumorfik.
private fun ColorScheme.withContainers(lowest: Long, low: Long, base: Long, high: Long, highest: Long): ColorScheme = copy(
    surfaceContainerLowest = Color(lowest),
    surfaceContainerLow = Color(low),
    surfaceContainer = Color(base),
    surfaceContainerHigh = Color(high),
    surfaceContainerHighest = Color(highest)
)

private val DarkColors = darkColorScheme(
    primary = DarkAccentPrimary,
    onPrimary = Color(0xFF1C1240),
    secondary = AccentSecondary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkOutline,
    error = DarkError
).withContainers(0xFF252934, 0xFF2C303C, 0xFF2F3441, 0xFF343A48, 0xFF3A4050)

private val LightColors = lightColorScheme(
    primary = AccentPrimary,
    onPrimary = Color_WhiteOn,
    secondary = AccentSecondary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceMuted,
    outline = LightOutline,
    error = LightError
).withContainers(0xFFD9DEE8, 0xFFE0E4EB, 0xFFE6EAF1, 0xFFEBEEF4, 0xFFF0F3F8)

private val NeonColors = darkColorScheme(
    primary = NeonPrimary,
    onPrimary = Color(0xFF00121A),
    secondary = NeonSecondary,
    background = NeonBackground,
    onBackground = NeonOnBackground,
    surface = NeonSurface,
    onSurface = NeonOnSurface,
    surfaceVariant = NeonSurfaceVariant,
    onSurfaceVariant = NeonOnSurfaceMuted,
    outline = NeonOutline,
    error = NeonError
).withContainers(0xFF111829, 0xFF17213B, 0xFF1A2542, 0xFF1F2B4B, 0xFF25325A)

private val PaperColors = lightColorScheme(
    primary = PaperPrimary,
    onPrimary = Color(0xFFFFF6EE),
    secondary = PaperSecondary,
    background = PaperBackground,
    onBackground = PaperOnBackground,
    surface = PaperSurface,
    onSurface = PaperOnSurface,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = PaperOnSurfaceMuted,
    outline = PaperOutline,
    error = PaperError
).withContainers(0xFFE0D1B6, 0xFFE8DCC5, 0xFFEDE1CC, 0xFFF1E6D3, 0xFFF5ECDB)

// background/surface here are flat fallback fills (used by anything that
// can't take a Brush, e.g. status/nav bar color) — the actual visible
// backdrop is MidnightBlueGlassGradient, painted separately behind each
// screen's Scaffold. surface/surfaceVariant are translucent on purpose:
// that's what makes a Card drawn on top of the gradient read as frosted
// glass rather than a solid panel.
private val GlassColors = darkColorScheme(
    primary = GlassPrimary,
    onPrimary = Color(0xFF00193D),
    secondary = GlassSecondary,
    background = GlassGradientMid,
    onBackground = GlassOnBackground,
    surface = GlassSurface,
    onSurface = GlassOnBackground,
    surfaceVariant = GlassSurfaceVariant,
    onSurfaceVariant = GlassOnSurfaceMuted,
    outline = GlassBorder,
    error = GlassError
).withContainers(0xFF18214F, 0xFF202C69, 0xFF253376, 0xFF2A397F, 0xFF304188)

// Batch 94: Burgundy Old Money. Palet gelap (burgundy pekat + emas antik); wadah Material opak, makin ke "tinggi" makin terang.
// primaryContainer/secondaryContainer diisi eksplisit supaya komponen Material yang memakainya tak jatuh ke ungu bawaan.
private val BurgundyColors = darkColorScheme(
    primary = BurgundyPrimary,
    onPrimary = BurgundyInk,
    primaryContainer = Color(0xFF6B2338),
    onPrimaryContainer = BurgundySecondary,
    secondary = BurgundySecondary,
    onSecondary = BurgundyInk,
    secondaryContainer = Color(0xFF58202F),
    onSecondaryContainer = BurgundyOnBackground,
    background = BurgundyBackground,
    onBackground = BurgundyOnBackground,
    surface = BurgundySurface,
    onSurface = BurgundyOnBackground,
    surfaceVariant = BurgundySurfaceVariant,
    onSurfaceVariant = BurgundyOnSurfaceMuted,
    outline = BurgundyOutline,
    error = BurgundyError
).withContainers(0xFF35101C, 0xFF401422, 0xFF4A1727, 0xFF561D2F, 0xFF632439)

// Formal & terstruktur (radius sedang, bukan "pil"): kesan klasik, bukan gelembung modern.
private val BurgundyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// Sharper, tighter corners than Material3's default (4/8/12/16/28dp) — part
// of what makes Midnight Neon read as a genuinely different theme rather
// than a recolor: every card/button/chip in the app is a bit more angular.
// Batch 98: Cosmic Orange. Palet gelap (ungu antariksa + oranye surya); wadah Material opak, makin ke "tinggi" makin terang.
// primaryContainer = oranye terbakar (teks amber di atasnya), secondaryContainer = ungu nebula.
private val CosmicColors = darkColorScheme(
    primary = CosmicPrimary,
    onPrimary = CosmicInk,
    primaryContainer = Color(0xFF6A3110),
    onPrimaryContainer = CosmicSecondary,
    secondary = CosmicSecondary,
    onSecondary = CosmicInk,
    secondaryContainer = Color(0xFF3B2B5E),
    onSecondaryContainer = CosmicOnBackground,
    background = CosmicBackground,
    onBackground = CosmicOnBackground,
    surface = CosmicSurface,
    onSurface = CosmicOnBackground,
    surfaceVariant = CosmicSurfaceVariant,
    onSurfaceVariant = CosmicOnSurfaceMuted,
    outline = CosmicOutline,
    error = CosmicError
).withContainers(0xFF1A1129, 0xFF231838, 0xFF2B1F46, 0xFF352757, 0xFF403068)

// Lembut seperti planet (radius besar, seragam: gambar kedalaman memakai satu radius per elemen).
private val CosmicShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

private val NeonShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

// Softer, rounder corners than Material3's default — the other half of
// Midnight Neon's approach applied in the opposite direction: every
// card/button/chip in the app reads a bit more organic/pillowy.
private val PaperShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

// Large, continuous-looking corner radii — the iOS "squircle" feel that
// makes glass panels read as floating cards rather than boxy Material
// surfaces.
private val GlassShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

/**
 * The seven selectable visual identities (BURGUNDY_OLD_MONEY added in Batch 94, COSMIC_ORANGE in Batch 98). LIGHT/DARK are the original
 * Material-default-shaped themes (SYSTEM resolves to one of these two
 * before reaching this function — see MainActivity's theme picker).
 * MIDNIGHT_NEON, WARM_PAPER, and MIDNIGHT_BLUE_GLASS are full alternate
 * identities: different color language AND different shape language AND
 * (for titles) different type style, not just a palette swap over the same
 * component shapes. MIDNIGHT_BLUE_GLASS is the app's default look.
 */
enum class AppThemeStyle { LIGHT, DARK, MIDNIGHT_NEON, WARM_PAPER, MIDNIGHT_BLUE_GLASS, BURGUNDY_OLD_MONEY, COSMIC_ORANGE }

/**
 * App theme. Defaults to following the system setting, but the caller
 * (see MainActivity's theme toggle) can force dark/light/a custom style
 * regardless of it.
 */
@Composable
fun VideoResizerTheme(
    style: AppThemeStyle = if (isSystemInDarkTheme()) AppThemeStyle.MIDNIGHT_BLUE_GLASS else AppThemeStyle.LIGHT,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = style == AppThemeStyle.DARK || style == AppThemeStyle.MIDNIGHT_NEON || style == AppThemeStyle.MIDNIGHT_BLUE_GLASS ||
        style == AppThemeStyle.BURGUNDY_OLD_MONEY || style == AppThemeStyle.COSMIC_ORANGE
    val colorScheme = when {
        // Batch 83: warna dinamis wallpaper hanya untuk AKSEN; netral (latar/permukaan/teks) tetap dari palet tema supaya
        // kontras sorot & bayangan neumorfik terjamin (netral dinamis bisa nyaris putih/hitam -> bayangan "nyatu").
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && style == AppThemeStyle.DARK -> {
            val dyn = dynamicDarkColorScheme(context)
            DarkColors.copy(
                primary = dyn.primary, onPrimary = dyn.onPrimary, primaryContainer = dyn.primaryContainer,
                onPrimaryContainer = dyn.onPrimaryContainer, secondary = dyn.secondary, onSecondary = dyn.onSecondary
            )
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && style == AppThemeStyle.LIGHT -> {
            val dyn = dynamicLightColorScheme(context)
            LightColors.copy(
                primary = dyn.primary, onPrimary = dyn.onPrimary, primaryContainer = dyn.primaryContainer,
                onPrimaryContainer = dyn.onPrimaryContainer, secondary = dyn.secondary, onSecondary = dyn.onSecondary
            )
        }
        style == AppThemeStyle.DARK -> DarkColors
        style == AppThemeStyle.LIGHT -> LightColors
        style == AppThemeStyle.MIDNIGHT_NEON -> NeonColors
        style == AppThemeStyle.WARM_PAPER -> PaperColors
        style == AppThemeStyle.MIDNIGHT_BLUE_GLASS -> GlassColors
        style == AppThemeStyle.BURGUNDY_OLD_MONEY -> BurgundyColors
        style == AppThemeStyle.COSMIC_ORANGE -> CosmicColors
        else -> DarkColors
    }
    val shapes = when (style) {
        AppThemeStyle.MIDNIGHT_NEON -> NeonShapes
        AppThemeStyle.WARM_PAPER -> PaperShapes
        AppThemeStyle.MIDNIGHT_BLUE_GLASS -> GlassShapes
        AppThemeStyle.BURGUNDY_OLD_MONEY -> BurgundyShapes
        AppThemeStyle.COSMIC_ORANGE -> CosmicShapes
        else -> Shapes() // Material3 defaults for LIGHT/DARK — unchanged from before this feature.
    }
    val typography = when (style) {
        AppThemeStyle.MIDNIGHT_NEON -> NeonTypography
        AppThemeStyle.WARM_PAPER -> PaperTypography
        AppThemeStyle.MIDNIGHT_BLUE_GLASS -> GlassTypography
        AppThemeStyle.BURGUNDY_OLD_MONEY -> BurgundyTypography
        AppThemeStyle.COSMIC_ORANGE -> CosmicTypography
        else -> Typography // unchanged from before this feature.
    }

    // Batch 74 (guard "WindowInsets edge-to-edge"): `statusBarColor`/`navigationBarColor` opak dihapus (deprecated dan tak
    // berefek di targetSdk 35; membuat jendela non-edge-to-edge). Gaya bar sistem kini diatur ulang saat `isDark` berubah:
    // status bar transparan, ikon terang/gelap mengikuti TEMA APLIKASI (bukan tema sistem), scrim navigation bar hanya
    // untuk navigasi 3-tombol. `MainActivity.onCreate` sudah memanggil `enableEdgeToEdge()` sebelum frame pertama.
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(isDark) {
            (view.context as ComponentActivity).enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { isDark },
                navigationBarStyle = SystemBarStyle.auto(EdgeToEdgeLightScrim, EdgeToEdgeDarkScrim) { isDark }
            )
            onDispose { }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes
    ) {
        CompositionLocalProvider(
            LocalIsGlassTheme provides (style == AppThemeStyle.MIDNIGHT_BLUE_GLASS),
            LocalThemeStyle provides style,
            LocalDepthSpec provides depthSpecFor(style)
        ) {
            content()
        }
    }
}
