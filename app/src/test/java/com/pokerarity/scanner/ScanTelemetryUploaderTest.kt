package com.pokerarity.scanner

import com.pokerarity.scanner.data.remote.ScanTelemetryUploader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTelemetryUploaderTest {

    @Test
    fun parseScanUploadResponse_acceptsSuccessfulResponseWithScreenshotUrl() {
        val result = ScanTelemetryUploader.parseScanUploadResponse(
            code = 200,
            body = """{"ok":true,"upload_id":"u1","screenshot_url":"https://example.com/storage/scans/2026/04/u1.png"}"""
        )

        assertTrue(result.success)
        assertEquals("https://example.com/storage/scans/2026/04/u1.png", result.screenshotUrl)
    }

    @Test
    fun parseScanUploadResponse_rejectsSuccessWithoutScreenshotUrl() {
        val result = ScanTelemetryUploader.parseScanUploadResponse(
            code = 200,
            body = """{"ok":true,"upload_id":"u1","screenshot_url":null}"""
        )

        assertFalse(result.success)
        assertEquals("Missing or invalid screenshot_url", result.error)
    }

    @Test
    fun parseScanUploadResponse_rejectsOkFalseResponses() {
        val result = ScanTelemetryUploader.parseScanUploadResponse(
            code = 422,
            body = """{"ok":false,"error":"screenshot file is required"}"""
        )

        assertFalse(result.success)
        assertEquals("HTTP 422", result.error)
    }

    @Test
    fun shouldStageOfflineTelemetryForLegacyEndpointFailures() {
        assertTrue(ScanTelemetryUploader.shouldStageOfflineTelemetryForStatus(404))
        assertTrue(ScanTelemetryUploader.shouldStageOfflineTelemetryForStatus(503))
        assertFalse(ScanTelemetryUploader.shouldStageOfflineTelemetryForStatus(403))
        assertFalse(ScanTelemetryUploader.shouldStageOfflineTelemetryForStatus(200))
    }
}
