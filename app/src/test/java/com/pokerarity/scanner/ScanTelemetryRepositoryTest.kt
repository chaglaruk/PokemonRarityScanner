package com.pokerarity.scanner

import com.pokerarity.scanner.data.repository.ScanTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ScanTelemetryRepositoryTest {

    @Test
    fun normalizedScreenshotFile_rejectsMissingLegacyMarkers() {
        assertNull(ScanTelemetryRepository.normalizedScreenshotFile(null))
        assertNull(ScanTelemetryRepository.normalizedScreenshotFile(""))
        assertNull(ScanTelemetryRepository.normalizedScreenshotFile("   "))
        assertNull(ScanTelemetryRepository.normalizedScreenshotFile("null"))
        assertNull(ScanTelemetryRepository.normalizedScreenshotFile(" NULL "))
    }

    @Test
    fun normalizedScreenshotFile_acceptsExistingFilesOnly() {
        val tempFile = File.createTempFile("telemetry", ".png")
        try {
            assertEquals(tempFile.absolutePath, ScanTelemetryRepository.normalizedScreenshotFile(tempFile.absolutePath)?.absolutePath)
            assertNull(ScanTelemetryRepository.normalizedScreenshotFile(tempFile.absolutePath + ".missing"))
        } finally {
            tempFile.delete()
        }
    }
}
