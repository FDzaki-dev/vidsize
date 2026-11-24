package com.example.videoresizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 77 — tes PERILAKU kedua proyek: invarian data [SocialPreset]. JVM murni (hanya enum + konstanta).
 * Preset diterapkan sebagai `ResolutionOption.CUSTOM` dengan lebar/tinggi persis, jadi salah ketik dimensi (mis. 4:5
 * ditulis 1080x1250) baru terlihat sebagai video berbeda rasio di HP; tes ini menangkapnya sebelum itu.
 */
class SocialPresetTest {

    @Test
    fun entries_memuatSemuaPreset_dalamUrutanDeklarasi() {
        assertEquals(SocialPreset.values().toList(), SocialPreset.ENTRIES)
        assertEquals(4, SocialPreset.ENTRIES.size)
    }

    @Test
    fun dimensi_cocokDenganRasioAspekPreset_tepatTanpaPembulatan() {
        for (preset in SocialPreset.ENTRIES) {
            val rasio = preset.aspectRatio
            assertNotEquals("${preset.name}: rasio ORIGINAL tak punya dimensi tetap", AspectRatioOption.ORIGINAL, rasio)
            assertEquals(
                "${preset.name}: ${preset.width}x${preset.height} tidak sesuai rasio ${rasio.label}",
                preset.width.toLong() * rasio.heightRatio,
                preset.height.toLong() * rasio.widthRatio,
            )
        }
    }

    @Test
    fun dimensi_positif_dan_genap_karenaEncoderMewajibkanGenap() {
        for (preset in SocialPreset.ENTRIES) {
            assertTrue("${preset.name}: lebar harus positif", preset.width > 0)
            assertTrue("${preset.name}: tinggi harus positif", preset.height > 0)
            assertEquals("${preset.name}: lebar harus genap", 0, preset.width % 2)
            assertEquals("${preset.name}: tinggi harus genap", 0, preset.height % 2)
        }
    }

    @Test
    fun bitrate_beradaDiDalamBatasKerasVideoResizer() {
        for (preset in SocialPreset.ENTRIES) {
            assertTrue(
                "${preset.name}: ${preset.bitrateKbps} kbps di luar ${VideoResizer.MIN_BITRATE_KBPS}..${VideoResizer.MAX_BITRATE_KBPS}",
                preset.bitrateKbps in VideoResizer.MIN_BITRATE_KBPS..VideoResizer.MAX_BITRATE_KBPS,
            )
        }
    }

    @Test
    fun label_tidakKosong_danUnikAntarPreset() {
        val labels = SocialPreset.ENTRIES.map { it.label }
        assertTrue("label kosong", labels.all { it.isNotBlank() })
        assertEquals("label ganda: $labels", labels.size, labels.toSet().size)
    }

    @Test
    fun nilaiPresetTetap_sesuaiSpesifikasiPlatform() {
        val ringkas = SocialPreset.ENTRIES.associate { it.name to Triple(it.width, it.height, it.bitrateKbps) }
        assertEquals(
            mapOf(
                "TIKTOK_REELS_SHORTS" to Triple(1080, 1920, 8_000),
                "YOUTUBE_LANDSCAPE" to Triple(1920, 1080, 12_000),
                "INSTAGRAM_FEED_SQUARE" to Triple(1080, 1080, 5_000),
                "INSTAGRAM_FEED_PORTRAIT" to Triple(1080, 1350, 6_000),
            ),
            ringkas,
        )
    }
}
