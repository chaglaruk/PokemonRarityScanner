package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.util.vision.VariantMergeLogic
import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier
import org.junit.Assert.assertFalse
import org.junit.Test

class VariantDecisionEngineGuardrailTest {

    @Test
    fun classifierOnlyShinyCostumeFallbackDoesNotOverrideWithoutVisualSupport() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            fullMatch = FullVariantMatch(
                finalSpecies = "Piplup",
                resolvedVariantClass = "base",
                resolvedShiny = false,
                resolvedCostume = false,
                explanationMode = "generic_species_only"
            ),
            fallbackMatch = VariantPrototypeClassifier.MatchResult(
                species = "Piplup",
                assetKey = "393_00_HALLOWEEN_2021_NOEVOLVE_shiny",
                spriteKey = "393_00_HALLOWEEN_2021_NOEVOLVE_shiny",
                variantType = "costume",
                isShiny = true,
                isCostumeLike = true,
                scope = "global",
                score = 0.362f,
                confidence = 0.491f,
                speciesMargin = 0.085f,
                variantMargin = 0.0f,
                topSpecies = listOf("Piplup:0.362", "Prinplup:0.447", "Empoleon:0.500")
            )
        )

        assertFalse(merged.isShiny)
        assertFalse(merged.hasCostume)
    }
}
