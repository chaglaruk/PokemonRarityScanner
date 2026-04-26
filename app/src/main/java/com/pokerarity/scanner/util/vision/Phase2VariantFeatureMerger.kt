// Purpose: Apply high-confidence Phase 2 variant predictions to visual features.
package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.VisualFeatures

object Phase2VariantFeatureMerger {
    fun merge(features: VisualFeatures, result: Phase2VariantClassifier.Result?): VisualFeatures {
        val appliedTargets = result?.predictions
            ?.filter { it.passedThreshold && it.predictedValue }
            ?.map { it.target }
            ?.toSet()
            .orEmpty()

        if (appliedTargets.isEmpty()) {
            return features
        }

        return features.copy(
            isShiny = features.isShiny || "isShiny" in appliedTargets,
            hasCostume = features.hasCostume || "hasCostume" in appliedTargets,
            hasSpecialForm = features.hasSpecialForm || "hasSpecialForm" in appliedTargets,
            hasLocationCard = features.hasLocationCard || "hasLocationCard" in appliedTargets
        )
    }
}
