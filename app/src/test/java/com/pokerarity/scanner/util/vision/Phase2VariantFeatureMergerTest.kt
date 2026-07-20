// Purpose: Cover strict Phase 2 visual feature promotion.
package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.VisualFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2VariantFeatureMergerTest {
    private companion object {
        const val STRICT_SHINY_MIN_CONFIDENCE = 0.97f
        const val STRICT_SHINY_MIN_MARGIN = 0.55f
        const val GLOBAL_COSTUME_MIN_MARGIN = 0.080f
        const val STRICT_OTHER_MIN_CONFIDENCE = 0.92f
        const val STRICT_OTHER_MIN_MARGIN = 0.36f
        const val MIN_BALANCED_OTHER_EXAMPLES = 3
    }

    @Test
    fun merge_promotesTrainedShinyAndDataBackedCostume() {
        val result = Phase2VariantClassifier.Result(
            species = "Flareon",
            supportedTargets = listOf("isShiny", "hasCostume"),
            predictions = listOf(
                prediction("isShiny", confidence = STRICT_SHINY_MIN_CONFIDENCE, margin = STRICT_SHINY_MIN_MARGIN),
                prediction("hasCostume", confidence = 0.72f, margin = 0.25f)
            ),
            appliedTargets = listOf("isShiny", "hasCostume"),
            minConfidence = 0.64f,
            minMargin = 0.18f,
            modelType = "test"
        )

        val merged = Phase2VariantFeatureMerger.merge(VisualFeatures(), result)

        assertTrue(merged.isShiny)
        assertTrue(merged.hasCostume)
    }

    @Test
    fun merge_rejectsCostumeWhenMarginDoesNotBeatNegativePrototype() {
        val result = Phase2VariantClassifier.Result(
            species = "Flareon",
            supportedTargets = listOf("hasCostume"),
            predictions = listOf(prediction("hasCostume", confidence = 0.50f, margin = 0.0005f)),
            appliedTargets = listOf("hasCostume"),
            minConfidence = 0.5f,
            minMargin = 0.001f,
            modelType = "test"
        )

        val merged = Phase2VariantFeatureMerger.merge(VisualFeatures(), result)

        assertFalse(merged.hasCostume)
    }

    @Test
    fun merge_rejectsCostumeWithOnePositiveAndOneNegativeExample() {
        val result = Phase2VariantClassifier.Result(
            species = "Pikachu",
            supportedTargets = listOf("hasCostume"),
            predictions = listOf(
                prediction(
                    "hasCostume",
                    confidence = 0.501f,
                    margin = 0.002f,
                    positiveCount = 1,
                    negativeCount = 1
                )
            ),
            appliedTargets = emptyList(),
            minConfidence = 0.5f,
            minMargin = 0.001f,
            modelType = "test"
        )

        val merged = Phase2VariantFeatureMerger.merge(VisualFeatures(), result)

        assertFalse(merged.hasCostume)
    }

    @Test
    fun merge_predictionWithMissingMetadataCannotPromoteOrDemote() {
        val promoteResult = phase2Result(
            prediction("hasCostume", confidence = 0.99f, margin = 0.50f, positiveCount = null, negativeCount = 5)
        )
        val demoteResult = phase2Result(
            prediction("hasCostume", confidence = 0.99f, margin = -0.50f, positiveCount = 5, negativeCount = null)
        )

        assertFalse(Phase2VariantFeatureMerger.merge(VisualFeatures(), promoteResult).hasCostume)
        assertTrue(Phase2VariantFeatureMerger.merge(VisualFeatures(hasCostume = true), demoteResult).hasCostume)
    }

    @Test
    fun merge_unsupportedPredictionCannotPromoteOrDemote() {
        val promoteResult = phase2Result(
            prediction("hasCostume", confidence = 0.99f, margin = 0.50f, supported = false)
        )
        val demoteResult = phase2Result(
            prediction("hasCostume", confidence = 0.99f, margin = -0.50f, supported = false)
        )

        assertFalse(Phase2VariantFeatureMerger.merge(VisualFeatures(), promoteResult).hasCostume)
        assertTrue(Phase2VariantFeatureMerger.merge(VisualFeatures(hasCostume = true), demoteResult).hasCostume)
    }

    @Test
    fun merge_zeroPositiveCountCannotPromoteOrDemote() {
        val promoteResult = phase2Result(
            prediction("hasCostume", confidence = 0.99f, margin = 0.50f, positiveCount = 0, negativeCount = 10)
        )
        val demoteResult = phase2Result(
            prediction("hasCostume", confidence = 0.99f, margin = -0.50f, positiveCount = 0, negativeCount = 10)
        )

        assertFalse(Phase2VariantFeatureMerger.merge(VisualFeatures(), promoteResult).hasCostume)
        assertTrue(Phase2VariantFeatureMerger.merge(VisualFeatures(hasCostume = true), demoteResult).hasCostume)
    }

    @Test
    fun merge_zeroNegativeCountCannotPromoteOrDemote() {
        val promoteResult = phase2Result(
            prediction("hasCostume", confidence = 0.99f, margin = 0.50f, positiveCount = 10, negativeCount = 0)
        )
        val demoteResult = phase2Result(
            prediction("hasCostume", confidence = 0.99f, margin = -0.50f, positiveCount = 10, negativeCount = 0)
        )

        assertFalse(Phase2VariantFeatureMerger.merge(VisualFeatures(), promoteResult).hasCostume)
        assertTrue(Phase2VariantFeatureMerger.merge(VisualFeatures(hasCostume = true), demoteResult).hasCostume)
    }

    @Test
    fun merge_combinedCountNineCannotPromoteOrDemote() {
        val promoteResult = phase2Result(
            prediction("hasCostume", confidence = 0.99f, margin = 0.50f, positiveCount = 4, negativeCount = 5)
        )
        val demoteResult = phase2Result(
            prediction("hasCostume", confidence = 0.99f, margin = -0.50f, positiveCount = 4, negativeCount = 5)
        )

        assertFalse(Phase2VariantFeatureMerger.merge(VisualFeatures(), promoteResult).hasCostume)
        assertTrue(Phase2VariantFeatureMerger.merge(VisualFeatures(hasCostume = true), demoteResult).hasCostume)
    }

    @Test
    fun merge_onePositiveAndNineNegativeCanProceedToTargetThresholds() {
        val promoteResult = phase2Result(
            prediction("hasCostume", confidence = 0.99f, margin = 0.50f, positiveCount = 1, negativeCount = 9)
        )

        assertTrue(Phase2VariantFeatureMerger.merge(VisualFeatures(), promoteResult).hasCostume)
    }

    @Test
    fun merge_ninePositiveAndOneNegativeCanProceedToTargetThresholds() {
        val promoteResult = phase2Result(
            prediction("hasCostume", confidence = 0.99f, margin = 0.50f, positiveCount = 9, negativeCount = 1)
        )

        assertTrue(Phase2VariantFeatureMerger.merge(VisualFeatures(), promoteResult).hasCostume)
    }

    @Test
    fun merge_diagnosticsOnlySpeciesNegativeCannotBlockAdequateGlobalPrediction() {
        val result = phase2Result(
            prediction(
                "hasCostume",
                confidence = 0.70f,
                margin = GLOBAL_COSTUME_MIN_MARGIN,
                source = "global",
                positiveCount = 5,
                negativeCount = 5
            ),
            prediction(
                "hasCostume",
                confidence = 0.70f,
                margin = -0.02f,
                source = "species",
                positiveCount = 1,
                negativeCount = 1
            )
        )

        val merged = Phase2VariantFeatureMerger.merge(VisualFeatures(), result)

        assertTrue(merged.hasCostume)
    }

    @Test
    fun merge_adequateSpeciesNegativeRetainsGlobalRescueBlock() {
        val result = phase2Result(
            prediction(
                "hasCostume",
                confidence = 0.70f,
                margin = GLOBAL_COSTUME_MIN_MARGIN,
                source = "global",
                positiveCount = 5,
                negativeCount = 5
            ),
            prediction(
                "hasCostume",
                confidence = 0.70f,
                margin = -0.02f,
                source = "species",
                positiveCount = 5,
                negativeCount = 5
            )
        )

        val merged = Phase2VariantFeatureMerger.merge(VisualFeatures(), result)

        assertFalse(merged.hasCostume)
    }

    @Test
    fun merge_existingVisualFlagsUnchangedWhenAllPredictionsAreDiagnosticsOnly() {
        val initial = VisualFeatures(isShiny = true, hasCostume = true, hasSpecialForm = false)
        val result = phase2Result(
            prediction("isShiny", confidence = 0.99f, margin = -0.50f, positiveCount = 1, negativeCount = 1),
            prediction("hasCostume", confidence = 0.99f, margin = -0.50f, positiveCount = 1, negativeCount = 1)
        )

        val merged = Phase2VariantFeatureMerger.merge(initial, result)

        assertEquals(initial, merged)
    }

    @Test
    fun merge_deterministicRepeatReturnsEqualVisualFeatures() {
        val result = phase2Result(
            prediction("isShiny", confidence = STRICT_SHINY_MIN_CONFIDENCE, margin = STRICT_SHINY_MIN_MARGIN)
        )
        val first = Phase2VariantFeatureMerger.merge(VisualFeatures(), result)
        val second = Phase2VariantFeatureMerger.merge(VisualFeatures(), result)

        assertEquals(first, second)
    }

    @Suppress("LongParameterList")
    private fun prediction(
        target: String,
        confidence: Float,
        margin: Float,
        positiveCount: Int? = 8,
        negativeCount: Int? = 8,
        supported: Boolean? = true,
        source: String = "species"
    ): Phase2VariantClassifier.Prediction {
        val capability = Phase2VariantClassifier
            .evaluateCapability(target, source, supported, positiveCount, negativeCount)
        return Phase2VariantClassifier.Prediction(
            target = target,
            predictedValue = margin >= 0f,
            confidence = confidence,
            margin = margin,
            positiveScore = 0.7f,
            negativeScore = 0.4f,
            positiveCount = positiveCount ?: 0,
            negativeCount = negativeCount ?: 0,
            passedThreshold = margin >= 0f,
            source = source,
            capability = capability
        )
    }

    private fun phase2Result(
        vararg predictions: Phase2VariantClassifier.Prediction
    ) = Phase2VariantClassifier.Result(
        species = "Pikachu",
        supportedTargets = predictions.map { it.target },
        predictions = predictions.toList(),
        appliedTargets = predictions.filter { it.passedThreshold && it.capability.decisionCapable }.map { it.target },
        minConfidence = 0.5f,
        minMargin = 0.001f,
        modelType = "test"
    )
}
