package com.example.videoresizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 77 — tes PERILAKU ketiga proyek: kalkulasi murni di companion [VideoResizer] yang hanya memakai tipe
 * primitif/enum (tanpa `Uri`/`Context`): [VideoResizer.requiredBitrateKbpsForTargetSize] ("Ukuran target (MB)"),
 * [VideoResizer.estimateCompressedSizeBytes] (pratinjau sebelum/sesudah tab Kompres) dan, sejak Batch 78,
 * [VideoResizer.compressVideoBitrateBps] (aturan bitrate video Kompres bersama ekspor & pratinjau; batas 85% bitrate
 * sumber menang atas lantai 250 kbps). JVM murni, tanpa Robolectric.
 *
 * Nilai harapan diturunkan dari rumus kontrak lewat implementasi referensi independen (urutan operasi `Double` sama),
 * bukan disalin dari keluaran fungsi. Contoh tangan: 1280x720 @30fps x 0,06 bpp = 1.658.880 bps -> 10 dtk, tanpa audio
 * = 1.658.880 x 10 / 8 = 2.073.600 byte. `computeTargetDimensions`/`estimateOutputSizeBytes` TIDAK diuji di sini: keduanya
 * menerima `ResizeRequest` yang mewajibkan `Uri` Android (butuh Robolectric/mock = dependensi baru). Hasil ekspor
 * sungguhan dan akurasi estimasi terhadap encoder tetap NOT VERIFIED (butuh perangkat).
 */
class VideoResizerKalkulasiTest {

    private companion object {
        const val AUDIO_BPS_ASUMSI = 128_000
        val REKOMENDASI = CompressionLevel.RECOMMENDED
        val MAKSIMAL = CompressionLevel.MAXIMUM

        /** Pembungkus supaya tiap tes hanya menyebut argumen yang relevan; default = sumber tak diketahui, tanpa mute. */
        fun estimasi(
            lebar: Int = 1280,
            tinggi: Int = 720,
            durasiSumberMs: Long = 0L,
            ukuranSumberByte: Long = 0L,
            durasiKlipMs: Long = 10_000L,
            mute: Boolean = false,
            level: CompressionLevel = REKOMENDASI,
            fps: Int = 0,
            punyaAudio: Boolean = true,
            bitrateAudioBps: Int = 0,
        ): Long? = VideoResizer.estimateCompressedSizeBytes(
            sourceWidth = lebar,
            sourceHeight = tinggi,
            sourceDurationMs = durasiSumberMs,
            sourceFileSizeBytes = ukuranSumberByte,
            clipDurationMs = durasiKlipMs,
            muteAudio = mute,
            level = level,
            sourceFps = fps,
            sourceHasAudio = punyaAudio,
            sourceAudioBitrateBps = bitrateAudioBps,
        )
    }

    // ---- requiredBitrateKbpsForTargetSize ("Ukuran target (MB)") ----

    @Test
    fun targetSize_tanpaAudio_bitrateVideoMenyerapSeluruhAnggaran() {
        // 10 MB x 8 bit / 60 dtk = 1.398.101 bps -> 1398 kbps (dipotong ke bawah)
        assertEquals(1398, VideoResizer.requiredBitrateKbpsForTargetSize(10.0, 60_000L, muteAudio = true))
    }

    @Test
    fun targetSize_denganAudio_dikurangiAsumsiAudio128kbps() {
        // 1.398.101 - 128.000 = 1.270.101 bps -> 1270 kbps
        assertEquals(1270, VideoResizer.requiredBitrateKbpsForTargetSize(10.0, 60_000L, muteAudio = false))
        assertEquals(1270, VideoResizer.requiredBitrateKbpsForTargetSize(100.0, 600_000L, muteAudio = false))
    }

    @Test
    fun targetSize_terlaluKecil_untukAudioSaja_mengembalikanNull() {
        // 0,5 MB / 60 dtk = 69.905 bps < 128.000 bps audio -> tak ada bitrate video yang muat
        assertNull(VideoResizer.requiredBitrateKbpsForTargetSize(0.5, 60_000L, muteAudio = false))
    }

    @Test
    fun targetSize_persisSamaDenganAudio_mengembalikanNull() {
        // 125/1024 MB x 1024 x 1024 x 8 / 8 dtk = tepat 128.000 bps (biner-eksak) -> sisa video 0 -> null
        assertNull(VideoResizer.requiredBitrateKbpsForTargetSize(0.1220703125, 8_000L, muteAudio = false))
    }

