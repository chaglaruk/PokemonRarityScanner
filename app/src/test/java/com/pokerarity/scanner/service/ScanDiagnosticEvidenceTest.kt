package com.pokerarity.scanner.service

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanDiagnosticEvidenceTest {
    @Test fun unavailableOwnedEvidenceNeverInvokesExporter() {
        var invoked = false
        val result = ScanDiagnosticEvidence.export(null, true) { invoked = true; null }
        assertFalse(invoked)
        assertNull(result.bundle)
        assertEquals(OwnedFrameHandoff.EvidenceStatus.UNAVAILABLE, result.status)
    }

    @Test fun pngDefaultKeepsExistingSuccessfulExporterContract() {
        val result = ScanDiagnosticEvidence.export(null, false) { null }
        assertNull(result.bundle)
        assertNull(result.status)
    }

    @Test fun optionalExportFailureIsExplicit() {
        val result = ScanDiagnosticEvidence.export(null, false) { throw IOException("controlled") }
        assertNull(result.bundle)
        assertEquals(OwnedFrameHandoff.EvidenceStatus.EXPORT_FAILED, result.status)
    }

    @Test(expected = CancellationException::class)
    fun optionalExportNeverSwallowsCoroutineCancellation() {
        ScanDiagnosticEvidence.export(null, false) { throw CancellationException("controlled") }
    }
}
