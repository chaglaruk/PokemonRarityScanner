package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.CostumeDecisionDiagnostics
import com.pokerarity.scanner.data.model.VisualFeatures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QaVariantDiagnosticsTest {
    @Test
    fun diagnosticsAreEnabledOnlyForTheIsolatedQaBuildType() {
        assertTrue(QaVariantDiagnosticsPolicy.isEnabled("qa"))
        assertFalse(QaVariantDiagnosticsPolicy.isEnabled("debug"))
        assertFalse(QaVariantDiagnosticsPolicy.isEnabled("release"))
    }

    @Test
    fun variantSummaryIncludesDiagnosticOnlyWhenExplicitlyEnabled() {
        val features = VisualFeatures(costumeDecisionDiagnostics = diagnostic())
        val qaSummary = requireNotNull(VariantVisualSummary.from(features, null, includeQaDiagnostics = true))

        assertNull(VariantVisualSummary.from(features, null, includeQaDiagnostics = false)?.costumeDecisionDiagnostics)
        assertNotNull(qaSummary.costumeDecisionDiagnostics)
        assertEquals(-0.044f, qaSummary.costumeDecisionDiagnostics?.scoreGap ?: 0f, 0.0001f)
    }

    @Test
    fun diagnosticIsFixedSizeAndContainsNoImageOcrOrPathPayload() {
        val summary = requireNotNull(
            VariantVisualSummary.from(
                VisualFeatures(costumeDecisionDiagnostics = diagnostic()),
                null,
                includeQaDiagnostics = true
            )
        )
        val report = QaVariantDecisionExporter.Report(
            scanId = "qa-123",
            sourceSha = "a".repeat(40),
            appVersion = "1.10.0-qa",
            metadataRevision = "snapshot-abc",
            species = "Charmander",
            cp = 533,
            hp = 77,
            maxHp = 77,
            shiny = "UNKNOWN",
            costume = "UNKNOWN",
            formStatus = "KNOWN",
            form = "Normal Form",
            variantSummary = summary
        )
        val json = QaVariantDecisionExporter.buildJsonForTest(report)

        assertTrue(json.length < 8_192)
        assertFalse(json.contains("screenshot", ignoreCase = true))
        assertFalse(json.contains("rawOcr", ignoreCase = true))
        assertFalse(json.contains("\\", ignoreCase = true))
        assertFalse(json.contains("/", ignoreCase = true))
        assertTrue(json.contains("-0.044"))
        assertTrue(json.contains("costume_not_better_than_normal"))
    }

    private fun diagnostic() = CostumeDecisionDiagnostics(
        cropWidth = 112,
        cropHeight = 108,
        candidateCount = 4,
        normalCandidateCount = 2,
        costumeCandidateCount = 2,
        bestCostumeKey = "004_00_11",
        bestCostumeScore = 0.345f,
        bestNormalKey = "004_00",
        bestNormalScore = 0.301f,
        scoreGap = -0.044f,
        signatureMatched = false,
        signatureConfidence = 0f,
        signatureRejectionReason = "costume_not_better_than_normal",
        denseVariantSpecies = false,
        heuristicPath = "signature_rejected_heuristic",
        heuristicEligible = false,
        heuristicCalled = false,
        heuristicMatched = null,
        heuristicConfidence = null,
        borderlineRescue = false,
        sparseMatchRescue = false,
        upperHeadAccessoryCue = false,
        strictShinyActivated = false,
        finalCostume = false,
        finalConfidence = 0f,
        decisionReason = "costume_not_better_than_normal"
    )
}