    @Test
    fun targetSize_terlaluKecil_tanpaAudio_naikKeLantaiBitrate() {
        // 0,5 MB / 60 dtk tanpa audio = 69 kbps -> dinaikkan ke MIN_BITRATE_KBPS
        assertEquals(
            VideoResizer.MIN_BITRATE_KBPS,
            VideoResizer.requiredBitrateKbpsForTargetSize(0.5, 60_000L, muteAudio = true),
        )
    }

    @Test
    fun targetSize_terlaluBesar_dipotongKePlafonBitrate() {
        // 1000 MB / 10 dtk = 838.860 kbps -> dipotong ke MAX_BITRATE_KBPS
        assertEquals(
            VideoResizer.MAX_BITRATE_KBPS,
            VideoResizer.requiredBitrateKbpsForTargetSize(1000.0, 10_000L, muteAudio = true),
        )
    }

    @Test
    fun targetSize_masukanTakValid_mengembalikanNull() {
        assertNull(VideoResizer.requiredBitrateKbpsForTargetSize(0.0, 60_000L, muteAudio = true))
        assertNull(VideoResizer.requiredBitrateKbpsForTargetSize(-5.0, 60_000L, muteAudio = true))
        assertNull(VideoResizer.requiredBitrateKbpsForTargetSize(10.0, 0L, muteAudio = true))
        assertNull(VideoResizer.requiredBitrateKbpsForTargetSize(10.0, -1L, muteAudio = false))
    }

    @Test
    fun targetSize_makinBesarTarget_bitrateTidakPernahTurun() {
        var sebelumnya = 0
        for (mb in listOf(1.0, 2.0, 5.0, 10.0, 25.0, 50.0, 100.0, 500.0)) {
            val kbps = checkNotNull(VideoResizer.requiredBitrateKbpsForTargetSize(mb, 120_000L, muteAudio = true)) {
                "$mb MB menghasilkan null"
            }
            assertTrue("$mb MB: $kbps kbps turun dari $sebelumnya", kbps >= sebelumnya)
            sebelumnya = kbps
        }
    }

    // ---- estimateCompressedSizeBytes (pratinjau tab Kompres) ----

    @Test
    fun estimasi_sumberTakDiketahui_tanpaAudio_memakaiTargetLevelPenuh() {
        // 1280 x 720 x 30 fps x 0,06 = 1.658.880 bps; x 10 dtk / 8 = 2.073.600 byte
        assertEquals(2_073_600L, estimasi(mute = true))
        // 1920 x 1080 x 30 x 0,06 = 3.732.480 bps; x 10 / 8 = 4.665.600 byte
        assertEquals(4_665_600L, estimasi(lebar = 1920, tinggi = 1080, mute = true))
    }

    @Test
    fun estimasi_levelMaksimal_lebihKecilDariRekomendasi() {
        // 1280 x 720 x 30 x 0,035 = 967.680 bps -> 1.209.600 byte
        assertEquals(1_209_600L, estimasi(mute = true, level = MAKSIMAL))
        val maksimal = checkNotNull(estimasi(level = MAKSIMAL))
        val rekomendasi = checkNotNull(estimasi(level = REKOMENDASI))
        assertTrue("maksimal $maksimal harus < rekomendasi $rekomendasi", maksimal < rekomendasi)
    }

    @Test
    fun estimasi_audio_asumsi128k_terukurTerprobe_atauNolBilaMuteAtauTanpaTrackAudio() {
        val tanpaAudio = 2_073_600L // video saja
        assertEquals(tanpaAudio + AUDIO_BPS_ASUMSI * 10L / 8, estimasi())
        assertEquals(2_233_600L, estimasi())
        assertEquals(2_393_600L, estimasi(bitrateAudioBps = 256_000)) // bitrate audio terprobe menggantikan 128k
        assertEquals(tanpaAudio, estimasi(mute = true, bitrateAudioBps = 256_000))
        assertEquals(tanpaAudio, estimasi(punyaAudio = false, bitrateAudioBps = 256_000))
    }

