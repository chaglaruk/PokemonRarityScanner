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
                caughtDateEpochMs = null,
                isShiny = true,
                isShadow = false,
                isLucky = false,
                hasCostume = true,
                hasSpecialForm = false,
                hasLocationCard = false,
                rarityScore = 80,
                rarityTier = "RARE",
                ivEstimate = "???"
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
                scanConfidenceLabel = "Medium"
            ),
            screenshot = ScanTelemetryPayload.ScreenshotInfo("x.png", 1080, 2400)
        )

        val json = Gson().toJson(payload)

        assertTrue(json.contains("\"species\":\"Flareon\""))
        assertTrue(json.contains("\"isShiny\":true"))
        assertTrue(json.contains("\"hasCostume\":true"))
        assertTrue(json.contains("\"pipelineMs\":2500"))
        assertTrue(json.contains("\"eventConfidenceCode\":\"generic\""))
        assertTrue(json.contains("\"mismatchGuard\":true"))
        assertTrue(json.contains("\"scanConfidenceScore\":68"))
    }
}
