package com.pokerarity.scanner.service

object ScanStartupPolicy {
    private const val STABLE_POKEMON_SCREEN_MS = 500L
    private const val MAX_MOTION_SCORE = 0.08f

    enum class StartAction {
        STOP,
        RESUME_OVERLAY,
        REQUEST_PERMISSION,
    }

    fun autoCaptureForManualStart(): Boolean = false

    fun startAction(overlayRunning: Boolean, projectionGranted: Boolean): StartAction = when {
        overlayRunning && projectionGranted -> StartAction.STOP
        !overlayRunning && projectionGranted -> StartAction.RESUME_OVERLAY
        else -> StartAction.REQUEST_PERMISSION
    }

    fun isScannerSessionActive(overlayRunning: Boolean, projectionGranted: Boolean): Boolean =
        overlayRunning && projectionGranted

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
