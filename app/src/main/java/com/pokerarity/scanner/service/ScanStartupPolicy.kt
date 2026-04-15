package com.pokerarity.scanner.service

object ScanStartupPolicy {
    private const val STABLE_POKEMON_SCREEN_MS = 500L
    private const val MAX_MOTION_SCORE = 0.08f

    fun autoCaptureForManualStart(): Boolean = false

    fun shouldAutoTriggerFromStableScreen(
        validPokemonScreen: Boolean,
        stableForMs: Long,
        motionScore: Float
    ): Boolean {
        return validPokemonScreen &&
            stableForMs >= STABLE_POKEMON_SCREEN_MS &&
            motionScore <= MAX_MOTION_SCORE
    }
}
