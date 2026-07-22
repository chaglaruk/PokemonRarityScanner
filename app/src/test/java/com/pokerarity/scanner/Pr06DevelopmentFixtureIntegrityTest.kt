package com.pokerarity.scanner

import android.graphics.BitmapFactory
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class Pr06DevelopmentFixtureIntegrityTest {

    @Test
    fun privacyPatternsRejectProhibitedMetadataText() {
        assertTrue(IPV4_ADDRESS_PATTERN.containsMatchIn("192.168.1.12"))
        assertTrue(IPV4_ADDRESS_PATTERN.containsMatchIn("192.168.1.12:5555"))
        assertTrue(ADB_COMMAND_PATTERN.containsMatchIn("adb connect 192.168.1.12:5555"))
        assertTrue(ADB_ENDPOINT_PATTERN.containsMatchIn("ADB endpoint"))
        assertTrue(DEVICE_SERIAL_PATTERN.containsMatchIn("device serial number"))
        assertTrue(WINDOWS_ABSOLUTE_PATH_PATTERN.containsMatchIn("C:\\Users\\example\\capture.png"))
        assertTrue(UNIX_USER_PATH_PATTERN.containsMatchIn("/Users/example/capture.png"))
        assertTrue(UNIX_USER_PATH_PATTERN.containsMatchIn("/home/example/capture.png"))
        assertTrue(BUILD_FINGERPRINT_PATTERN.containsMatchIn("ro.build.fingerprint=private/device"))
        assertTrue(WINDOWS_ACCOUNT_PATTERN.containsMatchIn("Windows username"))
        assertTrue(WIFI_IDENTIFIER_PATTERN.containsMatchIn("SSID: private-network"))
        assertTrue(EMAIL_ADDRESS_PATTERN.containsMatchIn("user@example.com"))
        assertTrue(PHONE_NUMBER_PATTERN.containsMatchIn("+44 7700 900123"))
        assertTrue(AUTH_CREDENTIAL_PATTERN.containsMatchIn("Authorization: Bearer private-token"))
    }

    @Test
    fun pngAncillaryMetadataParserRejectsPrivateAndMalformedChunks() {
        assertThrows(IllegalArgumentException::class.java) { parsePngChunks(PNG_MAGIC) }
        assertThrows(IllegalArgumentException::class.java) {
            parsePngChunks(PNG_MAGIC + pngChunk("tEXt", byteArrayOf(0x41)))
        }
        listOf(
            pngChunk("tEXt", "Comment\u0000adb connect 192.168.1.12:5555".toByteArray()),
            pngChunk("zTXt", zTextChunkData("Comment", "device serial number: private")),
            pngChunk("iTXt", iTextChunkData("Comment", "Authorization: Bearer private-token")),
        ).forEach { privateChunk ->
            val bytes = PNG_MAGIC + privateChunk + pngChunk("IEND", byteArrayOf())
            assertThrows(AssertionError::class.java) { scanPngAncillaryMetadata(bytes, "synthetic.png") }
        }
    }

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
        assertEquals(1, manifest.requireInt("schemaVersion"))
        assertEquals(1, metadata.requireInt("schemaVersion"))
        assertEquals("pr06_1080_development", manifest.requireString("datasetId"))
        assertEquals("pr06_1080_development", metadata.requireString("datasetId"))
        assertEquals("fixture_manifest.json", metadata.requireString("fixtureManifest"))
        assertEquals("development", manifest.requireString("corpusClass"))
        assertEquals(15, fixtureCount)
        assertEquals(15, metadata.requireInt("fixtureCount"))
        assertEquals("development", metadata.requireString("datasetRole"))
        assertTrue(metadata.requireBoolean("excludedFromHoldout"))
        assertTrue(metadata.requireBoolean("eligibleForDevelopmentMeasurement"))
        assertEquals(EXPECTED_AGGREGATE_BYTES, metadata.requireLong("sourceAggregateBytes"))
        assertTrue(metadata.requireBoolean("futureImmutableHoldoutCorpusSeparate"))
        assertEquals("Samsung", metadata.requireString("manufacturer"))
        assertEquals("SM-S931B", metadata.requireString("model"))
        assertEquals(1080, metadata.requireInt("nativeWidth"))
        assertEquals(2340, metadata.requireInt("nativeHeight"))
        assertFalse(metadata.requireBoolean("displaySizeOverride"))
        assertEquals("en", metadata.requireString("pokemonGoLanguage"))
        assertEquals(EXPECTED_POSITION_COUNTS, metadata.requireIntMap("positionCounts"))
        assertEquals(SOURCE_METADATA_SHA256, metadata.requireString("sourceMetadataSha256"))
        assertEquals(SOURCE_MANIFEST_SHA256, metadata.requireString("sourceManifestSha256"))
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
        assertEquals("Samsung", fixture.requireString("manufacturer"))
        assertEquals("SM-S931B", fixture.requireString("model"))
        assertEquals("en", fixture.requireString("language"))
        assertEquals(PUBLICATION_APPROVAL, fixture.requireString("publicationApproval"))
        assertEquals("permitted", fixture.requireString("normalInGameLocationDateVisible"))
        assertEquals(1080, fixture.requireInt("width"))
        assertEquals(2340, fixture.requireInt("height"))
        assertEquals("1080", fixture.requireString("captureGeometryClass"))
        assertEquals("confirmed", truth.requireString("status"))
        assertEquals("user_confirmed_live_source_screen", truth.requireString("source"))
        assertEquals(
            EXPECTED_TRUTH_BY_SET[fixture.requireString("setId")],
            FixtureTruth(
                truth.requireString("canonicalSpecies"),
                truth.requireString("visibleName"),
                truth.requireString("candyFamily"),
                truth.requireInt("cp"),
                truth.requireInt("hp"),
            ),
        )
        assertTrue(path.startsWith("scan_fixtures/pr06_1080_development/"))
        assertFalse("Absolute path: $path", File(path).isAbsolute)

        val byteCount = verifyImage(assets, path, hash)
        return FixtureRecord(
            id,
            path,
            hash,
            fixture.requireString("setId"),
            fixture.requireString("position"),
            byteCount
        )
    }

    private fun verifyImage(assets: File, path: String, hash: String): Long {
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
        scanPngAncillaryMetadata(bytes, path)
        return bytes.size.toLong()
    }

    private fun assertFixtureCollection(corpus: File, records: List<FixtureRecord>) {
        assertEquals(15, records.map(FixtureRecord::id).toSet().size)
        assertEquals(15, records.map(FixtureRecord::path).toSet().size)
        assertEquals(15, records.map(FixtureRecord::hash).toSet().size)
        val sets = records.groupBy(FixtureRecord::setId)
        assertEquals(5, sets.size)
        sets.forEach { (setId, setRecords) ->
            assertEquals(
                "Unexpected positions for $setId",
                EXPECTED_ONE_OF_EACH_POSITION,
                setRecords.groupingBy(FixtureRecord::position).eachCount(),
            )
        }
        assertEquals(EXPECTED_POSITION_COUNTS, records.groupingBy(FixtureRecord::position).eachCount())
        assertEquals(EXPECTED_AGGREGATE_BYTES, records.sumOf(FixtureRecord::byteCount))
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

        assertNoPrivateText("Fixture metadata", "$manifestText\n$metadataText")
    }

    private fun scanPngAncillaryMetadata(bytes: ByteArray, path: String) {
        parsePngChunks(bytes).filter { it.type in TEXTUAL_PNG_CHUNK_TYPES }.forEach { chunk ->
            assertNoPrivateText("$path ${chunk.type}", chunk.readText(bytes))
        }
    }

    private fun parsePngChunks(bytes: ByteArray): List<PngChunk> {
        require(bytes.size <= MAX_PNG_FILE_BYTES) { "PNG exceeds the fixture size limit" }
        require(bytes.startsWithPngMagic()) { "Invalid PNG signature" }

        val chunks = mutableListOf<PngChunk>()
        var offset = PNG_MAGIC.size
        while (offset < bytes.size) {
            require(bytes.size - offset >= PNG_CHUNK_OVERHEAD) { "Truncated PNG chunk header" }
            val length = bytes.readPngUInt32(offset)
            require(length <= MAX_PNG_CHUNK_BYTES) { "PNG chunk exceeds the size limit" }
            val dataStart = offset + PNG_CHUNK_HEADER_SIZE
            val dataEnd = dataStart + length.toInt()
            val chunkEnd = dataEnd + PNG_CHUNK_CRC_SIZE
            require(chunkEnd <= bytes.size) { "Truncated PNG chunk data or CRC" }

            val type = String(bytes, offset + PNG_CHUNK_LENGTH_SIZE, PNG_CHUNK_TYPE_SIZE, ASCII)
            chunks += PngChunk(type, dataStart, length.toInt())
            offset = chunkEnd
            if (type == IEND) {
                require(length == 0L) { "IEND must be empty" }
                require(offset == bytes.size) { "PNG contains data after IEND" }
                return chunks
            }
        }
        throw IllegalArgumentException("PNG is missing IEND")
    }

    private fun PngChunk.readText(bytes: ByteArray): String = when (type) {
        "tEXt", "eXIf" -> bytes.readLatin1(dataStart, length)
        "zTXt" -> {
            val separator = bytes.indexOfNull(dataStart, dataStart + length)
            require(separator >= dataStart && separator + 1 < dataStart + length) { "Malformed zTXt" }
            require(bytes[separator + 1].toInt() == 0) { "Unsupported zTXt compression method" }
            bytes.readLatin1(dataStart, separator - dataStart) + "\n" +
                bytes.inflateLatin1(separator + 2, dataStart + length)
        }
        "iTXt" -> bytes.readInternationalText(dataStart, dataStart + length)
        else -> error("Unexpected PNG text chunk: $type")
    }

    private fun ByteArray.readInternationalText(start: Int, end: Int): String {
        val keywordEnd = indexOfNull(start, end)
        require(keywordEnd >= start && keywordEnd + 2 < end) { "Malformed iTXt" }
        val compressed = this[keywordEnd + 1].toInt()
        require(compressed == 0 || compressed == 1) { "Unsupported iTXt compression flag" }
        require(this[keywordEnd + 2].toInt() == 0) { "Unsupported iTXt compression method" }
        val languageEnd = indexOfNull(keywordEnd + 3, end)
        require(languageEnd >= keywordEnd + 3) { "Malformed iTXt language tag" }
        val translatedEnd = indexOfNull(languageEnd + 1, end)
        require(translatedEnd >= languageEnd + 1) { "Malformed iTXt translated keyword" }
        val textStart = translatedEnd + 1
        val text = if (compressed == 1) inflateLatin1(textStart, end) else readLatin1(textStart, end - textStart)
        return readLatin1(start, keywordEnd - start) + "\n" +
            readLatin1(keywordEnd + 3, languageEnd - keywordEnd - 3) + "\n" +
            readLatin1(languageEnd + 1, translatedEnd - languageEnd - 1) + "\n" + text
    }

    private fun ByteArray.inflateLatin1(start: Int, end: Int): String {
        require(end - start <= MAX_TEXT_METADATA_BYTES) { "Compressed PNG text exceeds the size limit" }
        val output = ByteArrayOutputStream()
        InflaterInputStream(ByteArrayInputStream(copyOfRange(start, end))).use { input ->
            val buffer = ByteArray(INFLATE_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                require(output.size() + read <= MAX_TEXT_METADATA_BYTES) { "Expanded PNG text exceeds the size limit" }
                output.write(buffer, 0, read)
            }
        }
        return output.toString(LATIN1.name())
    }

    private fun ByteArray.readLatin1(start: Int, length: Int): String {
        require(length <= MAX_TEXT_METADATA_BYTES) { "PNG text exceeds the size limit" }
        return String(this, start, length, LATIN1)
    }

    private fun ByteArray.indexOfNull(start: Int, end: Int): Int {
        for (index in start until end) if (this[index].toInt() == 0) return index
        return -1
    }

    private fun ByteArray.readPngUInt32(offset: Int): Long =
        ((this[offset].toInt() and 0xFF).toLong() shl 24) or
            ((this[offset + 1].toInt() and 0xFF).toLong() shl 16) or
            ((this[offset + 2].toInt() and 0xFF).toLong() shl 8) or
            (this[offset + 3].toInt() and 0xFF).toLong()

    private fun assertNoPrivateText(source: String, text: String) {
        PRIVATE_TEXT_PATTERNS.forEach { (description, pattern) ->
            assertFalse("$source contains $description", pattern.containsMatchIn(text))
        }
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

    private fun JsonObject.requireIntMap(name: String) =
        requireObject(name).entrySet().associate { (key, value) -> key to value.asInt }

    private fun JsonObject.requireBoolean(name: String) =
        get(name)?.takeUnless { it.isJsonNull }?.asBoolean ?: error("Missing $name")

    private fun ByteArray.startsWithPngMagic() = size >= PNG_MAGIC.size &&
        copyOfRange(0, PNG_MAGIC.size).contentEquals(PNG_MAGIC)

    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02X".format(it) }

    private fun pngChunk(type: String, data: ByteArray): ByteArray {
        val bytes = ByteArray(PNG_CHUNK_OVERHEAD + data.size)
        bytes.writePngUInt32(0, data.size.toLong())
        type.toByteArray(ASCII).copyInto(bytes, PNG_CHUNK_LENGTH_SIZE)
        data.copyInto(bytes, PNG_CHUNK_HEADER_SIZE)
        return bytes
    }

    private fun zTextChunkData(keyword: String, text: String): ByteArray =
        keyword.toByteArray(LATIN1) + byteArrayOf(0, 0) + deflate(text)

    private fun iTextChunkData(keyword: String, text: String): ByteArray =
        keyword.toByteArray(UTF8) + byteArrayOf(0, 1, 0, 0, 0) + deflate(text)

    private fun deflate(text: String): ByteArray {
        val output = ByteArrayOutputStream()
        DeflaterOutputStream(output).use { it.write(text.toByteArray(LATIN1)) }
        return output.toByteArray()
    }

    private fun ByteArray.writePngUInt32(offset: Int, value: Long) {
        this[offset] = (value shr 24).toByte()
        this[offset + 1] = (value shr 16).toByte()
        this[offset + 2] = (value shr 8).toByte()
        this[offset + 3] = value.toByte()
    }

    private data class FixtureRecord(
        val id: String,
        val path: String,
        val hash: String,
        val setId: String,
        val position: String,
        val byteCount: Long
    )

    private data class FixtureTruth(
        val canonicalSpecies: String,
        val visibleName: String,
        val candyFamily: String,
        val cp: Int,
        val hp: Int,
    )

    private data class PngChunk(val type: String, val dataStart: Int, val length: Int)

    private companion object {
        const val EXPECTED_AGGREGATE_BYTES = 8_725_743L
        const val SOURCE_METADATA_SHA256 = "1057AC98F1702D8B484C3A5AF727FDAD4C7520EAE455A98BFCDA05423575954E"
        const val SOURCE_MANIFEST_SHA256 = "4FAD46777EB5B77D581416E3B82ADC0E691EA1EDC9023C97A91360B46EADC06B"
        const val PUBLICATION_APPROVAL = "user_authorized_public_development_fixture_corpus"
        const val MAX_PNG_FILE_BYTES = 16 * 1024 * 1024
        const val MAX_PNG_CHUNK_BYTES = 8 * 1024 * 1024L
        const val MAX_TEXT_METADATA_BYTES = 1024 * 1024
        const val INFLATE_BUFFER_BYTES = 8 * 1024
        const val PNG_CHUNK_LENGTH_SIZE = 4
        const val PNG_CHUNK_TYPE_SIZE = 4
        const val PNG_CHUNK_HEADER_SIZE = PNG_CHUNK_LENGTH_SIZE + PNG_CHUNK_TYPE_SIZE
        const val PNG_CHUNK_CRC_SIZE = 4
        const val PNG_CHUNK_OVERHEAD = PNG_CHUNK_HEADER_SIZE + PNG_CHUNK_CRC_SIZE
        const val IEND = "IEND"
        val ASCII = StandardCharsets.US_ASCII
        val LATIN1 = StandardCharsets.ISO_8859_1
        val UTF8 = StandardCharsets.UTF_8
        val EXPECTED_ONE_OF_EACH_POSITION = mapOf("reference" to 1, "shifted" to 1, "scrolled" to 1)
        val EXPECTED_POSITION_COUNTS = mapOf("reference" to 5, "shifted" to 5, "scrolled" to 5)
        val EXPECTED_TRUTH_BY_SET = mapOf(
            "pokemonSet01" to FixtureTruth("Pikipek", "Pikipek", "Pikipek Candy", 236, 51),
            "pokemonSet02" to FixtureTruth("Torchic", "Torchic", "Torchic Candy", 581, 84),
            "pokemonSet03" to FixtureTruth("Farfetch'd", "Farfetch'd", "Farfetch'd Candy", 468, 69),
            "pokemonSet04" to FixtureTruth("Skwovet", "Skwovet", "Skwovet Candy", 734, 133),
            "pokemonSet05" to FixtureTruth("Chikorita", "Chikorita", "Chikorita Candy", 340, 70),
        )
        val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val TEXTUAL_PNG_CHUNK_TYPES = setOf("tEXt", "zTXt", "iTXt", "eXIf")
        const val IPV4_OCTET = "(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)"
        val IPV4_ADDRESS_PATTERN = Regex("\\b$IPV4_OCTET(?:\\.$IPV4_OCTET){3}(?::\\d{1,5})?\\b")
        val ADB_COMMAND_PATTERN = Regex("(?i)\\badb(?:\\.exe)?\\s+(?:connect|disconnect|shell|tcpip)\\b")
        val ADB_ENDPOINT_PATTERN = Regex("(?i)\\badb[ _-]?(?:endpoint|ip|serial)\\b")
        val DEVICE_SERIAL_PATTERN = Regex(
            "(?i)\\b(?:android[ _-]?serial|device[ _-]?serial|serial[ _-]?(?:number|no\\.?|id))\\b",
        )
        val WINDOWS_ABSOLUTE_PATH_PATTERN = Regex("(?i)(?:\\b[a-z]:[\\\\/]|\\\\\\\\[^\\\\/\\s]+[\\\\/])")
        val UNIX_USER_PATH_PATTERN = Regex("/(?:Users|home)/")
        val BUILD_FINGERPRINT_PATTERN = Regex("(?i)(?:ro\\.build\\.fingerprint|build[ _-]?fingerprint)")
        val WINDOWS_ACCOUNT_PATTERN = Regex(
            "(?i)\\b(?:windows[ _-]?(?:user|username|account)|user[ _-]?name|account[ _-]?(?:id|identifier))\\b",
        )
        val WIFI_IDENTIFIER_PATTERN = Regex("(?i)\\b(?:wi-?fi|wlan|bssid|ssid)\\b")
        val EMAIL_ADDRESS_PATTERN = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
        val PHONE_NUMBER_PATTERN = Regex("(?<!\\d)(?:\\+?\\d{1,3}[ .-])?(?:\\(?\\d{2,4}\\)?[ .-]){1,2}\\d{3,6}(?!\\d)")
        val AUTH_CREDENTIAL_PATTERN = Regex(
            "(?i)(?:\\bauthorization\\s*:|\\b(?:access[ _-]?token|auth[ _-]?token|api[ _-]?key|bearer)\\b)",
        )
        val PRIVATE_TEXT_PATTERNS = listOf(
            "an IPv4 address" to IPV4_ADDRESS_PATTERN,
            "an ADB command" to ADB_COMMAND_PATTERN,
            "an ADB endpoint" to ADB_ENDPOINT_PATTERN,
            "a device serial" to DEVICE_SERIAL_PATTERN,
            "an absolute Windows path" to WINDOWS_ABSOLUTE_PATH_PATTERN,
            "an absolute Unix path" to UNIX_USER_PATH_PATTERN,
            "a build fingerprint" to BUILD_FINGERPRINT_PATTERN,
            "a Windows username or account identifier" to WINDOWS_ACCOUNT_PATTERN,
            "a Wi-Fi identifier" to WIFI_IDENTIFIER_PATTERN,
            "an email address" to EMAIL_ADDRESS_PATTERN,
            "a phone number" to PHONE_NUMBER_PATTERN,
            "an authentication credential" to AUTH_CREDENTIAL_PATTERN,
        )
    }
}
