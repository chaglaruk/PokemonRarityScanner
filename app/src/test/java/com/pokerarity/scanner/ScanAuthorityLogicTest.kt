package com.pokerarity.scanner

import com.pokerarity.scanner.util.ocr.ScanAuthorityLogic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanAuthorityLogicTest {

    @Test
    fun rawOcrLockedSpeciesWithoutCandySkipsGlobalClassifierWork() {
        val shouldSkip = ScanAuthorityLogic.shouldSkipGlobalClassifierForLockedOcr(
            currentSpecies = "Espeon",
            parsedRawSpecies = "Espeon",
            parsedFallbackSpecies = null,
            candyName = null
        )

        assertTrue(shouldSkip)
    }

    @Test
    fun fallbackOcrLockedSpeciesWithoutCandySkipsGlobalClassifierWork() {
        val shouldSkip = ScanAuthorityLogic.shouldSkipGlobalClassifierForLockedOcr(
            currentSpecies = "Pikachu",
            parsedRawSpecies = null,
            parsedFallbackSpecies = "Pikachu",
            candyName = null
        )

        assertTrue(shouldSkip)
    }

    @Test
    fun unknownSpeciesDoesNotSkipGlobalClassifierWork() {
        val shouldSkip = ScanAuthorityLogic.shouldSkipGlobalClassifierForLockedOcr(
            currentSpecies = "Unknown",
            parsedRawSpecies = "Unknown",
            parsedFallbackSpecies = null,
            candyName = null
        )

        assertFalse(shouldSkip)
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

    @Test
    fun absentParsedAuthorityDoesNotSkipGlobalClassifierWork() {
        val shouldSkip = ScanAuthorityLogic.shouldSkipGlobalClassifierForLockedOcr(
            currentSpecies = "Wartortle",
            parsedRawSpecies = null,
            parsedFallbackSpecies = null,
            candyName = null
        )

        assertFalse(shouldSkip)
    }
}
