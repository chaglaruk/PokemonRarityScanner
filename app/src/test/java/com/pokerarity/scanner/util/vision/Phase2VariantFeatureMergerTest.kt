// Purpose: Cover strict Phase 2 visual feature promotion.
package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.VisualFeatures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2VariantFeatureMergerTest {
    @Test
    fun merge_promotesTrainedShinyAndDataBackedCostume() {
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
    fun merge_promotesCostumeWithOnePositiveAndOneNegativeExample() {
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
            appliedTargets = listOf("hasCostume"),
            minConfidence = 0.5f,
            minMargin = 0.001f,
            modelType = "test"
        )

        val merged = Phase2VariantFeatureMerger.merge(VisualFeatures(), result)

        assertTrue(merged.hasCostume)
    }

    @Test
    fun merge_globalShinySignalCanDemoteButNotPromote() {
        val promoteResult = Phase2VariantClassifier.Result(
            species = "Pikachu",
            supportedTargets = listOf("isShiny"),
            predictions = listOf(
                prediction("isShiny", confidence = 0.95f, margin = 0.40f, source = "global")
            ),
            appliedTargets = listOf("isShiny"),
            minConfidence = 0.5f,
            minMargin = 0.001f,
            modelType = "test"
        )
        val demoteResult = promoteResult.copy(
            predictions = listOf(
                prediction("isShiny", confidence = 0.51f, margin = -0.02f, source = "global")
            ),
            appliedTargets = emptyList()
        )

        assertFalse(Phase2VariantFeatureMerger.merge(VisualFeatures(), promoteResult).isShiny)
        assertFalse(Phase2VariantFeatureMerger.merge(VisualFeatures(isShiny = true), demoteResult).isShiny)
    }

    @Test
    fun merge_globalCostumeNeedsLargeMarginToPromote() {
        val weakResult = Phase2VariantClassifier.Result(
            species = "Pikachu",
            supportedTargets = listOf("hasCostume"),
            predictions = listOf(
                prediction("hasCostume", confidence = 0.51f, margin = 0.02f, source = "global")
            ),
            appliedTargets = listOf("hasCostume"),
            minConfidence = 0.5f,
            minMargin = 0.001f,
            modelType = "test"
        )
        val strongResult = weakResult.copy(
            predictions = listOf(
                prediction("hasCostume", confidence = 0.55f, margin = 0.09f, source = "global")
            )
        )

        assertFalse(Phase2VariantFeatureMerger.merge(VisualFeatures(), weakResult).hasCostume)
        assertTrue(Phase2VariantFeatureMerger.merge(VisualFeatures(), strongResult).hasCostume)
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
        margin: Float,
        positiveCount: Int = 8,
        negativeCount: Int = 8,
        source: String = "species"
    ) = Phase2VariantClassifier.Prediction(
        target = target,
        predictedValue = margin >= 0f,
        confidence = confidence,
        margin = margin,
        positiveScore = 0.7f,
        negativeScore = 0.4f,
        positiveCount = positiveCount,
        negativeCount = negativeCount,
        passedThreshold = margin >= 0f,
        source = source
    )
}
