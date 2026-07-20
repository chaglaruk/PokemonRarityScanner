package com.pokerarity.scanner

import com.pokerarity.scanner.util.ocr.ScanAuthorityLogic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanAuthorityLogicTest {

    @Test
    fun exactParsedSpeciesBlocksClassifierOverrideWithoutCandyCorroboration() {
        val blocked = ScanAuthorityLogic.shouldAcceptClassifierSpeciesOverride(
            currentSpecies = "Squirtle",
            parsedRawSpecies = "Squirtle",
            parsedFallbackSpecies = null,
            candyName = null,
            classifierSpecies = "Blastoise",
            classifierInCandyFamily = false
        )

        assertFalse(blocked)
    }

    @Test
    fun unknownSpeciesWithoutCandyBlocksClassifierOverride() {
        val allowed = ScanAuthorityLogic.shouldAcceptClassifierSpeciesOverride(
            currentSpecies = "Unknown",
            parsedRawSpecies = null,
            parsedFallbackSpecies = null,
            candyName = null,
            classifierSpecies = "Blastoise",
            classifierInCandyFamily = false
        )

        assertFalse(allowed)
    }

    @Test
    fun unknownSpeciesWithCandyFamilyAllowsClassifierOverride() {
        val allowed = ScanAuthorityLogic.shouldAcceptClassifierSpeciesOverride(
            currentSpecies = "Unknown",
            parsedRawSpecies = null,
            parsedFallbackSpecies = null,
            candyName = "Squirtle",
            classifierSpecies = "Blastoise",
            classifierInCandyFamily = true
        )

        assertFalse(allowed)
    }

    @Test
    fun candySpeciesBlocksCrossFamilyClassifierOverrideEvenWhenNameMissing() {
        val allowed = ScanAuthorityLogic.shouldAcceptClassifierSpeciesOverride(
            currentSpecies = "Snorlax",
            parsedRawSpecies = null,
            parsedFallbackSpecies = null,
            candyName = "Snorlax",
            classifierSpecies = "Minccino",
            classifierInCandyFamily = false
        )

        assertFalse(allowed)
    }

    @Test
    fun candyFamilyStillAllowsSameFamilyClassifierOverride() {
        val allowed = ScanAuthorityLogic.shouldAcceptClassifierSpeciesOverride(
            currentSpecies = "Pikachu",
            parsedRawSpecies = null,
            parsedFallbackSpecies = null,
            candyName = "Pikachu",
            classifierSpecies = "Raichu",
            classifierInCandyFamily = true
        )

        assertFalse(allowed)
    }

    @Test
    fun exactParsedSpeciesDoesNotBlockSameSpecies() {
        val allowed = ScanAuthorityLogic.shouldAcceptClassifierSpeciesOverride(
            currentSpecies = "Pikachu",
            parsedRawSpecies = "Pikachu",
            parsedFallbackSpecies = null,
            candyName = null,
            classifierSpecies = "Pikachu",
            classifierInCandyFamily = false
        )

        assertFalse(allowed)
    }

    @Test
    fun lockedOcrSpeciesBlocksSameFamilyScopedPassEvenWhenClassifierScoresBetter() {
        val preferred = ScanAuthorityLogic.shouldPreferClassifierSpeciesForScopedPass(
            currentSpecies = "Wartortle",
            parsedRawSpecies = "Wartortle",
            parsedFallbackSpecies = null,
            candyName = null,
            classifierSpecies = "Squirtle",
            classifierConfidence = 0.52f,
            classifierScore = 0.466f,
            currentSpeciesScore = 0.546f,
            sameFamilyWithCurrent = true
        )

        assertFalse(preferred)
    }

    @Test
    fun ambiguousSameFamilySpeciesCanStillDriveScopedPassWhenClassifierClearlyWins() {
        val preferred = ScanAuthorityLogic.shouldPreferClassifierSpeciesForScopedPass(
            currentSpecies = "Wartortle",
            parsedRawSpecies = null,
            parsedFallbackSpecies = null,
            candyName = null,
            classifierSpecies = "Squirtle",
            classifierConfidence = 0.46f,
            classifierScore = 0.420f,
            currentSpeciesScore = 0.546f,
            sameFamilyWithCurrent = true
        )

        assertFalse(preferred)
    }

    @Test
    fun ambiguousSameFamilySpeciesDoesNotDriveScopedPassWhenScoresAreTooClose() {
        val preferred = ScanAuthorityLogic.shouldPreferClassifierSpeciesForScopedPass(
            currentSpecies = "Wartortle",
            parsedRawSpecies = null,
            parsedFallbackSpecies = null,
            candyName = null,
            classifierSpecies = "Squirtle",
            classifierConfidence = 0.46f,
            classifierScore = 0.500f,
            currentSpeciesScore = 0.546f,
            sameFamilyWithCurrent = true
        )

        assertFalse(preferred)
    }

    @Test
    fun scopedPassRequiresClassifierConfidenceAtLeastMinimum() {
        val belowMinimum = ScanAuthorityLogic.shouldPreferClassifierSpeciesForScopedPass(
            currentSpecies = "Wartortle",
            parsedRawSpecies = null,
            parsedFallbackSpecies = null,
            candyName = null,
            classifierSpecies = "Squirtle",
            classifierConfidence = 0.399f,
            classifierScore = 0.466f,
            currentSpeciesScore = 0.546f,
            sameFamilyWithCurrent = true
        )
        val atMinimum = ScanAuthorityLogic.shouldPreferClassifierSpeciesForScopedPass(
            currentSpecies = "Wartortle",
            parsedRawSpecies = null,
            parsedFallbackSpecies = null,
            candyName = null,
            classifierSpecies = "Squirtle",
            classifierConfidence = 0.40f,
            classifierScore = 0.466f,
            currentSpeciesScore = 0.546f,
            sameFamilyWithCurrent = true
        )

        assertFalse(belowMinimum)
        assertFalse(atMinimum)
    }

    @Test
    fun scopedPassUsesInclusiveSameFamilyScoreMargin() {
        val exactlyAtMargin = ScanAuthorityLogic.shouldPreferClassifierSpeciesForScopedPass(
            currentSpecies = "Wartortle",
            parsedRawSpecies = null,
            parsedFallbackSpecies = null,
            candyName = null,
            classifierSpecies = "Squirtle",
            classifierConfidence = 0.40f,
            classifierScore = 0.466f,
            currentSpeciesScore = 0.546f,
            sameFamilyWithCurrent = true
        )
        val justOutsideMargin = ScanAuthorityLogic.shouldPreferClassifierSpeciesForScopedPass(
            currentSpecies = "Wartortle",
            parsedRawSpecies = null,
            parsedFallbackSpecies = null,
            candyName = null,
            classifierSpecies = "Squirtle",
            classifierConfidence = 0.40f,
            classifierScore = 0.467f,
            currentSpeciesScore = 0.546f,
            sameFamilyWithCurrent = true
        )

        assertFalse(exactlyAtMargin)
        assertFalse(justOutsideMargin)
    }

    @Test
    fun blockedFamilyDowngradeRejectsPikachuToPichuOverride() {
        val allowed = ScanAuthorityLogic.shouldAcceptClassifierSpeciesOverride(
            currentSpecies = "Pikachu",
            parsedRawSpecies = "Pikachu",
            parsedFallbackSpecies = null,
            candyName = "Pikachu",
            classifierSpecies = "Pichu",
            classifierInCandyFamily = true
        )

        assertFalse(allowed)
    }

    @Test
    fun blockedFamilyDowngradeRejectsEeveeEvolutionOverrides() {
        val eeveeEvolutions = listOf(
            "Flareon",
            "Vaporeon",
            "Jolteon",
            "Espeon",
            "Umbreon",
            "Leafeon",
            "Glaceon",
            "Sylveon"
        )

        eeveeEvolutions.forEach { evolution ->
            val allowed = ScanAuthorityLogic.shouldAcceptClassifierSpeciesOverride(
                currentSpecies = "Eevee",
                parsedRawSpecies = "Eevee",
                parsedFallbackSpecies = null,
                candyName = "Eevee",
                classifierSpecies = evolution,
                classifierInCandyFamily = true
            )

            assertFalse("$evolution should not override locked Eevee OCR", allowed)
        }
    }

    @Test
    fun lockedOcrSpeciesSkipsGlobalClassifierWork() {
        val shouldSkip = ScanAuthorityLogic.shouldSkipGlobalClassifierForLockedOcr(
            currentSpecies = "Espeon",
            parsedRawSpecies = "Espeon",
            parsedFallbackSpecies = null,
            candyName = null
        )

        assertTrue(shouldSkip)
    }

    @Test
    fun familyOnlyHintDoesNotSkipGlobalClassifierWork() {
        val shouldSkip = ScanAuthorityLogic.shouldSkipGlobalClassifierForLockedOcr(
            currentSpecies = "Espeon",
            parsedRawSpecies = null,
            parsedFallbackSpecies = null,
            candyName = "Eevee"
        )

        assertFalse(shouldSkip)
    }
}
