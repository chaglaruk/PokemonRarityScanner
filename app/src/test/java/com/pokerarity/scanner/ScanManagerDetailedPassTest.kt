package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
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
            speciesEvidence = hardEvidence()
        )

        assertTrue(shouldRun)
    }

    @Test
    fun missingOnlyStardust_doesNotRequestDetailedPass() {
        val pokemon = basePokemon().copy(stardust = null)

        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = pokemon,
            cpQuality = 0.9,
            speciesEvidence = hardEvidence()
        )

        assertFalse(shouldRun)
    }

    @Test
    fun missingBothCosts_andWeakName_requestsDetailedPass() {
        val pokemon = basePokemon().copy(stardust = null, powerUpCandyCost = null)

        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = pokemon,
            cpQuality = 0.9,
            speciesEvidence = softEvidence()
        )

        assertTrue(shouldRun)
    }

    @Test
    fun reliablePrimaryAndSecondaryFields_skipDetailedPass() {
        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = basePokemon(),
            cpQuality = 0.9,
            speciesEvidence = hardEvidence()
        )

        assertFalse(shouldRun)
    }

    @Test
    fun strongNumericAndRepeatedFuzzyNameStillRequestsDetailedPass() {
        val pokemon = basePokemon().copy(
            name = "Poliwrath",
            realName = "Poliwrath",
            rawOcrText = "CP:1234|HP:120/120|Name:Poliwrat|NameHC:Poliwrat"
        )

        val shouldRun = ScanFrameFusion.shouldRunDetailedPass(
            pokemon = pokemon,
            cpQuality = 0.9,
            speciesEvidence = SpeciesEvidence(
                selectedCanonicalSpecies = "Poliwrath",
                authority = SpeciesAuthority.SAFE_FUZZY,
                profileStatus = SpeciesProfileStatus.COMPATIBLE,
                reasonCodes = emptyList(),
                observationsAgree = true,
                authorityConflict = false
            )
        )

        assertTrue(shouldRun)
    }

    @Test
    fun missingCaughtDate_requestsDetailedPass() {
        val pokemon = basePokemon().copy(caughtDate = null)

        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = pokemon,
            cpQuality = 0.9,
            speciesEvidence = hardEvidence()
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

    // ── Seam tests: structured authority must not consume topCandidateScore ──

    @Test
    fun reviewedAliasWithAuthorityBandedScoreSkipsDetailedPass() {
        // REVIEWED_ALIAS topCandidateScore is authority-banded (0.75-0.80) and
        // must not be consumed as legacy topTextConfidence. Complete hard
        // authority evidence must skip the detailed pass.
        listOf(0.75f, 0.80f).forEach { banded ->
            val evidence = hardEvidence().copy(
                authority = SpeciesAuthority.REVIEWED_ALIAS,
                topCandidateScore = banded
            )
            val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
                pokemon = basePokemon(),
                cpQuality = 0.9,
                speciesEvidence = evidence
            )
            assertFalse("REVIEWED_ALIAS topCandidateScore=$banded must skip detailed pass", shouldRun)
        }
    }

    @Test
    fun exactCanonicalWithAuthorityBandedScoreSkipsDetailedPass() {
        val evidence = hardEvidence().copy(topCandidateScore = 0.95f)
        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = basePokemon(),
            cpQuality = 0.9,
            speciesEvidence = evidence
        )
        assertFalse("EXACT_CANONICAL with complete evidence must skip detailed pass", shouldRun)
    }

    @Test
    fun reviewedAliasCloseCandidateMarginStillRequestsDetailedPass() {
        val evidence = hardEvidence().copy(
            authority = SpeciesAuthority.REVIEWED_ALIAS,
            candidatesClose = true
        )
        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = basePokemon(),
            cpQuality = 0.9,
            speciesEvidence = evidence
        )
        assertTrue("Close candidate margin must request detailed pass", shouldRun)
    }

    @Test
    fun exactCanonicalCloseCandidateMarginStillRequestsDetailedPass() {
        val evidence = hardEvidence().copy(candidatesClose = true)
        val shouldRun = ScanManager.shouldRunDetailedPassForAuthoritative(
            pokemon = basePokemon(),
            cpQuality = 0.9,
            speciesEvidence = evidence
        )
        assertTrue("Close candidate margin must request detailed pass", shouldRun)
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

    private fun hardEvidence(): SpeciesEvidence = SpeciesEvidence(
        selectedCanonicalSpecies = "Pikachu",
        authority = SpeciesAuthority.EXACT_CANONICAL,
        profileStatus = SpeciesProfileStatus.COMPATIBLE,
        reasonCodes = emptyList(),
        observationsAgree = true,
        authorityConflict = false,
        topCandidateScore = 0.95f
    )

    private fun softEvidence(): SpeciesEvidence = hardEvidence().copy(
        authority = SpeciesAuthority.SAFE_FUZZY,
        topCandidateScore = 0.82f
    )
}
