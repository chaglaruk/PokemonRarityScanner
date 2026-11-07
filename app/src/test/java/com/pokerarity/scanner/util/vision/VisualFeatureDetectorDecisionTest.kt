// Purpose: Cover guardrails around visual costume fallback decisions.
package com.pokerarity.scanner.util.vision

import androidx.test.core.app.ApplicationProvider
import android.graphics.Bitmap
import android.graphics.Color
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

    @Test
    fun resolveShinyResult_reportsSupportedColorFallbackAfterWeakPrimarySignature() {
        val result = detector.resolveShinyResult(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 0.62f),
                matchedCount = 1,
                primaryMatched = true,
                primaryConfidence = 0.62f,
                chosenFromPrimary = true
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(true, 0.82f),
                raw = Pair(false, 0f),
                hue = Pair(true, 0.81f),
                histogramHue = Pair(false, 0f)
            ),
            pokemonName = "Eevee"
        )

        assertEquals(Pair(true, 0.82f), result.result)
        assertTrue(result.colorFallbackCalled)
        assertEquals("supported_color_fallback", result.colorFallbackReason)
    }

    @Test
    fun resolveShinyResult_preservesStrongPrimarySignatureWithoutColorFallback() {
        val result = detector.resolveShinyResult(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 0.98f),
                matchedCount = 1,
                primaryMatched = true,
                primaryConfidence = 0.98f,
                chosenFromPrimary = true
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(false, 0f),
                raw = Pair(false, 0f),
                hue = Pair(false, 0f),
                histogramHue = Pair(false, 0f)
            ),
            pokemonName = "Pikachu"
        )

        assertEquals(Pair(true, 0.98f), result.result)
        assertFalse(result.colorFallbackCalled)
        assertEquals("strong_primary_signature_preserved", result.colorFallbackReason)
    }

    @Test
    fun detectNeutralShinyEvidence_acceptsAchromaticShinyPixelsAgainstChromaticNormalReference() {
        val sprite = syntheticSprite(Color.rgb(160, 160, 144))
        val evidence = detector.detectNeutralShinyEvidence(
            sprite,
            VisualFeatureDetector.ColorReference(
                normal = listOf(208, 80, 64),
                shiny = listOf(160, 160, 144)
            )
        )

        assertTrue(evidence.matched)
        assertTrue(evidence.confidence >= 0.78f)
        assertTrue(evidence.pixelRatio >= 0.30f)
    }

    @Test
    fun detectNeutralShinyEvidence_rejectsChromaticNormalBodyPixels() {
        val sprite = syntheticSprite(Color.rgb(208, 80, 64))
        val evidence = detector.detectNeutralShinyEvidence(
            sprite,
            VisualFeatureDetector.ColorReference(
                normal = listOf(208, 80, 64),
                shiny = listOf(160, 160, 144)
            )
        )

        assertFalse(evidence.matched)
        assertEquals(0f, evidence.confidence)
    }

    @Test
    fun resolveShinyResult_usesNeutralColorOnlyWhenSignatureCandidatesAreAmbiguous() {
        val evidence = VisualFeatureDetector.NeutralShinyColorEvidence(
            matched = true,
            confidence = 0.82f,
            pixelRatio = 0.49f
        )
        val result = detector.resolveShinyResult(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(false, 0f),
                matchedCount = 0,
                primaryMatched = false,
                primaryConfidence = 0f,
                chosenFromPrimary = true
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(false, 0f),
                raw = Pair(false, 0f),
                hue = Pair(false, 0f),
                histogramHue = Pair(false, 0f),
                neutral = evidence
            ),
            pokemonName = "Slugma",
            signatureCandidates = listOf(
                signatureCandidate(-0.020f),
                signatureCandidate(-0.012f),
                signatureCandidate(0.009f)
            )
        )

        assertEquals(Pair(true, 0.82f), result.result)
        assertEquals("neutral_color_ambiguous_signature", result.colorFallbackReason)
        assertTrue(result.neutralFallbackApplied)
    }

    @Test
    fun resolveShinyResult_doesNotOverrideClearNormalSignatureEvidence() {
        val result = detector.resolveShinyResult(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(false, 0f),
                matchedCount = 0,
                primaryMatched = false,
                primaryConfidence = 0f,
                chosenFromPrimary = true
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(false, 0f),
                raw = Pair(false, 0f),
                hue = Pair(false, 0f),
                histogramHue = Pair(false, 0f),
                neutral = VisualFeatureDetector.NeutralShinyColorEvidence(
                    matched = true,
                    confidence = 0.82f,
                    pixelRatio = 0.49f
                )
            ),
            pokemonName = "Slugma",
            signatureCandidates = listOf(
                signatureCandidate(-0.081f),
                signatureCandidate(-0.012f),
                signatureCandidate(0.009f)
            )
        )

        assertEquals(Pair(false, 0f), result.result)
        assertFalse(result.neutralFallbackApplied)
    }

    @Test
    fun resolveShinyResult_acceptsThreeStrongShinySignatureViewsWhenPrimaryIsWeak() {
        val candidates = listOf(
            measuredSignatureCandidate(0.3559f, 0.6118f, 0.4693f, 0.6920f, 0.3361f),
            measuredSignatureCandidate(0.3383f, 0.5825f, 0.5436f, 0.6273f, 0.2890f),
            measuredSignatureCandidate(0.2533f, 0.4408f, 0.4708f, 0.5884f, 0.3351f)
        )
        val result = detector.resolveShinyResult(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 0.5436f),
                matchedCount = 3,
                primaryMatched = true,
                primaryConfidence = 0.4693f,
                chosenFromPrimary = false
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(false, 0f),
                raw = Pair(false, 0f),
                hue = Pair(true, 0.4983f),
                histogramHue = Pair(true, 0.8f)
            ),
            pokemonName = "Cottonee",
            signatureCandidates = candidates
        )

        assertTrue(result.result.first)
        assertTrue(result.result.second >= 0.78f)
    }

    @Test
    fun resolveShinyResult_acceptsNeutralShinyWhenAllSignaturesSlightlyFavorNormal() {
        val result = detector.resolveShinyResult(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(false, 0f),
                matchedCount = 0,
                primaryMatched = false,
                primaryConfidence = 0f,
                chosenFromPrimary = true
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(false, 0f),
                raw = Pair(false, 0f),
                hue = Pair(false, 0f),
                histogramHue = Pair(false, 0f),
                neutral = VisualFeatureDetector.NeutralShinyColorEvidence(
                    matched = true,
                    confidence = 0.814f,
                    pixelRatio = 0.49f
                )
            ),
            pokemonName = "Slugma",
            signatureCandidates = listOf(
                signatureCandidate(-0.0755f),
                signatureCandidate(-0.0583f),
                signatureCandidate(-0.0390f)
            )
        )

        assertEquals(Pair(true, 0.814f), result.result)
        assertTrue(result.neutralFallbackApplied)
    }

    @Test
    fun hasUpperHeadRedAccessoryCue_acceptsMeasuredScreenSpaceCrownWithoutDarkPixels() {
        val bitmap = syntheticScreenWithBrightCrown()

        assertTrue(detector.hasUpperHeadRedAccessoryCue(bitmap))

        bitmap.recycle()
    }

    @Test
    fun hasUpperHeadRedAccessoryCue_rejectsRedOutsideUpperHeadAndSmallGlints() {
        val bitmap = Bitmap.createBitmap(360, 780, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(80, 150, 90))
        fillRect(bitmap, 12, 96, 27, 111, Color.rgb(235, 74, 42))
        fillRect(bitmap, 150, 400, 165, 415, Color.rgb(235, 74, 42))
        fillRect(bitmap, 150, 96, 156, 102, Color.rgb(235, 74, 42))

        assertFalse(detector.hasUpperHeadRedAccessoryCue(bitmap))

        bitmap.recycle()
    }

    @Test
    fun shouldUseUpperHeadAccessoryRescue_allowsNearCostumeReferenceWithCrownCue() {
        val details = CostumeSignatureStore.MatchDetails(
            matched = false,
            confidence = 0f,
            bestCostume = 0.3637f,
            bestNormal = 0.3451f,
            scoreGap = -0.0186f,
            costumeCandidateCount = 2,
            denseVariantSpecies = false
        )

        assertTrue(detector.shouldUseUpperHeadAccessoryRescue(details))
    }

    @Test
    fun shouldUseUpperHeadAccessoryRescue_rejectsCostumeFreeNearMissAndDenseSpecies() {
        val costumeFreeNearMiss = CostumeSignatureStore.MatchDetails(
            matched = false,
            confidence = 0f,
            bestCostume = 0.3534f,
            bestNormal = 0.3196f,
            scoreGap = -0.0339f,
            costumeCandidateCount = 2,
            denseVariantSpecies = false
        )
        val denseSpecies = costumeFreeNearMiss.copy(
            bestCostume = 0.30f,
            scoreGap = 0f,
            denseVariantSpecies = true
        )

        assertFalse(detector.shouldUseUpperHeadAccessoryRescue(costumeFreeNearMiss))
        assertFalse(detector.shouldUseUpperHeadAccessoryRescue(denseSpecies))
    }

    @Test
    fun resolveShinyResult_rejectsAccessoryContaminatedTwoOfThreeSignatureViews() {
        val result = detector.resolveShinyResult(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 1f),
                matchedCount = 2,
                primaryMatched = true,
                primaryConfidence = 1f,
                chosenFromPrimary = true
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(false, 0f),
                raw = Pair(false, 0f),
                hue = Pair(true, 0.4917f),
                histogramHue = Pair(true, 0.8f)
            ),
            pokemonName = "Cottonee",
            signatureCandidates = listOf(
                signatureCandidate(0.2019f, matched = true, colorGap = 0.3558f, confidence = 1f),
                signatureCandidate(0.1190f, matched = true, colorGap = 0.2176f, confidence = 0.8785f),
                signatureCandidate(-0.1919f, colorGap = -0.3005f)
            ),
            costumeAccessoryCue = true
        )

        assertEquals(Pair(false, 0f), result.result)
        assertEquals("costume_accessory_signature_conflict", result.colorFallbackReason)
        assertFalse(result.neutralFallbackApplied)
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

    private fun measuredSignatureCandidate(
        scoreGap: Float,
        colorGap: Float,
        confidence: Float,
        normalScore: Float,
        shinyScore: Float
    ) = ShinySignatureStore.SignatureMatchDetails(
        referenceAvailable = true,
        matched = true,
        confidence = confidence,
        normalScore = normalScore,
        shinyScore = shinyScore,
        colorGap = colorGap,
        scoreGap = scoreGap,
        rejectionReason = "signature_threshold_match"
    )

    private fun syntheticScreenWithBrightCrown(): Bitmap {
        val bitmap = Bitmap.createBitmap(360, 780, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(80, 150, 90))
        fillRect(bitmap, 150, 96, 165, 111, Color.rgb(235, 74, 42))
        return bitmap
    }

    private fun fillRect(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        for (y in top until bottom) {
            for (x in left until right) {
                bitmap.setPixel(x, y, color)
            }
        }
    }

    private fun syntheticSprite(bodyColor: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(160 * 160) { Color.BLACK }
        for (y in 20 until 156) {
            for (x in 26 until 134) {
                val normalizedX = (x - 80f) / 54f
                val normalizedY = (y - 90f) / 70f
                if (normalizedX * normalizedX + normalizedY * normalizedY <= 1f) {
                    pixels[y * 160 + x] = bodyColor
                }
            }
        }
        bitmap.setPixels(pixels, 0, 160, 0, 0, 160, 160)
        return bitmap
    }
}