    @Test
    fun estimasi_fps_terprobe_dipakai_danNolAtauNegatifJatuhKe30() {
        assertEquals(4_147_200L, estimasi(mute = true, fps = 60)) // 2 x bitrate video 30 fps
        assertEquals(estimasi(mute = true, fps = 30), estimasi(mute = true, fps = 0))
        assertEquals(estimasi(mute = true, fps = 30), estimasi(mute = true, fps = -1))
        assertEquals(CompressionLevel.ASSUMED_FPS, 30)
    }

    @Test
    fun estimasi_dimensiGanjil_dibulatkanKeAtasKeGenap() {
        assertEquals(estimasi(lebar = 1280, tinggi = 720, mute = true), estimasi(lebar = 1279, tinggi = 719, mute = true))
    }

    @Test
    fun estimasi_sumberSudahEfisien_dibatasi85PersenBitrateVideoSumber() {
        // sumber 7.500.000 byte / 60 dtk = 1.000.000 bps; audio asumsi 128.000 -> video sumber 872.000 bps
        // batas = 872.000 x 0,85 = 741.200 bps (< target level 3.732.480); (741.200 + 128.000) x 30 dtk / 8 = 3.259.500 byte
        assertEquals(
            3_259_500L,
            estimasi(lebar = 1920, tinggi = 1080, durasiSumberMs = 60_000L, ukuranSumberByte = 7_500_000L, durasiKlipMs = 30_000L),
        )
        // sumber di-mute -> audio 0: video sumber 1.000.000 bps x 0,85 = 850.000; x 30 / 8 = 3.187.500 byte
        assertEquals(
            3_187_500L,
            estimasi(
                lebar = 1920, tinggi = 1080, durasiSumberMs = 60_000L, ukuranSumberByte = 7_500_000L,
                durasiKlipMs = 30_000L, mute = true,
            ),
        )
    }

    @Test
    fun estimasi_seluruhKlip_sumberEfisien_tidakPernahLebihBesarDariFileSumber() {
        val ukuranSumber = 7_500_000L
        val hasil = checkNotNull(
            estimasi(lebar = 1920, tinggi = 1080, durasiSumberMs = 60_000L, ukuranSumberByte = ukuranSumber, durasiKlipMs = 60_000L),
        )
        assertEquals(6_519_000L, hasil)
        assertTrue("estimasi $hasil tidak lebih kecil dari sumber $ukuranSumber", hasil < ukuranSumber)
    }

    @Test
    fun estimasi_sumberBitrateRendah_batasSumberMengalahkanLantai250kbps() {
        // Batch 78 (sebelumnya karakterisasi "lantai menang" = file membesar): sumber 1.000.000 byte / 80 dtk = 100.000 bps tanpa
        // audio -> batas 85% = 85.000 bps (di atas lantai mutlak 64 kbps) MENANG atas lantai 250 kbps -> 85.000 x 8 dtk / 8 =
        // 85.000 byte, lebih kecil dari jatah sumber utk 8 dtk (100.000 byte).
        assertEquals(
            85_000L,
            estimasi(lebar = 1920, tinggi = 1080, durasiSumberMs = 80_000L, ukuranSumberByte = 1_000_000L, durasiKlipMs = 8_000L, mute = true),
        )
    }

    @Test
    fun estimasi_sumberDiBawahLantaiMutlak_dijepitKe64kbps() {
        // 400.000 byte / 80 dtk = 40.000 bps -> 85% = 34.000 < lantai mutlak 64.000 -> 64.000 x 8 dtk / 8 = 64.000 byte
        assertEquals(
            64_000L,
            estimasi(lebar = 1920, tinggi = 1080, durasiSumberMs = 80_000L, ukuranSumberByte = 400_000L, durasiKlipMs = 8_000L, mute = true),
        )
    }

    @Test
    fun estimasi_seluruhKlip_semuaBitrateSumberDiAtasLantaiMutlak_tidakPernahLebihBesarDariSumber() {
        // Mencakup sumber di bawah 294 kbps (area bug lama) sampai 5 Mbps; durasi 60 dtk, tanpa audio.
        for (bps in listOf(100_000L, 150_000L, 200_000L, 250_000L, 294_000L, 300_000L, 500_000L, 1_000_000L, 5_000_000L)) {
            val ukuranSumber = bps * 60L / 8
            val hasil = checkNotNull(
                estimasi(
                    lebar = 1920, tinggi = 1080, durasiSumberMs = 60_000L, ukuranSumberByte = ukuranSumber,
                    durasiKlipMs = 60_000L, mute = true,
                ),
            ) { "estimasi null utk sumber $bps bps" }
            assertTrue("sumber $bps bps: estimasi $hasil tidak lebih kecil dari sumber $ukuranSumber", hasil < ukuranSumber)
        }
    }

