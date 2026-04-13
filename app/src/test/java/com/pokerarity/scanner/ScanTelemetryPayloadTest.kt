package com.pokerarity.scanner

import com.google.gson.Gson
import com.pokerarity.scanner.data.model.ScanTelemetryPayload
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTelemetryPayloadTest {

    @Test
    fun serializesRecognitionPayloadAndOmitsIvFields() {
        val payload = ScanTelemetryPayload(
            uploadId = "u1",
            uploadedAtEpochMs = 123L,
            app = ScanTelemetryPayload.AppInfo("pkg", "1.0", 1),
            device = ScanTelemetryPayload.DeviceInfo("Google", "Pixel", 35),
            prediction = ScanTelemetryPayload.PredictionInfo(
                species = "Flareon",
                speciesId = "136",
                formDetected = "base",
                cp = 2307,
                hp = null,
                maxHp = 161,
                caughtDateEpochMs = null,
                isShiny = true,
                isShadow = false,
                isLucky = false,
                hasCostume = true,
                hasSpecialForm = false,
                hasLocationCard = false,
                isEventBoosted = true,
                rarityScore = 80,
                rarityTier = "RARE"
            ),
            debug = ScanTelemetryPayload.DebugInfo(
                "Name:FIareole|NameDynamic:Flareon",
                2500L,
                listOf("test"),
                mapOf("base" to 10),
                explanationMode = "generic_variant",
                eventConfidenceCode = "generic",
                eventConfidenceLabel = "Generic",
                mismatchGuard = true,
                recognitionSummary = "ML Kit dynamic ROI resolved Flareon with live event context.",
                scanConfidenceScore = 68,
                scanConfidenceLabel = "Medium",
                ocrConfidenceScore = 85,
                contradictionField = "variant",
                cpOcrStatus = "parsed",
                hpOcrStatus = "max_hp_parsed",
                diagnosticDirectory = "/tmp/iv",
                diagnosticFiles = mapOf("hp" to "/tmp/iv/hp.png"),
                dynamicNameSource = "mlkit_dynamic",
                livingDbVersion = "2026-04-13"
            ),
            screenshot = ScanTelemetryPayload.ScreenshotInfo("x.png", 1080, 2400)
        )

        val json = Gson().toJson(payload)

        assertTrue(json.contains("\"species\":\"Flareon\""))
        assertTrue(json.contains("\"speciesId\":\"136\""))
        assertTrue(json.contains("\"formDetected\":\"base\""))
        assertTrue(json.contains("\"isEventBoosted\":true"))
        assertTrue(json.contains("\"isShiny\":true"))
        assertTrue(json.contains("\"hasCostume\":true"))
        assertTrue(json.contains("\"pipelineMs\":2500"))
        assertTrue(json.contains("\"maxHp\":161"))
        assertTrue(json.contains("\"eventConfidenceCode\":\"generic\""))
        assertTrue(json.contains("\"mismatchGuard\":true"))
        assertTrue(json.contains("\"scanConfidenceScore\":68"))
        assertTrue(json.contains("\"ocrConfidenceScore\":85"))
        assertTrue(json.contains("\"contradictionField\":\"variant\""))
        assertTrue(json.contains("\"hpOcrStatus\":\"max_hp_parsed\""))
        assertTrue(json.contains("\"recognitionSummary\":\"ML Kit dynamic ROI resolved Flareon with live event context.\""))
        assertTrue(json.contains("\"dynamicNameSource\":\"mlkit_dynamic\""))
        assertTrue(json.contains("\"livingDbVersion\":\"2026-04-13\""))
        assertFalse(json.contains("ivSolveMode"))
        assertFalse(json.contains("ivEstimate"))
        assertFalse(json.contains("candidateCount"))
    }
}
