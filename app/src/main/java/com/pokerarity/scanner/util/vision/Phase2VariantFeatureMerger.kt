// Purpose: Apply high-confidence Phase 2 variant predictions to visual features.
package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.VisualFeatures

object Phase2VariantFeatureMerger {
    private const val SHINY_MIN_EXAMPLES = 1
    private const val SHINY_DEMOTION_CONFIDENCE = 0.505f
    private const val SHINY_DEMOTION_MARGIN = -0.010f
    private const val STRICT_SHINY_CONFIDENCE = 0.97f
    private const val STRICT_SHINY_MARGIN = 0.55f
    private const val TRAINED_COSTUME_CONFIDENCE = 0.5f
    private const val TRAINED_COSTUME_MARGIN = 0.001f
    private const val GLOBAL_COSTUME_MARGIN = 0.080f
    private const val COSTUME_DEMOTION_MARGIN = -0.006f
    private const val STRICT_COSTUME_CONFIDENCE = 0.95f
    private const val STRICT_COSTUME_MARGIN = 0.50f
    private const val STRICT_OTHER_CONFIDENCE = 0.92f
    private const val STRICT_OTHER_MARGIN = 0.36f
    private const val OTHER_DEMOTION_CONFIDENCE = 0.54f
    private const val OTHER_DEMOTION_MARGIN = -0.080f
    private const val MIN_COSTUME_EXAMPLES = 1
    private const val MIN_BALANCED_EXAMPLES = 3

    fun merge(features: VisualFeatures, result: Phase2VariantClassifier.Result?): VisualFeatures {
        val predictions = result?.predictions.orEmpty()
        val negativeTargets = predictions
            .filter { !it.predictedValue && canDemote(features, it) }
            .map { it.target }
            .toSet()
        val appliedTargets = predictions
            .filter { it.passedThreshold && it.predictedValue && canPromote(features, it) }
            .map { it.target }
            .toSet()

        if (appliedTargets.isEmpty() && negativeTargets.isEmpty()) {
            return features
        }

        return features.copy(
            isShiny = when {
                "isShiny" in negativeTargets -> false
                "isShiny" in appliedTargets -> true
                else -> features.isShiny
            },
            hasCostume = when {
                "hasCostume" in negativeTargets -> false
                "hasCostume" in appliedTargets -> true
                else -> features.hasCostume
            },
            hasSpecialForm = when {
                "hasSpecialForm" in negativeTargets -> false
                "hasSpecialForm" in appliedTargets -> true
                else -> features.hasSpecialForm
            },
            hasLocationCard = when {
                "hasLocationCard" in negativeTargets -> false
                "hasLocationCard" in appliedTargets -> true
                else -> features.hasLocationCard
            }
        )
    }

    private fun canPromote(
        features: VisualFeatures,
        prediction: Phase2VariantClassifier.Prediction
    ): Boolean {
        return when (prediction.target) {
            "isShiny" -> features.isShiny ||
                (
                    prediction.source != "global" &&
                    prediction.positiveCount >= SHINY_MIN_EXAMPLES &&
                        prediction.negativeCount >= SHINY_MIN_EXAMPLES &&
                        prediction.confidence >= 0.502f &&
                        prediction.margin >= 0.003f
                    ) ||
                (
                    prediction.source != "global" &&
                    prediction.confidence >= STRICT_SHINY_CONFIDENCE &&
                    prediction.margin >= STRICT_SHINY_MARGIN
                    )
            "hasCostume" -> features.hasCostume ||
                (hasCostumeExamples(prediction) &&
                    (
                        (prediction.source == "global" &&
                            prediction.confidence >= TRAINED_COSTUME_CONFIDENCE &&
                            prediction.margin >= GLOBAL_COSTUME_MARGIN) ||
                            (prediction.source != "global" &&
                                prediction.confidence >= TRAINED_COSTUME_CONFIDENCE &&
                            prediction.margin >= TRAINED_COSTUME_MARGIN) ||
                            (prediction.confidence >= STRICT_COSTUME_CONFIDENCE &&
                                prediction.margin >= STRICT_COSTUME_MARGIN)
                        ))
            "hasSpecialForm",
            "hasLocationCard" -> hasBalancedExamples(prediction) &&
                prediction.confidence >= STRICT_OTHER_CONFIDENCE &&
                    prediction.margin >= STRICT_OTHER_MARGIN
            else -> false
        }
    }

    private fun hasBalancedExamples(prediction: Phase2VariantClassifier.Prediction): Boolean =
        prediction.positiveCount >= MIN_BALANCED_EXAMPLES &&
            prediction.negativeCount >= MIN_BALANCED_EXAMPLES

    private fun hasCostumeExamples(prediction: Phase2VariantClassifier.Prediction): Boolean =
        prediction.positiveCount >= MIN_COSTUME_EXAMPLES &&
            prediction.negativeCount >= MIN_COSTUME_EXAMPLES

    private fun canDemote(
        features: VisualFeatures,
        prediction: Phase2VariantClassifier.Prediction
    ): Boolean {
        return when (prediction.target) {
            "isShiny" -> features.isShiny &&
                prediction.positiveCount >= SHINY_MIN_EXAMPLES &&
                prediction.negativeCount >= MIN_BALANCED_EXAMPLES &&
                prediction.confidence >= SHINY_DEMOTION_CONFIDENCE &&
                prediction.margin <= SHINY_DEMOTION_MARGIN
            "hasCostume" -> features.hasCostume &&
                prediction.source != "global" &&
                hasCostumeExamples(prediction) &&
                prediction.confidence >= TRAINED_COSTUME_CONFIDENCE &&
                prediction.margin <= COSTUME_DEMOTION_MARGIN
            "hasSpecialForm",
            "hasLocationCard" -> hasBalancedExamples(prediction) &&
                prediction.confidence >= OTHER_DEMOTION_CONFIDENCE &&
                prediction.margin <= OTHER_DEMOTION_MARGIN
            else -> false
        }
    }
}
