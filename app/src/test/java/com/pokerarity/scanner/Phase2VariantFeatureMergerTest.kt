// Purpose: Verify Phase 2 visual feature promotion rules.
package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import com.pokerarity.scanner.util.vision.Phase2VariantFeatureMerger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2VariantFeatureMergerTest {
    @Test
    fun strongPassedPositiveTargetsPromoteVisualFeatures() {
        val result = phase2Result(
            predictions = listOf(
                prediction("isShiny", predictedValue = true, passedThreshold = true, confidence = 0.98f, margin = 0.56f),
                prediction("hasCostume", predictedValue = true, passedThreshold = true, confidence = 0.96f, margin = 0.51f),
                prediction("hasLocationCard", predictedValue = true, passedThreshold = true, confidence = 0.93f, margin = 0.37f)
            )
        )

        val merged = Phase2VariantFeatureMerger.merge(VisualFeatures(), result)

        assertTrue(merged.isShiny)
        assertTrue(merged.hasCostume)
        assertTrue(merged.hasLocationCard)
    }

    @Test
    fun failedOrNegativeTargetsDoNotPromoteFeatures() {
        val result = phase2Result(
            predictions = listOf(
                prediction("isShiny", predictedValue = true, passedThreshold = false),
                prediction("hasCostume", predictedValue = false, passedThreshold = true)
            )
        )

        val merged = Phase2VariantFeatureMerger.merge(VisualFeatures(), result)

        assertFalse(merged.isShiny)
        assertFalse(merged.hasCostume)
    }

    @Test
    fun trainedShinyTargetNeedsVisualConfirmationOrStrictSignal() {
        val result = phase2Result(
            predictions = listOf(
                prediction(
                    "isShiny",
                    predictedValue = true,
                    passedThreshold = true,
                    confidence = 0.503f,
                    margin = 0.004f,
                    positiveCount = 5,
                    negativeCount = 5
                )
            )
        )

        assertTrue(result.predictions.single().capability.decisionCapable)

        val merged = Phase2VariantFeatureMerger.merge(VisualFeatures(), result)

        assertFalse(merged.isShiny)
        assertTrue(Phase2VariantFeatureMerger.merge(VisualFeatures(isShiny = true), result).isShiny)
    }

    private fun phase2Result(predictions: List<Phase2VariantClassifier.Prediction>) =
        Phase2VariantClassifier.Result(
            species = "Pikachu",
            supportedTargets = predictions.map { it.target },
            predictions = predictions,
            appliedTargets = Phase2VariantClassifier.selectAppliedTargets(predictions),
            minConfidence = 0.7f,
            minMargin = 0.12f,
            modelType = "test"
        )

    @Suppress("LongParameterList")
    private fun prediction(
        target: String,
        predictedValue: Boolean,
        passedThreshold: Boolean,
        confidence: Float = if (passedThreshold) 0.9f else 0.4f,
        margin: Float = if (predictedValue) 0.3f else -0.3f,
        positiveCount: Int? = 5,
        negativeCount: Int? = 5,
        supported: Boolean? = true,
        source: String = "species"
    ): Phase2VariantClassifier.Prediction {
        val capability = Phase2VariantClassifier
            .evaluateCapability(target, source, supported, positiveCount, negativeCount)
        return Phase2VariantClassifier.Prediction(
            target = target,
            predictedValue = predictedValue,
            confidence = confidence,
            margin = margin,
            positiveScore = 0.8f,
            negativeScore = 0.5f,
            positiveCount = positiveCount,
            negativeCount = negativeCount,
            passedThreshold = passedThreshold,
            source = source,
            capability = capability
        )
    }
}
