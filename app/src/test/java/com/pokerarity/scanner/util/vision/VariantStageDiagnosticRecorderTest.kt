package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.FullVariantCandidate
import com.pokerarity.scanner.data.model.FullVariantMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VariantStageDiagnosticRecorderTest {

    @Test
    fun snapshotPreservesDecisionValuesAndSerializesWithinFixedBound() {
        val recorder = createBoundedFixtureRecorder()

        val report = recorder.snapshot(
            build = QaBuildIdentity("com.pokerarity.scanner.sprint6qa.diag1", "1", 1, "a".repeat(40)),
            metadata = QaMetadataRevision("qa_apk_bundled_assets", "b".repeat(64), emptyList())
        )
        val json = QaVariantDiagnosticWriter.serialize(report)

        assertEquals(recorder.scanId, report.scanId)
        assertEquals("Cottonee", report.lockedSpecies)
        assertEquals(176, report.visualDetector.spriteCropWidth ?: -1)
        assertEquals(true, report.visualDetector.shiny)
        assertEquals("color_fallback", report.visualDetector.shinyDecisionPath)
        val costume = report.costumeSignatures.first()
        assertEquals(false, costume.matched)
        assertEquals(0.42f, costume.confidence ?: -1f, 0f)
        assertEquals(true, costume.finalMatched)
        assertEquals(0.81f, costume.finalConfidence ?: -1f, 0f)
        assertEquals("signature_rejected_heuristic_allowed", costume.heuristicPath)
        assertEquals(VariantStageDiagnosticRecorder.MAX_STAGE_RECORDS, report.costumeSignatures.size)
        assertEquals(30, report.fullVariantMatcher?.candidateCount ?: -1)
        assertEquals(
            VariantStageDiagnosticRecorder.MAX_CANDIDATES,
            report.fullVariantMatcher?.candidates?.size ?: -1
        )
        assertTrue(json.toByteArray(Charsets.UTF_8).size <= QaVariantDiagnosticWriter.MAX_REPORT_BYTES)
        assertFalse(json.contains("rawOcrText"))
        assertFalse(json.contains("CP:595"))
        assertFalse(json.contains("C:\\Users\\"))
    }

    private fun createBoundedFixtureRecorder(): VariantStageDiagnosticRecorder {
        val recorder = VariantStageDiagnosticRecorder()
        recorder.recordSpeciesAuthority("Cottonee")
        recorder.recordDetectorInput("Cottonee", 1080, 2400)
        recorder.recordSpriteCrop(176, 214)
        recorder.recordSelectedFrameReference(
            QaSelectedFrameReference("sha256:" + "c".repeat(24), "sha256-prefix-96bit", "available")
        )
        recorder.recordShinyChoice(
            matched = true,
            confidence = 0.84f,
            fallbackCalled = true,
            fallbackReason = "color_fallback_positive_supported_by_rgb_and_hue_groups",
            decisionPath = "color_fallback"
        )
        recorder.recordCostumeSignature(visualCostumeStage())
        recorder.recordCostumeDecision(
            QaCostumeDecision(
                matched = true,
                confidence = 0.81f,
                heuristicPath = "signature_rejected_heuristic_allowed",
                signatureDecisionPath = "signature_rejected_normal_candidate_not_beaten_by_margin",
                heuristicCalled = true,
                heuristicResult = true to 0.81f,
                strictShiny = true,
                strictShinyReason = "costume_true_confidence_at_least_0_60"
            )
        )
        repeat(40) { index -> recorder.recordCostumeSignature(costumeStage(index)) }
        val candidates = prototypeCandidates()
        recorder.recordFullVariant(fullVariant(candidates), candidates)
        return recorder
    }

    private fun costumeStage(index: Int) = QaCostumeStage(
        source = "variant_decision_engine",
        referenceAvailable = true,
        candidateCount = 22,
        costumeCandidateCount = 9,
        bestNormalKey = "546_00",
        bestNormalScore = 0.34f,
        bestCostumeKey = "546_00_costume_$index",
        bestCostumeScore = 0.23f,
        scoreGap = 0.11f,
        confidence = 0.81f,
        matched = true,
        denseVariantSpecies = true,
        rejectionReason = "costume_thresholds_passed"
    )

    private fun visualCostumeStage() = QaCostumeStage(
        source = VariantStageDiagnosticRecorder.VISUAL_COSTUME_SOURCE,
        referenceAvailable = true,
        candidateCount = 22,
        costumeCandidateCount = 9,
        bestNormalKey = "546_00",
        bestNormalScore = 0.35f,
        bestCostumeKey = "546_00_crown",
        bestCostumeScore = 0.34f,
        scoreGap = 0.01f,
        confidence = 0.42f,
        matched = false,
        denseVariantSpecies = true,
        rejectionReason = "normal_candidate_not_beaten_by_margin"
    )

    private fun prototypeCandidates() = (0 until 30).map { index ->
        FullVariantCandidate(
            species = "Cottonee",
            spriteKey = "546_00_candidate_$index",
            variantClass = if (index == 0) "costume" else "base",
            isShiny = false,
            isCostumeLike = index == 0,
            matchScore = 0.1f + index * 0.001f,
            source = "prototype",
            classifierConfidence = 0.8f
        )
    }

    private fun fullVariant(candidates: List<FullVariantCandidate>) = FullVariantMatch(
        finalSpecies = "Cottonee",
        winnerSpecies = "Cottonee",
        finalSpriteKey = candidates.first().spriteKey,
        resolvedVariantClass = "costume",
        resolvedCostume = true,
        variantConfidence = 0.8f,
        explanationMode = "exact_authoritative",
        candidates = candidates
    )
}
