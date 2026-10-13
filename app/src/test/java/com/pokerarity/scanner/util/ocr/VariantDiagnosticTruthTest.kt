package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.RecognitionTruth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VariantDiagnosticTruthTest {
    @Test fun rawPositiveDoesNotReplaceTypedUnknown() {
        val result = VariantDiagnosticTruth.build(true, RecognitionTruth.UNKNOWN, RecognitionTruth.UNKNOWN)
        assertEquals(true, result.rawFullMatchShiny)
        assertEquals(RecognitionTruth.UNKNOWN, result.finalTypedShiny)
        assertEquals("typed_insufficient_evidence", result.finalShinyReason)
    }

    @Test fun rawNegativeDoesNotReplaceTypedPositive() {
        val result = VariantDiagnosticTruth.build(false, RecognitionTruth.TRUE, RecognitionTruth.TRUE)
        assertEquals(false, result.rawFullMatchShiny)
        assertEquals(RecognitionTruth.TRUE, result.finalTypedShiny)
    }

    @Test fun unavailableIdentityDoesNotBecomeFalse() {
        val result = VariantDiagnosticTruth.build(null, null, null)
        assertNull(result.rawFullMatchShiny)
        assertNull(result.finalTypedShiny)
        assertEquals("typed_identity_unavailable", result.finalShinyReason)
    }

    @Test fun qualifiedNegativeRemainsDistinct() {
        val result = VariantDiagnosticTruth.build(true, RecognitionTruth.FALSE, null)
        assertEquals(RecognitionTruth.FALSE, result.finalTypedShiny)
        assertEquals("typed_explicit_negative_evidence", result.finalShinyReason)
    }

    @Test fun inconsistentTypedReportsAreNotSilentlyResolved() {
        val result = VariantDiagnosticTruth.build(true, RecognitionTruth.TRUE, RecognitionTruth.UNKNOWN)
        assertNull(result.finalTypedShiny)
        assertEquals("diagnostic_identity_disagreement", result.finalShinyReason)
    }
}
