package com.pokerarity.scanner

import android.graphics.BitmapFactory
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class Pr06DevelopmentFixtureIntegrityTest {

    @Test
    fun pr06DevelopmentCorpusIsCompleteSanitizedAndBytePreserved() {
        val repo = findRepoRoot()
        val assets = File(repo, "app/src/androidTest/assets")
        val corpus = File(assets, "scan_fixtures/pr06_1080_development")
        val manifestFile = File(corpus, "fixture_manifest.json")
        val metadataFile = File(corpus, "dataset_metadata.json")
        assertTrue("Missing PR-06 fixture manifest", manifestFile.isFile)
        assertTrue("Missing PR-06 dataset metadata", metadataFile.isFile)

        val manifestText = manifestFile.readText()
        val metadataText = metadataFile.readText()
        val manifest = JsonParser.parseString(manifestText).asJsonObject
        val metadata = JsonParser.parseString(metadataText).asJsonObject
        val fixtures = manifest.requireArray("fixtures")

        assertDatasetMetadata(manifest, metadata, fixtures.size())
        val records = fixtures.map { verifyFixture(assets, it.asJsonObject) }
        assertFixtureCollection(corpus, records)
        assertNoPrivateMetadata(manifest, metadata, manifestText, metadataText)
    }

    private fun assertDatasetMetadata(manifest: JsonObject, metadata: JsonObject, fixtureCount: Int) {
        assertEquals("pr06_1080_development", manifest.requireString("datasetId"))
        assertEquals("development", manifest.requireString("corpusClass"))
        assertEquals(15, fixtureCount)
        assertEquals(15, metadata.requireInt("fixtureCount"))
        assertEquals("development", metadata.requireString("datasetRole"))
        assertTrue(metadata.requireBoolean("excludedFromHoldout"))
        assertTrue(metadata.requireBoolean("eligibleForDevelopmentMeasurement"))
        assertEquals(8_725_743L, metadata.requireLong("sourceAggregateBytes"))
    }

    private fun verifyFixture(assets: File, fixture: JsonObject): FixtureRecord {
        val id = fixture.requireString("id")
        val path = fixture.requireString("relativePath")
        val hash = fixture.requireString("sha256")
        val truth = fixture.requireObject("truth")
        assertEquals("development", fixture.requireString("corpusClass"))
        assertTrue(fixture.requireBoolean("excludedFromHoldout"))
        assertTrue(fixture.requireBoolean("eligibleForDevelopmentMeasurement"))
        assertTrue(fixture.requireBoolean("nativeImage"))
        assertFalse(fixture.requireBoolean("imageTransformed"))
        assertEquals(1080, fixture.requireInt("width"))
        assertEquals(2340, fixture.requireInt("height"))
        assertEquals("1080", fixture.requireString("captureGeometryClass"))
        assertEquals("confirmed", truth.requireString("status"))
        assertEquals("user_confirmed_live_source_screen", truth.requireString("source"))
        assertTrue(truth.requireString("canonicalSpecies").isNotBlank())
        assertTrue(truth.requireString("visibleName").isNotBlank())
        assertTrue(truth.requireString("candyFamily").isNotBlank())
        assertTrue(truth.requireInt("cp") > 0)
        assertTrue(truth.requireInt("hp") > 0)
        assertTrue(path.startsWith("scan_fixtures/pr06_1080_development/"))
        assertFalse("Absolute path: $path", File(path).isAbsolute)

        verifyImage(assets, path, hash)
        return FixtureRecord(
            id,
            path,
            hash,
            fixture.requireString("setId"),
            fixture.requireString("position")
        )
    }

    private fun verifyImage(assets: File, path: String, hash: String) {
        val imageFile = File(assets, path)
        assertTrue("Missing fixture: $path", imageFile.isFile)
        val bytes = imageFile.readBytes()
        assertTrue("Invalid PNG magic: $path", bytes.startsWithPngMagic())
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull("Undecodable PNG: $path", bitmap)
        try {
            assertEquals("Unexpected width: $path", 1080, bitmap!!.width)
            assertEquals("Unexpected height: $path", 2340, bitmap.height)
        } finally {
            bitmap?.recycle()
        }
        assertEquals("SHA-256 mismatch: $path", hash, bytes.sha256())
    }

    private fun assertFixtureCollection(corpus: File, records: List<FixtureRecord>) {
        assertEquals(15, records.map(FixtureRecord::id).toSet().size)
        assertEquals(15, records.map(FixtureRecord::path).toSet().size)
        assertEquals(15, records.map(FixtureRecord::hash).toSet().size)
        val sets = records.groupBy(FixtureRecord::setId)
        assertEquals(5, sets.size)
        assertTrue(sets.values.all { it.map(FixtureRecord::position).toSet() == EXPECTED_POSITIONS })
        assertEquals(EXPECTED_POSITION_COUNTS, records.groupingBy(FixtureRecord::position).eachCount())
        assertEquals(records.map(FixtureRecord::path).toSet(), corpus.listFiles { file -> file.extension == "png" }
            .orEmpty()
            .map { "scan_fixtures/pr06_1080_development/${it.name}" }
            .toSet())
    }

    private fun assertNoPrivateMetadata(
        manifest: JsonObject,
        metadata: JsonObject,
        manifestText: String,
        metadataText: String
    ) {
        val prohibitedKeys = setOf(
            "adbendpoint", "adbip", "accountid", "androidbuildfingerprint",
            "buildfingerprint", "deviceserial", "ipaddress", "port", "serial",
            "stagingdirectory", "sourceabsolutepath",
            "username", "windowsusername", "wifiidentifier"
        )
        val keys = mutableSetOf<String>()
        collectKeys(manifest, keys)
        collectKeys(metadata, keys)
        val foundProhibitedKeys = keys.intersect(prohibitedKeys)
        assertTrue("Prohibited metadata keys: $foundProhibitedKeys", foundProhibitedKeys.isEmpty())

        val text = "$manifestText\n$metadataText"
        assertFalse("Contains an absolute Windows path", Regex("(?i)[a-z]:\\\\").containsMatchIn(text))
        assertFalse(
            "Contains an absolute Unix path",
            Regex("(?m)(?<![A-Za-z0-9_])/Users/|(?<![A-Za-z0-9_])/home/").containsMatchIn(text)
        )
        assertFalse(
            "Contains an IP address and port",
            Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}:\\d{1,5}\\b").containsMatchIn(text)
        )
        assertFalse("Contains a build fingerprint", Regex("(?i)build[ _-]?fingerprint").containsMatchIn(text))
    }

    private fun collectKeys(element: JsonElement, keys: MutableSet<String>) {
        when {
            element.isJsonObject -> element.asJsonObject.entrySet().forEach { (key, value) ->
                keys += key.lowercase()
                collectKeys(value, keys)
            }
            element.isJsonArray -> element.asJsonArray.forEach { collectKeys(it, keys) }
        }
    }

    private fun findRepoRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (File(directory, "app/src/androidTest/assets").isDirectory) return directory
            directory = directory.parentFile ?: return@repeat
        }
        error("Repository root was not found")
    }

    private fun JsonObject.requireArray(name: String) =
        get(name)?.takeUnless { it.isJsonNull }?.asJsonArray ?: error("Missing $name")

    private fun JsonObject.requireObject(name: String) =
        get(name)?.takeUnless { it.isJsonNull }?.asJsonObject ?: error("Missing $name")

    private fun JsonObject.requireString(name: String) =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() } ?: error("Missing $name")

    private fun JsonObject.requireInt(name: String) =
        get(name)?.takeUnless { it.isJsonNull }?.asInt ?: error("Missing $name")

    private fun JsonObject.requireLong(name: String) =
        get(name)?.takeUnless { it.isJsonNull }?.asLong ?: error("Missing $name")

    private fun JsonObject.requireBoolean(name: String) =
        get(name)?.takeUnless { it.isJsonNull }?.asBoolean ?: error("Missing $name")

    private fun ByteArray.startsWithPngMagic() = size >= PNG_MAGIC.size &&
        copyOfRange(0, PNG_MAGIC.size).contentEquals(PNG_MAGIC)

    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02X".format(it) }

    private data class FixtureRecord(
        val id: String,
        val path: String,
        val hash: String,
        val setId: String,
        val position: String
    )

    private companion object {
        val EXPECTED_POSITIONS = setOf("reference", "shifted", "scrolled")
        val EXPECTED_POSITION_COUNTS = mapOf("reference" to 5, "shifted" to 5, "scrolled" to 5)
        val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    }
}
