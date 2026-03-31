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
    fun sameFamilyClassifierCanDriveScopedPassWhenItClearlyBeatsLockedOcrSpecies() {
        val preferred = ScanAuthorityLogic.shouldPreferClassifierSpeciesForScopedPass(
            currentSpecies = "Wartortle",
            parsedRawSpecies = "Wartortle",
            parsedFallbackSpecies = null,
            candyName = null,
            classifierSpecies = "Squirtle",
            classifierConfidence = 0.31f,
            classifierScore = 0.466f,
            currentSpeciesScore = 0.546f,
            sameFamilyWithCurrent = true
        )

        assertTrue(preferred)
    }

    @Test
    fun sameFamilyClassifierDoesNotDriveScopedPassWhenScoresAreTooClose() {
        val preferred = ScanAuthorityLogic.shouldPreferClassifierSpeciesForScopedPass(
            currentSpecies = "Wartortle",
            parsedRawSpecies = "Wartortle",
            parsedFallbackSpecies = null,
            candyName = null,
            classifierSpecies = "Squirtle",
            classifierConfidence = 0.31f,
            classifierScore = 0.520f,
            currentSpeciesScore = 0.546f,
            sameFamilyWithCurrent = true
        )

        assertFalse(preferred)
    }
}
