package com.pokerarity.scanner.util.vision

import com.google.gson.Gson
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier.TargetCapabilityReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2VariantClassifierMetadataTest {

    private val gson = Gson()

    @Test
    fun missingSupportedFieldProducesMissingMetadataReason() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", null, 5, 5)
        assertEquals(TargetCapabilityReason.missing_metadata, cap.reason)
        assertFalse(cap.decisionCapable)
        assertEquals(10, cap.combinedCount)
    }

    @Test
    fun missingPositiveCountProducesMissingMetadataReason() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, null, 5)
        assertEquals(TargetCapabilityReason.missing_metadata, cap.reason)
        assertFalse(cap.decisionCapable)
        assertNull(cap.combinedCount)
    }

    @Test
    fun missingNegativeCountProducesMissingMetadataReason() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 5, null)
        assertEquals(TargetCapabilityReason.missing_metadata, cap.reason)
        assertFalse(cap.decisionCapable)
        assertNull(cap.combinedCount)
    }

    @Test
    fun gsonAbsentCountRemainsNull() {
        val json = """{"supported": true}"""
        val model = gson.fromJson(json, Phase2VariantClassifier.TargetModel::class.java)
        assertTrue(model.supported == true)
        assertNull(model.positiveCount)
        assertNull(model.negativeCount)
    }

    @Test
    fun gsonExplicitCountZeroRemainsZero() {
        val json = """{"supported": true, "positiveCount": 0, "negativeCount": 0}"""
        val model = gson.fromJson(json, Phase2VariantClassifier.TargetModel::class.java)
        assertTrue(model.supported == true)
        assertEquals(0, model.positiveCount)
        assertEquals(0, model.negativeCount)
    }

    @Test
    fun supportedFalseWithPresentCountsProducesUnsupportedReason() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", false, 10, 10)
        assertEquals(TargetCapabilityReason.unsupported, cap.reason)
        assertFalse(cap.decisionCapable)
        assertEquals(20, cap.combinedCount)
    }

    @Test
    fun supportedTrueWithZeroPositiveCountProducesZeroPositiveReason() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 0, 0)
        assertEquals(TargetCapabilityReason.zero_positive, cap.reason)
        assertFalse(cap.decisionCapable)
        assertEquals(0, cap.combinedCount)
    }

    @Test
    fun supportedTrueWithPositiveAtLeastOneAndZeroNegativeCountProducesZeroNegativeReason() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 5, 0)
        assertEquals(TargetCapabilityReason.zero_negative, cap.reason)
        assertFalse(cap.decisionCapable)
        assertEquals(5, cap.combinedCount)
    }

    @Test
    fun supportedTrueWithCombinedNineProducesBelowMinimumCombinedSamplesReason() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 4, 5)
        assertEquals(TargetCapabilityReason.below_minimum_combined_samples, cap.reason)
        assertFalse(cap.decisionCapable)
        assertEquals(9, cap.combinedCount)
    }

    @Test
    fun supportedTrueWithPositiveOneAndNegativeNineIsDecisionCapable() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 1, 9)
        assertEquals(TargetCapabilityReason.decision_capable, cap.reason)
        assertTrue(cap.decisionCapable)
        assertEquals(10, cap.combinedCount)
    }

    @Test
    fun supportedTrueWithPositiveNineAndNegativeOneIsDecisionCapable() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 9, 1)
        assertEquals(TargetCapabilityReason.decision_capable, cap.reason)
        assertTrue(cap.decisionCapable)
        assertEquals(10, cap.combinedCount)
    }

    @Test
    fun capabilityReasonPrecedenceIsExact() {
        // missing_metadata beats supported=false
        assertEquals(
            TargetCapabilityReason.missing_metadata,
            Phase2VariantClassifier.evaluateCapability("t", "s", null, 0, 0).reason
        )
        // unsupported beats zero_positive
        assertEquals(
            TargetCapabilityReason.unsupported,
            Phase2VariantClassifier.evaluateCapability("t", "s", false, 0, 0).reason
        )
        // zero_positive beats zero_negative
        assertEquals(
            TargetCapabilityReason.zero_positive,
            Phase2VariantClassifier.evaluateCapability("t", "s", true, 0, 0).reason
        )
        // zero_negative beats below_minimum_combined_samples
        assertEquals(
            TargetCapabilityReason.zero_negative,
            Phase2VariantClassifier.evaluateCapability("t", "s", true, 5, 0).reason
        )
        // below_minimum_combined_samples beats decision_capable
        assertEquals(
            TargetCapabilityReason.below_minimum_combined_samples,
            Phase2VariantClassifier.evaluateCapability("t", "s", true, 4, 5).reason
        )
    }

    @Test
    fun globalAndSpeciesSourceProduceTheSameCapabilityResultForSameCounts() {
        val globalCap = Phase2VariantClassifier.evaluateCapability("hasCostume", "global", true, 5, 5)
        val speciesCap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 5, 5)

        assertEquals(globalCap.reason, speciesCap.reason)
        assertEquals(globalCap.decisionCapable, speciesCap.decisionCapable)
        assertEquals(globalCap.combinedCount, speciesCap.combinedCount)
    }

    @Test
    fun negativeOrInvalidCountsFailClosedToMissingMetadata() {
        val negPos = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, -1, 5)
        val negNeg = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 5, -1)

        assertEquals(TargetCapabilityReason.missing_metadata, negPos.reason)
        assertEquals(TargetCapabilityReason.missing_metadata, negNeg.reason)
        assertFalse(negPos.decisionCapable)
        assertFalse(negNeg.decisionCapable)
        assertNull(negPos.combinedCount)
        assertNull(negNeg.combinedCount)
    }

    @Test
    fun appliedTargetSelectionExcludesThresholdPassedDiagnosticsOnlyPredictions() {
        val diagCap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 1, 1)
        val prediction = Phase2VariantClassifier.Prediction(
            target = "hasCostume",
            predictedValue = true,
            confidence = 0.99f,
            margin = 0.50f,
            positiveScore = 0.8f,
            negativeScore = 0.3f,
            positiveCount = 1,
            negativeCount = 1,
            passedThreshold = true,
            source = "species",
            capability = diagCap
        )

        val result = Phase2VariantClassifier.Result(
            species = "Pikachu",
            supportedTargets = listOf("hasCostume"),
            predictions = listOf(prediction),
            appliedTargets = if (prediction.passedThreshold && prediction.capability.decisionCapable) {
                listOf("hasCostume")
            } else {
                emptyList()
            },
            minConfidence = 0.5f,
            minMargin = 0.1f,
            modelType = "test",
            capabilities = listOf(diagCap)
        )

        assertTrue(prediction.passedThreshold)
        assertFalse(prediction.capability.decisionCapable)
        assertTrue(result.appliedTargets.isEmpty())
    }

    @Test
    fun appliedTargetSelectionRetainsThresholdPassedDecisionCapablePredictions() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 5, 5)
        val prediction = Phase2VariantClassifier.Prediction(
            target = "hasCostume",
            predictedValue = true,
            confidence = 0.99f,
            margin = 0.50f,
            positiveScore = 0.8f,
            negativeScore = 0.3f,
            positiveCount = 5,
            negativeCount = 5,
            passedThreshold = true,
            source = "species",
            capability = cap
        )

        val result = Phase2VariantClassifier.Result(
            species = "Pikachu",
            supportedTargets = listOf("hasCostume"),
            predictions = listOf(prediction),
            appliedTargets = if (prediction.passedThreshold && prediction.capability.decisionCapable) {
                listOf("hasCostume")
            } else {
                emptyList()
            },
            minConfidence = 0.5f,
            minMargin = 0.1f,
            modelType = "test",
            capabilities = listOf(cap)
        )

        assertTrue(prediction.passedThreshold)
        assertTrue(prediction.capability.decisionCapable)
        assertEquals(listOf("hasCostume"), result.appliedTargets)
    }

    @Test
    fun capabilityOrderingIsDeterministicByTargetThenSource() {
        val caps = listOf(
            Phase2VariantClassifier.evaluateCapability("isShiny", "species", true, 5, 5),
            Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 5, 5),
            Phase2VariantClassifier.evaluateCapability("hasCostume", "global", true, 5, 5),
            Phase2VariantClassifier.evaluateCapability("isShiny", "global", true, 5, 5)
        ).sortedWith(compareBy<Phase2VariantClassifier.TargetCapability> { it.target }.thenBy { it.source })

        assertEquals("hasCostume", caps[0].target)
        assertEquals("global", caps[0].source)

        assertEquals("hasCostume", caps[1].target)
        assertEquals("species", caps[1].source)

        assertEquals("isShiny", caps[2].target)
        assertEquals("global", caps[2].source)

        assertEquals("isShiny", caps[3].target)
        assertEquals("species", caps[3].source)
    }
}
