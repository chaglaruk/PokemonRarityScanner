package com.pokerarity.scanner

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManualGateActiveTruthOverlayTest {

    @Test
    fun humanReviewedOverlayRaisesActiveSpeciesTruthAboveNinetyPercentWithoutPromotingUnknowns() {
        val activeCases = loadActiveCases()
        val activeById = activeCases.associateBy { it.requireString("id") }
        val existingLabeled = activeCases.count {
            it.requireObject("expected").nullableString("species") != null
        }

        assertEquals(44, activeCases.size)
        assertEquals(activeCases.size, activeById.size)
        assertEquals(16, existingLabeled)

        val overlay = loadOverlay()
        assertOverlayMetadata(overlay)

        val reviewedIds = verifyReviewedGroups(overlay.requireArray("groups"), activeById)
        val leftOutIds = verifyLeftOutFixtures(overlay.requireArray("leftOutFixtureIds"), activeById)

        assertEquals(24, overlay.requireInt("recordCount"))
        assertEquals(24, reviewedIds.size)
        assertEquals(24, reviewedIds.toSet().size)
        assertEquals(4, leftOutIds.size)
        assertEquals(4, leftOutIds.toSet().size)
        assertTrue(reviewedIds.toSet().intersect(leftOutIds.toSet()).isEmpty())

        assertUnlabeledPartition(activeCases, reviewedIds, leftOutIds)

        val combinedLabeled = existingLabeled + reviewedIds.size
        assertEquals(40, combinedLabeled)
        assertTrue(combinedLabeled.toDouble() / activeCases.size.toDouble() >= 0.90)
    }

    private fun loadActiveCases(): List<JsonObject> {
        val text = File(findRepoRoot(), ACTIVE_CASES_PATH)
            .readText(Charsets.UTF_8)
            .removePrefix("\uFEFF")
        return JsonParser.parseString(text).asJsonArray.map(JsonElement::getAsJsonObject)
    }

    private fun loadOverlay(): JsonObject {
        val resource = javaClass.classLoader.getResource(OVERLAY_RESOURCE)
        assertNotNull("Missing manual truth overlay: $OVERLAY_RESOURCE", resource)
        val bytes = resource!!.readBytes()
        assertCanonicalJson(bytes)
        return JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
    }

    private fun assertOverlayMetadata(overlay: JsonObject) {
        assertEquals(ROOT_KEYS, overlay.keySet())
        assertEquals(1, overlay.requireInt("schemaVersion"))
        assertEquals("active_regression_truth_overlay", overlay.requireString("datasetRole"))
        assertEquals("HUMAN_REVIEW", overlay.requireString("truthSource"))
        assertEquals(REVIEW_SOURCE_MAIN_SHA, overlay.requireString("originMainShaAtReview"))
        assertTrue(overlay.requireBoolean("authoritativeForManualGateTruth"))
        assertFalse(overlay.requireBoolean("containsScreenshotBytes"))
        assertFalse(overlay.requireBoolean("publicationApproved"))
        assertFalse(overlay.requireBoolean("suggestionsPromoted"))
        assertEquals("NOT_REVIEWED", overlay.requireString("privacyDisposition"))
        assertEquals("NOT_VERIFIED", overlay.requireString("provenanceDisposition"))
        overlay.requireString("humanReviewGeneratedAtUtc")

        val groups = overlay.requireArray("groups")
        assertEquals(12, overlay.requireInt("groupCount"))
        assertEquals(12, groups.size())
    }

    private fun verifyReviewedGroups(
        groups: JsonArray,
        activeById: Map<String, JsonObject>,
    ): List<String> {
        val reviewedIds = mutableListOf<String>()
        groups.map(JsonElement::getAsJsonObject).forEach { group ->
            assertEquals(GROUP_KEYS, group.keySet())
            assertEquals("CONFIRMED", group.requireString("reviewStatus"))

            val fixtureIds = group.requireArray("fixtureIds").map(JsonElement::getAsString)
            assertEquals(2, fixtureIds.size)
            assertEquals(2, fixtureIds.toSet().size)
            verifyTruth(group.requireObject("truth"))

            fixtureIds.forEach { fixtureId ->
                assertPreviouslyUnlabeledActiveFixture(fixtureId, activeById)
                reviewedIds += fixtureId
            }
        }
        return reviewedIds
    }

    private fun verifyTruth(truth: JsonObject) {
        assertEquals(TRUTH_KEYS, truth.keySet())
        val species = truth.requireString("species")
        assertNotEquals("species must be a reviewed label, not a sentinel", "UNKNOWN", species)
        assertTrue(truth.requireInt("cp") > 0)
        assertTrue(truth.requireInt("hp") > 0)
        assertUnknownOrPositiveInt(truth, "maxHp")
        assertTriState(truth, "shiny")
        assertTriState(truth, "lucky")
        assertTriState(truth, "costume")
        assertTriState(truth, "locationCard")
        assertTriState(truth, "datePresent")
        truth.requireString("form")
    }

    private fun verifyLeftOutFixtures(
        ids: JsonArray,
        activeById: Map<String, JsonObject>,
    ): List<String> {
        val leftOutIds = ids.map(JsonElement::getAsString)
        leftOutIds.forEach { fixtureId ->
            assertPreviouslyUnlabeledActiveFixture(fixtureId, activeById)
        }
        return leftOutIds
    }

    private fun assertPreviouslyUnlabeledActiveFixture(
        fixtureId: String,
        activeById: Map<String, JsonObject>,
    ) {
        val active = activeById[fixtureId]
        assertNotNull("Fixture is not active: $fixtureId", active)
        assertEquals(
            "Truth overlay must only reference previously unlabeled active fixtures: $fixtureId",
            null,
            active!!.requireObject("expected").nullableString("species"),
        )
    }

    private fun assertUnlabeledPartition(
        activeCases: List<JsonObject>,
        reviewedIds: List<String>,
        leftOutIds: List<String>,
    ) {
        val previouslyUnlabeledIds = activeCases
            .filter { it.requireObject("expected").nullableString("species") == null }
            .map { it.requireString("id") }
            .toSet()

        assertEquals(28, previouslyUnlabeledIds.size)
        assertEquals(previouslyUnlabeledIds, reviewedIds.toSet() + leftOutIds.toSet())
    }

    private fun assertCanonicalJson(bytes: ByteArray) {
        assertTrue("Overlay must end with one LF", bytes.isNotEmpty() && bytes.last() == '\n'.code.toByte())
        assertFalse(
            "Overlay must not end with two LFs",
            bytes.size > 1 && bytes[bytes.lastIndex - 1] == '\n'.code.toByte(),
        )
        assertFalse("Overlay must not contain CR", bytes.any { it == '\r'.code.toByte() })
        assertFalse(
            "Overlay must not contain a UTF-8 BOM",
            bytes.take(3) == listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
        )
    }

    private fun assertUnknownOrPositiveInt(obj: JsonObject, name: String) {
        val value = obj.get(name) ?: error("Missing $name")
        if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            assertTrue("$name must be positive", value.asInt > 0)
        } else {
            assertEquals("UNKNOWN", value.asString)
        }
    }

    private fun assertTriState(obj: JsonObject, name: String) {
        val value = obj.get(name) ?: error("Missing $name")
        if (value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
            value.asBoolean
        } else {
            assertEquals("UNKNOWN", value.asString)
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

    private fun JsonObject.requireArray(name: String) =
        get(name)?.takeUnless { it.isJsonNull }?.asJsonArray ?: error("Missing $name")

    private fun JsonObject.requireObject(name: String) =
        get(name)?.takeUnless { it.isJsonNull }?.asJsonObject ?: error("Missing $name")

    private fun JsonObject.requireString(name: String): String {
        val value = get(name)?.takeUnless { it.isJsonNull }?.asJsonPrimitive ?: error("Missing $name")
        assertTrue("$name must be string", value.isString)
        return value.asString.takeIf(String::isNotBlank) ?: error("Missing $name")
    }

    private fun JsonObject.nullableString(name: String): String? =
        get(name)
            ?.takeUnless { it.isJsonNull }
            ?.asString
            ?.takeIf(String::isNotBlank)

    private fun JsonObject.requireInt(name: String): Int {
        val value = get(name)?.takeUnless { it.isJsonNull }?.asJsonPrimitive ?: error("Missing $name")
        assertTrue("$name must be number", value.isNumber)
        return value.asInt
    }

    private fun JsonObject.requireBoolean(name: String): Boolean {
        val value = get(name)?.takeUnless { it.isJsonNull }?.asJsonPrimitive ?: error("Missing $name")
        assertTrue("$name must be boolean", value.isBoolean)
        return value.asBoolean
    }

    companion object {
        private const val ACTIVE_CASES_PATH = "app/src/androidTest/assets/scan_regression_cases.json"
        private const val OVERLAY_RESOURCE = "scan_fixtures/manual_gate_active_truth_20260820.json"
        private const val REVIEW_SOURCE_MAIN_SHA = "e9a853532df706d94b588cc3baaa3fd67f76e718"

        private val ROOT_KEYS = setOf(
            "authoritativeForManualGateTruth",
            "containsScreenshotBytes",
            "datasetRole",
            "groupCount",
            "groups",
            "humanReviewGeneratedAtUtc",
            "leftOutFixtureIds",
            "originMainShaAtReview",
            "privacyDisposition",
            "provenanceDisposition",
            "publicationApproved",
            "recordCount",
            "schemaVersion",
            "suggestionsPromoted",
            "truthSource",
        )

        private val GROUP_KEYS = setOf("fixtureIds", "reviewStatus", "truth")

        private val TRUTH_KEYS = setOf(
            "costume",
            "cp",
            "datePresent",
            "form",
            "hp",
            "locationCard",
            "lucky",
            "maxHp",
            "shiny",
            "species",
        )
    }
}
