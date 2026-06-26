package com.pokerarity.scanner

import com.google.gson.Gson
import com.pokerarity.scanner.data.repository.ScanTelemetryRepository
import com.pokerarity.scanner.data.local.db.TelemetryUploadEntity
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.util.ocr.ScanDecision
import com.pokerarity.scanner.util.ocr.ScanDecisionSeverity
import com.pokerarity.scanner.util.ocr.ScanDecisionType
import com.pokerarity.scanner.util.ocr.SpeciesResolverTrace
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

    @Test
    fun buildPayloadDebugInfo_ignoresStableNameDynamicMissingMarkers() {
        val debugInfo = ScanTelemetryRepository.buildPayloadDebugInfo(
            pokemonData = privacyFixturePokemonData().copy(rawOcrText = "NameDynamic:missing|Name:Pikachu"),
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

        assertNull(debugInfo.dynamicNameSource)
    }

    @Test
    fun buildPayloadDebugInfo_omitsResolverTrace() {
        val debugInfo = ScanTelemetryRepository.buildPayloadDebugInfo(
            pokemonData = privacyFixturePokemonData().copy(
                speciesResolverTrace = SpeciesResolverTrace(
                    winningSpecies = "Secretmon",
                    confidence = 0.99f,
                    winnerReason = "raw Secret OCR token"
                )
            ),
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

        assertFalse(json.contains("Secretmon"))
        assertFalse(json.contains("raw Secret OCR token"))
        assertPrivacySafeTelemetryJson(json)
    }

    @Test
    fun buildPayloadDebugInfo_omitsScanDecisionTrace() {
        val debugInfo = ScanTelemetryRepository.buildPayloadDebugInfo(
            pokemonData = privacyFixturePokemonData().copy(
                scanDecision = ScanDecision(
                    decision = ScanDecisionType.UNCERTAIN,
                    confidence = 0.33f,
                    severity = ScanDecisionSeverity.WARNING,
                    userSafeReason = "Try again.",
                    developerReasons = listOf("raw OCR gate token C:/Users/ExampleUser/secret.txt"),
                    evidenceUsed = listOf("RawText"),
                    evidenceMissing = listOf("screen_state"),
                    recommendedNextAction = "ask_user_to_retry",
                    retryEligible = false,
                    mayShowOverlay = false,
                    maySaveScan = false,
                    collectionSafe = false
                )
            ),
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

        assertFalse(json.contains("raw OCR gate token"))
        assertFalse(json.contains("ask_user_to_retry"))
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
        ),
        ocrConfidenceReasons = com.pokerarity.scanner.data.model.OcrConfidenceReasonsBuilder()
            .withCp(777, reasonCodes = listOf("C:/Users/ExampleUser/secret_cp_log.txt", "Name:secret_name"))
            .addWarning("leak: /tmp/ocr_debug.png")
            .build()
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
