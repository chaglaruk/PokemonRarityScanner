package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.Pokemon

sealed interface OverlayIntent {
    data object StartScan : OverlayIntent
    data object StopScan : OverlayIntent
    data class ShowResult(val pokemon: Pokemon) : OverlayIntent
    data class ShowError(val message: String) : OverlayIntent
}

sealed class OverlayState {
    data object Idle : OverlayState()
    data class Scanning(val startedAtMillis: Long) : OverlayState()
    data class Result(val pokemon: Pokemon) : OverlayState()
    data class Error(val message: String) : OverlayState()
}

sealed interface OverlayEffect {
    data class ShowToast(val message: String) : OverlayEffect
    data object VibrateSuccess : OverlayEffect
}
