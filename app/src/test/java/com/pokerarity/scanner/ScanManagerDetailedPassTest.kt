package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.service.ScanManager
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanManagerDetailedPassTest {

    @Test
    fun missingHp_requestsDetailedPass() {
        val pokemon = basePokemon().copy(hp = null, maxHp = null)

        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = pokemon,
            cpQuality = 0.9,
            topTextConfidence = 0.95
        )

        assertTrue(shouldRun)
    }

    @Test
    fun missingOnlyStardust_doesNotRequestDetailedPass() {
        val pokemon = basePokemon().copy(stardust = null)

        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = pokemon,
            cpQuality = 0.9,
            topTextConfidence = 0.95
        )

        assertFalse(shouldRun)
    }

    @Test
    fun missingBothCosts_andWeakName_requestsDetailedPass() {
        val pokemon = basePokemon().copy(stardust = null, powerUpCandyCost = null)

        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = pokemon,
            cpQuality = 0.9,
            topTextConfidence = 0.82
        )

        assertTrue(shouldRun)
    }

    @Test
    fun reliablePrimaryAndSecondaryFields_skipDetailedPass() {
        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = basePokemon(),
            cpQuality = 0.9,
            topTextConfidence = 0.95
        )

        assertFalse(shouldRun)
    }

    @Test
    fun missingCaughtDate_requestsDetailedPass() {
        val pokemon = basePokemon().copy(caughtDate = null)

        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = pokemon,
            cpQuality = 0.9,
            topTextConfidence = 0.95
        )

        assertTrue(shouldRun)
    }

    @Test
    fun screenshotPathSanitizerAcceptsOnlyScanPngsInCacheAndCapsFrames() {
        val cacheDir = Files.createTempDirectory("scan-cache").toFile()
        val valid = (1..4).map { index -> File(cacheDir, "scan_$index.png").apply { writeText("png") } }
        val wrongName = File(cacheDir, "other.png").apply { writeText("png") }
        val outside = Files.createTempFile("scan-outside", ".png").toFile()

        val sanitized = ScanManager.sanitizeScreenshotPaths(
            listOf(valid[0].path, wrongName.path, valid[1].path, outside.path, valid[2].path, valid[3].path),
            cacheDir
        )

        assertEquals(valid.take(3).map { it.canonicalPath }, sanitized)
    }

    @Test
    fun screenshotPathSanitizerRejectsSiblingDirectoryPrefixMatch() {
        val root = Files.createTempDirectory("scan-root").toFile()
        val cacheDir = File(root, "cache").apply { mkdirs() }
        val siblingDir = File(root, "cache_evil").apply { mkdirs() }
        val siblingFile = File(siblingDir, "scan_1.png").apply { writeText("png") }

        val sanitized = ScanManager.sanitizeScreenshotPaths(listOf(siblingFile.path), cacheDir)

        assertTrue(sanitized.isEmpty())
    }

    private fun basePokemon(): PokemonData {
        return PokemonData(
            cp = 1234,
            hp = 120,
            maxHp = 120,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = "Pikachu",
            megaEnergy = null,
            weight = null,
            height = null,
            gender = null,
            stardust = 3000,
            arcLevel = 0.5f,
            caughtDate = java.util.Date(),
            rawOcrText = ""
        )
    }
}
