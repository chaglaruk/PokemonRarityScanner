package com.pokerarity.scanner

import com.google.gson.Gson
import com.pokerarity.scanner.data.repository.ScanTelemetryRepository
import com.pokerarity.scanner.data.local.db.TelemetryUploadEntity
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ScanTelemetryRepositoryTest {
    private val gson = Gson()

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

    @Test
    fun screenshotSourceFileName_stripsAbsoluteWindowsPath() {
        val localScreenshotPath = "C:/Users/ExampleUser/AppData/Local/Temp/scan-fixture.png"

        val fileName = ScanTelemetryRepository.screenshotSourceFileName(localScreenshotPath)

        assertEquals("scan-fixture.png", fileName)
        assertFalse(fileName.orEmpty().contains("C:/Users", ignoreCase = true))
        assertFalse(fileName.orEmpty().contains("ExampleUser"))
        assertFalse(fileName.orEmpty().contains("AppData", ignoreCase = true))
        assertFalse(fileName.orEmpty().contains("Temp", ignoreCase = true))
    }

    @Test
    fun screenshotSourceFileName_keepsMetadataOnlyUploadPathAbsent() {
        assertNull(ScanTelemetryRepository.screenshotSourceFileName(null))
    }

    @Test
    fun buildPayloadDebugInfo_omitsDiagnosticPathsFromProductionHelper() {
        val debugInfo = ScanTelemetryRepository.buildPayloadDebugInfo(
            pokemonData = privacyFixturePokemonData(),
            rarityScore = RarityScore(
                totalScore = 42,
                tier = RarityTier.RARE,
                breakdown = mapOf("base" to 42),
                explanation = listOf("fixture explanation")
            ),
            pipelineMs = 1200L,
            livingDbVersion = "fixture-db",
            phase2Result = null
        )
        val json = gson.toJson(debugInfo)

        assertEquals("", debugInfo.rawOcrText)
        assertNull(debugInfo.diagnosticDirectory)
        assertNull(debugInfo.diagnosticFiles)
        assertPrivacySafeTelemetryJson(json)
    }

    private fun privacyFixturePokemonData(): PokemonData = PokemonData(
        cp = 777,
        hp = 88,
        maxHp = 99,
        name = "Pikachu",
        realName = null,
        candyName = "Pikachu Candy",
        megaEnergy = null,
        weight = null,
        height = null,
        stardust = null,
        caughtDate = null,
        rawOcrText = "NameDynamic:ocr\nC:/Users/ExampleUser/AppData/Local/Temp/raw-ocr.txt",
        ocrDiagnosticsDir = "C:/Users/ExampleUser/AppData/Local/Temp/ocr",
        ocrDiagnosticsFiles = mapOf(
            "full" to "C:/Users/ExampleUser/AppData/Local/Temp/full.png"
        )
    )

    private fun assertPrivacySafeTelemetryJson(json: String) {
        listOf(
            "api_key",
            "apiKey",
            "api-key",
            "authorization",
            "bearer",
            "bearer token",
            "token",
            "auth",
            "secret",
            "C:/Users",
            "C:\\Users",
            "ExampleUser",
            "AppData",
            "Temp",
            "/tmp",
            "raw-ocr.txt",
            "full.png",
            "diagnosticDirectory",
            "diagnosticFiles",
            "deviceId",
            "androidId"
        ).forEach { forbidden ->
            assertFalse(
                "Telemetry JSON leaked forbidden value: $forbidden",
                json.contains(forbidden, ignoreCase = true)
            )
        }
    }
}
