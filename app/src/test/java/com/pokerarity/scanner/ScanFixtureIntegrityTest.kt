package com.pokerarity.scanner

import android.graphics.BitmapFactory
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ScanFixtureIntegrityTest {

    @Test
    fun activeScanFixturesAreValidPngImages() {
        val result = ScanFixtureIntegrity.scan()

        println("FIXTURE_INTEGRITY ${result.summaryLine()}")
        assertEquals(result.total, result.labeled + result.unlabeled)
        assertEquals(result.total, result.inspected)
        assertTrue(
            "Active fixture integrity failures:\n${result.failures.joinToString("\n")}",
            result.failures.isEmpty()
        )
    }
}

internal object ScanFixtureIntegrity {
    private val pngMagic = byteArrayOf(
        0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    )

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun scan(): FixtureIntegrityResult {
        val repo = findRepoRoot()
        val assets = File(repo, "app/src/androidTest/assets")
        val manifest = File(assets, "scan_regression_cases.json")
        val bytes = manifest.readBytes()
        val hasUtf8Bom = bytes.size >= 3 &&
            bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()
        val offset = if (hasUtf8Bom) 3 else 0
        val json = String(bytes, offset, bytes.size - offset, Charsets.UTF_8)
        val entries = JsonParser.parseString(json).asJsonArray
        val ids = mutableSetOf<String>()
        val paths = mutableSetOf<String>()
        val failures = mutableListOf<String>()
        var inspected = 0
        var labeled = 0
        var unlabeled = 0
        var strict = 0
        var corrupt = 0
        var missing = 0
        var decodeFailures = 0

        entries.forEach { element ->
            inspected++
            val entry = element.asJsonObject
            val id = entry.get("id")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            val assetPath = entry.get("assetPath")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            val expected = entry.getAsJsonObject("expected")
            val expectedSpecies = expected?.get("species")
            when {
                expectedSpecies == null -> failures += "$id: missing expected.species"
                expectedSpecies.isJsonNull -> unlabeled++
                expectedSpecies.asString.isBlank() -> failures += "$id: blank expected.species"
                else -> labeled++
            }
            if (entry.get("strict")?.takeUnless { it.isJsonNull }?.asBoolean == true) strict++

            if (id.isBlank()) failures += "blank id"
            else if (!ids.add(id)) failures += "duplicate id: $id"
            if (assetPath.isBlank()) failures += "$id: blank asset path"
            else if (!paths.add(assetPath)) failures += "$id: duplicate asset path: $assetPath"

            val file = File(assets, assetPath)
            if (!file.isFile) {
                missing++
                failures += "$id: missing: $assetPath"
                return@forEach
            }

            val imageBytes = file.readBytes()
            if (imageBytes.isEmpty() || imageBytes.size < pngMagic.size ||
                !imageBytes.copyOfRange(0, pngMagic.size).contentEquals(pngMagic)
            ) {
                corrupt++
                failures += "$id: invalid PNG magic: $assetPath"
            }

            val image = runCatching { BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) }.getOrNull()
            if (image == null || image.width <= 0 || image.height <= 0) {
                decodeFailures++
                failures += "$id: JVM image decode failed: $assetPath"
            }
        }

        return FixtureIntegrityResult(
            manifestUtf8Bom = hasUtf8Bom,
            total = entries.size(),
            inspected = inspected,
            labeled = labeled,
            unlabeled = unlabeled,
            strict = strict,
            corrupt = corrupt,
            missing = missing,
            decodeFailures = decodeFailures,
            failures = failures.sorted()
        )
    }

    private fun findRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (File(dir, "app/src/androidTest/assets/scan_regression_cases.json").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("Repository root containing scan_regression_cases.json was not found")
    }
}

internal data class FixtureIntegrityResult(
    val manifestUtf8Bom: Boolean,
    val total: Int,
    val inspected: Int,
    val labeled: Int,
    val unlabeled: Int,
    val strict: Int,
    val corrupt: Int,
    val missing: Int,
    val decodeFailures: Int,
    val failures: List<String>
) {
    fun summaryLine(): String =
        "total=$total labeled=$labeled unlabeled=$unlabeled strict=$strict " +
            "corrupt=$corrupt missing=$missing decodeFailures=$decodeFailures utf8Bom=$manifestUtf8Bom"
}
