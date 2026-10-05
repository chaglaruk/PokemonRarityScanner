// Purpose: Phase 2F single publication seam for terminal scan side effects.
package com.pokerarity.scanner.service

import android.content.Intent
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier

/**
 * Phase 2F bounded publication seam: every externally visible/persistent terminal side
 * effect of an accepted scan result funnels through one interface, so the coordinator's
 * stale-publication guard has a single choke point and tests can observe exactly-once
 * publication. Production implementation lives in [ScanManager].
 */
internal interface ScanPublicationSink {
    fun showResultOverlay(intent: Intent)

    suspend fun saveScan(
        pokemon: PokemonData,
        features: VisualFeatures,
        rarityScore: RarityScore
    )

    fun enqueueTelemetry(payload: TelemetryPayload)
}

/** Bounded telemetry publication payload for the [ScanPublicationSink] seam. */
internal data class TelemetryPayload(
    val uploadId: String?,
    val pokemon: PokemonData,
    val features: VisualFeatures,
    val rarityScore: RarityScore,
    val pipelineMs: Long,
    val phase2Result: Phase2VariantClassifier.Result?
)
