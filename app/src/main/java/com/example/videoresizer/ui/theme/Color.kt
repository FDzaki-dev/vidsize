package com.example.videoresizer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Color_WhiteOn = Color(0xFFFFFFFF)

// Batch 83: SEMUA tema memakai dasar bernada-tengah supaya kedalaman neumorfik terlihat (lihat Depth.kt). Dasar hitam
// pekat/putih bersih membuat bayangan/sorot "nyatu" dengan latar — nada dasar dinaikkan (gelap) / diturunkan (terang).
// Dark palette — this is the app's primary, default look
val DarkBackground = Color(0xFF2A2E3A)
val DarkSurface = Color(0xFF2F3441)
val DarkSurfaceVariant = Color(0xFF262A35)
val DarkOnBackground = Color(0xFFECEEF4)
val DarkOnSurface = Color(0xFFECEEF4)
val DarkOnSurfaceMuted = Color(0xFFAEB3C2)
val AccentPrimary = Color(0xFF5B3FE0)
val DarkAccentPrimary = Color(0xFFB6A2FF)
val AccentPrimaryVariant = Color(0xFF9B82FF)
val AccentSecondary = Color(0xFF34D6C4)
val DarkError = Color(0xFFFF6B6B)
val DarkOutline = Color(0xFF3D4352)

// Light palette — kept for devices/users that prefer light mode
val LightBackground = Color(0xFFE3E7EE)
val LightSurface = Color(0xFFE6EAF1)
val LightSurfaceVariant = Color(0xFFD9DEE8)
val LightOnBackground = Color(0xFF1B1F2A)
val LightOnSurface = Color(0xFF1B1F2A)
val LightOnSurfaceMuted = Color(0xFF525A6B)
val LightError = Color(0xFFBA1A1A)
val LightOutline = Color(0xFFC3CAD8)

// "Midnight Neon" — a genuinely distinct dark theme, not a recolor of the
// default: cooler near-black background, electric cyan/magenta accents
// instead of purple/teal, paired in Theme.kt with sharper corner shapes
// and a monospace label typeface for a "display panel" character.
val NeonBackground = Color(0xFF16203A)
val NeonSurface = Color(0xFF1A2542)
val NeonSurfaceVariant = Color(0xFF131B31)
val NeonOnBackground = Color(0xFFE4FBFF)
val NeonOnSurface = Color(0xFFE4FBFF)
val NeonOnSurfaceMuted = Color(0xFF8FB5C5)
val NeonPrimary = Color(0xFF00E5FF)
val NeonSecondary = Color(0xFFFF2E92)
val NeonError = Color(0xFFFF4569)
val NeonOutline = Color(0xFF2A3A5C)

// "Warm Paper" — a genuinely distinct light theme: warm cream instead of
// stark white, terracotta/olive accents instead of purple/teal, paired in
// Theme.kt with softer/rounder corner shapes and serif titles for an
// editorial, printed-page character.
val PaperBackground = Color(0xFFEBDFCB)
val PaperSurface = Color(0xFFEDE1CC)
val PaperSurfaceVariant = Color(0xFFE0D1B6)
val PaperOnBackground = Color(0xFF3A2E22)
val PaperOnSurface = Color(0xFF3A2E22)
val PaperOnSurfaceMuted = Color(0xFF5F4F3B)
val PaperPrimary = Color(0xFF9A3519)
val PaperSecondary = Color(0xFF7A8B5C)
val PaperError = Color(0xFFB3413A)
val PaperOutline = Color(0xFFCDB892)

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
// Batch 83: permukaan kaca kini BURAM (frosted opak, nada navy terang) — dulu putih 13-20% (0x22/0x33FFFFFF) sehingga isi
// kartu menembus ke gradien dan teks sulit dibaca. Kesan kaca dipertahankan lewat kilau/ornamen (Ornaments.kt).
val GlassSurface = Color(0xFF253376)
val GlassSurfaceVariant = Color(0xFF1A2559)
val GlassOnBackground = Color(0xFFF2F5FF)
val GlassOnSurfaceMuted = Color(0xFFC9D2F0)
val GlassPrimary = Color(0xFF8FBAFF)
val GlassSecondary = Color(0xFF64D2FF)
val GlassError = Color(0xFFFF6B6B)
val GlassBorder = Color(0x40FFFFFF)

// "Burgundy Old Money" (Batch 94) — oxblood/burgundy pekat berpadu emas antik + gading champagne: kesan "old money"
// (wallpaper damask di ruang perpustakaan, bingkai emas, huruf serif). Dasar tetap bernada-tengah (aturan Batch 83)
// supaya kedalaman neumorfik terlihat; kartu = burgundy lebih terang dari latar, teks = gading hangat (kontras >= 8:1).
val BurgundyBackground = Color(0xFF3A0F1B)
val BurgundySurface = Color(0xFF4A1727)
val BurgundySurfaceVariant = Color(0xFF330C17)
val BurgundyOnBackground = Color(0xFFF6EBD9)
val BurgundyOnSurfaceMuted = Color(0xFFD9BFA5)
val BurgundyPrimary = Color(0xFFD4AF63)
val BurgundySecondary = Color(0xFFE9D3A3)
val BurgundyInk = Color(0xFF3E0D1B)
val BurgundyError = Color(0xFFFF9E8F)
val BurgundyOutline = Color(0xFF7A3548)

// "Cosmic Orange" (Batch 98) — ungu-indigo antariksa pekat berpadu oranye surya + amber: nebula, gerhana, astrolab. Dasar tetap
// bernada-tengah (aturan Batch 83) supaya kedalaman neumorfik terlihat; kartu = ungu lebih terang dari latar, teks = putih
// hangat (kontras >= 9:1), `onPrimary` = tinta cokelat-ungu pekat (hero oranye bertinta gelap, kontras ~ 6,8:1).
val CosmicBackground = Color(0xFF1E1433)
val CosmicSurface = Color(0xFF2B1F46)
val CosmicSurfaceVariant = Color(0xFF191027)
val CosmicOnBackground = Color(0xFFFFF1E2)
val CosmicOnSurfaceMuted = Color(0xFFD3C2DE)
val CosmicPrimary = Color(0xFFFF8A2A)
val CosmicSecondary = Color(0xFFFFC56E)
val CosmicInk = Color(0xFF2A1205)
val CosmicError = Color(0xFFFF7B9C)
val CosmicOutline = Color(0xFF5B4580)

/** The gradient painted behind every screen when Midnight Blue Glass is active. */
val MidnightBlueGlassGradient = Brush.verticalGradient(
    listOf(GlassGradientTop, GlassGradientMid, GlassGradientBottom)
)
