package com.example.videoresizer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Whether Midnight Blue Glass is the currently active theme. Read by each
 * screen (ResizerScreen/StudioScreen/BatchScreen) to decide whether to
 * paint [MidnightBlueGlassGradient] or a plain flat background behind
 * itself — a CompositionLocal instead of a parameter so it doesn't need
 * threading through every screen's function signature.
 */
val LocalIsGlassTheme = compositionLocalOf { false }

private val DarkColors = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = Color_WhiteOn,
    secondary = AccentSecondary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkOutline,
    error = DarkError
)

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
)

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
)

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
)

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
)

// Sharper, tighter corners than Material3's default (4/8/12/16/28dp) — part
// of what makes Midnight Neon read as a genuinely different theme rather
// than a recolor: every card/button/chip in the app is a bit more angular.
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
 * The five selectable visual identities. LIGHT/DARK are the original
 * Material-default-shaped themes (SYSTEM resolves to one of these two
 * before reaching this function — see MainActivity's theme picker).
 * MIDNIGHT_NEON, WARM_PAPER, and MIDNIGHT_BLUE_GLASS are full alternate
 * identities: different color language AND different shape language AND
 * (for titles) different type style, not just a palette swap over the same
 * component shapes. MIDNIGHT_BLUE_GLASS is the app's default look.
 */
enum class AppThemeStyle { LIGHT, DARK, MIDNIGHT_NEON, WARM_PAPER, MIDNIGHT_BLUE_GLASS }

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
    val isDark = style == AppThemeStyle.DARK || style == AppThemeStyle.MIDNIGHT_NEON || style == AppThemeStyle.MIDNIGHT_BLUE_GLASS
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && style == AppThemeStyle.DARK ->
            dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && style == AppThemeStyle.LIGHT ->
            dynamicLightColorScheme(context)
        style == AppThemeStyle.DARK -> DarkColors
        style == AppThemeStyle.LIGHT -> LightColors
        style == AppThemeStyle.MIDNIGHT_NEON -> NeonColors
        style == AppThemeStyle.WARM_PAPER -> PaperColors
        style == AppThemeStyle.MIDNIGHT_BLUE_GLASS -> GlassColors
        else -> DarkColors
    }
    val shapes = when (style) {
        AppThemeStyle.MIDNIGHT_NEON -> NeonShapes
        AppThemeStyle.WARM_PAPER -> PaperShapes
        AppThemeStyle.MIDNIGHT_BLUE_GLASS -> GlassShapes
        else -> Shapes() // Material3 defaults for LIGHT/DARK — unchanged from before this feature.
    }
    val typography = when (style) {
        AppThemeStyle.MIDNIGHT_NEON -> NeonTypography
        AppThemeStyle.WARM_PAPER -> PaperTypography
        AppThemeStyle.MIDNIGHT_BLUE_GLASS -> GlassTypography
        else -> Typography // unchanged from before this feature.
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes
    ) {
        CompositionLocalProvider(LocalIsGlassTheme provides (style == AppThemeStyle.MIDNIGHT_BLUE_GLASS)) {
            content()
        }
    }
}
