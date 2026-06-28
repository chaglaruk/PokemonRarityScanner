package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.util.vision.VariantMergeLogic
import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VariantRecognitionGuardrailTest {

    @Test
    fun weakClassifierOnlyShinyDoesNotBecomeVariantRecognition() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            fullMatch = FullVariantMatch(
                finalSpecies = "Pidgey",
                resolvedVariantClass = "base",
                resolvedShiny = false,
                resolvedCostume = false,
                explanationMode = "generic_species_only"
            ),
            fallbackMatch = VariantPrototypeClassifier.MatchResult(
                species = "Pidgey",
                assetKey = "016_00_shiny",
                spriteKey = "016_00_shiny",
                variantType = "base",
                isShiny = true,
                isCostumeLike = false,
                scope = "species",
                score = 0.520f,
                confidence = 0.615f,
                speciesMargin = 0.0f,
                variantMargin = 0.040f,
                bestBaseScore = 0.520f,
                bestBaseShinyPeerScore = 0.560f,
                topSpecies = listOf("016_00_shiny:0.520", "016_00:0.560")
            )
        )

        assertFalse(merged.isShiny)
    }

    @Test
    fun highConfidenceFullVariantMatchCanBecomeVariantRecognition() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            fullMatch = FullVariantMatch(
                finalSpecies = "Blastoise",
                finalSpriteKey = "009_00_05_shiny",
                resolvedVariantClass = "costume",
                resolvedShiny = true,
                resolvedCostume = true,
                resolvedForm = false,
                variantConfidence = 0.81f,
                shinyConfidence = 0.77f,
                explanationMode = "exact_authoritative"
            ),
            fallbackMatch = null
        )

        assertTrue(merged.isShiny)
        assertTrue(merged.hasCostume)
    }
}
