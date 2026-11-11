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
class VisualFeatureDetectorBodyColorConflictTest {
    private val detector = VisualFeatureDetector(ApplicationProvider.getApplicationContext())

    @Test
    fun resolveShinyResult_weakBodyColorCannotOverrideStrongNormalOpposingView() {
        listOf(
            VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(true, 0.01f),
                raw = Pair(false, 0f),
                hue = Pair(false, 0f),
                histogramHue = Pair(false, 0f)
            ),
            VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(false, 0f),
                raw = Pair(true, 0.10f),
                hue = Pair(false, 0f),
                histogramHue = Pair(false, 0f)
            )
        ).forEach { weakBodyColor ->
            val result = detector.resolveShinyResult(
                signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                    result = Pair(true, 0.98f),
                    matchedCount = 2,
                    primaryMatched = true,
                    primaryConfidence = 0.98f,
                    chosenFromPrimary = true
                ),
                colorEvidence = weakBodyColor,
                pokemonName = "Pikachu",
                signatureCandidates = cottoneeLikeOpposingViews()
            )
            assertEquals(Pair(false, 0f), result.result)
            assertTrue(result.colorFallbackCalled)
            assertEquals("normal_opposing_signature_view", result.colorFallbackReason)
            assertFalse(
                detector.hasStrongShinySignatureEvidence(
                    VisualFeatureDetector.SignatureConsensus(
                        result = Pair(true, 0.98f),
                        matchedCount = 2,
                        primaryMatched = true,
                        primaryConfidence = 0.98f,
                        chosenFromPrimary = true
                    ),
                    cottoneeLikeOpposingViews(),
                    finalShiny = true,
                    independentBodyColorSupport = false
                )
            )
        }
    }

    @Test
    fun resolveShinyResult_preservesConfidentRawBodyColorAgainstNormalOpposingView() {
        val result = detector.resolveShinyResult(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 0.98f),
                matchedCount = 2,
                primaryMatched = true,
                primaryConfidence = 0.98f,
                chosenFromPrimary = true
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(false, 0f),
                raw = Pair(true, 0.84f),
                hue = Pair(false, 0f),
                histogramHue = Pair(false, 0f)
            ),
            pokemonName = "Pikachu",
            signatureCandidates = cottoneeLikeOpposingViews()
        )
        assertEquals(Pair(true, 0.98f), result.result)
        assertFalse(result.colorFallbackCalled)
        assertEquals("strong_primary_signature_preserved", result.colorFallbackReason)
    }


    private fun signatureCandidate(
        scoreGap: Float,
        matched: Boolean = false,
        colorGap: Float? = null,
        confidence: Float = 0f
    ) = ShinySignatureStore.SignatureMatchDetails(
        referenceAvailable = true,
        matched = matched,
        confidence = confidence,
        normalScore = null,
        shinyScore = null,
        colorGap = colorGap,
        scoreGap = scoreGap,
        rejectionReason = if (matched) "signature_threshold_match" else "shiny_reference_not_closer"
    )

    private fun cottoneeLikeOpposingViews() = listOf(
        signatureCandidate(0.1928f, matched = true, colorGap = 0.3489f, confidence = 0.99f),
        signatureCandidate(0.1036f, matched = true, colorGap = 0.1917f, confidence = 0.83f),
        signatureCandidate(-0.1729f, colorGap = -0.2692f)
    )
}
