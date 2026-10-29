package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.Pokemon

sealed interface OverlayIntent {
    data object StartScan : OverlayIntent
    data object StopScan : OverlayIntent
    data class ShowResult(
        val pokemon: Pokemon,
        val requestToken: ScanRequestToken? = null
    ) : OverlayIntent
    data class HistorySaveFinished(
        val requestToken: ScanRequestToken,
        val status: OverlayHistorySaveStatus
    ) : OverlayIntent
    data class ShowError(val message: String) : OverlayIntent
}

enum class OverlayHistorySaveStatus {
    PENDING,
    SAVED,
    FAILED,
    UNKNOWN,
    UNTRACKED
}

sealed class OverlayState {
    data object Idle : OverlayState()
    data class Scanning(val startedAtMillis: Long) : OverlayState()
    data class Result(
        val pokemon: Pokemon,
        val requestToken: ScanRequestToken? = null,
        val historySaveStatus: OverlayHistorySaveStatus =
            if (requestToken == null) OverlayHistorySaveStatus.UNTRACKED else OverlayHistorySaveStatus.PENDING
    ) : OverlayState()
    data class Error(val message: String) : OverlayState()
}

sealed interface OverlayEffect {
    data class ShowToast(val message: String) : OverlayEffect
    data object VibrateSuccess : OverlayEffect
}
