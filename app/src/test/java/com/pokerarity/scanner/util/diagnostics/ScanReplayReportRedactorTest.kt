package com.pokerarity.scanner.util.diagnostics

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanReplayReportRedactorTest {
    @Test
    fun aggregateOmitsPerScanFieldsAndNormalizesUnknownOutcomeLabels() {
        val species = "PrivateSpeciesFixture"
        val ocr = "Sensitive OCR sample"
        val localPath = "C:\\private\\frame.png"
        val rows = listOf(
            mapOf(
                "outcome" to "ACCEPT",
                "totalLatencyMs" to 12L,
                "species" to species,
                "cp" to 456,
                "rawOcr" to ocr,
                "path" to localPath
            ),
            mapOf(
                "outcome" to species,
                "totalLatencyMs" to 22L,
                "species" to species,
                "rawOcr" to ocr
            )
        )

        val report = Gson().toJson(ScanReplayReportRedactor.aggregate(rows))

        assertTrue(report.contains("\"frameCount\":2"))
        assertTrue(report.contains("\"ACCEPT\":1"))
        assertTrue(report.contains("\"OTHER\":1"))
        assertTrue(report.contains("\"p50\":12"))
        assertTrue(report.contains("\"p95\":22"))
        assertFalse(report.contains(species))
        assertFalse(report.contains(ocr))
        assertFalse(report.contains(localPath))
        assertFalse(report.contains("456"))
    }

    @Test
    fun aggregatePreservesOnlyAllowlistedRouteAndScreenCategories() {
        val aggregate = ScanReplayReportRedactor.aggregate(
            listOf(
                mapOf("outcome" to "PROCEED_DETAIL", "totalLatencyMs" to 10L),
                mapOf("outcome" to "PokemonDetail", "totalLatencyMs" to 20L),
                mapOf("outcome" to "private-frame-name", "totalLatencyMs" to -1L)
            )
        )

        assertEquals(setOf("frameCount", "outcomeCounts", "errorCount", "totalLatencyMs"), aggregate.keys)
        assertEquals(mapOf("PokemonDetail" to 1, "PROCEED_DETAIL" to 1, "OTHER" to 1), aggregate["outcomeCounts"])
        assertEquals(mapOf("p50" to 10L, "p95" to 20L), aggregate["totalLatencyMs"])
    }
}
