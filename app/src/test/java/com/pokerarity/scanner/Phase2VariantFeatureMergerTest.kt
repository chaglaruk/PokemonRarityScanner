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
    fun passedPositiveTargetsPromoteVisualFeatures() {
        val result = phase2Result(
            predictions = listOf(
                prediction("isShiny", predictedValue = true, passedThreshold = true),
                prediction("hasCostume", predictedValue = true, passedThreshold = true),
                prediction("hasLocationCard", predictedValue = true, passedThreshold = true)
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

    private fun phase2Result(predictions: List<Phase2VariantClassifier.Prediction>) =
        Phase2VariantClassifier.Result(
            species = "Pikachu",
            supportedTargets = predictions.map { it.target },
            predictions = predictions,
            appliedTargets = predictions.filter { it.passedThreshold }.map { it.target },
            minConfidence = 0.7f,
            minMargin = 0.12f,
            modelType = "test"
        )

    private fun prediction(
        target: String,
        predictedValue: Boolean,
        passedThreshold: Boolean
    ) = Phase2VariantClassifier.Prediction(
        target = target,
        predictedValue = predictedValue,
        confidence = if (passedThreshold) 0.9f else 0.4f,
        margin = if (predictedValue) 0.3f else -0.3f,
        positiveScore = 0.8f,
        negativeScore = 0.5f,
        positiveCount = 3,
        negativeCount = 3,
        passedThreshold = passedThreshold
    )
}
