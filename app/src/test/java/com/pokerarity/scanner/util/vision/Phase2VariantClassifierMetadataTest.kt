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
        assertEquals(TargetCapabilityReason.MISSING_METADATA, cap.reason)
        assertFalse(cap.decisionCapable)
        assertEquals(10, cap.combinedCount)
    }

    @Test
    fun missingPositiveCountProducesMissingMetadataReason() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, null, 5)
        assertEquals(TargetCapabilityReason.MISSING_METADATA, cap.reason)
        assertFalse(cap.decisionCapable)
        assertNull(cap.combinedCount)
    }

    @Test
    fun missingNegativeCountProducesMissingMetadataReason() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 5, null)
        assertEquals(TargetCapabilityReason.MISSING_METADATA, cap.reason)
        assertFalse(cap.decisionCapable)
        assertNull(cap.combinedCount)
    }

    @Test
    fun gsonAbsentCountRemainsNullInTargetModel() {
        val json = """{"supported": true}"""
        val model = gson.fromJson(json, Phase2VariantClassifier.TargetModel::class.java)
        assertTrue(model.supported == true)
        assertNull(model.positiveCount)
        assertNull(model.negativeCount)
    }

    @Test
    fun gsonExplicitCountZeroRemainsZeroInTargetModel() {
        val json = """{"supported": true, "positiveCount": 0, "negativeCount": 0}"""
        val model = gson.fromJson(json, Phase2VariantClassifier.TargetModel::class.java)
        assertTrue(model.supported == true)
        assertEquals(0, model.positiveCount)
        assertEquals(0, model.negativeCount)
    }

    @Test
    fun predictionPreservesMissingCountsAsNull() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, null, null)
        val prediction = Phase2VariantClassifier.Prediction(
            target = "hasCostume",
            predictedValue = true,
            confidence = 0.99f,
            margin = 0.50f,
            positiveScore = 0.8f,
            negativeScore = 0.3f,
            passedThreshold = true,
            source = "species",
            capability = cap
        )
        assertNull(prediction.positiveCount)
        assertNull(prediction.negativeCount)
        assertNull(prediction.capability.positiveCount)
        assertNull(prediction.capability.negativeCount)
    }

    @Test
    fun supportedFalseWithPresentCountsProducesUnsupportedReason() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", false, 10, 10)
        assertEquals(TargetCapabilityReason.UNSUPPORTED, cap.reason)
        assertFalse(cap.decisionCapable)
        assertEquals(20, cap.combinedCount)
    }

    @Test
    fun supportedTrueWithZeroPositiveCountProducesZeroPositiveReason() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 0, 0)
        assertEquals(TargetCapabilityReason.ZERO_POSITIVE, cap.reason)
        assertFalse(cap.decisionCapable)
        assertEquals(0, cap.combinedCount)
    }

    @Test
    fun supportedTrueWithPositiveAtLeastOneAndZeroNegativeCountProducesZeroNegativeReason() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 5, 0)
        assertEquals(TargetCapabilityReason.ZERO_NEGATIVE, cap.reason)
        assertFalse(cap.decisionCapable)
        assertEquals(5, cap.combinedCount)
    }

    @Test
    fun supportedTrueWithCombinedNineProducesBelowMinimumCombinedSamplesReason() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 4, 5)
        assertEquals(TargetCapabilityReason.BELOW_MINIMUM_COMBINED_SAMPLES, cap.reason)
        assertFalse(cap.decisionCapable)
        assertEquals(9, cap.combinedCount)
    }

    @Test
    fun supportedTrueWithPositiveOneAndNegativeNineIsDecisionCapable() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 1, 9)
        assertEquals(TargetCapabilityReason.DECISION_CAPABLE, cap.reason)
        assertTrue(cap.decisionCapable)
        assertEquals(10, cap.combinedCount)
    }

    @Test
    fun supportedTrueWithPositiveNineAndNegativeOneIsDecisionCapable() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 9, 1)
        assertEquals(TargetCapabilityReason.DECISION_CAPABLE, cap.reason)
        assertTrue(cap.decisionCapable)
        assertEquals(10, cap.combinedCount)
    }

    @Test
    fun capabilityReasonPrecedenceIsExact() {
        assertEquals(
            TargetCapabilityReason.MISSING_METADATA,
            Phase2VariantClassifier.evaluateCapability("t", "s", null, 0, 0).reason
        )
        assertEquals(
            TargetCapabilityReason.UNSUPPORTED,
            Phase2VariantClassifier.evaluateCapability("t", "s", false, 0, 0).reason
        )
        assertEquals(
            TargetCapabilityReason.ZERO_POSITIVE,
            Phase2VariantClassifier.evaluateCapability("t", "s", true, 0, 0).reason
        )
        assertEquals(
            TargetCapabilityReason.ZERO_NEGATIVE,
            Phase2VariantClassifier.evaluateCapability("t", "s", true, 5, 0).reason
        )
        assertEquals(
            TargetCapabilityReason.BELOW_MINIMUM_COMBINED_SAMPLES,
            Phase2VariantClassifier.evaluateCapability("t", "s", true, 4, 5).reason
        )
    }

    @Test
    fun capabilityReasonCodeStringsAreStable() {
        assertEquals("missing_metadata", TargetCapabilityReason.MISSING_METADATA.code)
        assertEquals("unsupported", TargetCapabilityReason.UNSUPPORTED.code)
        assertEquals("zero_positive", TargetCapabilityReason.ZERO_POSITIVE.code)
        assertEquals("zero_negative", TargetCapabilityReason.ZERO_NEGATIVE.code)
        assertEquals("below_minimum_combined_samples", TargetCapabilityReason.BELOW_MINIMUM_COMBINED_SAMPLES.code)
        assertEquals("decision_capable", TargetCapabilityReason.DECISION_CAPABLE.code)
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

        assertEquals(TargetCapabilityReason.MISSING_METADATA, negPos.reason)
        assertEquals(TargetCapabilityReason.MISSING_METADATA, negNeg.reason)
        assertFalse(negPos.decisionCapable)
        assertFalse(negNeg.decisionCapable)
        assertNull(negPos.combinedCount)
        assertNull(negNeg.combinedCount)
    }

    @Test
    fun selectAppliedTargetsExcludesThresholdPassedDiagnosticsOnlyPredictions() {
        val diagCap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 1, 1)
        val prediction = Phase2VariantClassifier.Prediction(
            target = "hasCostume",
            predictedValue = true,
            confidence = 0.99f,
            margin = 0.50f,
            positiveScore = 0.8f,
            negativeScore = 0.3f,
            passedThreshold = true,
            source = "species",
            capability = diagCap
        )

        val applied = Phase2VariantClassifier.selectAppliedTargets(listOf(prediction))

        assertTrue(prediction.passedThreshold)
        assertFalse(prediction.capability.decisionCapable)
        assertTrue(applied.isEmpty())
    }

    @Test
    fun selectAppliedTargetsIncludesThresholdPassedDecisionCapablePredictions() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 5, 5)
        val prediction = Phase2VariantClassifier.Prediction(
            target = "hasCostume",
            predictedValue = true,
            confidence = 0.99f,
            margin = 0.50f,
            positiveScore = 0.8f,
            negativeScore = 0.3f,
            passedThreshold = true,
            source = "species",
            capability = cap
        )

        val applied = Phase2VariantClassifier.selectAppliedTargets(listOf(prediction))

        assertTrue(prediction.passedThreshold)
        assertTrue(prediction.capability.decisionCapable)
        assertEquals(listOf("hasCostume"), applied)
    }

    @Test
    fun selectAppliedTargetsExcludesThresholdFailedDecisionCapablePredictions() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 5, 5)
        val prediction = Phase2VariantClassifier.Prediction(
            target = "hasCostume",
            predictedValue = true,
            confidence = 0.99f,
            margin = 0.50f,
            positiveScore = 0.8f,
            negativeScore = 0.3f,
            passedThreshold = false,
            source = "species",
            capability = cap
        )

        val applied = Phase2VariantClassifier.selectAppliedTargets(listOf(prediction))

        assertFalse(prediction.passedThreshold)
        assertTrue(prediction.capability.decisionCapable)
        assertTrue(applied.isEmpty())
    }

    @Test
    fun selectAppliedTargetsDeduplicatesAndSortsDeterministically() {
        val cap = Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 5, 5)
        val predictions = listOf(
            Phase2VariantClassifier.Prediction(
                target = "isShiny", predictedValue = true, confidence = 0.99f,
                margin = 0.50f, positiveScore = 0.8f, negativeScore = 0.3f,
                passedThreshold = true, source = "species", capability = cap
            ),
            Phase2VariantClassifier.Prediction(
                target = "hasCostume", predictedValue = true, confidence = 0.99f,
                margin = 0.50f, positiveScore = 0.8f, negativeScore = 0.3f,
                passedThreshold = true, source = "species", capability = cap
            ),
            Phase2VariantClassifier.Prediction(
                target = "isShiny", predictedValue = true, confidence = 0.99f,
                margin = 0.50f, positiveScore = 0.8f, negativeScore = 0.3f,
                passedThreshold = true, source = "global", capability = cap
            ),
            Phase2VariantClassifier.Prediction(
                target = "isShiny", predictedValue = true, confidence = 0.99f,
                margin = 0.50f, positiveScore = 0.8f, negativeScore = 0.3f,
                passedThreshold = true, source = "species", capability = cap
            )
        )

        val applied = Phase2VariantClassifier.selectAppliedTargets(predictions)

        assertEquals(listOf("hasCostume", "isShiny"), applied)
    }

    @Test
    fun sortCapabilitiesOrdersByTargetThenSource() {
        val caps = listOf(
            Phase2VariantClassifier.evaluateCapability("isShiny", "species", true, 5, 5),
            Phase2VariantClassifier.evaluateCapability("hasCostume", "species", true, 5, 5),
            Phase2VariantClassifier.evaluateCapability("hasCostume", "global", true, 5, 5),
            Phase2VariantClassifier.evaluateCapability("isShiny", "global", true, 5, 5)
        )

        val sorted = Phase2VariantClassifier.sortCapabilities(caps)

        assertEquals("hasCostume", sorted[0].target)
        assertEquals("global", sorted[0].source)

        assertEquals("hasCostume", sorted[1].target)
        assertEquals("species", sorted[1].source)

        assertEquals("isShiny", sorted[2].target)
        assertEquals("global", sorted[2].source)

        assertEquals("isShiny", sorted[3].target)
        assertEquals("species", sorted[3].source)
    }
}
