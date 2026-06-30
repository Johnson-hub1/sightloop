package com.example.services

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

class HapticManager(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        try {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } catch (e: Exception) {
            Log.e("HapticManager", "Failed to access system Vibrator service", e)
            null
        }
    }

    /**
     * Confirms screen interactions like switching modes or starting a scan.
     */
    fun playClick() {
        vibrate(80)
    }

    /**
     * Tactile notification indicating visual analysis is in progress.
     */
    fun playProcessingTick() {
        val pattern = longArrayOf(0, 40, 100, 40)
        vibratePattern(pattern, -1)
    }

    /**
     * Tactile success feedback when visual scene is analyzed and description is ready.
     */
    fun playSuccess() {
        val pattern = longArrayOf(0, 150, 100, 150)
        vibratePattern(pattern, -1)
    }

    /**
     * Alerts the user that highly dangerous hazards (e.g. stairs, walls) have been located near their field.
     */
    fun playHazardWarning(intensity: String) {
        when (intensity.uppercase()) {
            "HIGH" -> {
                // Urgent, persistent double pulses
                val highPattern = longArrayOf(0, 350, 100, 350, 100, 350, 100)
                vibratePattern(highPattern, -1)
            }
            "MEDIUM" -> {
                // Moderate rhythm pulse
                val medPattern = longArrayOf(0, 200, 150, 200)
                vibratePattern(medPattern, -1)
            }
            "LOW" -> {
                // Gentle alerting pulse
                vibrate(150)
            }
        }
    }

    fun stop() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("HapticManager", "Error stopping vibration", e)
        }
    }

    private fun vibrate(durationMs: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.e("HapticManager", "Error playing vibrational shot", e)
        }
    }

    private fun vibratePattern(pattern: LongArray, repeat: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = IntArray(pattern.size) { index ->
                    if (index % 2 == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE
                }
                v.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, repeat))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, repeat)
            }
        } catch (e: Exception) {
            Log.e("HapticManager", "Error playing vibrational pattern", e)
        }
    }
}
