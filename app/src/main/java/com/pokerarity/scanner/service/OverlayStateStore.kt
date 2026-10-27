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
    private val historySaveRetries = linkedMapOf<ScanRequestToken, () -> Boolean>()
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
                historySaveRetries.keys
                    .filter { it != intent.requestToken }
                    .forEach(historySaveRetries::remove)
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
                if (status != OverlayHistorySaveStatus.FAILED) {
                    historySaveRetries.remove(intent.requestToken)
                }
                trimSaveOutcomes()
                _historySaveOutcomes.value = saveOutcomes.toMap()

                val current = _state.value
                if (current is OverlayState.Result && current.requestToken == intent.requestToken) {
                    _state.value = current.copy(historySaveStatus = status)
                }
            }
        }
    }

    @Synchronized
    fun registerHistorySaveRetry(requestToken: ScanRequestToken, retry: () -> Boolean) {
        if (saveOutcomes[requestToken] != OverlayHistorySaveStatus.SAVED &&
            saveOutcomes[requestToken] != OverlayHistorySaveStatus.UNKNOWN) {
            historySaveRetries[requestToken] = retry
        }
    }

    @Synchronized
    fun clearVisibleHistorySaveRetry() {
        visibleResultOwner?.let(historySaveRetries::remove)
    }

    /**
     * Retries only the visible result's failed History write. The action must schedule
     * the write and return false if its owner can no longer accept work.
     */
    @Synchronized
    fun requestHistorySaveRetry(requestToken: ScanRequestToken): Boolean =
        if (visibleResultOwner == requestToken &&
            saveOutcomes[requestToken] == OverlayHistorySaveStatus.FAILED) {
            historySaveRetries[requestToken]?.let { retry ->
                setHistorySaveStatus(requestToken, OverlayHistorySaveStatus.PENDING)

                val scheduled = runCatching(retry).getOrDefault(false)
                if (!scheduled) {
                    setHistorySaveStatus(requestToken, OverlayHistorySaveStatus.FAILED)
                }
                scheduled
            } ?: false
        } else {
            false
        }

    private fun setHistorySaveStatus(
        requestToken: ScanRequestToken,
        status: OverlayHistorySaveStatus
    ) {
        saveOutcomes[requestToken] = status
        trimSaveOutcomes()
        _historySaveOutcomes.value = saveOutcomes.toMap()
        val current = _state.value
        if (current is OverlayState.Result && current.requestToken == requestToken) {
            _state.value = current.copy(historySaveStatus = status)
        }
    }

    private fun trimSaveOutcomes() {
        while (saveOutcomes.size > MAX_TRACKED_SAVE_OUTCOMES) {
            val oldestUnpinned = saveOutcomes.keys.firstOrNull { it != visibleResultOwner } ?: break
            saveOutcomes.remove(oldestUnpinned)
            historySaveRetries.remove(oldestUnpinned)
        }
    }

    fun emit(effect: OverlayEffect) {
        _effects.tryEmit(effect)
    }

    @Synchronized
    fun resetToIdle() {
        _state.value = OverlayState.Idle
        saveOutcomes.clear()
        historySaveRetries.clear()
        visibleResultOwner = null
        _historySaveOutcomes.value = emptyMap()
    }
}