    // ---- compressVideoBitrateBps (aturan bitrate video Kompres, dipakai bersama ekspor & pratinjau) ----

    @Test
    fun bitrateKompres_tanpaInfoSumber_hanyaDijepitKeBatasKeras() {
        assertEquals(250_000.0, VideoResizer.compressVideoBitrateBps(100_000.0, null), 0.0) // naik ke lantai 250 kbps
        assertEquals(1_000_000.0, VideoResizer.compressVideoBitrateBps(1_000_000.0, null), 0.0)
        assertEquals(50_000_000.0, VideoResizer.compressVideoBitrateBps(119_439_360.0, null), 0.0) // turun ke plafon 50 Mbps
    }

    @Test
    fun bitrateKompres_batasSumber85Persen_menangAtasLantai250kbps() {
        // sumber 280 kbps: 85% = 238.000 (< lantai 250.000, > lantai mutlak 64.000) -> 238.000
        assertEquals(238_000.0, VideoResizer.compressVideoBitrateBps(3_732_480.0, 280_000.0), 1e-6)
        // target level di bawah lantai tetap dinaikkan ke 250.000 bila batas sumber (850.000) longgar
        assertEquals(250_000.0, VideoResizer.compressVideoBitrateBps(100_000.0, 1_000_000.0), 0.0)
    }

    @Test
    fun bitrateKompres_batasSumberDiAtasTarget_tidakMenaikkanTarget() {
        assertEquals(1_000_000.0, VideoResizer.compressVideoBitrateBps(1_000_000.0, 5_000_000.0), 0.0)
    }

    @Test
    fun bitrateKompres_sumberNolAtauNegatif_dianggapTakDiketahui() {
        assertEquals(1_000_000.0, VideoResizer.compressVideoBitrateBps(1_000_000.0, 0.0), 0.0)
        assertEquals(1_000_000.0, VideoResizer.compressVideoBitrateBps(1_000_000.0, -5.0), 0.0)
    }

    @Test
    fun bitrateKompres_tidakPernahDiBawahLantaiMutlak64kbps_dan_selaluPositif() {
        assertEquals(64_000.0, VideoResizer.compressVideoBitrateBps(3_000_000.0, 10_000.0), 0.0)
        assertEquals(64_000.0, VideoResizer.compressVideoBitrateBps(3_000_000.0, 0.5), 0.0) // sumber ~0 bps: encoder tetap dapat nilai > 0
        assertEquals(64, VideoResizer.MIN_COMPRESS_BITRATE_KBPS)
        assertTrue(VideoResizer.MIN_COMPRESS_BITRATE_KBPS < VideoResizer.MIN_BITRATE_KBPS)
    }

    @Test
    fun estimasi_resolusiSangatBesar_dipotongKePlafon50Mbps() {
        // 7680 x 4320 x 60 x 0,06 = 119.439.360 bps -> plafon 50.000.000 bps; x 8 dtk / 8 = 50.000.000 byte
        assertEquals(50_000_000L, estimasi(lebar = 7680, tinggi = 4320, durasiKlipMs = 8_000L, mute = true, fps = 60))
    }

    @Test
    fun estimasi_infoSumberTakLengkap_tidakMenerapkanBatas() {
        // durasi sumber 0 (ukuran ada) -> sumberVideoBps null -> pakai target level penuh
        assertEquals(2_073_600L, estimasi(durasiSumberMs = 0L, ukuranSumberByte = 5_000_000L, mute = true))
        // ukuran sumber 0 (durasi ada) -> idem
        assertEquals(2_073_600L, estimasi(durasiSumberMs = 60_000L, ukuranSumberByte = 0L, mute = true))
    }

    @Test
    fun estimasi_masukanTakValid_mengembalikanNull() {
        assertNull(estimasi(durasiKlipMs = 0L))
        assertNull(estimasi(durasiKlipMs = -1L))
        assertNull(estimasi(lebar = 0))
        assertNull(estimasi(tinggi = 0))
    }
}
