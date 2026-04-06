package com.pokerarity.scanner.data.remote

import android.content.Context
import com.pokerarity.scanner.data.local.TelemetryPreferences
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.ScanTelemetryRepository
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScanTelemetryCoordinator private constructor(
    context: Context
) {
    private val appContext = context.applicationContext
    private val repository = ScanTelemetryRepository(appContext)
    private val telemetryPrefs = TelemetryPreferences(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isEnabled(): Boolean = repository.isEnabled()

    /**
     * Returns a new upload ID ONLY if:
     * 1. Telemetry is enabled at build-time
     * 2. User has explicitly opted in to telemetry
     * Otherwise returns null (telemetry skipped for this scan)
     */
    fun newUploadIdOrNull(): String? {
        if (!repository.isEnabled()) return null
        // 🔴 SECURITY FIX: Check user consent before creating upload ID
        if (!telemetryPrefs.userConsent) return null
        return repository.newUploadId()
    }

    fun enqueueAndFlush(
        uploadId: String?,
        pokemonData: PokemonData,
        features: VisualFeatures,
        rarityScore: RarityScore,
        screenshotPath: String?,
        pipelineMs: Long?,
        phase2Result: Phase2VariantClassifier.Result? = null
    ) {
        if (uploadId.isNullOrBlank()) return
        scope.launch {
            repository.enqueueScan(uploadId, pokemonData, features, rarityScore, screenshotPath, pipelineMs, phase2Result)
            repository.flushPending()
        }
    }

    fun submitFeedback(uploadId: String, category: String, notes: String? = null) {
        if (!repository.isEnabled()) return
        if (!telemetryPrefs.userConsent) return  // Don't submit if user opted out
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

