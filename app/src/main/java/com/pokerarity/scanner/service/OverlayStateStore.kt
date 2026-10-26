package com.pokerarity.scanner.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object OverlayStateStore {

    private const val MAX_TRACKED_SAVE_OUTCOMES = 16

    private val _state = MutableStateFlow<OverlayState>(OverlayState.Idle)
    val state = _state.asStateFlow()

    private val saveOutcomes = linkedMapOf<ScanRequestToken, OverlayHistorySaveStatus>()
    private var visibleResultOwner: ScanRequestToken? = null
    private val _historySaveOutcomes = MutableStateFlow<Map<ScanRequestToken, OverlayHistorySaveStatus>>(emptyMap())
    val historySaveOutcomes = _historySaveOutcomes.asStateFlow()

    private val _effects = MutableSharedFlow<OverlayEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    @Synchronized
    fun dispatch(intent: OverlayIntent) {
        when (intent) {
            OverlayIntent.StartScan -> _state.value = OverlayState.Scanning(System.currentTimeMillis())
            OverlayIntent.StopScan -> _state.value = OverlayState.Idle
            is OverlayIntent.ShowError -> _state.value = OverlayState.Error(intent.message)
            is OverlayIntent.ShowResult -> {
                visibleResultOwner = intent.requestToken
                val status = intent.requestToken?.let(saveOutcomes::get)
                    ?: if (intent.requestToken == null) {
                        OverlayHistorySaveStatus.UNTRACKED
                    } else {
                        OverlayHistorySaveStatus.PENDING
                    }
                _state.value = OverlayState.Result(intent.pokemon, intent.requestToken, status)
            }
            is OverlayIntent.HistorySaveFinished -> {
                require(intent.status != OverlayHistorySaveStatus.PENDING &&
                    intent.status != OverlayHistorySaveStatus.UNTRACKED)
                val status = intent.status
                saveOutcomes[intent.requestToken] = status
                trimSaveOutcomes()
                _historySaveOutcomes.value = saveOutcomes.toMap()

                val current = _state.value
                if (current is OverlayState.Result && current.requestToken == intent.requestToken) {
                    _state.value = current.copy(historySaveStatus = status)
                }
            }
        }
    }

    private fun trimSaveOutcomes() {
        while (saveOutcomes.size > MAX_TRACKED_SAVE_OUTCOMES) {
            val oldestUnpinned = saveOutcomes.keys.firstOrNull { it != visibleResultOwner } ?: break
            saveOutcomes.remove(oldestUnpinned)
        }
    }

    fun emit(effect: OverlayEffect) {
        _effects.tryEmit(effect)
    }

    @Synchronized
    fun resetToIdle() {
        _state.value = OverlayState.Idle
        saveOutcomes.clear()
        visibleResultOwner = null
        _historySaveOutcomes.value = emptyMap()
    }
}
