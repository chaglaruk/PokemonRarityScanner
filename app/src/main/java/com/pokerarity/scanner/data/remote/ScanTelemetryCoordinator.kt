package com.pokerarity.scanner.data.remote

import android.content.Context
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.ScanTelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScanTelemetryCoordinator private constructor(
    context: Context
) {
    private val appContext = context.applicationContext
    private val repository = ScanTelemetryRepository(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isEnabled(): Boolean = repository.isEnabled()

    fun newUploadIdOrNull(): String? = if (repository.isEnabled()) repository.newUploadId() else null

    fun enqueueAndFlush(
        uploadId: String?,
        pokemonData: PokemonData,
        features: VisualFeatures,
        rarityScore: RarityScore,
        screenshotPath: String?,
        pipelineMs: Long?
    ) {
        if (uploadId.isNullOrBlank()) return
        scope.launch {
            repository.enqueueScan(uploadId, pokemonData, features, rarityScore, screenshotPath, pipelineMs)
            repository.flushPending()
        }
    }

    fun submitFeedback(uploadId: String, category: String, notes: String? = null) {
        if (!repository.isEnabled()) return
        scope.launch {
            repository.submitFeedback(uploadId, category, notes)
        }
    }

    fun flushPendingAsync() {
        scope.launch { repository.flushPending() }
    }

    companion object {
        @Volatile
        private var INSTANCE: ScanTelemetryCoordinator? = null

        fun getInstance(context: Context): ScanTelemetryCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScanTelemetryCoordinator(context).also { INSTANCE = it }
            }
        }
    }
}
