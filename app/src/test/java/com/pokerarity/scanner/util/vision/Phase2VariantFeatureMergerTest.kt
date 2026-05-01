// Purpose: Cover strict Phase 2 visual feature promotion.
package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.VisualFeatures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2VariantFeatureMergerTest {
    @Test
    fun merge_promotesTrainedShinyButNotBorderlineCostume() {
        val result = Phase2VariantClassifier.Result(
            species = "Flareon",
            supportedTargets = listOf("isShiny", "hasCostume"),
            predictions = listOf(
                prediction("isShiny", confidence = 0.70f, margin = 0.24f),
                prediction("hasCostume", confidence = 0.72f, margin = 0.25f)
            ),
            appliedTargets = listOf("isShiny", "hasCostume"),
            minConfidence = 0.64f,
            minMargin = 0.18f,
            modelType = "test"
        )

        val merged = Phase2VariantFeatureMerger.merge(VisualFeatures(), result)

        assertTrue(merged.isShiny)
        assertFalse(merged.hasCostume)
    }

    @Test
    fun merge_preservesExistingVisualSignals() {
        val result = Phase2VariantClassifier.Result(
            species = "Raichu",
            supportedTargets = listOf("hasCostume"),
            predictions = listOf(prediction("hasCostume", confidence = 0.70f, margin = 0.24f)),
            appliedTargets = listOf("hasCostume"),
            minConfidence = 0.64f,
            minMargin = 0.18f,
            modelType = "test"
        )

        val merged = Phase2VariantFeatureMerger.merge(
            VisualFeatures(hasCostume = true),
            result
        )

        assertTrue(merged.hasCostume)
    }

    private fun prediction(
        target: String,
        confidence: Float,
        margin: Float
    ) = Phase2VariantClassifier.Prediction(
        target = target,
        predictedValue = true,
        confidence = confidence,
        margin = margin,
        positiveScore = 0.7f,
        negativeScore = 0.4f,
        positiveCount = 8,
        negativeCount = 8,
        passedThreshold = true
    )
}
