package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.util.vision.VariantMergeLogic
import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for VariantMergeLogic — no Android device required.
 * Mirrors existing androidTest cases + additional edge cases.
 */
class VariantMergeLogicTest {

    @Test
    fun fullVariantMatchResolvedFlagsDriveMergedVisuals() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            fullMatch = FullVariantMatch(
                finalSpecies = "Blastoise",
                finalSpriteKey = "009_00_05_shiny",
                resolvedVariantClass = "costume",
                resolvedShiny = true,
                resolvedCostume = true,
                resolvedForm = false,
                speciesConfidence = 0.92f,
                variantConfidence = 0.81f,
                shinyConfidence = 0.77f,
                explanationMode = "exact_authoritative"
            ),
            fallbackMatch = null
        )

        assertTrue(merged.hasCostume)
        assertTrue(merged.isShiny)
        assertFalse(merged.hasSpecialForm)
        assertTrue(merged.confidence >= 0.81f)
    }

    @Test
    fun baseShinyFullMatchDoesNotOverrideVisualNegativeWithoutStrongConfidence() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(isShiny = false, confidence = 1.0f),
            fullMatch = FullVariantMatch(
                finalSpecies = "Stonjourner",
                finalSpriteKey = "873_00_shiny",
                resolvedVariantClass = "base",
                resolvedShiny = true,
                resolvedCostume = false,
                resolvedForm = false,
                variantConfidence = 0.53f,
                shinyConfidence = 0.53f,
                explanationMode = "generic_species_only"
            ),
            fallbackMatch = null
        )

        assertFalse(merged.isShiny)
    }

    @Test
    fun weakGenericFormFullMatchDoesNotOverrideWithoutSupport() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            fullMatch = FullVariantMatch(
                finalSpecies = "Cottonee",
                finalSpriteKey = "546_00_CSPRING_2024",
                resolvedVariantClass = "form",
                resolvedShiny = false,
                resolvedCostume = false,
                resolvedForm = true,
                variantConfidence = 0.44f,
                shinyConfidence = 0f,
                explanationMode = "generic_variant"
            ),
            fallbackMatch = VariantPrototypeClassifier.MatchResult(
                species = "Cottonee",
                assetKey = "546_00_CSPRING_2024",
                spriteKey = "546_00_CSPRING_2024",
                variantType = "form",
                isShiny = false,
                isCostumeLike = false,
                scope = "species",
                score = 0.5618136f,
                confidence = 0.35828313f,
                speciesMargin = 0.0f,
                variantMargin = 0.0f,
                topSpecies = listOf("546_00_CSPRING_2024:0.562", "546_00:0.582")
            )
        )

        assertFalse(merged.hasSpecialForm)
    }

    @Test
    fun weakGenericCostumeFullMatchDoesNotOverrideWithoutVisualSupport() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(hasCostume = false),
            fullMatch = FullVariantMatch(
                finalSpecies = "Charmander",
                finalSpriteKey = "004_00_11",
                resolvedVariantClass = "costume",
                resolvedShiny = false,
                resolvedCostume = true,
                resolvedForm = false,
                variantConfidence = 0.54f,
                shinyConfidence = 0f,
                explanationMode = "generic_variant"
            ),
            fallbackMatch = VariantPrototypeClassifier.MatchResult(
                species = "Charmander",
                assetKey = "004_00_11",
                spriteKey = "004_00_11",
                variantType = "costume",
                isShiny = false,
                isCostumeLike = true,
                scope = "species",
                score = 0.46270153f,
                confidence = 0.52f,
                speciesMargin = 0.0f,
                variantMargin = 0.0f,
                rescueKind = "species_costume_rescue",
                topSpecies = listOf("004_00_11:0.463", "004_00_11_shiny:0.478")
            )
        )

        assertFalse(merged.hasCostume)
    }

    @Test
    fun strongSameSpeciesNonBaseFallbackCanRestoreShinyOnFullMatch() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(isShiny = false),
            fullMatch = FullVariantMatch(
                finalSpecies = "Spinda",
                finalSpriteKey = "327_00_F02",
                resolvedVariantClass = "form",
                resolvedShiny = false,
                resolvedCostume = false,
                resolvedForm = true,
                variantConfidence = 0.55f,
                shinyConfidence = 0f,
                explanationMode = "generic_variant"
            ),
            fallbackMatch = VariantPrototypeClassifier.MatchResult(
                species = "Spinda",
                assetKey = "327_00_F02_shiny",
                spriteKey = "327_00_F02_shiny",
                variantType = "form",
                isShiny = true,
                isCostumeLike = false,
                scope = "global",
                score = 0.4541286f,
                confidence = 0.6242096f,
                speciesMargin = 0.0f,
                variantMargin = 0.0f,
                topSpecies = listOf("Spinda:0.454")
            )
        )

        assertTrue(merged.hasSpecialForm)
        assertTrue(merged.isShiny)
    }

    @Test
    fun genericFullVariantMatchDoesNotClearExistingIndependentVisualFlags() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(isLucky = true, hasLocationCard = true, confidence = 0.4f),
            fullMatch = FullVariantMatch(
                finalSpecies = "Lapras",
                resolvedVariantClass = "base",
                resolvedShiny = false,
                resolvedCostume = false,
                resolvedForm = false,
                variantConfidence = 0.35f,
                explanationMode = "generic_species_only"
            ),
            fallbackMatch = null
        )

        assertTrue(merged.isLucky)
        assertTrue(merged.hasLocationCard)
        assertFalse(merged.hasCostume)
        assertFalse(merged.isShiny)
        assertFalse(merged.hasSpecialForm)
        assertTrue(merged.confidence >= 0.4f)
    }

    // --- Existing regression cases (ported from androidTest) ---

    @Test
    fun speciesScopedCostumeRescuePromotesCostumeBelowMainThreshold() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(hasCostume = true),
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
        val merged = VariantMergeLogic.mergeVisualFeatures(
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
        val merged = VariantMergeLogic.mergeVisualFeatures(
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

        assertFalse(merged.hasSpecialForm)
        assertFalse(merged.isShiny)
    }

    @Test
    fun lowConfidenceSpeciesCostumeShinyRescueDoesNotLeakShinyFlag() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(hasCostume = true),
            match = VariantPrototypeClassifier.MatchResult(
                species = "Pichu",
                assetKey = "172_00_06_shiny",
                spriteKey = "172_00_06_shiny",
                variantType = "costume",
                isShiny = true,
                isCostumeLike = true,
                scope = "species",
                score = 0.349f,
                confidence = 0.52f,
                speciesMargin = 0.0f,
                variantMargin = 0.004f,
                bestBaseScore = 0.362f,
                bestNonBaseScore = 0.349f,
                bestNonBaseSpecies = "Pichu",
                bestNonBaseAssetKey = "172_00_06_shiny",
                bestNonBaseSpriteKey = "172_00_06_shiny",
                bestNonBaseVariantType = "costume",
                bestNonBaseIsShiny = true,
                bestNonBaseIsCostumeLike = true,
                topSpecies = listOf("172_00_06_shiny:0.349", "172_00_shiny:0.350", "172_00_07_shiny:0.354")
            )
        )

        assertTrue(merged.hasCostume)
        assertFalse(merged.isShiny)
    }

    @Test
    fun lowConfidenceSpeciesCostumeRescueWithoutVisualSupportDoesNotPromoteCostume() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            match = VariantPrototypeClassifier.MatchResult(
                species = "Squirtle",
                assetKey = "007_00_05",
                spriteKey = "007_00_05",
                variantType = "costume",
                isShiny = false,
                isCostumeLike = true,
                scope = "species",
                score = 0.435f,
                confidence = 0.52f,
                speciesMargin = 0.0f,
                variantMargin = 0.010f,
                bestBaseScore = 0.462f,
                bestNonBaseScore = 0.435f,
                bestNonBaseSpecies = "Squirtle",
                bestNonBaseAssetKey = "007_00_05",
                bestNonBaseSpriteKey = "007_00_05",
                bestNonBaseVariantType = "costume",
                bestNonBaseIsShiny = false,
                bestNonBaseIsCostumeLike = true,
                topSpecies = listOf("007_00_05:0.435", "007_00:0.462")
            )
        )

        assertFalse(merged.hasCostume)
        assertFalse(merged.isShiny)
    }

    @Test
    fun speciesScopedCostumeShinyComboPromotesBothFlags() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            match = VariantPrototypeClassifier.MatchResult(
                species = "Blastoise",
                assetKey = "009_00_05_shiny",
                spriteKey = "009_00_05_shiny",
                variantType = "costume",
                isShiny = true,
                isCostumeLike = true,
                scope = "species",
                score = 0.402f,
                confidence = 0.52f,
                speciesMargin = 0.0f,
                variantMargin = 0.012f,
                bestBaseScore = 0.441f,
                bestNonBaseScore = 0.402f,
                bestNonBaseSpecies = "Blastoise",
                bestNonBaseAssetKey = "009_00_05_shiny",
                bestNonBaseSpriteKey = "009_00_05_shiny",
                bestNonBaseVariantType = "costume",
                bestNonBaseIsShiny = true,
                bestNonBaseIsCostumeLike = true,
                rescueKind = "exact_non_base_consensus",
                topSpecies = listOf("009_00_05_shiny:0.402", "009_00:0.441")
            )
        )

        assertTrue(merged.hasCostume)
        assertTrue(merged.isShiny)
    }

    @Test
    fun sameSpeciesShinyCostumeRescuePromotesBothFlags() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            match = VariantPrototypeClassifier.MatchResult(
                species = "Blastoise",
                assetKey = "009_00_05_shiny",
                spriteKey = "009_00_05_shiny",
                variantType = "costume",
                isShiny = true,
                isCostumeLike = true,
                scope = "species",
                score = 0.393f,
                confidence = 0.52f,
                speciesMargin = 0.0f,
                variantMargin = 0.001f,
                bestBaseScore = 0.392f,
                bestNonBaseScore = 0.393f,
                bestNonBaseSpecies = "Blastoise",
                bestNonBaseAssetKey = "009_00_05_shiny",
                bestNonBaseSpriteKey = "009_00_05_shiny",
                bestNonBaseVariantType = "costume",
                bestNonBaseIsShiny = true,
                bestNonBaseIsCostumeLike = true,
                rescueKind = "same_species_shiny_costume_rescue",
                topSpecies = listOf("009_00_05_shiny:0.393", "009_00_shiny:0.392", "009_00:0.450")
            )
        )

        assertTrue(merged.hasCostume)
        assertTrue(merged.isShiny)
    }

    @Test
    fun sameVariantShinyPeerPromotesShinyForVisualCostume() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(hasCostume = true),
            match = VariantPrototypeClassifier.MatchResult(
                species = "Squirtle",
                assetKey = "007_00_05",
                spriteKey = "007_00_05",
                variantType = "costume",
                isShiny = false,
                isCostumeLike = true,
                scope = "species",
                score = 0.382f,
                confidence = 0.52f,
                speciesMargin = 0.0f,
                variantMargin = 0.013f,
                bestBaseScore = 0.395f,
                bestNonBaseScore = 0.382f,
                bestNonBaseSpecies = "Squirtle",
                bestNonBaseAssetKey = "007_00_05",
                bestNonBaseSpriteKey = "007_00_05",
                bestNonBaseVariantType = "costume",
                bestNonBaseIsShiny = false,
                bestNonBaseIsCostumeLike = true,
                bestShinyPeerScore = 0.432f,
                bestShinyPeerAssetKey = "007_00_05_shiny",
                bestShinyPeerSpriteKey = "007_00_05_shiny",
                topSpecies = listOf("007_00_05:0.382", "007_00:0.395", "007_00_05_shiny:0.432")
            )
        )

        assertTrue(merged.hasCostume)
        assertTrue(merged.isShiny)
    }

    @Test
    fun closeShinyPeerDoesNotPromoteShinyForRegularCostume() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(hasCostume = true),
            match = VariantPrototypeClassifier.MatchResult(
                species = "Pikachu",
                assetKey = "025_00_12",
                spriteKey = "025_00_12",
                variantType = "costume",
                isShiny = false,
                isCostumeLike = true,
                scope = "species",
                score = 0.349f,
                confidence = 0.52f,
                speciesMargin = 0.0f,
                variantMargin = 0.004f,
                bestBaseScore = 0.360f,
                bestNonBaseScore = 0.349f,
                bestNonBaseSpecies = "Pikachu",
                bestNonBaseAssetKey = "025_00_12",
                bestNonBaseSpriteKey = "025_00_12",
                bestNonBaseVariantType = "costume",
                bestNonBaseIsShiny = false,
                bestNonBaseIsCostumeLike = true,
                bestShinyPeerScore = 0.364f,
                bestShinyPeerAssetKey = "025_00_12_shiny",
                bestShinyPeerSpriteKey = "025_00_12_shiny",
                topSpecies = listOf("025_00_12:0.349", "025_01_12:0.353", "025_00_12_shiny:0.364")
            )
        )

        assertTrue(merged.hasCostume)
        assertFalse(merged.isShiny)
    }

    @Test
    fun suppressedCostumeCanPromoteShinyFromBasePeer() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            match = VariantPrototypeClassifier.MatchResult(
                species = "Squirtle",
                assetKey = "007_00_05",
                spriteKey = "007_00_05",
                variantType = "costume",
                isShiny = false,
                isCostumeLike = true,
                scope = "species",
                score = 0.431f,
                confidence = 0.52f,
                speciesMargin = 0.0f,
                variantMargin = 0.010f,
                bestBaseScore = 0.441f,
                bestBaseAssetKey = "007_00",
                bestBaseSpriteKey = "007_00",
                bestNonBaseScore = 0.431f,
                bestNonBaseSpecies = "Squirtle",
                bestNonBaseAssetKey = "007_00_05",
                bestNonBaseSpriteKey = "007_00_05",
                bestNonBaseVariantType = "costume",
                bestNonBaseIsShiny = false,
                bestNonBaseIsCostumeLike = true,
                bestBaseShinyPeerScore = 0.477f,
                bestBaseShinyPeerAssetKey = "007_00_shiny",
                bestBaseShinyPeerSpriteKey = "007_00_shiny",
                topSpecies = listOf("007_00_05:0.431", "007_00:0.441", "007_00_shiny:0.477")
            )
        )

        assertFalse(merged.hasCostume)
        assertTrue(merged.isShiny)
    }

    @Test
    fun denseSpeciesCostumeNearTieStillPromotesCostume() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(hasCostume = true),
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

    // --- New edge case tests ---

    @Test
    fun nullMatchReturnsOriginalFeatures() {
        val original = VisualFeatures(isShiny = true, confidence = 0.9f)
        val merged = VariantMergeLogic.mergeVisualFeatures(original, null)
        assertTrue(merged.isShiny)
        assertTrue(merged.confidence == 0.9f)
    }

    @Test
    fun highConfidenceShinyWithNonBaseSetsShiny() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            match = VariantPrototypeClassifier.MatchResult(
                species = "Charizard",
                assetKey = "006_00_shiny",
                spriteKey = "006_00_shiny",
                variantType = "form",
                isShiny = true,
                isCostumeLike = false,
                scope = "global",
                score = 0.75f,
                confidence = 0.72f,
                speciesMargin = 0.1f,
                variantMargin = 0.05f,
                bestBaseScore = 0.60f,
                bestNonBaseScore = 0.75f,
                bestNonBaseSpecies = "Charizard",
                bestNonBaseAssetKey = "006_00_shiny",
                bestNonBaseSpriteKey = "006_00_shiny",
                bestNonBaseVariantType = "form",
                bestNonBaseIsShiny = true,
                bestNonBaseIsCostumeLike = false,
                topSpecies = listOf("006_00_shiny:0.75")
            )
        )

        assertTrue(merged.isShiny)
        assertTrue(merged.hasSpecialForm)
    }
    @Test
    fun costumeRescueDoesNotSuppressVisualShiny() {
        // Visual detector correctly sees shiny. Classifier sees "costume regular"
        // with low confidence that gets rescued. Shiny must NOT be suppressed.
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(isShiny = true, hasCostume = true, confidence = 0.7f),
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
                rescueKind = "family_costume_rescue",
                topSpecies = listOf("025_00_12:0.360")
            )
        )

        assertTrue("Costume should be promoted", merged.hasCostume)
        assertTrue("Visual shiny must NOT be suppressed by costume rescue", merged.isShiny)
    }
}
