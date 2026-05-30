// Purpose: Cover strict Phase 2 visual feature promotion.
package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.VisualFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2VariantFeatureMergerTest {
    private companion object {
        const val TRAINED_SHINY_MIN_CONFIDENCE = 0.502f
        const val TRAINED_SHINY_MIN_MARGIN = 0.003f
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
    fun merge_documentsTrainedShinyPromotionBoundary() {
        val belowConfidence = phase2Result(
            prediction(
                "isShiny",
                confidence = TRAINED_SHINY_MIN_CONFIDENCE - 0.001f,
                margin = TRAINED_SHINY_MIN_MARGIN
            )
        )
        val belowMargin = phase2Result(
            prediction(
                "isShiny",
                confidence = TRAINED_SHINY_MIN_CONFIDENCE,
                margin = TRAINED_SHINY_MIN_MARGIN - 0.001f
            )
        )
        val atThreshold = phase2Result(
            prediction(
                "isShiny",
                confidence = TRAINED_SHINY_MIN_CONFIDENCE,
                margin = TRAINED_SHINY_MIN_MARGIN
            )
        )

        assertFalse(Phase2VariantFeatureMerger.merge(VisualFeatures(), belowConfidence).isShiny)
        assertFalse(Phase2VariantFeatureMerger.merge(VisualFeatures(), belowMargin).isShiny)
        assertTrue(Phase2VariantFeatureMerger.merge(VisualFeatures(), atThreshold).isShiny)
    }

    @Test
    fun merge_documentsGlobalCostumePromotionBoundary() {
        val belowMargin = phase2Result(
            prediction(
                "hasCostume",
                confidence = 0.5f,
                margin = GLOBAL_COSTUME_MIN_MARGIN - 0.001f,
                source = "global"
            )
        )
        val atThreshold = phase2Result(
            prediction(
                "hasCostume",
                confidence = 0.5f,
                margin = GLOBAL_COSTUME_MIN_MARGIN,
                source = "global"
            )
        )

        assertFalse(Phase2VariantFeatureMerger.merge(VisualFeatures(), belowMargin).hasCostume)
        assertTrue(Phase2VariantFeatureMerger.merge(VisualFeatures(), atThreshold).hasCostume)
    }

    @Test
    fun merge_documentsStrictOtherFeaturePromotionBoundary() {
        val belowConfidence = phase2Result(
            prediction(
                "hasSpecialForm",
                confidence = STRICT_OTHER_MIN_CONFIDENCE - 0.001f,
                margin = STRICT_OTHER_MIN_MARGIN,
                positiveCount = MIN_BALANCED_OTHER_EXAMPLES,
                negativeCount = MIN_BALANCED_OTHER_EXAMPLES
            )
        )
        val belowMargin = phase2Result(
            prediction(
                "hasSpecialForm",
                confidence = STRICT_OTHER_MIN_CONFIDENCE,
                margin = STRICT_OTHER_MIN_MARGIN - 0.001f,
                positiveCount = MIN_BALANCED_OTHER_EXAMPLES,
                negativeCount = MIN_BALANCED_OTHER_EXAMPLES
            )
        )
        val belowExamples = phase2Result(
            prediction(
                "hasSpecialForm",
                confidence = STRICT_OTHER_MIN_CONFIDENCE,
                margin = STRICT_OTHER_MIN_MARGIN,
                positiveCount = MIN_BALANCED_OTHER_EXAMPLES - 1,
                negativeCount = MIN_BALANCED_OTHER_EXAMPLES
            )
        )
        val atThreshold = phase2Result(
            prediction(
                "hasSpecialForm",
                confidence = STRICT_OTHER_MIN_CONFIDENCE,
                margin = STRICT_OTHER_MIN_MARGIN,
                positiveCount = MIN_BALANCED_OTHER_EXAMPLES,
                negativeCount = MIN_BALANCED_OTHER_EXAMPLES
            )
        )

        assertFalse(Phase2VariantFeatureMerger.merge(VisualFeatures(), belowConfidence).hasSpecialForm)
        assertFalse(Phase2VariantFeatureMerger.merge(VisualFeatures(), belowMargin).hasSpecialForm)
        assertFalse(Phase2VariantFeatureMerger.merge(VisualFeatures(), belowExamples).hasSpecialForm)
        assertTrue(Phase2VariantFeatureMerger.merge(VisualFeatures(), atThreshold).hasSpecialForm)
    }

    @Test
    fun merge_speciesEvidenceWinsOverWeakerGlobalNegativeMatch() {
        val result = phase2Result(
            prediction(
                "isShiny",
                confidence = 0.60f,
                margin = -0.02f,
                source = "global"
            ),
            prediction(
                "isShiny",
                confidence = TRAINED_SHINY_MIN_CONFIDENCE,
                margin = TRAINED_SHINY_MIN_MARGIN,
                source = "species"
            )
        )

        val merged = Phase2VariantFeatureMerger.merge(VisualFeatures(), result)

        assertTrue(merged.isShiny)
    }

    @Test
    fun merge_globalCostumeRescueDoesNotOverrideStrongSpeciesNegativeMatch() {
        val result = phase2Result(
            prediction(
                "hasCostume",
                confidence = 0.70f,
                margin = GLOBAL_COSTUME_MIN_MARGIN,
                source = "global"
            ),
            prediction(
                "hasCostume",
                confidence = 0.70f,
                margin = -0.02f,
                source = "species"
            )
        )

        val merged = Phase2VariantFeatureMerger.merge(VisualFeatures(), result)

        assertFalse(merged.hasCostume)
    }

    @Test
    fun merge_globalSpecialFormRescueDoesNotOverrideStrongSpeciesNegativeMatch() {
        val result = phase2Result(
            prediction(
                "hasSpecialForm",
                confidence = STRICT_OTHER_MIN_CONFIDENCE,
                margin = STRICT_OTHER_MIN_MARGIN,
                positiveCount = MIN_BALANCED_OTHER_EXAMPLES,
                negativeCount = MIN_BALANCED_OTHER_EXAMPLES,
                source = "global"
            ),
            prediction(
                "hasSpecialForm",
                confidence = 0.60f,
                margin = -0.09f,
                positiveCount = MIN_BALANCED_OTHER_EXAMPLES,
                negativeCount = MIN_BALANCED_OTHER_EXAMPLES,
                source = "species"
            )
        )

        val merged = Phase2VariantFeatureMerger.merge(VisualFeatures(), result)

        assertFalse(merged.hasSpecialForm)
    }

    @Test
    fun merge_unsupportedTargetRemainsUnchanged() {
        val features = VisualFeatures(isShiny = true, hasCostume = false, hasSpecialForm = false)
        val result = phase2Result(
            prediction(
                "hasUnsupportedAura",
                confidence = 0.99f,
                margin = 0.99f,
                source = "species"
            )
        )

        val merged = Phase2VariantFeatureMerger.merge(features, result)

        assertEquals(features, merged)
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

    private fun phase2Result(
        vararg predictions: Phase2VariantClassifier.Prediction
    ) = Phase2VariantClassifier.Result(
        species = "Pikachu",
        supportedTargets = predictions.map { it.target },
        predictions = predictions.toList(),
        appliedTargets = predictions.filter { it.passedThreshold }.map { it.target },
        minConfidence = 0.5f,
        minMargin = 0.001f,
        modelType = "test"
    )
}
