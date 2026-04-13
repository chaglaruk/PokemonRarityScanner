package com.pokerarity.scanner.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object OverlayStateStore {

    private val _state = MutableStateFlow<OverlayState>(OverlayState.Idle)
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<OverlayEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    fun dispatch(intent: OverlayIntent) {
        _state.value = when (intent) {
            OverlayIntent.StartScan -> OverlayState.Scanning(System.currentTimeMillis())
            OverlayIntent.StopScan -> OverlayState.Idle
            is OverlayIntent.ShowError -> OverlayState.Error(intent.message)
            is OverlayIntent.ShowResult -> OverlayState.Result(intent.pokemon)
        }
    }

    fun emit(effect: OverlayEffect) {
        _effects.tryEmit(effect)
    }

    fun resetToIdle() {
        _state.value = OverlayState.Idle
    }
}
