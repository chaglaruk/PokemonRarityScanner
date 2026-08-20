package com.pokerarity.scanner.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the committed candidate manifest structural integrity for Manual Gate A tooling.
 *
 * This test reads the manifest from test resources using Gson and verifies:
 * - Record counts (100 development + 20 holdout = 120 total)
 * - Unique SHA-256 hashes across all records
 * - No truth labels present
 * - Holdout quarantine flag active
 * - Candidate-only and non-authoritative flags
 * - Source corpus identity constants
 *
 * This test does NOT infer or verify any truth data. It only validates
 * the structural integrity of the committed manifest.
 */
class ManualGateManifestIntegrityTest {

    companion object {
        private const val EXPECTED_TOTAL = 120
        private const val EXPECTED_DEV = 100
        private const val EXPECTED_HOLDOUT = 20
        private const val EXPECTED_SOURCE_COUNT = 730
        private const val EXPECTED_SOURCE_BYTES = 473_826_206L
        private const val EXPECTED_SOURCE_DIGEST =
            "e3e3dadc4ffb64bf0db32f63f0ec0d08321eebdb82952bde068e6d6eaccc0dd1"
    }

    private val manifest: JsonObject by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream(
            "scan_fixtures/candidate_2026_s25_manifest.json"
        ) ?: error("candidate manifest not found on classpath")
        JsonParser.parseString(stream.bufferedReader().readText()).asJsonObject
    }

    @Test
    fun testTotalRecordCount() {
        val records = manifest.getAsJsonArray("records")
        assertEquals(EXPECTED_TOTAL, records.size())
    }

    @Test
    fun testDevelopmentRecordCount() {
        val records = manifest.getAsJsonArray("records")
        val devCount = records.count {
            it.asJsonObject.get("lane").asString == "development_candidate"
        }
        assertEquals(EXPECTED_DEV, devCount)
    }

    @Test
    fun testHoldoutRecordCount() {
        val records = manifest.getAsJsonArray("records")
        val holdoutCount = records.count {
            it.asJsonObject.get("lane").asString == "prospective_holdout_candidate"
        }
        assertEquals(EXPECTED_HOLDOUT, holdoutCount)
    }

    @Test
    fun testUniqueSha256Hashes() {
        val records = manifest.getAsJsonArray("records")
        val hashes = mutableSetOf<String>()
        records.forEachIndexed { index, element ->
            val sha = element.asJsonObject.get("sha256").asString
            assertTrue(
                "Duplicate SHA-256 at index $index: $sha",
                hashes.add(sha)
            )
        }
        assertEquals(EXPECTED_TOTAL, hashes.size)
    }

    @Test
    fun testNoTruthLabelsPresent() {
        assertFalse(manifest.get("truthLabelsPresent").asBoolean)
    }

    @Test
    fun testHoldoutQuarantined() {
        assertTrue(manifest.get("prospectiveHoldoutQuarantined").asBoolean)
    }

    @Test
    fun testCandidateOnly() {
        assertTrue(manifest.get("candidateOnly").asBoolean)
    }

    @Test
    fun testNotAuthoritative() {
        assertFalse(manifest.get("authoritative").asBoolean)
    }

    @Test
    fun testNoScreenshotBytes() {
        assertFalse(manifest.get("containsScreenshotBytes").asBoolean)
    }

    @Test
    fun testSourceCorpusIdentity() {
        assertEquals(EXPECTED_SOURCE_COUNT, manifest.get("sourceFileCount").asInt)
        assertEquals(EXPECTED_SOURCE_BYTES, manifest.get("sourceAggregateBytes").asLong)
        assertEquals(EXPECTED_SOURCE_DIGEST, manifest.get("sourceDigestSha256").asString)
    }

    @Test
    fun testSchemaVersion() {
        assertEquals(1, manifest.get("schemaVersion").asInt)
    }

    @Test
    fun testAllRecordsHaveValidLane() {
        val validLanes = setOf("development_candidate", "prospective_holdout_candidate")
        val records = manifest.getAsJsonArray("records")
        records.forEachIndexed { index, element ->
            val lane = element.asJsonObject.get("lane").asString
            assertTrue(
                "Invalid lane '$lane' at index $index",
                lane in validLanes
            )
        }
    }

    @Test
    fun testAllRecordsHaveUnreviewedStatus() {
        val records = manifest.getAsJsonArray("records")
        records.forEach { element ->
            assertEquals(
                "unreviewed",
                element.asJsonObject.get("manualTruthStatus").asString
            )
        }
    }

    @Test
    fun testAllRecordsNeedPrivacyReview() {
        val records = manifest.getAsJsonArray("records")
        records.forEach { element ->
            assertEquals(
                "NEEDS_HUMAN_PRIVACY_REVIEW",
                element.asJsonObject.get("privacyClassification").asString
            )
        }
    }

    @Test
    fun testNoRecordHasForbiddenFields() {
        val forbidden = setOf(
            "canonicalSpecies", "rawOcrText", "sourcePath", "fileName",
            "accountId", "deviceSerial", "adbEndpoint", "networkIdentifier",
            "authToken", "telemetryPayload", "screenshotBytes",
            "thumbnailBytes", "imageData"
        )
        val records = manifest.getAsJsonArray("records")
        records.forEachIndexed { index, element ->
            val record = element.asJsonObject
            for (field in forbidden) {
                assertFalse(
                    "Forbidden field '$field' found at index $index",
                    record.has(field)
                )
            }
        }
    }
}
