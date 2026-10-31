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

    @Test
    fun chooseShinyDecision_weakPositivePrimaryEvaluatesSupportedColorFallback() {
        val recorder = VariantStageDiagnosticRecorder()
        val result = detector.chooseShinyResult(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 0.60f),
                matchedCount = 1,
                primaryMatched = true,
                primaryConfidence = 0.60f,
                chosenFromPrimary = true
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(true, 0.86f),
                raw = Pair(true, 0.82f),
                hue = Pair(true, 0.80f),
                histogramHue = Pair(false, 0f)
            ),
            pokemonName = "Cottonee",
            diagnostics = recorder
        )

        assertEquals(Pair(true, 0.86f), result)
        val report = recorder.snapshot(
            QaBuildIdentity("com.example.diag", "1", 1, "a".repeat(40)),
            QaMetadataRevision("test", "b".repeat(64), emptyList())
        )
        assertTrue(report.visualDetector.colorFallbackCalled == true)
        assertEquals(
            "color_fallback_positive_supported_by_two_signals_and_hue",
            report.visualDetector.colorFallbackReason
        )
        assertEquals("color_fallback", report.visualDetector.shinyDecisionPath)
    }

    @Test
    fun chooseShinyDecision_characterizesCorrelatedHueFallbackAndCounterfactuals() {
        val consensus = VisualFeatureDetector.SignatureConsensus(
            result = Pair(true, 0.8984769f),
            matchedCount = 2,
            primaryMatched = true,
            primaryConfidence = 0.8663592f,
            chosenFromPrimary = false,
            selectedSource = "alternate_masked_full"
        )
        val evidence = VisualFeatureDetector.ShinyColorEvidence(
            masked = Pair(false, 0f),
            raw = Pair(false, 0f),
            hue = Pair(true, 0.49833333f),
            histogramHue = Pair(true, 0.8f)
        )

        val current = detector.chooseShinyDecision(consensus, evidence, "Cottonee")
        assertEquals(Pair(true, 0.8f), current.result)
        assertEquals("color_fallback_positive_supported_by_two_signals_and_hue", current.colorFallbackReason)

        val groupedHue = detector.chooseShinyDecision(
            consensus,
            evidence.copy(hue = Pair(false, 0f)),
            "Cottonee"
        )
        assertEquals(Pair(false, 0f), groupedHue.result)
        assertEquals("color_fallback_histogram_only_below_0_90", groupedHue.colorFallbackReason)
        assertEquals("weak_signature_rejected_after_unsupported_color_fallback", groupedHue.selectedPath)

        val rgbOnly = detector.chooseShinyDecision(
            consensus,
            evidence.copy(hue = Pair(false, 0f), histogramHue = Pair(false, 0f)),
            "Cottonee"
        )
        assertEquals(Pair(false, 0f), rgbOnly.result)
        assertEquals("color_fallback_no_positive_color_signal", rgbOnly.colorFallbackReason)
    }

    @Test
    fun chooseShinyDecision_characterizesPixelNormalCrownCottoneeHueFallbackCounterfactual() {
        // Trace values came from the normal crown CP595/HP98 screen; no screenshot or OCR is embedded here.
        val consensus = VisualFeatureDetector.SignatureConsensus(
            result = Pair(true, 0.918954f),
            matchedCount = 2,
            primaryMatched = true,
            primaryConfidence = 0.8799572f,
            chosenFromPrimary = false,
            selectedSource = "alternate_masked_full"
        )
        val evidence = VisualFeatureDetector.ShinyColorEvidence(
            masked = Pair(false, 0f),
            raw = Pair(false, 0f),
            hue = Pair(true, 0.49166664f),
            histogramHue = Pair(true, 0.8f)
        )

        val current = detector.chooseShinyDecision(consensus, evidence, "Cottonee")
        assertEquals(Pair(true, 0.8f), current.result)
        assertEquals("color_fallback_positive_supported_by_two_signals_and_hue", current.colorFallbackReason)

        val groupedHue = detector.chooseShinyDecision(
            consensus,
            evidence.copy(hue = Pair(false, 0f)),
            "Cottonee"
        )
        assertEquals(Pair(false, 0f), groupedHue.result)
        assertEquals("color_fallback_histogram_only_below_0_90", groupedHue.colorFallbackReason)
        assertEquals("weak_signature_rejected_after_unsupported_color_fallback", groupedHue.selectedPath)
    }

    @Test
    fun chooseShinyDecision_preservesStrongPrimaryPositive() {
        val decision = detector.chooseShinyDecision(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 0.84f),
                matchedCount = 1,
                primaryMatched = true,
                primaryConfidence = 0.84f,
                chosenFromPrimary = true
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(false, 0f),
                raw = Pair(false, 0f),
                hue = Pair(false, 0f),
                histogramHue = Pair(false, 0f)
            ),
            pokemonName = "Cottonee"
        )

        assertEquals(Pair(true, 0.84f), decision.result)
        assertFalse(decision.colorFallbackCalled)
        assertEquals("skipped_strong_primary_signature_positive", decision.colorFallbackReason)
    }

    @Test
    fun chooseShinyDecision_rejectsWeakPositiveWhenColorFallbackHasNoSupport() {
        val decision = detector.chooseShinyDecision(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 0.60f),
                matchedCount = 1,
                primaryMatched = true,
                primaryConfidence = 0.60f,
                chosenFromPrimary = true
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(false, 0f),
                raw = Pair(false, 0f),
                hue = Pair(false, 0f),
                histogramHue = Pair(false, 0f)
            ),
            pokemonName = "Cottonee"
        )

        assertEquals(Pair(false, 0f), decision.result)
        assertTrue(decision.colorFallbackCalled)
        assertEquals("color_fallback_no_positive_color_signal", decision.colorFallbackReason)
        assertEquals("weak_signature_rejected_after_unsupported_color_fallback", decision.selectedPath)
    }

    @Test
    fun chooseShinyDecision_keepsUnsupportedAlternateOnlyNegative() {
        val consensus = detector.chooseBestShinySignatureResult(
            primaryResult = Pair(false, 0f),
            extraResults = listOf(Pair(true, 0.91f))
        )
        val decision = detector.chooseShinyDecision(
            signatureConsensus = consensus,
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(true, 1f),
                raw = Pair(true, 1f),
                hue = Pair(true, 1f),
                histogramHue = Pair(false, 0f)
            ),
            pokemonName = "Cottonee"
        )

        assertEquals(Pair(false, 0f), decision.result)
        assertTrue(decision.colorFallbackCalled)
        assertEquals("color_fallback_blocked_primary_signature_unmatched_or_below_0_55", decision.colorFallbackReason)
    }
}
