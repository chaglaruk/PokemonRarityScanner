package com.pokerarity.scanner.util.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrDiagnosticsReleasePolicyTest {
    @Test
    fun diagnosticsExportIsAllowedForDebugBuilds() {
        assertTrue(OcrDiagnosticsExporter.shouldExport(isDebugBuild = true))
    }

    @Test
    fun diagnosticsExportFailsClosedForReleaseBuilds() {
        assertFalse(OcrDiagnosticsExporter.shouldExport(isDebugBuild = false))
    }
}
