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
    fun unknownSpeciesAllowsClassifierOverride() {
        val allowed = ScanAuthorityLogic.shouldAcceptClassifierSpeciesOverride(
            currentSpecies = "Unknown",
            parsedRawSpecies = null,
            parsedFallbackSpecies = null,
            candyName = null,
            classifierSpecies = "Blastoise",
            classifierInCandyFamily = false
        )

        assertTrue(allowed)
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

        assertTrue(allowed)
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

        assertTrue(preferred)
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
