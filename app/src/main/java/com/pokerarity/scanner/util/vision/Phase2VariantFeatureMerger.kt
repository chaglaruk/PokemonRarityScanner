// Purpose: Apply high-confidence Phase 2 variant predictions to visual features.
package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.VisualFeatures

@Suppress("TooManyFunctions") // The bounded trace helpers live beside the Phase 2 merge gates they report.
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

    private data class DecisionContext(
        val prediction: Phase2VariantClassifier.Prediction,
        val explicitNegativeConflict: Boolean,
        val demoted: Boolean,
        val promoted: Boolean,
        val blocked: Boolean
    )

    fun merge(features: VisualFeatures, result: Phase2VariantClassifier.Result?): VisualFeatures =
        merge(features, result, null)

    @Suppress("CyclomaticComplexMethod") // Target selection and trace reporting share the applied gate checks.
    internal fun merge(
        features: VisualFeatures,
        result: Phase2VariantClassifier.Result?,
        onDecision: ((List<QaPhase2Decision>) -> Unit)?
    ): VisualFeatures {
        val predictions = result?.predictions.orEmpty()
        val negativeTargets = predictions
            .filter { !it.predictedValue && canDemote(features, it) }
            .map { it.target }
            .toSet()
        val appliedTargets = predictions
            .filter {
                it.passedThreshold &&
                    it.predictedValue &&
                    canPromote(features, it) &&
                    !isBlockedBySpeciesNegative(predictions, it)
            }
            .map { it.target }
            .toSet()

        onDecision?.invoke(
            phase2DecisionReport(features, predictions, negativeTargets, appliedTargets)
        )

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

    private fun phase2DecisionReport(
        features: VisualFeatures,
        predictions: List<Phase2VariantClassifier.Prediction>,
        negativeTargets: Set<String>,
        appliedTargets: Set<String>
    ): List<QaPhase2Decision> = predictions
        .take(VariantStageDiagnosticRecorder.MAX_CANDIDATES)
        .map { prediction ->
            val blocked = isBlockedBySpeciesNegative(predictions, prediction)
            val promoted = prediction.passedThreshold && prediction.predictedValue &&
                canPromote(features, prediction) && !blocked
            val demoted = !prediction.predictedValue && canDemote(features, prediction)
            val decisionContext = DecisionContext(
                prediction = prediction,
                explicitNegativeConflict = prediction.target in negativeTargets &&
                    prediction.target in appliedTargets,
                demoted = demoted,
                promoted = promoted,
                blocked = blocked
            )
            val action = when (prediction.target) {
                in negativeTargets -> "demoted"
                in appliedTargets -> "promoted"
                else -> "no_change"
            }
            QaPhase2Decision(
                target = prediction.target,
                prediction = prediction.predictedValue,
                confidence = prediction.confidence,
                margin = prediction.margin,
                positiveScore = prediction.positiveScore,
                negativeScore = prediction.negativeScore,
                positiveCount = prediction.capability.positiveCount,
                negativeCount = prediction.capability.negativeCount,
                source = prediction.source,
                decisionCapable = prediction.capability.decisionCapable,
                passedThreshold = prediction.passedThreshold,
                action = action,
                reasonCode = phase2DecisionReasonCode(decisionContext)
            )
        }

    private fun phase2DecisionReasonCode(context: DecisionContext): String = when {
        context.explicitNegativeConflict -> "explicit_negative_precedes_positive_for_target"
        context.demoted -> "explicit_negative_evidence_gates_passed"
        context.promoted -> "positive_prediction_and_merge_gates_passed"
        context.blocked -> "global_promotion_blocked_by_species_negative"
        else -> phase2PredictionRejectionReason(context.prediction)
    }

    private fun phase2PredictionRejectionReason(prediction: Phase2VariantClassifier.Prediction): String = when {
        !prediction.capability.decisionCapable -> "target_not_decision_capable"
        prediction.predictedValue && !prediction.passedThreshold -> "prediction_threshold_not_passed"
        prediction.predictedValue && prediction.target == "isShiny" ->
            "shiny_promotion_requires_existing_visual_or_strict_species_evidence"
        prediction.predictedValue && prediction.target == "hasCostume" ->
            "costume_training_counts_or_margin_gate_failed"
        prediction.predictedValue -> "promotion_gate_failed"
        else -> "negative_prediction_lacked_explicit_demotion_evidence"
    }

    /**
     * Phase 2E explicit-negative contract for shiny: true only when the trained
     * classifier demoted an existing shiny positive under the same evidence rules the
     * merge applies. This is the one shiny path that may establish tri-state FALSE;
     * a classifier merely not firing never does.
     */
    fun shinyDemotionApplied(features: VisualFeatures, result: Phase2VariantClassifier.Result?): Boolean {
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
