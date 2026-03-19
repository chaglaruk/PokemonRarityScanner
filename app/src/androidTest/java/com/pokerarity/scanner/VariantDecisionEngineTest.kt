package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.util.vision.VariantDecisionEngine
import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VariantDecisionEngineTest {

    private val engine = VariantDecisionEngine(ApplicationProvider.getApplicationContext())

    @Test
    fun speciesScopedCostumeRescuePromotesCostumeBelowMainThreshold() {
        val merged = engine.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            match = VariantPrototypeClassifier.MatchResult(
                species = "Pikachu",
                assetKey = "025_00_12",
                spriteKey = "025_00_12",
                variantType = "costume",
                isShiny = false,
                isCostumeLike = true,
                scope = "species",
                score = 0.360f,
                confidence = 0.471f,
                speciesMargin = 0.0f,
                variantMargin = 0.009f,
                bestBaseScore = 0.404f,
                bestNonBaseScore = 0.360f,
                bestNonBaseSpecies = "Pikachu",
                bestNonBaseAssetKey = "025_00_12",
                bestNonBaseSpriteKey = "025_00_12",
                bestNonBaseVariantType = "costume",
                bestNonBaseIsShiny = false,
                bestNonBaseIsCostumeLike = true,
                topSpecies = listOf("025_00_12:0.360", "025_01_13:0.370", "025_01_12:0.370")
            )
        )

        assertTrue(merged.hasCostume)
        assertFalse(merged.isShiny)
    }

    @Test
    fun speciesScopedCostumeRescueDoesNotPromoteWhenBaseIsTooClose() {
        val merged = engine.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            match = VariantPrototypeClassifier.MatchResult(
                species = "Pikachu",
                assetKey = "025_00_12",
                spriteKey = "025_00_12",
                variantType = "costume",
                isShiny = false,
                isCostumeLike = true,
                scope = "species",
                score = 0.360f,
                confidence = 0.471f,
                speciesMargin = 0.0f,
                variantMargin = 0.009f,
                bestBaseScore = 0.385f,
                bestNonBaseScore = 0.360f,
                bestNonBaseSpecies = "Pikachu",
                bestNonBaseAssetKey = "025_00_12",
                bestNonBaseSpriteKey = "025_00_12",
                bestNonBaseVariantType = "costume",
                bestNonBaseIsShiny = false,
                bestNonBaseIsCostumeLike = true,
                topSpecies = listOf("025_00_12:0.360", "025_01_13:0.370", "025_01_12:0.370")
            )
        )

        assertFalse(merged.hasCostume)
    }

    @Test
    fun lowConfidenceSpeciesFormMatchDoesNotLeakShinyFlag() {
        val merged = engine.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            match = VariantPrototypeClassifier.MatchResult(
                species = "Piloswine",
                assetKey = "221_01_shiny",
                spriteKey = "221_01_shiny",
                variantType = "form",
                isShiny = true,
                isCostumeLike = false,
                scope = "species",
                score = 0.459f,
                confidence = 0.379f,
                speciesMargin = 0.0f,
                variantMargin = 0.001f,
                bestBaseScore = 0.595f,
                bestNonBaseScore = 0.459f,
                bestNonBaseSpecies = "Piloswine",
                bestNonBaseAssetKey = "221_01_shiny",
                bestNonBaseSpriteKey = "221_01_shiny",
                bestNonBaseVariantType = "form",
                bestNonBaseIsShiny = true,
                bestNonBaseIsCostumeLike = false,
                topSpecies = listOf("221_01_shiny:0.459", "221_00_shiny:0.460", "221_00:0.595")
            )
        )

        assertTrue(merged.hasSpecialForm)
        assertFalse(merged.isShiny)
    }

    @Test
    fun denseSpeciesCostumeNearTieStillPromotesCostume() {
        val merged = engine.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            match = VariantPrototypeClassifier.MatchResult(
                species = "Pikachu",
                assetKey = "025_01_13",
                spriteKey = "025_01_13",
                variantType = "costume",
                isShiny = false,
                isCostumeLike = true,
                scope = "species",
                score = 0.342f,
                confidence = 0.481f,
                speciesMargin = 0.0f,
                variantMargin = 0.009f,
                bestBaseScore = 0.352f,
                bestNonBaseScore = 0.342f,
                bestNonBaseSpecies = "Pikachu",
                bestNonBaseAssetKey = "025_01_13",
                bestNonBaseSpriteKey = "025_01_13",
                bestNonBaseVariantType = "costume",
                bestNonBaseIsShiny = false,
                bestNonBaseIsCostumeLike = true,
                topSpecies = listOf("025_01_13:0.342", "025_01_13_shiny:0.351", "025_01:0.352")
            )
        )

        assertTrue(merged.hasCostume)
        assertFalse(merged.isShiny)
    }
}
