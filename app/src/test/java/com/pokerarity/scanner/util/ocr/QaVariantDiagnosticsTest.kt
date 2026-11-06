package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.CostumeDecisionDiagnostics
import com.pokerarity.scanner.data.model.VariantDecisionTrace
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val productionSummary = VariantVisualSummary.from(features, null, includeQaDiagnostics = false)

        assertNull(productionSummary?.costumeDecisionDiagnostics)
        assertNull(productionSummary?.qaStageDiagnostics)
        assertEquals(-0.044f, qaSummary.costumeDecisionDiagnostics?.scoreGap ?: 0f, 0.0001f)
    }

    @Test
    fun qaStageSummaryCapturesClassifierAndPhase2Decisions() {
        val qa = summary(includeQa = true)
        val stages = requireNotNull(qa.qaStageDiagnostics)
        val production = summary(includeQa = false)

        assertEquals("costume", stages.classifierVariantType)
        assertEquals(true, stages.classifierIsCostume)
        assertEquals(0.45f, stages.classifierScore)
        assertEquals("889_00_FCROWNED_SHIELD", stages.classifierSpriteKey)
        assertEquals(false, stages.fullVariantIsCostume)
        assertEquals("889_00_FHERO", stages.fullVariantSpriteKey)
        assertEquals(0.48f, stages.fullVariantVariantConfidence)
        assertEquals(true, stages.visualCostumeBeforePhase2)
        assertEquals(true, stages.phase2MayApply)
        assertEquals(2, stages.phase2CostumePredictions?.size)
        assertEquals(true, stages.phase2CostumePredictions?.first()?.predictedValue)
        assertEquals(91, stages.phase2CostumePredictions?.first()?.positiveCount)
        assertNull(production.qaStageDiagnostics)
        assertNull(production.costumeDecisionDiagnostics)
    }

    @Test
    fun exportedDiagnosticIsBoundedAndContainsNoImageOcrOrPathPayload() {
        val json = QaVariantDecisionExporter.buildJsonForTest(report(summary(includeQa = true)))

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

    private fun summary(includeQa: Boolean): VariantVisualSummary = requireNotNull(
        VariantVisualSummary.from(
            features = VisualFeatures(costumeDecisionDiagnostics = diagnostic()),
            trace = trace(),
            includeQaDiagnostics = includeQa,
            phase2Input = QaVariantPhase2Input(
                visualCostumeBeforePhase2 = true,
                mayApply = true,
                result = phase2Result()
            )
        )
    )

    private fun trace() = VariantDecisionTrace(
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

    private fun phase2Result(): Phase2VariantClassifier.Result {
        val capability = Phase2VariantClassifier.TargetCapability(
            target = "hasCostume",
            source = "global",
            supported = true,
            positiveCount = 91,
            negativeCount = 80,
            combinedCount = 171,
            reason = Phase2VariantClassifier.TargetCapabilityReason.DECISION_CAPABLE,
            decisionCapable = true
        )
        val prediction = Phase2VariantClassifier.Prediction(
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
            capability = capability
        )
        return Phase2VariantClassifier.Result(
            species = "Zamazenta",
            supportedTargets = listOf("hasCostume"),
            predictions = listOf(prediction, prediction, prediction),
            appliedTargets = listOf("hasCostume"),
            minConfidence = 0.5f,
            minMargin = 0.08f,
            modelType = "species_conditioned_variant_prototype_v1",
            capabilities = listOf(capability)
        )
    }

    private fun report(variantSummary: VariantVisualSummary) = QaVariantDecisionExporter.Report(
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
        variantSummary = variantSummary
    )

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
