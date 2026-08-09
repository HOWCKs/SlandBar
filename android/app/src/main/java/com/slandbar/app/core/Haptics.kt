package com.slandbar.app.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.slandbar.app.core.model.HapticProfile

/** Perfis de vibração (igual ao conceito de "haptics mecânicos" do Notch Touch). */
object Haptics {

    fun vibrate(context: Context, profile: HapticProfile) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (profile) {
                HapticProfile.SOFT -> VibrationEffect.createOneShot(18, 90)
                HapticProfile.MECHANICAL ->
                    VibrationEffect.createWaveform(longArrayOf(0, 12, 30, 14), -1)
                HapticProfile.DEEP -> VibrationEffect.createOneShot(70, 210)
            }
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }
}
