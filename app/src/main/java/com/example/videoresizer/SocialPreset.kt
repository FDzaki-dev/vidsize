package com.example.videoresizer

/**
 * One-tap export presets tuned to match what TikTok/Instagram/YouTube
 * actually recommend for their own upload pipelines, so a novice user
 * doesn't have to know what "1080x1920" or "8000 kbps" means or hunt for
 * platform specs themselves — the same "Export for..." shortcut CapCut,
 * InShot, and other mainstream editors expose.
 *
 * Applying a preset sets [ResolutionOption.CUSTOM] with an exact width/height
 * (rather than reusing the 480p/720p/1080p height-only presets) because
 * several platform specs — Instagram's 4:5 portrait feed post in particular
 * (1080x1350) — don't line up with "pick a height, derive the width from a
 * ratio" the way the existing resolution presets do.
 *
 * Bitrates are deliberately conservative, real-world values (not a
 * theoretical maximum): every platform re-compresses on upload anyway, so
 * requesting more than this just makes the local export slower/bigger for
 * no visible benefit once it's actually posted.
 */
enum class SocialPreset(
    val label: String,
    val aspectRatio: AspectRatioOption,
    val width: Int,
    val height: Int,
    val bitrateKbps: Int
) {
    TIKTOK_REELS_SHORTS(
        label = "TikTok / Reels / Shorts",
        aspectRatio = AspectRatioOption.PORTRAIT_9_16,
        width = 1080,
        height = 1920,
        bitrateKbps = 8_000
    ),
    YOUTUBE_LANDSCAPE(
        label = "YouTube (16:9)",
        aspectRatio = AspectRatioOption.LANDSCAPE_16_9,
        width = 1920,
        height = 1080,
        bitrateKbps = 12_000
    ),
    INSTAGRAM_FEED_SQUARE(
        label = "Instagram Feed (1:1)",
        aspectRatio = AspectRatioOption.SQUARE_1_1,
        width = 1080,
        height = 1080,
        bitrateKbps = 5_000
    ),
    INSTAGRAM_FEED_PORTRAIT(
        label = "Instagram Feed (4:5)",
        aspectRatio = AspectRatioOption.PORTRAIT_4_5,
        width = 1080,
        height = 1350,
        bitrateKbps = 6_000
    );

    companion object {
        val ENTRIES: List<SocialPreset> = values().toList()
    }
}
