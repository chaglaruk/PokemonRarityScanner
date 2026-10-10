package com.pokerarity.scanner.util.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppraisalBarStabilityTest {
    private fun panel(min: Double, max: Double) = AppraisalBarObservation(
        AppraisalStat.entries.associateWith { AppraisalStatObservation(
            AppraisalStatObservation.EndpointState.Interval(min, max), null, emptyList()) }, emptyList())

    @Test
    fun overlappingSamplesKeepTheUnionInsteadOfForcingBoundaryPrecision() {
        val result = AppraisalBarStability.combine(listOf(panel(0.2, 0.3), panel(0.25, 0.35)))
        result.readings.values.forEach {
            assertEquals(AppraisalStatObservation.EndpointState.Interval(0.2, 0.35), it.endpoint)
            assertTrue(AppraisalIvInterpreter.interpretStat(it, null) is AppraisalIvEvidence.Unknown)
        }
    }

    @Test
    fun disjointAnimatedEndpointsAndSingleFrameRemainUnknown() {
        for (samples in listOf(listOf(panel(0.2, 0.3), panel(0.4, 0.5)), listOf(panel(0.2, 0.3)))) {
            assertTrue(AppraisalBarStability.combine(samples).readings.values.all {
                it.endpoint is AppraisalStatObservation.EndpointState.Unknown })
        }
    }

    @Test
    fun occludedStatDoesNotEraseIndependentStatesOrSupplyPositiveEvidence() {
        val clear = panel(0.2, 0.3)
        val covered = clear.copy(readings = clear.readings + (AppraisalStat.ATTACK to AppraisalStatObservation(
            AppraisalStatObservation.EndpointState.Unsupported("covered"), null, listOf("covered"))))
        val result = AppraisalBarStability.combine(listOf(clear, covered))
        assertTrue(result.readings.getValue(AppraisalStat.ATTACK).endpoint is
            AppraisalStatObservation.EndpointState.Unknown)
        assertTrue(result.readings.getValue(AppraisalStat.DEFENSE).endpoint is
            AppraisalStatObservation.EndpointState.Interval)
        assertTrue(result.readings.getValue(AppraisalStat.STAMINA).endpoint is
            AppraisalStatObservation.EndpointState.Interval)
    }
}
