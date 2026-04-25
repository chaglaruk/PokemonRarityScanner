package com.pokerarity.scanner

import com.pokerarity.scanner.data.repository.ScanTelemetryRepository
import com.pokerarity.scanner.data.local.db.TelemetryUploadEntity
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

    @Test
    fun prepareUploadEntity_stripsMissingScreenshotForMetadataOnlyUpload() {
        val entity = TelemetryUploadEntity(
            uploadId = "u1",
            payloadJson = "{}",
            screenshotPath = "missing-file.png"
        )

        val prepared = ScanTelemetryRepository.prepareUploadEntity(entity)

        assertNull(prepared.screenshotFile)
        assertNull(prepared.entity.screenshotPath)
        assertEquals(entity.uploadId, prepared.entity.uploadId)
    }

    @Test
    fun prepareUploadEntity_preservesExistingScreenshot() {
        val tempFile = File.createTempFile("telemetry", ".png")
        try {
            val entity = TelemetryUploadEntity(
                uploadId = "u1",
                payloadJson = "{}",
                screenshotPath = tempFile.absolutePath
            )

            val prepared = ScanTelemetryRepository.prepareUploadEntity(entity)

            assertEquals(tempFile.absolutePath, prepared.screenshotFile?.absolutePath)
            assertEquals(tempFile.absolutePath, prepared.entity.screenshotPath)
        } finally {
            tempFile.delete()
        }
    }
}
