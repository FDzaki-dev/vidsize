package com.example.videoresizer

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Pengganti [HapticFeedback] bawaan Compose yang memakai [Vibrator] langsung.
 *
 * Bawaan Compose menerjemahkan `TextHandleMove` ke tick tipis `View.performHapticFeedback` yang di banyak HP nyaris
 * tak terasa; 90 titik panggil di `MainActivity.kt` semuanya memakai tipe itu (atau `LongPress`). Dipasang SEKALI lewat
 * `CompositionLocalProvider(LocalHapticFeedback ...)` di `MainActivity`, sehingga semua titik panggil lama otomatis
 * terasa lebih kuat tanpa diubah satu per satu:
 *  - `LongPress` (aksi penting: mulai/batal/konfirmasi) -> `EFFECT_HEAVY_CLICK`.
 *  - tipe lain, termasuk `TextHandleMove` (ketukan biasa) -> `EFFECT_CLICK`.
 *
 * Bila getaran tak bisa dijalankan (tanpa vibrator, API < 26, atau izin VIBRATE ditolak) jatuh ke [fallback].
 */
class VibratorHapticFeedback(
    context: Context,
    private val fallback: HapticFeedback,
) : HapticFeedback {

    private val vibrator: Vibrator? = resolveVibrator(context.applicationContext)

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        val strong = hapticFeedbackType == HapticFeedbackType.LongPress
        if (!vibrate(strong)) fallback.performHapticFeedback(hapticFeedbackType)
    }

    private fun vibrate(strong: Boolean): Boolean {
        val target = vibrator ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !target.hasVibrator()) return false
        return try {
            target.vibrate(effectFor(strong))
            true
        } catch (denied: SecurityException) {
            Log.w(TAG, "Getaran ditolak, pakai haptic bawaan Compose", denied)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun effectFor(strong: Boolean): VibrationEffect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(
                if (strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK
            )
        } else {
            VibrationEffect.createOneShot(if (strong) STRONG_MS else TAP_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }

    private companion object {
        const val TAG = "VidsizeHaptics"
        const val TAP_MS = 30L
        const val STRONG_MS = 45L

        @Suppress("DEPRECATION")
        fun resolveVibrator(appContext: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
    }
}
