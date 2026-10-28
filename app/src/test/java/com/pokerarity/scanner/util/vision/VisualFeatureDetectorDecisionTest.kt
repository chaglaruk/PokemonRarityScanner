// Purpose: Cover guardrails around visual costume fallback decisions.
package com.pokerarity.scanner.util.vision

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class VisualFeatureDetectorDecisionTest {

    private val detector = VisualFeatureDetector(ApplicationProvider.getApplicationContext())

    @Test
    fun shouldUseCostumeHeuristic_rejectsWeakDenseSignatureSupport() {
        val details = CostumeSignatureStore.MatchDetails(
            matched = false,
            confidence = 0f,
            bestCostume = 0.348f,
            bestNormal = 0.350f,
            scoreGap = 0.002f,
            costumeCandidateCount = 10,
            denseVariantSpecies = true
        )

        assertFalse(detector.shouldUseCostumeHeuristic(details, "Flareon"))
    }

    @Test
    fun shouldUseCostumeHeuristic_allowsStrongSignatureSupport() {
        val details = CostumeSignatureStore.MatchDetails(
            matched = false,
            confidence = 0f,
            bestCostume = 0.26f,
            bestNormal = 0.33f,
            scoreGap = 0.07f,
            costumeCandidateCount = 3,
            denseVariantSpecies = false
        )

        assertTrue(detector.shouldUseCostumeHeuristic(details, "Raichu"))
    }

    @Test
    fun chooseBestShinySignatureResult_rejectsOneUnsupportedAlternate() {
        val primary = Pair(false, 0f)

        val result = detector.chooseBestShinySignatureResult(
            primaryResult = primary,
            extraResults = listOf(Pair(true, 0.91f))
        )

        assertEquals(primary, result.result)
        assertEquals(1, result.matchedCount)
        assertFalse(result.primaryMatched)
        assertTrue(result.chosenFromPrimary)
    }

    @Test
    fun chooseBestShinySignatureResult_selectsStrongestCandidateAfterConsensus() {
        val strongest = Pair(true, 0.93f)

        val result = detector.chooseBestShinySignatureResult(
            primaryResult = Pair(false, 0f),
            extraResults = listOf(Pair(true, 0.82f), strongest)
        )

        assertEquals(strongest, result.result)
        assertEquals(2, result.matchedCount)
        assertTrue(result.primaryMatched.not())
        assertFalse(result.chosenFromPrimary)
    }

    @Test
    fun chooseShinyResult_keepsSignatureDecisionWhenColorFallbackHasNoSpecies() {
        val signature = Pair(false, 0.42f)
        val result = detector.chooseShinyResult(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = signature,
                matchedCount = 0,
                primaryMatched = false,
                primaryConfidence = 0.42f,
                chosenFromPrimary = true
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(true, 1f),
                raw = Pair(true, 1f),
                hue = Pair(true, 1f),
                histogramHue = Pair(true, 1f)
            ),
            pokemonName = null
        )

        assertEquals(signature, result)
    }
}
