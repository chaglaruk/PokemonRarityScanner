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
    fun sameSpeciesShinyCostumeRescueCanRestoreFlagsOnBaseFullMatch() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            fullMatch = FullVariantMatch(
                finalSpecies = "Hoothoot",
                finalSpriteKey = "163_00_shiny",
                resolvedVariantClass = "base",
                resolvedShiny = false,
                resolvedCostume = false,
                resolvedForm = false,
                variantConfidence = 0.44f,
                shinyConfidence = 0.44f,
                explanationMode = "generic_species_only"
            ),
            fallbackMatch = VariantPrototypeClassifier.MatchResult(
                species = "Hoothoot",
                assetKey = "163_00_JAN_2022_NOEVOLVE_shiny",
                spriteKey = "163_00_JAN_2022_NOEVOLVE_shiny",
                variantType = "costume",
                isShiny = true,
                isCostumeLike = true,
                scope = "species",
                score = 0.450f,
                confidence = 0.52f,
                speciesMargin = 0.0f,
                variantMargin = 0.0f,
                rescueKind = "same_species_shiny_costume_rescue",
                topSpecies = listOf("163_00_shiny:0.443", "163_00_JAN_2022_NOEVOLVE_shiny:0.450")
            )
        )

        assertTrue(merged.hasCostume)
        assertTrue(merged.isShiny)
    }

    @Test
    fun visualCostumeSupportLetsSameSpeciesFallbackRestoreShinyCostume() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(hasCostume = true, confidence = 0.61f),
            fullMatch = FullVariantMatch(
                finalSpecies = "Raichu",
                resolvedVariantClass = "base",
                resolvedShiny = false,
                resolvedCostume = false,
                resolvedForm = false,
                variantConfidence = 0.35f,
                shinyConfidence = 0.35f,
                explanationMode = "generic_species_only"
            ),
            fallbackMatch = VariantPrototypeClassifier.MatchResult(
                species = "Raichu",
                assetKey = "026_00_CANNIVERSARY_shiny",
                spriteKey = "026_00_CANNIVERSARY_shiny",
                variantType = "costume",
                isShiny = true,
                isCostumeLike = true,
                scope = "species",
                score = 0.379f,
                confidence = 0.441f,
                speciesMargin = 0.0f,
                variantMargin = 0.0f,
                topSpecies = listOf("026_00_CANNIVERSARY_shiny:0.379")
            )
        )

        assertTrue(merged.hasCostume)
        assertTrue(merged.isShiny)
    }

    @Test
    fun genericBaseFullMatchUsesModerateSameSpeciesCostumeFallback() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            fullMatch = FullVariantMatch(
                finalSpecies = "Absol",
                resolvedVariantClass = "base",
                resolvedShiny = false,
                resolvedCostume = false,
                resolvedForm = false,
                variantConfidence = 0.35f,
                explanationMode = "generic_species_only"
            ),
            fallbackMatch = VariantPrototypeClassifier.MatchResult(
                species = "Absol",
                assetKey = "359_00_CFALL_2022_NOEVOLVE",
                spriteKey = "359_00_CFALL_2022_NOEVOLVE",
                variantType = "costume",
                isShiny = false,
                isCostumeLike = true,
                scope = "species",
                score = 0.549f,
                confidence = 0.514f,
                speciesMargin = 0.0f,
                variantMargin = 0.0f,
                topSpecies = listOf("359_00_CFALL_2022_NOEVOLVE:0.549")
            )
        )

        assertTrue(merged.hasCostume)
        assertFalse(merged.isShiny)
    }

    @Test
    fun strongSameSpeciesFormFallbackRestoresGenericFormMatch() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            fullMatch = FullVariantMatch(
                finalSpecies = "Butterfree",
                resolvedVariantClass = "form",
                resolvedShiny = false,
                resolvedCostume = false,
                resolvedForm = true,
                variantConfidence = 0.44f,
                shinyConfidence = 0.44f,
                explanationMode = "generic_variant"
            ),
            fallbackMatch = VariantPrototypeClassifier.MatchResult(
                species = "Butterfree",
                assetKey = "012_00_FGIGANTAMAX_shiny",
                spriteKey = "012_00_FGIGANTAMAX_shiny",
                variantType = "form",
                isShiny = true,
                isCostumeLike = false,
                scope = "global",
                score = 0.441f,
                confidence = 0.592f,
                speciesMargin = 0.0f,
                variantMargin = 0.0f,
                topSpecies = listOf("Butterfree:0.441")
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

    @Test
    fun genericFullMatchUsesSameSpeciesBaseShinyFallback() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            fullMatch = FullVariantMatch(
                finalSpecies = "Rowlet",
                resolvedVariantClass = "base",
                resolvedShiny = false,
                resolvedCostume = false,
                explanationMode = "generic_species_only"
            ),
            fallbackMatch = VariantPrototypeClassifier.MatchResult(
                species = "Rowlet",
                assetKey = "722_00_shiny",
                spriteKey = "722_00_shiny",
                variantType = "base",
                isShiny = true,
                isCostumeLike = false,
                scope = "species",
                score = 0.529f,
                confidence = 0.544f,
                speciesMargin = 0.0f,
                variantMargin = 0.085f,
                topSpecies = listOf("722_00_shiny:0.529", "722_00:0.614")
            )
        )

        assertTrue("Same-species base shiny fallback should mark shiny", merged.isShiny)
        assertFalse(merged.hasCostume)
    }

    @Test
    fun genericFullMatchUsesSameSpeciesShinyCostumeFallback() {
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

        assertTrue("Same-species shiny costume fallback should mark shiny", merged.isShiny)
        assertTrue("Same-species shiny costume fallback should mark costume", merged.hasCostume)
    }

    @Test
    fun genericFullMatchUsesSameSpeciesCostumeThatBeatsBase() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            fullMatch = FullVariantMatch(
                finalSpecies = "Dedenne",
                resolvedVariantClass = "base",
                resolvedShiny = false,
                resolvedCostume = false,
                explanationMode = "generic_species_only"
            ),
            fallbackMatch = VariantPrototypeClassifier.MatchResult(
                species = "Dedenne",
                assetKey = "702_00_WINTER_2024",
                spriteKey = "702_00_WINTER_2024",
                variantType = "costume",
                isShiny = false,
                isCostumeLike = true,
                scope = "species",
                score = 0.505f,
                confidence = 0.421f,
                speciesMargin = 0.0f,
                variantMargin = 0.030f,
                bestBaseScore = 0.542f,
                bestNonBaseScore = 0.505f,
                bestNonBaseVariantType = "costume",
                bestNonBaseIsCostumeLike = true,
                topSpecies = listOf("702_00_WINTER_2024:0.505", "702_00_WINTER_2024_shiny:0.535", "702_00:0.542")
            )
        )

        assertTrue("Costume fallback that beats base should mark costume", merged.hasCostume)
        assertFalse(merged.isShiny)
    }

    @Test
    fun genericFullMatchUsesSameSpeciesShinyFormFallback() {
        val merged = VariantMergeLogic.mergeVisualFeatures(
            visualFeatures = VisualFeatures(),
            fullMatch = FullVariantMatch(
                finalSpecies = "Butterfree",
                resolvedVariantClass = "base",
                resolvedShiny = false,
                resolvedCostume = false,
                resolvedForm = false,
                explanationMode = "generic_species_only"
            ),
            fallbackMatch = VariantPrototypeClassifier.MatchResult(
                species = "Butterfree",
                assetKey = "012_00_FGIGANTAMAX_shiny",
                spriteKey = "012_00_FGIGANTAMAX_shiny",
                variantType = "form",
                isShiny = true,
                isCostumeLike = false,
                scope = "species",
                score = 0.461f,
                confidence = 0.465f,
                speciesMargin = 0.0f,
                variantMargin = 0.035f,
                topSpecies = listOf("012_00_FGIGANTAMAX_shiny:0.461", "012_00_FGIGANTAMAX:0.496")
            )
        )

        assertTrue("Same-species shiny form fallback should mark shiny", merged.isShiny)
        assertTrue("Same-species shiny form fallback should mark form", merged.hasSpecialForm)
    }
}
