package com.example.videoresizer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Color_WhiteOn = Color(0xFFFFFFFF)

// Dark palette — this is the app's primary, default look
val DarkBackground = Color(0xFF0E0E12)
val DarkSurface = Color(0xFF17171D)
val DarkSurfaceVariant = Color(0xFF212129)
val DarkOnBackground = Color(0xFFECECF1)
val DarkOnSurface = Color(0xFFECECF1)
val DarkOnSurfaceMuted = Color(0xFFA0A0AC)
val AccentPrimary = Color(0xFF7C5CFF)
val AccentPrimaryVariant = Color(0xFF9B82FF)
val AccentSecondary = Color(0xFF34D6C4)
val DarkError = Color(0xFFFF6B6B)
val DarkOutline = Color(0xFF2E2E38)

// Light palette — kept for devices/users that prefer light mode
val LightBackground = Color(0xFFFAFAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0F0F5)
val LightOnBackground = Color(0xFF17171D)
val LightOnSurface = Color(0xFF17171D)
val LightOnSurfaceMuted = Color(0xFF63636F)
val LightError = Color(0xFFBA1A1A)
val LightOutline = Color(0xFFE0E0E8)

// "Midnight Neon" — a genuinely distinct dark theme, not a recolor of the
// default: cooler near-black background, electric cyan/magenta accents
// instead of purple/teal, paired in Theme.kt with sharper corner shapes
// and a monospace label typeface for a "display panel" character.
val NeonBackground = Color(0xFF06070C)
val NeonSurface = Color(0xFF0D1220)
val NeonSurfaceVariant = Color(0xFF141B2E)
val NeonOnBackground = Color(0xFFE4FBFF)
val NeonOnSurface = Color(0xFFE4FBFF)
val NeonOnSurfaceMuted = Color(0xFF7FA8B8)
val NeonPrimary = Color(0xFF00E5FF)
val NeonSecondary = Color(0xFFFF2E92)
val NeonError = Color(0xFFFF4569)
val NeonOutline = Color(0xFF223247)

// "Warm Paper" — a genuinely distinct light theme: warm cream instead of
// stark white, terracotta/olive accents instead of purple/teal, paired in
// Theme.kt with softer/rounder corner shapes and serif titles for an
// editorial, printed-page character.
val PaperBackground = Color(0xFFFAF3E8)
val PaperSurface = Color(0xFFFFFBF3)
val PaperSurfaceVariant = Color(0xFFF0E4D0)
val PaperOnBackground = Color(0xFF3A2E22)
val PaperOnSurface = Color(0xFF3A2E22)
val PaperOnSurfaceMuted = Color(0xFF8A7860)
val PaperPrimary = Color(0xFFC96342)
val PaperSecondary = Color(0xFF7A8B5C)
val PaperError = Color(0xFFB3413A)
val PaperOutline = Color(0xFFDCC9A8)

// "Midnight Blue Glass" — iOS-style glassmorphism, the app's new default
// look: a deep midnight-blue vertical gradient behind everything (painted
// as a Brush, since a flat ColorScheme.background can't hold a gradient),
// with frosted, semi-transparent "glass" cards floating on top — alpha-based
// translucent fills + a soft light-colored hairline border, which reads as
// glass on any device without needing true backdrop blur (which would need
// gating around API 31's RenderEffect and buys little extra over a gradient
// backdrop this dark). Paired in Theme.kt with much larger, iOS-style
// continuous corner radii and in Type.kt with tighter letter-spacing.
val GlassGradientTop = Color(0xFF0A0F24)
val GlassGradientMid = Color(0xFF121B3E)
val GlassGradientBottom = Color(0xFF1B2A5C)
val GlassSurface = Color(0x33FFFFFF)
val GlassSurfaceVariant = Color(0x22FFFFFF)
val GlassOnBackground = Color(0xFFF2F5FF)
val GlassOnSurfaceMuted = Color(0xFFB9C2E0)
val GlassPrimary = Color(0xFF4C8DFF)
val GlassSecondary = Color(0xFF64D2FF)
val GlassError = Color(0xFFFF6B6B)
val GlassBorder = Color(0x40FFFFFF)

/** The gradient painted behind every screen when Midnight Blue Glass is active. */
val MidnightBlueGlassGradient = Brush.verticalGradient(
    listOf(GlassGradientTop, GlassGradientMid, GlassGradientBottom)
)
