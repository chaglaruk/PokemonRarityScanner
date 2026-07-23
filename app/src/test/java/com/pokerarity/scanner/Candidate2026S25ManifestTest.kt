package com.pokerarity.scanner

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Candidate2026S25ManifestTest {

    @Test
    fun candidateManifestIsStrictNonAuthoritativeAndContainsNoScreenshotBytes() {
        val resource = javaClass.classLoader.getResource(MANIFEST_RESOURCE)
        assertNotNull("Missing candidate manifest: $MANIFEST_RESOURCE", resource)
        val bytes = resource!!.readBytes()
        assertCanonicalJson(bytes)

        val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        assertEquals(ROOT_KEYS, root.keySet())
        assertEquals(1, root.requireInt("schemaVersion"))
        assertEquals("candidate_2026_s25", root.requireString("datasetId"))
        assertTrue(root.requireBoolean("candidateOnly"))
        assertFalse(root.requireBoolean("authoritative"))
        assertFalse(root.requireBoolean("containsScreenshotBytes"))
        assertFalse(root.requireBoolean("truthLabelsPresent"))
        assertTrue(root.requireBoolean("prospectiveHoldoutQuarantined"))
        assertEquals(730, root.requireInt("sourceFileCount"))
        assertEquals(473_826_206L, root.requireLong("sourceAggregateBytes"))
        assertEquals(SOURCE_DIGEST_SHA256, root.requireString("sourceDigestSha256"))
        assertEquals(61, root.requireInt("nearDuplicateGroupCount"))
        assertEquals(176, root.requireInt("nearDuplicateGroupedFileCount"))
        assertEquals(5, root.requireInt("redundantExcludedCount"))
        assertEquals(724, root.requireInt("structurallyEligibleCount"))
        assertNearDuplicateMethod(root.requireObject("nearDuplicateMethod"))

        val records = root.requireArray("records").map(JsonElement::getAsJsonObject)
        assertEquals(EXPECTED_IDS, records.map { it.requireString("id") })
        assertEquals(EXPECTED_LANE_COUNTS, records.groupingBy { it.requireString("lane") }.eachCount())
        assertRecords(records)
        assertClusters(root, records)
        assertNoForbiddenMetadata(root)
        assertNoScreenshotResources()
    }

    private fun assertCanonicalJson(bytes: ByteArray) {
        assertTrue("Manifest must end with one LF", bytes.isNotEmpty() && bytes.last() == '\n'.code.toByte())
        assertFalse(
            "Manifest must not end with two LFs",
            bytes.size > 1 && bytes[bytes.lastIndex - 1] == '\n'.code.toByte(),
        )
        assertFalse("Manifest must not contain CR", bytes.any { it == '\r'.code.toByte() })
        assertFalse(
            "Manifest must not contain a UTF-8 BOM",
            bytes.take(3) == listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
        )
        val text = bytes.toString(Charsets.UTF_8)
        val canonical = CANONICAL_GSON.toJson(canonicalize(JsonParser.parseString(text))) + "\n"
        assertEquals("Manifest must use sorted keys and canonical 2-space indentation", canonical, text)
    }

    private fun canonicalize(element: JsonElement): JsonElement = when {
        element.isJsonObject -> JsonObject().also { canonical ->
            element.asJsonObject.keySet().sorted().forEach { key ->
                canonical.add(key, canonicalize(element.asJsonObject.get(key)))
            }
        }
        element.isJsonArray -> JsonArray().also { canonical ->
            element.asJsonArray.forEach { canonical.add(canonicalize(it)) }
        }
        else -> element.deepCopy()
    }

    private fun assertRecords(records: List<JsonObject>) {
        assertEquals(RECORD_COUNT, records.size)
        val hashes = mutableSetOf<String>()
        records.forEach { record ->
            assertEquals(RECORD_KEYS, record.keySet())
            assertTrue(SHA256_PATTERN.matches(record.requireString("sha256")))
            assertTrue("Duplicate SHA-256", hashes.add(record.requireString("sha256")))
            assertEquals(1080, record.requirePositiveInt("width"))
            assertEquals(2340, record.requirePositiveInt("height"))
            assertEquals("PNG", record.requireString("format"))
            assertEquals("RGBA", record.requireString("colorMode"))
            assertEquals(8, record.requirePositiveInt("bitDepth"))
            record.requirePositiveInt("byteSize")
            assertEquals("NEEDS_HUMAN_PRIVACY_REVIEW", record.requireString("privacyClassification"))
            assertEquals("unreviewed", record.requireString("manualTruthStatus"))
            assertEquals("user_supplied_local_corpus", record.requireString("provenanceStatus"))
            assertEquals("not_approved", record.requireString("publicationStatus"))
            BOOLEAN_FIELDS.forEach { field -> record.requireBoolean(field) }
            val clusterReference = record.get("nearDuplicateClusterId")
            assertTrue("Missing nearDuplicateClusterId", clusterReference != null)
            val duplicateDecision = record.get("duplicateDecisionReason")
            assertTrue("Missing duplicateDecisionReason", duplicateDecision != null)
            if (!clusterReference.isJsonNull) {
                assertTrue(CLUSTER_ID_PATTERN.matches(clusterReference.asString))
                assertTrue(
                    "Invalid duplicate decision",
                    !duplicateDecision.isJsonNull && duplicateDecision.asString in DUPLICATE_DECISIONS,
                )
            } else {
                assertTrue("Unclustered record must not have a duplicate decision", duplicateDecision.isJsonNull)
            }
        }
    }

    private fun assertClusters(root: JsonObject, records: List<JsonObject>) {
        val recordsById = records.associateBy { it.requireString("id") }
        val clusters = root.requireArray("nearDuplicateClusters").map(JsonElement::getAsJsonObject)
        clusters.forEach { assertEquals(CLUSTER_KEYS, it.keySet()) }
        val clustersById = clusters.associateBy { it.requireString("id") }
        assertEquals("Duplicate cluster ID", clusters.size, clustersById.size)

        clustersById.forEach { (clusterId, cluster) ->
            assertTrue(CLUSTER_ID_PATTERN.matches(clusterId))
            val members = cluster.requireArray("selectedMemberIds").map(JsonElement::getAsString)
            assertTrue("Cluster must contain at least one selected record", members.isNotEmpty())
            assertEquals("Duplicate cluster member", members.size, members.toSet().size)
            assertTrue(
                "Source cluster count is smaller than selected count",
                cluster.requirePositiveInt("sourceMemberCount") >= members.size,
            )
            assertTrue("Invalid cluster decision", cluster.requireString("decisionReason") in DUPLICATE_DECISIONS)
            assertTrue("Unknown cluster member", members.all(recordsById::containsKey))
            assertEquals(
                "Near-duplicate cluster crosses candidate lanes",
                1,
                members.map { recordsById.getValue(it).requireString("lane") }.toSet().size,
            )
            members.forEach { memberId ->
                assertEquals(clusterId, recordsById.getValue(memberId).requireString("nearDuplicateClusterId"))
            }
        }

        records.forEach { record ->
            val reference = record.get("nearDuplicateClusterId")
            if (!reference.isJsonNull) {
                val cluster = clustersById[reference.asString]
                assertNotNull("Dangling near-duplicate cluster reference", cluster)
                assertTrue(
                    cluster!!.requireArray("selectedMemberIds")
                        .map(JsonElement::getAsString)
                        .contains(record.requireString("id")),
                )
            }
        }
    }

    private fun assertNearDuplicateMethod(method: JsonObject) {
        assertEquals(NEAR_DUPLICATE_METHOD_KEYS, method.keySet())
        assertEquals("Pillow", method.requireString("imageLibrary"))
        assertEquals("L", method.requireString("colorMode"))
        method.requireObject("pHash").also {
            assertEquals(PHASH_KEYS, it.keySet())
            assertEquals("32x32", it.requireString("resize"))
            assertEquals(8, it.requireInt("dctSize"))
            assertEquals("excluding_dc", it.requireString("median"))
            assertEquals(8, it.requireInt("maxHammingDistance"))
        }
        method.requireObject("dHash").also {
            assertEquals(DHASH_KEYS, it.keySet())
            assertEquals("9x8", it.requireString("resize"))
            assertEquals(8, it.requireInt("maxHammingDistance"))
        }
        method.requireObject("thumbnail").also {
            assertEquals(THUMBNAIL_KEYS, it.keySet())
            assertEquals("36x78", it.requireString("resize"))
            assertEquals(1.0, it.requireDouble("maxMae"), 0.0)
            assertEquals(0.999, it.requireDouble("minCorrelation"), 0.0)
        }
    }

    private fun assertNoForbiddenMetadata(root: JsonObject) {
        val keys = mutableSetOf<String>()
        val values = mutableListOf<String>()
        collectMetadata(root, keys, values)
        val forbiddenKeys = keys.map(::normalizeKey).intersect(FORBIDDEN_KEYS)
        assertTrue("Forbidden candidate metadata keys: $forbiddenKeys", forbiddenKeys.isEmpty())
        values.forEach { value ->
            FORBIDDEN_VALUE_PATTERNS.forEach { (description, pattern) ->
                assertFalse("Candidate manifest contains $description", pattern.containsMatchIn(value))
            }
        }
    }

    private fun assertNoScreenshotResources() {
        val resourceRoot = File(findRepoRoot(), "app/src/test/resources/scan_fixtures")
        val files = resourceRoot.walkTopDown().filter(File::isFile).toList()
        assertTrue("Candidate manifest source file is missing", files.any { it.name == MANIFEST_FILE_NAME })
        files.forEach { file ->
            assertFalse(
                "Screenshot resource is forbidden: ${file.name}",
                file.extension.lowercase() in IMAGE_EXTENSIONS,
            )
            val prefix = file.inputStream().use { input -> ByteArray(PNG_MAGIC.size).also { input.read(it) } }
            assertFalse("PNG bytes are forbidden in test resources", prefix.contentEquals(PNG_MAGIC))
        }
    }

    private fun collectMetadata(element: JsonElement, keys: MutableSet<String>, values: MutableList<String>) {
        when {
            element.isJsonObject -> element.asJsonObject.entrySet().forEach { (key, value) ->
                keys += key
                collectMetadata(value, keys, values)
            }
            element.isJsonArray -> element.asJsonArray.forEach { collectMetadata(it, keys, values) }
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> values += element.asString
        }
    }

    private fun findRepoRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile ?: return@repeat
        }
        error("Repository root was not found")
    }

    private fun normalizeKey(value: String) = value.lowercase().filter(Char::isLetterOrDigit)

    private fun JsonObject.requireArray(name: String) =
        get(name)?.takeUnless { it.isJsonNull }?.asJsonArray ?: error("Missing $name")

    private fun JsonObject.requireObject(name: String) =
        get(name)?.takeUnless { it.isJsonNull }?.asJsonObject ?: error("Missing $name")

    private fun JsonObject.requireString(name: String): String {
        val value = requirePrimitive(name)
        assertTrue("$name must be string", value.isString)
        return value.asString.takeIf(String::isNotBlank) ?: error("Missing $name")
    }

    private fun JsonObject.requireInt(name: String): Int {
        val value = requirePrimitive(name)
        assertTrue("$name must be number", value.isNumber)
        return value.asInt
    }

    private fun JsonObject.requirePositiveInt(name: String) = requireInt(name).also {
        assertTrue("$name must be positive", it > 0)
    }

    private fun JsonObject.requireLong(name: String): Long {
        val value = requirePrimitive(name)
        assertTrue("$name must be number", value.isNumber)
        return value.asLong
    }

    private fun JsonObject.requireDouble(name: String): Double {
        val value = requirePrimitive(name)
        assertTrue("$name must be number", value.isNumber)
        return value.asDouble
    }

    private fun JsonObject.requirePrimitive(name: String): JsonPrimitive =
        get(name)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            ?: error("Missing $name")

    private fun JsonObject.requireBoolean(name: String): Boolean {
        val value = get(name)?.takeUnless { it.isJsonNull }?.asJsonPrimitive ?: error("Missing $name")
        assertTrue("$name must be boolean", value.isBoolean)
        return value.asBoolean
    }

    private companion object {
        const val MANIFEST_RESOURCE = "scan_fixtures/candidate_2026_s25_manifest.json"
        const val MANIFEST_FILE_NAME = "candidate_2026_s25_manifest.json"
        const val RECORD_COUNT = 120
        const val DEVELOPMENT_COUNT = 100
        const val HOLDOUT_COUNT = 20
        const val SOURCE_DIGEST_SHA256 = "e3e3dadc4ffb64bf0db32f63f0ec0d08321eebdb82952bde068e6d6eaccc0dd1"
        val CANONICAL_GSON = GsonBuilder().serializeNulls().setPrettyPrinting().disableHtmlEscaping().create()
        val EXPECTED_IDS = (1..DEVELOPMENT_COUNT).map { "s25_2026_dev_%03d".format(it) } +
            (1..HOLDOUT_COUNT).map { "s25_2026_holdout_%03d".format(it) }
        val EXPECTED_LANE_COUNTS = mapOf(
            "development_candidate" to DEVELOPMENT_COUNT,
            "prospective_holdout_candidate" to HOLDOUT_COUNT,
        )
        val ROOT_KEYS = setOf(
            "schemaVersion", "datasetId", "candidateOnly", "authoritative", "containsScreenshotBytes",
            "truthLabelsPresent", "prospectiveHoldoutQuarantined", "sourceFileCount", "sourceAggregateBytes",
            "sourceDigestSha256", "nearDuplicateGroupCount", "nearDuplicateGroupedFileCount",
            "redundantExcludedCount", "structurallyEligibleCount", "nearDuplicateMethod", "nearDuplicateClusters",
            "records",
        )
        val RECORD_KEYS = setOf(
            "id", "lane", "sha256", "nearDuplicateClusterId", "duplicateDecisionReason", "width", "height",
            "format", "colorMode", "bitDepth", "byteSize", "privacyClassification", "manualTruthStatus",
            "provenanceStatus", "publicationStatus",
            "overlayPresent", "likelyDetailsScreen", "likelyCpPresent", "likelyHpPresent", "likelyNamePresent",
            "likelyCandyFamilyPresent",
        )
        val CLUSTER_KEYS = setOf("id", "selectedMemberIds", "sourceMemberCount", "decisionReason")
        val NEAR_DUPLICATE_METHOD_KEYS = setOf("imageLibrary", "colorMode", "pHash", "dHash", "thumbnail")
        val PHASH_KEYS = setOf("resize", "dctSize", "median", "maxHammingDistance")
        val DHASH_KEYS = setOf("resize", "maxHammingDistance")
        val THUMBNAIL_KEYS = setOf("resize", "maxMae", "minCorrelation")
        val DUPLICATE_DECISIONS = setOf(
            "REDUNDANT_NEAR_IDENTICAL", "PRESERVE_SCROLL_VARIANT", "PRESERVE_STATE_VARIANT",
            "PRESERVE_LAYOUT_VARIANT", "FALSE_POSITIVE_SIMILARITY", "NEEDS_HUMAN_REVIEW",
        )
        val BOOLEAN_FIELDS = setOf(
            "overlayPresent", "likelyDetailsScreen", "likelyCpPresent", "likelyHpPresent", "likelyNamePresent",
            "likelyCandyFamilyPresent",
        )
        val FORBIDDEN_KEYS = setOf(
            "canonicalspecies", "species", "visiblename", "candyfamily", "cp", "hp", "rawocr", "ocrtext",
            "path", "relativepath", "sourcepath", "filename", "accountid", "accountidentifier", "device",
            "devicemodel", "deviceserial", "adb", "adbendpoint", "ipaddress", "network", "ssid", "bssid",
            "authorization", "authtoken", "apikey", "telemetry", "telemetrypayload", "screenshot",
            "screenshotpath", "imagebytes", "base64",
        )
        val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
        val CLUSTER_ID_PATTERN = Regex("^near_[0-9]{3}$")
        val FORBIDDEN_VALUE_PATTERNS = listOf(
            "an absolute path" to Regex("(?i)(?:[a-z]:[\\\\/]|/(?:Users|home)/)"),
            "a source filename" to Regex("(?i)\\b[^\\s/\\\\]+\\.(?:png|jpe?g|webp)\\b"),
            "an IPv4 address" to Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?\\b"),
            "ADB data" to Regex("(?i)\\badb\\b"),
            "an account, authentication, network or telemetry value" to Regex(
                "(?i)\\b(?:account[ _-]?(?:id|identifier)|device[ _-]?serial|network[ _-]?identifier|" +
                    "authorization|bearer|auth[ _-]?token|api[ _-]?key|password|secret|telemetry)\\b",
            ),
        )
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
        val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
