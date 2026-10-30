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
            fallbackReason = "color_fallback_positive_supported_by_two_signals_and_hue",
            decisionPath = "color_fallback"
        )
        repeat(40) { index ->
            recorder.recordCostumeSignature(
                QaCostumeStage(
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
            )
        }
        val candidates = (0 until 30).map { index ->
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
        recorder.recordFullVariant(
            FullVariantMatch(
                finalSpecies = "Cottonee",
                winnerSpecies = "Cottonee",
                finalSpriteKey = candidates.first().spriteKey,
                resolvedVariantClass = "costume",
                resolvedCostume = true,
                variantConfidence = 0.8f,
                explanationMode = "exact_authoritative",
                candidates = candidates
            ),
            candidates
        )

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
        assertEquals(VariantStageDiagnosticRecorder.MAX_STAGE_RECORDS, report.costumeSignatures.size)
        assertEquals(30, report.fullVariantMatcher?.candidateCount ?: -1)
        assertEquals(VariantStageDiagnosticRecorder.MAX_CANDIDATES, report.fullVariantMatcher?.candidates?.size ?: -1)
        assertTrue(json.toByteArray(Charsets.UTF_8).size <= QaVariantDiagnosticWriter.MAX_REPORT_BYTES)
        assertFalse(json.contains("rawOcrText"))
        assertFalse(json.contains("CP:595"))
        assertFalse(json.contains("C:\\Users\\"))
    }
}
