// Purpose: Apply high-confidence Phase 2 variant predictions to visual features.
package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.VisualFeatures

object Phase2VariantFeatureMerger {
    private const val SHINY_MIN_EXAMPLES = 1
    private const val STRICT_SHINY_CONFIDENCE = 0.97f
    private const val STRICT_SHINY_MARGIN = 0.55f
    private const val STRICT_COSTUME_CONFIDENCE = 0.95f
    private const val STRICT_COSTUME_MARGIN = 0.50f
    private const val STRICT_OTHER_CONFIDENCE = 0.92f
    private const val STRICT_OTHER_MARGIN = 0.36f
    private const val MIN_BALANCED_EXAMPLES = 3

    fun merge(features: VisualFeatures, result: Phase2VariantClassifier.Result?): VisualFeatures {
        val appliedTargets = result?.predictions
            ?.filter { it.passedThreshold && it.predictedValue && canPromote(features, it) }
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

    private fun canPromote(
        features: VisualFeatures,
        prediction: Phase2VariantClassifier.Prediction
    ): Boolean {
        if (prediction.positiveCount < MIN_BALANCED_EXAMPLES ||
            prediction.negativeCount < MIN_BALANCED_EXAMPLES
        ) {
            return false
        }
        return when (prediction.target) {
            "isShiny" -> features.isShiny ||
                (
                    prediction.positiveCount >= SHINY_MIN_EXAMPLES &&
                        prediction.negativeCount >= SHINY_MIN_EXAMPLES &&
                        prediction.confidence >= 0.502f &&
                        prediction.margin >= 0.003f
                    ) ||
                (
                    prediction.confidence >= STRICT_SHINY_CONFIDENCE &&
                        prediction.margin >= STRICT_SHINY_MARGIN
                    )
            "hasCostume" -> features.hasCostume ||
                (prediction.confidence >= STRICT_COSTUME_CONFIDENCE &&
                    prediction.margin >= STRICT_COSTUME_MARGIN)
            "hasSpecialForm",
            "hasLocationCard" -> prediction.confidence >= STRICT_OTHER_CONFIDENCE &&
                prediction.margin >= STRICT_OTHER_MARGIN
            else -> false
        }
    }
}
