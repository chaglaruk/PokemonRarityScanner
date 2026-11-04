// Purpose: Cover guardrails around visual costume fallback decisions.
package com.pokerarity.scanner.util.vision

import android.graphics.Bitmap
import android.graphics.Color
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
    fun cottoneeFlowerCrownCue_requiresLockedSpeciesCandidateAndRedHeadCluster() {
        val sprite = redFlowerSprite()
        try {
            val result = CostumeAccessoryCue.evaluate(
                lockedSpecies = "Cottonee",
                detectorInputSpecies = "Cottonee",
                details = flowerCrownDetails(),
                spriteCrop = sprite
            )

            assertTrue(result.accepted)
            assertEquals("cottonee_spring_2024_red_flower_cluster_supported", result.reasonCode)
            assertEquals(9, result.redPixelCount)
            assertEquals(9, result.largestRedClusterSize)
            assertTrue(result.redPixelRatio >= 0.025f)
        } finally {
            sprite.recycle()
        }
    }

    @Test
    fun cottoneeFlowerCrownCue_rejectsSpeciesAndNonCrownCandidate() {
        val sprite = redFlowerSprite()
        try {
            val otherLockedSpecies = CostumeAccessoryCue.evaluate(
                "Latios", "Latios", flowerCrownDetails(), sprite
            )
            val detectorSpeciesMismatch = CostumeAccessoryCue.evaluate(
                "Cottonee", "Latios", flowerCrownDetails(), sprite
            )
            val otherCostumeCandidate = CostumeAccessoryCue.evaluate(
                "Cottonee", "Cottonee", flowerCrownDetails("546_00_shiny"), sprite
            )

            assertFalse(otherLockedSpecies.accepted)
            assertFalse(detectorSpeciesMismatch.accepted)
            assertFalse(otherCostumeCandidate.accepted)
        } finally {
            sprite.recycle()
        }
    }

    @Test
    fun cottoneeFlowerCrownCue_rejectsHeadWithoutFlowerCluster() {
        val noFlower = Bitmap.createBitmap(90, 90, Bitmap.Config.ARGB_8888)
        try {
            val result = CostumeAccessoryCue.evaluate(
                "Cottonee", "Cottonee", flowerCrownDetails(), noFlower
            )

            assertFalse(result.accepted)
            assertEquals(0, result.largestRedClusterSize)
        } finally {
            noFlower.recycle()
        }
    }

    @Test
    fun cottoneeFlowerCrownCue_rejectsScatteredRedWithoutCluster() {
        val scatteredRed = Bitmap.createBitmap(90, 90, Bitmap.Config.ARGB_8888)
        try {
            for (y in listOf(21, 27)) {
                for (x in listOf(25, 31, 37, 43)) {
                    scatteredRed.setPixel(x, y, Color.RED)
                }
            }
            val scattered = CostumeAccessoryCue.evaluate(
                "Cottonee", "Cottonee", flowerCrownDetails(), scatteredRed
            )

            assertEquals(8, scattered.redPixelCount)
            assertEquals(1, scattered.largestRedClusterSize)
            assertFalse(scattered.accepted)
        } finally {
            scatteredRed.recycle()
        }
    }

    private fun flowerCrownDetails(bestKey: String = "546_00_SPRING_2024") =
        CostumeSignatureStore.MatchDetails(
            matched = false,
            confidence = 0f,
            bestCostume = 0.41547716f,
            bestNormal = 0.3379258f,
            scoreGap = -0.077551365f,
            costumeCandidateCount = 2,
            denseVariantSpecies = false,
            bestCostumeKey = bestKey,
            bestNormalKey = "546_00_shiny",
            candidateCount = 4,
            rejectionReason = "normal_candidate_not_beaten_by_margin"
        )

    private fun redFlowerSprite(): Bitmap = Bitmap.createBitmap(90, 90, Bitmap.Config.ARGB_8888).apply {
        // The production cue samples from (25,21) in steps of three within this normalized head region.
        for (y in listOf(21, 24, 27)) {
            for (x in listOf(25, 28, 31)) setPixel(x, y, Color.RED)
        }
    }

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
    fun chooseBestShinySignatureResult_preservesReasonForSelectedAlternate() {
        val result = detector.chooseBestShinySignatureResult(
            primaryResult = Pair(true, 0.8799572f),
            extraResults = listOf(Pair(true, 0.918954f), Pair(false, 0f)),
            primaryReasonCode = "relative_score_and_color_gaps_passed",
            extraReasonCodes = listOf("relative_score_and_color_gaps_passed", "normal_signature_score_wins")
        )

        assertTrue(result.chosenFromPrimary.not())
        assertEquals("relative_score_and_color_gaps_passed", result.primaryReasonCode)
        assertEquals("relative_score_and_color_gaps_passed", result.selectedReasonCode)
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
            "color_fallback_positive_supported_by_rgb_and_hue_groups",
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
            selectedSource = "alternate_masked_full",
            primaryReasonCode = "relative_score_and_color_gaps_passed",
            selectedReasonCode = "relative_score_and_color_gaps_passed"
        )
        val evidence = VisualFeatureDetector.ShinyColorEvidence(
            masked = Pair(false, 0f),
            raw = Pair(false, 0f),
            hue = Pair(true, 0.49833333f),
            histogramHue = Pair(true, 0.8f)
        )

        val current = detector.chooseShinyDecision(consensus, evidence, "Cottonee")
        assertEquals(Pair(false, 0f), current.result)
        assertEquals("color_fallback_missing_independent_rgb_support", current.colorFallbackReason)
        assertEquals("weak_signature_rejected_after_unsupported_color_fallback", current.selectedPath)

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
            selectedSource = "alternate_masked_full",
            primaryReasonCode = "relative_score_and_color_gaps_passed",
            selectedReasonCode = "relative_score_and_color_gaps_passed"
        )
        val evidence = VisualFeatureDetector.ShinyColorEvidence(
            masked = Pair(false, 0f),
            raw = Pair(false, 0f),
            hue = Pair(true, 0.49166664f),
            histogramHue = Pair(true, 0.8f)
        )

        val current = detector.chooseShinyDecision(consensus, evidence, "Cottonee")
        assertEquals(Pair(false, 0f), current.result)
        assertEquals("color_fallback_missing_independent_rgb_support", current.colorFallbackReason)
        assertEquals("weak_signature_rejected_after_unsupported_color_fallback", current.selectedPath)

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
                result = Pair(true, 0.57082677f),
                matchedCount = 1,
                primaryMatched = true,
                primaryConfidence = 0.57082677f,
                chosenFromPrimary = true,
                primaryReasonCode = "absolute_score_passed",
                selectedReasonCode = "absolute_score_passed"
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(false, 0f),
                raw = Pair(false, 0f),
                hue = Pair(true, 0.49833333f),
                histogramHue = Pair(true, 0.8f)
            ),
            pokemonName = "Cottonee"
        )

        assertEquals(Pair(true, 0.57082677f), decision.result)
        assertFalse(decision.colorFallbackCalled)
        assertEquals("skipped_strong_primary_signature_positive", decision.colorFallbackReason)
    }

    @Test
    fun chooseShinyDecision_preservesAbsolutePrimaryWhenHigherConfidenceAlternateWinsConsensus() {
        val decision = detector.chooseShinyDecision(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 0.65f),
                matchedCount = 3,
                primaryMatched = true,
                primaryConfidence = 0.5372751f,
                chosenFromPrimary = false,
                selectedSource = "alternate_upper",
                primaryReasonCode = "absolute_score_passed",
                selectedReasonCode = "weak_relative_fallback_passed"
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(false, 0f),
                raw = Pair(false, 0f),
                hue = Pair(true, 0.505f),
                histogramHue = Pair(true, 0.8f)
            ),
            pokemonName = "Cottonee"
        )

        assertEquals(Pair(true, 0.5372751f), decision.result)
        assertFalse(decision.colorFallbackCalled)
        assertEquals("skipped_strong_primary_signature_positive", decision.colorFallbackReason)
        assertEquals("strong_primary_signature", decision.selectedPath)
    }

    @Test
    fun chooseShinyDecision_highConfidenceRelativePrimaryCannotBypassCorrelatedHueEvidence() {
        val decision = detector.chooseShinyDecision(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 0.8984769f),
                matchedCount = 2,
                primaryMatched = true,
                primaryConfidence = 0.8663592f,
                chosenFromPrimary = false,
                selectedSource = "alternate_masked_full",
                primaryReasonCode = "relative_score_and_color_gaps_passed",
                selectedReasonCode = "relative_score_and_color_gaps_passed"
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(false, 0f),
                raw = Pair(false, 0f),
                hue = Pair(true, 0.49166664f),
                histogramHue = Pair(true, 0.8f)
            ),
            pokemonName = "Cottonee"
        )

        assertEquals(Pair(false, 0f), decision.result)
        assertTrue(decision.colorFallbackCalled)
        assertEquals("color_fallback_missing_independent_rgb_support", decision.colorFallbackReason)
        assertEquals("weak_signature_rejected_after_unsupported_color_fallback", decision.selectedPath)
    }

    @Test
    fun chooseShinyDecision_rejectsNormalCrownRelativeMatchForEitherSelectedCrop() {
        // Same reported normal-crown evidence, with only the selected crop changing.
        val evidence = VisualFeatureDetector.ShinyColorEvidence(
            masked = Pair(false, 0f),
            raw = Pair(false, 0f),
            hue = Pair(true, 0.49166664f),
            histogramHue = Pair(true, 0.8f)
        )
        val relativeReason = "relative_score_and_color_gaps_passed"
        val cases = listOf(
            "primary" to VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 0.8799572f),
                matchedCount = 2,
                primaryMatched = true,
                primaryConfidence = 0.8799572f,
                chosenFromPrimary = true,
                selectedSource = "primary",
                primaryReasonCode = relativeReason,
                selectedReasonCode = relativeReason
            ),
            "alternate_masked_full" to VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 0.918954f),
                matchedCount = 2,
                primaryMatched = true,
                primaryConfidence = 0.8799572f,
                chosenFromPrimary = false,
                selectedSource = "alternate_masked_full",
                primaryReasonCode = relativeReason,
                selectedReasonCode = relativeReason
            )
        )

        cases.forEach { (source, consensus) ->
            val decision = detector.chooseShinyDecision(consensus, evidence, "Cottonee")

            assertEquals("$source must not turn correlated hue into Shiny TRUE", Pair(false, 0f), decision.result)
            assertTrue("$source must evaluate the color fallback", decision.colorFallbackCalled)
            assertEquals("color_fallback_missing_independent_rgb_support", decision.colorFallbackReason)
            assertEquals("weak_signature_rejected_after_unsupported_color_fallback", decision.selectedPath)
        }
    }

    @Test
    fun chooseShinyDecision_preservesRealShinyCottoneeAbsolutePrimaryWithUpperAlternateSelected() {
        // Values are from the live diag11 Shiny Cottonee CP193/HP54 trace; no frame is embedded here.
        val decision = detector.chooseShinyDecision(
            signatureConsensus = VisualFeatureDetector.SignatureConsensus(
                result = Pair(true, 0.5889965f),
                matchedCount = 3,
                primaryMatched = true,
                primaryConfidence = 0.5569799f,
                chosenFromPrimary = false,
                selectedSource = "alternate_upper",
                primaryReasonCode = "absolute_score_passed",
                selectedReasonCode = "relative_score_and_color_gaps_passed"
            ),
            colorEvidence = VisualFeatureDetector.ShinyColorEvidence(
                masked = Pair(false, 0f),
                raw = Pair(false, 0f),
                hue = Pair(true, 0.49833333f),
                histogramHue = Pair(true, 0.8f)
            ),
            pokemonName = "Cottonee"
        )

        assertEquals(Pair(true, 0.5569799f), decision.result)
        assertFalse(decision.colorFallbackCalled)
        assertEquals("strong_primary_signature", decision.selectedPath)
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
