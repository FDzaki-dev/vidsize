package com.example.videoresizer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

/**
 * Midnight Neon's type scale: wider letter-spacing and heavier weight on
 * titles for a "display panel" feel, plus `FontFamily.Monospace` (a
 * built-in generic family — no font files to bundle, so this can't
 * introduce a missing-resource build failure) on labels, where a
 * technical/readout feel actually fits the numbers and short tags being
 * shown (percentages, kbps, resolutions).
 */
val NeonTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.4.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
)

/**
 * Warm Paper's type scale: `FontFamily.Serif` (also built-in/generic, same
 * zero-new-assets guarantee as Monospace above) on titles only, for an
 * editorial/printed feel, while body and label text stay on the default
 * sans so long paragraphs of settings text remain easy to read.
 */
val PaperTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 23.sp, fontFamily = FontFamily.Serif),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, fontFamily = FontFamily.Serif),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

/**
 * Midnight Blue Glass's type scale: tighter (slightly negative) letter
 * spacing and bolder titles for the dense, tracked-in look of iOS system
 * type, using the platform's default sans family (no bundled font files,
 * same zero-new-asset guarantee as Neon/Paper above).
 */
val GlassTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = (-0.1).sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = (-0.1).sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp)
)
