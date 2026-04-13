package com.pokerarity.scanner

import com.google.gson.Gson
import com.pokerarity.scanner.data.model.ScanTelemetryPayload
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTelemetryPayloadTest {

    @Test
    fun serializesPredictionFlagsAndSpecies() {
        val payload = ScanTelemetryPayload(
            uploadId = "u1",
            uploadedAtEpochMs = 123L,
            app = ScanTelemetryPayload.AppInfo("pkg", "1.0", 1),
            device = ScanTelemetryPayload.DeviceInfo("Google", "Pixel", 35),
            prediction = ScanTelemetryPayload.PredictionInfo(
                species = "Flareon",
                cp = 2307,
                hp = null,
                maxHp = 161,
                stardustCost = 4000,
                candyCost = 3,
                caughtDateEpochMs = null,
                isShiny = true,
                isShadow = false,
                isLucky = false,
                hasCostume = true,
                hasSpecialForm = false,
                hasLocationCard = false,
                rarityScore = 80,
                rarityTier = "RARE",
                ivEstimate = "84% - 91%",
                ivSolveMode = "RANGE",
                ivExact = null,
                ivMin = 84,
                ivMax = 91,
                ivCandidateCount = 4
            ),
            debug = ScanTelemetryPayload.DebugInfo(
                "Name:FIareole",
                2500L,
                listOf("test"),
                mapOf("base" to 10),
                explanationMode = "generic_variant",
                eventConfidenceCode = "generic",
                eventConfidenceLabel = "Generic",
                mismatchGuard = true,
                whyNotExact = "Event token is not date-safe enough.",
                scanConfidenceScore = 68,
                scanConfidenceLabel = "Medium",
                ocrConfidenceScore = 85,
                calculationErrorMargin = 7,
                contradictionField = "stardust",
                cpOcrStatus = "parsed",
                hpOcrStatus = "max_hp_parsed",
                diagnosticDirectory = "/tmp/iv",
                diagnosticFiles = mapOf("hp" to "/tmp/iv/hp.png"),
                ivSolve = ScanTelemetryPayload.IvSolveInfo(
                    mode = "RANGE",
                    ivExact = null,
                    ivMin = 84,
                    ivMax = 91,
                    candidateCount = 4,
                    levelMin = 25.0f,
                    levelMax = 26.5f,
                    signalsUsed = listOf("cp", "hp", "stardust", "candy")
                )
            ),
            screenshot = ScanTelemetryPayload.ScreenshotInfo("x.png", 1080, 2400)
        )

        val json = Gson().toJson(payload)

        assertTrue(json.contains("\"species\":\"Flareon\""))
        assertTrue(json.contains("\"isShiny\":true"))
        assertTrue(json.contains("\"hasCostume\":true"))
        assertTrue(json.contains("\"pipelineMs\":2500"))
        assertTrue(json.contains("\"maxHp\":161"))
        assertTrue(json.contains("\"eventConfidenceCode\":\"generic\""))
        assertTrue(json.contains("\"mismatchGuard\":true"))
        assertTrue(json.contains("\"scanConfidenceScore\":68"))
        assertTrue(json.contains("\"ocrConfidenceScore\":85"))
        assertTrue(json.contains("\"calculationErrorMargin\":7"))
        assertTrue(json.contains("\"contradictionField\":\"stardust\""))
        assertTrue(json.contains("\"hpOcrStatus\":\"max_hp_parsed\""))
        assertTrue(json.contains("\"ivSolveMode\":\"RANGE\""))
        assertTrue(json.contains("\"candyCost\":3"))
        assertTrue(json.contains("\"candidateCount\":4"))
    }
}
