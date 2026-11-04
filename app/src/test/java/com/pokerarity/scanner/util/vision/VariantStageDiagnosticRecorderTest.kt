package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.FullVariantCandidate
import com.pokerarity.scanner.data.model.FullVariantMatch
import java.io.File
import java.nio.file.Files
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
        val accessoryCue = report.visualDetector.costumeAccessoryCue
        assertEquals(true, accessoryCue?.accepted)
        assertEquals(100_000, accessoryCue?.redPixelCount)
        assertEquals(100_000, accessoryCue?.largestRedClusterSize)
        assertEquals(100_000, accessoryCue?.sampledPixelCount)
        assertEquals(1f, accessoryCue?.redPixelRatio ?: -1f, 0f)
        assertTrue((accessoryCue?.reasonCode?.length ?: 0) <= 96)
        val costume = report.costumeSignatures.first()
        assertEquals(false, costume.matched)
        assertEquals(0.42f, costume.confidence ?: -1f, 0f)
        assertEquals(true, costume.finalMatched)
        assertEquals(0.81f, costume.finalConfidence ?: -1f, 0f)
        assertEquals("signature_rejected_heuristic_allowed", costume.heuristicPath)
        assertEquals(0.5625f, costume.bestNormalHeadPHashDistance ?: -1f, 0f)
        assertEquals(0.453125f, costume.bestCostumeHeadPHashDistance ?: -1f, 0f)
        assertEquals(0.109375f, costume.headPHashAdvantage ?: -1f, 0.000001f)
        assertTrue(json.contains("\"headPHashAdvantage\""))
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

    @Test
    fun staleExactFramePendingDirectoryIsRemovedWithoutDeletingRecentOrCompletedEvidence() {
        val parent = Files.createTempDirectory("qa-exact-frame-cleanup").toFile()
        try {
            val stale = File(parent, ".${"a".repeat(32)}.pending")
            val recent = File(parent, ".${"b".repeat(32)}.pending")
            val complete = File(parent, "c".repeat(32))
            val unrelated = File(parent, ".other.pending")
            for (directory in listOf(stale, recent, complete, unrelated)) {
                assertTrue(directory.mkdir())
                File(directory, "detector-input.png").writeText("test fixture")
            }
            assertTrue(stale.setLastModified(1_000L))
            assertTrue(recent.setLastModified(600_000L))
            assertTrue(complete.setLastModified(1_000L))
            assertTrue(unrelated.setLastModified(1_000L))

            val removed = QaVariantDiagnosticWriter.removeStalePendingFrameDirectories(parent, 650_000L)

            assertEquals(1, removed)
            assertFalse(stale.exists())
            assertTrue(recent.exists())
            assertTrue(complete.exists())
            assertTrue(unrelated.exists())
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun reportRetentionCountsOnlyFinalVariantReportsAndKeepsCapacityBounded() {
        val directory = Files.createTempDirectory("qa-variant-report-retention").toFile()
        try {
            repeat(QaVariantDiagnosticWriter.MAX_REPORTS - 1) { index ->
                File(directory, "variant-${index.toString(16).padStart(32, '0')}.json")
                    .writeText("bounded report")
            }
            File(directory, "costume-regional-evidence.json").writeText("independent QA evidence")
            File(directory, "variant-${"f".repeat(32)}.json.tmp").writeText("incomplete report")
            File(directory, "variant-invalid.json").writeText("unrecognized file")
            File(directory, "nested").mkdir()
            File(File(directory, "nested"), "variant-${"e".repeat(32)}.json").writeText("nested report")

            assertEquals(QaVariantDiagnosticWriter.MAX_REPORTS - 1,
                QaVariantDiagnosticWriter.retainedVariantReportCount(directory))
            assertTrue(QaVariantDiagnosticWriter.canWriteReport(directory))

            File(directory, "variant-${"d".repeat(32)}.json").writeText("bounded report")

            assertEquals(QaVariantDiagnosticWriter.MAX_REPORTS,
                QaVariantDiagnosticWriter.retainedVariantReportCount(directory))
            assertFalse(QaVariantDiagnosticWriter.canWriteReport(directory))
        } finally {
            directory.deleteRecursively()
        }
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
                strictShinyReason = "costume_true_confidence_at_least_0_60",
                accessoryCue = QaCostumeAccessoryCue(
                    accepted = true,
                    redPixelCount = Int.MAX_VALUE,
                    largestRedClusterSize = Int.MAX_VALUE,
                    sampledPixelCount = Int.MAX_VALUE,
                    redPixelRatio = 1.5f,
                    reasonCode = "flower_cluster".repeat(30)
                )
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
        rejectionReason = "normal_candidate_not_beaten_by_margin",
        bestNormalHeadPHashDistance = 0.5625f,
        bestCostumeHeadPHashDistance = 0.453125f,
        headPHashAdvantage = 0.109375f
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
