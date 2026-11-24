package com.example.videoresizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/**
 * Batch 76 — tes PERILAKU pertama proyek: kontrak [CrashLogger.nonFatal], batas kegagalan terpusat Batch 75 yang
 * menggantikan 20 blok `catch (e: Exception)`. JVM murni: `nonFatal` inline dan hanya memakai stdlib, jadi tak butuh
 * Robolectric/Android. Penulisan log ke MediaStore TIDAK diuji di sini (butuh perangkat) dan tetap NOT VERIFIED.
 */
class CrashLoggerTest {

    @Test
    fun blockBerhasil_mengembalikanNilaiBlock_tanpaMemanggilOnFailure() {
        var onFailureDipanggil = false
        val hasil = CrashLogger.nonFatal<String>(
            onFailure = { onFailureDipanggil = true; "dipulihkan" },
            block = { "berhasil" },
        )
        assertEquals("berhasil", hasil)
        assertFalse(onFailureDipanggil)
    }

    @Test
    fun exception_dialihkanKeOnFailure_denganInstansSama_dan_nilaiOnFailureJadiHasil() {
        val penyebab = IllegalStateException("kegagalan buatan")
        var diterima: Exception? = null
        val hasil = CrashLogger.nonFatal<String>(
            onFailure = { diterima = it; "dipulihkan" },
            block = { throw penyebab },
        )
        assertEquals("dipulihkan", hasil)
        assertSame(penyebab, diterima)
    }

    @Test
    fun ioException_tetapDitangkapSebagaiException() {
        val penyebab = IOException("penyimpanan penuh buatan")
        var diterima: Exception? = null
        CrashLogger.nonFatal<Unit>(
            onFailure = { diterima = it },
            block = { throw penyebab },
        )
        assertSame(penyebab, diterima)
    }

    @Test
    fun error_dilemparUlang_danOnFailureTidakDipanggil() {
        val buatan = OutOfMemoryError("buatan: bukan OOM sungguhan")
        var onFailureDipanggil = false
        val dilempar = assertThrows(OutOfMemoryError::class.java) {
            CrashLogger.nonFatal<Unit>(
                onFailure = { onFailureDipanggil = true },
                block = { throw buatan },
            )
        }
        assertSame(buatan, dilempar)
        assertFalse(onFailureDipanggil)
    }

    @Test
    fun returnNonLokal_dariBlock_keluarDariFungsiPemanggil() {
        assertEquals("return-dari-block", keluarDariBlock())
    }

    @Test
    fun returnNonLokal_dariOnFailure_keluarDariFungsiPemanggil() {
        assertEquals("return-dari-onFailure", keluarDariOnFailure())
    }

    @Test
    fun exceptionDariOnFailure_tidakDitelan() {
        val dilempar = assertThrows(IllegalArgumentException::class.java) {
            CrashLogger.nonFatal<Unit>(
                onFailure = { throw IllegalArgumentException("dari onFailure") },
                block = { throw IllegalStateException("dari block") },
            )
        }
        assertEquals("dari onFailure", dilempar.message)
    }

    private fun keluarDariBlock(): String {
        CrashLogger.nonFatal<Unit>(onFailure = { }) { return "return-dari-block" }
        return "lanjut-setelah-nonFatal"
    }

    private fun keluarDariOnFailure(): String {
        CrashLogger.nonFatal<Unit>(onFailure = { return "return-dari-onFailure" }) {
            throw IllegalStateException("kegagalan buatan")
        }
        return "lanjut-setelah-nonFatal"
    }
}
