package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pokerarity.scanner.util.vision.CostumeSignatureStore
import com.pokerarity.scanner.util.vision.VisualFeatureDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualFeatureDetectorDecisionTest {

    private val detector = VisualFeatureDetector(ApplicationProvider.getApplicationContext())

    @Test
    fun denseVariantNearTieStillRunsCostumeHeuristic() {
        val details = CostumeSignatureStore.MatchDetails(
            matched = false,
            confidence = 0f,
            bestCostume = 0.23711109f,
            bestNormal = 0.25843185f,
            scoreGap = 0.02132076f,
            costumeCandidateCount = 40,
            denseVariantSpecies = true
        )

        assertTrue(detector.shouldUseCostumeHeuristic(details, "Pikachu"))
    }

    @Test
    fun denseVariantSmallNegativeGapStillRunsCostumeHeuristic() {
        val details = CostumeSignatureStore.MatchDetails(
            matched = false,
            confidence = 0f,
            bestCostume = 0.36963448f,
            bestNormal = 0.36333567f,
            scoreGap = -0.00629881f,
            costumeCandidateCount = 25,
            denseVariantSpecies = true
        )

        assertTrue(detector.shouldUseCostumeHeuristic(details, "Raichu"))
    }

    @Test
    fun highConfidenceAlternateOnlyConsensusWithoutSupportIsRejected() {
        val result = detector.chooseShinyResult(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 1.0f),
                matchedCount = 3,
                primaryMatched = false
            ),
            maskedColorResult = Pair(false, 0f),
            rawColorResult = Pair(false, 0f),
            hueResult = Pair(false, 0f),
            histHueResult = Pair(false, 0f),
            pokemonName = "Pikachu",
            costumeResult = Pair(false, 0f)
        )

        assertFalse(result.first)
    }

    @Test
    fun strongPrimarySignatureCanCarryShinyWithoutColorSupport() {
        val result = detector.chooseShinyResult(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 0.98f),
                matchedCount = 1,
                primaryMatched = true
            ),
            maskedColorResult = Pair(false, 0f),
            rawColorResult = Pair(false, 0f),
            hueResult = Pair(false, 0f),
            histHueResult = Pair(false, 0f),
            pokemonName = "Pikachu",
            costumeResult = Pair(false, 0f)
        )

        assertTrue(result.first)
    }

    @Test
    fun costumeSignalBlocksSignatureOnlyShinyConsensus() {
        val result = detector.chooseShinyResult(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 0.82f),
                matchedCount = 2,
                primaryMatched = false
            ),
            maskedColorResult = Pair(false, 0f),
            rawColorResult = Pair(false, 0f),
            hueResult = Pair(false, 0f),
            histHueResult = Pair(false, 0f),
            pokemonName = "Pikachu",
            costumeResult = Pair(true, 0.35f)
        )

        assertFalse(result.first)
    }
}
