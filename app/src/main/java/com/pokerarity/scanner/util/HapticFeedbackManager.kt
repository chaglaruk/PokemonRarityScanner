package com.pokerarity.scanner.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.model.Pokemon

class HapticFeedbackManager(private val context: Context) {

    fun vibrateForResult(pokemon: Pokemon) {
        val vibrator = getVibrator() ?: return
        val effect = when {
            pokemon.ivSolveMode == IvSolveMode.EXACT && pokemon.iv == 100 ->
                VibrationEffect.createWaveform(longArrayOf(0, 35, 55, 35), -1)
            extractLowestIvValue(pokemon.ivText) <= 20 ->
                VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE)
            else -> null
        } ?: return
        vibrator.vibrate(effect)
    }

    private fun extractLowestIvValue(ivText: String?): Int {
        if (ivText.isNullOrBlank()) return Int.MAX_VALUE
        val numbers = Regex("""\d{1,3}""").findAll(ivText).mapNotNull { it.value.toIntOrNull() }.toList()
        return numbers.minOrNull() ?: Int.MAX_VALUE
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
