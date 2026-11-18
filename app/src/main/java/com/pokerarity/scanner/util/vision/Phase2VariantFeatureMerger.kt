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

    fun merge(
        features: VisualFeatures,
        result: Phase2VariantClassifier.Result?,
        allowShinyModelDecision: Boolean = true
    ): VisualFeatures {
        val predictions = result?.predictions.orEmpty()
        val negativeTargets = predictions
            .filter { canApplyNegative(features, it, allowShinyModelDecision) }
            .map { it.target }
            .toSet()
        val appliedTargets = predictions
            .filter { canApplyPositive(features, predictions, it, allowShinyModelDecision) }
            .map { it.target }
            .toSet()

        if (appliedTargets.isEmpty() && negativeTargets.isEmpty()) {
            return features
        }

        return features.copy(
            isShiny = mergedFlag(features.isShiny, "isShiny", negativeTargets, appliedTargets),
            hasCostume = mergedFlag(features.hasCostume, "hasCostume", negativeTargets, appliedTargets),
            hasSpecialForm = mergedFlag(features.hasSpecialForm, "hasSpecialForm", negativeTargets, appliedTargets),
            hasLocationCard = mergedFlag(features.hasLocationCard, "hasLocationCard", negativeTargets, appliedTargets)
        )
    }

    private fun mergedFlag(
        current: Boolean,
        target: String,
        negativeTargets: Set<String>,
        positiveTargets: Set<String>
    ): Boolean = when {
        target in negativeTargets -> false
        target in positiveTargets -> true
        else -> current
    }

    /**
     * Phase 2E explicit-negative contract for shiny: true only when the trained
     * classifier demoted an existing shiny positive under the same evidence rules the
     * merge applies. This is the one shiny path that may establish tri-state FALSE;
     * a classifier merely not firing never does.
     */
    fun shinyDemotionApplied(
        features: VisualFeatures,
        result: Phase2VariantClassifier.Result?,
        allowShinyModelDecision: Boolean = true
    ): Boolean {
        if (!allowShinyModelDecision) return false
        val predictions = result?.predictions.orEmpty()
        return predictions.any { it.target == "isShiny" && !it.predictedValue && canDemote(features, it) }
    }

    private fun canPromote(
        features: VisualFeatures,
        prediction: Phase2VariantClassifier.Prediction
    ): Boolean {
        if (!prediction.capability.decisionCapable) return false
        return when (prediction.target) {
            "isShiny" -> features.isShiny ||
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
        (prediction.capability.positiveCount ?: -1) >= MIN_BALANCED_EXAMPLES &&
            (prediction.capability.negativeCount ?: -1) >= MIN_BALANCED_EXAMPLES

    private fun hasCostumeExamples(prediction: Phase2VariantClassifier.Prediction): Boolean =
        (prediction.capability.positiveCount ?: -1) >= MIN_COSTUME_EXAMPLES &&
            (prediction.capability.negativeCount ?: -1) >= MIN_COSTUME_EXAMPLES

    private fun canApplyNegative(
        features: VisualFeatures,
        prediction: Phase2VariantClassifier.Prediction,
        allowShinyModelDecision: Boolean
    ): Boolean = (prediction.target != "isShiny" || allowShinyModelDecision) &&
        !prediction.predictedValue &&
        canDemote(features, prediction)

    private fun canApplyPositive(
        features: VisualFeatures,
        predictions: List<Phase2VariantClassifier.Prediction>,
        prediction: Phase2VariantClassifier.Prediction,
        allowShinyModelDecision: Boolean
    ): Boolean = (prediction.target != "isShiny" || allowShinyModelDecision) &&
        prediction.passedThreshold &&
        prediction.predictedValue &&
        canPromote(features, prediction) &&
        !isBlockedBySpeciesNegative(predictions, prediction)

    private fun isBlockedBySpeciesNegative(
        predictions: List<Phase2VariantClassifier.Prediction>,
        prediction: Phase2VariantClassifier.Prediction
    ): Boolean {
        if (prediction.source != "global") return false
        return predictions.any {
            it.target == prediction.target &&
                it.source != "global" &&
                !it.predictedValue &&
                it.capability.decisionCapable &&
                Phase2NegativeEvidence.hasNegativeEvidence(it)
        }
    }

    private fun canDemote(
        features: VisualFeatures,
        prediction: Phase2VariantClassifier.Prediction
    ): Boolean {
        if (!prediction.capability.decisionCapable) return false
        return when (prediction.target) {
            "isShiny" -> features.isShiny && Phase2NegativeEvidence.hasNegativeEvidence(prediction)
            "hasCostume" -> features.hasCostume && Phase2NegativeEvidence.hasNegativeEvidence(prediction)
            "hasSpecialForm",
            "hasLocationCard" -> Phase2NegativeEvidence.hasNegativeEvidence(prediction)
            else -> false
        }
    }
}

/**
 * Explicit-negative evidence rules shared by the merge and the Phase 2E shiny
 * demotion contract. A prediction only counts as negative evidence when the trained
 * per-target data is decision-capable and the confidence/margin gates pass.
 */
private object Phase2NegativeEvidence {

    fun hasNegativeEvidence(prediction: Phase2VariantClassifier.Prediction): Boolean =
        when (prediction.target) {
            "isShiny" -> hasShinyNegativeEvidence(prediction)
            "hasCostume" -> hasCostumeNegativeEvidence(prediction)
            "hasSpecialForm", "hasLocationCard" -> hasOtherNegativeEvidence(prediction)
            else -> false
        }

    private fun hasShinyNegativeEvidence(prediction: Phase2VariantClassifier.Prediction): Boolean =
        (prediction.capability.positiveCount ?: -1) >= SHINY_MIN_EXAMPLES &&
            (prediction.capability.negativeCount ?: -1) >= MIN_BALANCED_EXAMPLES &&
            prediction.confidence >= SHINY_DEMOTION_CONFIDENCE &&
            prediction.margin <= SHINY_DEMOTION_MARGIN

    private fun hasCostumeNegativeEvidence(prediction: Phase2VariantClassifier.Prediction): Boolean =
        prediction.source != "global" &&
            hasCostumeExamples(prediction) &&
            prediction.confidence >= TRAINED_COSTUME_CONFIDENCE &&
            prediction.margin <= COSTUME_DEMOTION_MARGIN

    private fun hasOtherNegativeEvidence(prediction: Phase2VariantClassifier.Prediction): Boolean =
        hasBalancedExamples(prediction) &&
            prediction.confidence >= OTHER_DEMOTION_CONFIDENCE &&
            prediction.margin <= OTHER_DEMOTION_MARGIN

    private fun hasBalancedExamples(prediction: Phase2VariantClassifier.Prediction): Boolean =
        (prediction.capability.positiveCount ?: -1) >= MIN_BALANCED_EXAMPLES &&
            (prediction.capability.negativeCount ?: -1) >= MIN_BALANCED_EXAMPLES

    private fun hasCostumeExamples(prediction: Phase2VariantClassifier.Prediction): Boolean =
        (prediction.capability.positiveCount ?: -1) >= MIN_COSTUME_EXAMPLES &&
            (prediction.capability.negativeCount ?: -1) >= MIN_COSTUME_EXAMPLES

    private const val SHINY_MIN_EXAMPLES = 1
    private const val SHINY_DEMOTION_CONFIDENCE = 0.505f
    private const val SHINY_DEMOTION_MARGIN = -0.010f
    private const val TRAINED_COSTUME_CONFIDENCE = 0.5f
    private const val COSTUME_DEMOTION_MARGIN = -0.006f
    private const val OTHER_DEMOTION_CONFIDENCE = 0.54f
    private const val OTHER_DEMOTION_MARGIN = -0.080f
    private const val MIN_COSTUME_EXAMPLES = 1
    private const val MIN_BALANCED_EXAMPLES = 3
}
