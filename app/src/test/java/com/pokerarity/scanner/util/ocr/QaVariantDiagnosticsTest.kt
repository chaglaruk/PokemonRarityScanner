package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.CostumeDecisionDiagnostics
import com.pokerarity.scanner.data.model.VariantDecisionTrace
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
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
        val phase2Capability = Phase2VariantClassifier.TargetCapability(
            target = "hasCostume",
            source = "global",
            supported = true,
            positiveCount = 91,
            negativeCount = 80,
            combinedCount = 171,
            reason = Phase2VariantClassifier.TargetCapabilityReason.DECISION_CAPABLE,
            decisionCapable = true
        )
        val phase2Prediction = Phase2VariantClassifier.Prediction(
            target = "hasCostume",
            predictedValue = true,
            confidence = 0.55f,
            margin = 0.09f,
            positiveScore = 0.7f,
            negativeScore = 0.61f,
            positiveCount = 91,
            negativeCount = 80,
            passedThreshold = true,
            source = "global",
            capability = phase2Capability
        )
        val trace = VariantDecisionTrace(
            classifierScope = "species",
            classifierSpecies = "Zamazenta",
            classifierSpriteKey = "889_00_FCROWNED_SHIELD",
            classifierVariantType = "costume",
            classifierCostume = true,
            classifierConfidence = 0.55f,
            classifierScore = 0.45f,
            classifierVariantMargin = 0.04f,
            classifierBestBaseScore = 0.49f,
            classifierBestNonBaseScore = 0.45f,
            classifierBestNonBaseType = "costume",
            fullVariantSpecies = "Zamazenta",
            fullVariantSpriteKey = "889_00_FHERO",
            fullVariantClass = "base",
            fullVariantCostume = false,
            fullVariantVariantConfidence = 0.48f,
            fullVariantExplanationMode = "generic_species_only"
        )
        val summary = requireNotNull(
            VariantVisualSummary.from(
                VisualFeatures(costumeDecisionDiagnostics = diagnostic()),
                trace,
                includeQaDiagnostics = true,
                visualCostumeBeforePhase2 = true,
                phase2MayApply = true,
                phase2Result = Phase2VariantClassifier.Result(
                    species = "Zamazenta",
                    supportedTargets = listOf("hasCostume"),
                    predictions = listOf(phase2Prediction),
                    appliedTargets = listOf("hasCostume"),
                    minConfidence = 0.5f,
                    minMargin = 0.08f,
                    modelType = "species_conditioned_variant_prototype_v1",
                    capabilities = listOf(phase2Capability)
                )
            )
        )
        val productionSummary = requireNotNull(
            VariantVisualSummary.from(
                VisualFeatures(costumeDecisionDiagnostics = diagnostic()),
                trace,
                includeQaDiagnostics = false,
                visualCostumeBeforePhase2 = true,
                phase2MayApply = true,
                phase2Result = Phase2VariantClassifier.Result(
                    species = "Zamazenta",
                    supportedTargets = listOf("hasCostume"),
                    predictions = listOf(phase2Prediction),
                    appliedTargets = listOf("hasCostume"),
                    minConfidence = 0.5f,
                    minMargin = 0.08f,
                    modelType = "species_conditioned_variant_prototype_v1",
                    capabilities = listOf(phase2Capability)
                )
            )
        )
        assertNull(productionSummary.classifierVariantType)
        assertNull(productionSummary.visualCostumeBeforePhase2)
        assertNull(productionSummary.phase2CostumePredictions)
        assertNull(productionSummary.costumeDecisionDiagnostics)
        assertEquals("costume", summary.classifierVariantType)
        assertEquals(true, summary.classifierIsCostume)
        assertEquals(0.45f, summary.classifierScore)
        assertEquals("889_00_FCROWNED_SHIELD", summary.classifierSpriteKey)
        assertEquals("base", summary.fullVariantClass)
        assertEquals(true, summary.fullVariantIsCostume)
        assertEquals("889_00_FHERO", summary.fullVariantSpriteKey)
        assertEquals(0.48f, summary.fullVariantVariantConfidence)
        assertEquals(true, summary.visualCostumeBeforePhase2)
        assertEquals(true, summary.phase2CostumePredictions?.single()?.predictedValue)
        assertEquals(91, summary.phase2CostumePredictions?.single()?.positiveCount)
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
        assertTrue(json.contains("889_00_FCROWNED_SHIELD"))
        assertTrue(json.contains("decision_capable"))
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
