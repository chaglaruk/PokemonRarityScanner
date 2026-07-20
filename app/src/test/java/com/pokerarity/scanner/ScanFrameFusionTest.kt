package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFrameFusionTest {

    @Test
    fun bestFrameSelectedByStrongerCpNameDateQuality() {
        val weakFrame = frame(
            path = "weak.png",
            data = pokemon(PokemonConfig(
                cp = 42, hp = null, maxHp = null, name = "Unknown", realName = "Unknown", caughtDate = null
            )),
            cpQuality = 0.95
        )
        val strongFrame = frame(
            path = "strong.png",
            data = pokemon(PokemonConfig(
                cp = 621, name = "Pikachu", realName = "Pikachu", caughtDate = defaultCaughtDate
            )),
            cpQuality = 0.60
        )

        val selected = ScanFrameFusion.selectBestFrame(listOf(weakFrame, strongFrame))

        assertEquals("strong.png", selected?.path)
    }

    @Test
    fun oneWrongSingleFrameSpeciesDoesNotBeatRepeatedBetterSpecies() {
        val wrongSingle = frame(
            path = "wrong.png",
            data = pokemon(PokemonConfig(cp = 3100, name = "Mewtwo", realName = "Mewtwo")),
            cpQuality = 0.95
        )
        val repeatedOne = frame(
            path = "repeated-1.png",
            data = pokemon(PokemonConfig(cp = 621, name = "Pikachu", realName = "Pikachu")),
            cpQuality = 0.88
        )
        val repeatedTwo = frame(
            path = "repeated-2.png",
            data = pokemon(PokemonConfig(cp = 621, name = "Pikachu", realName = "Pikachu")),
            cpQuality = 0.86
        )

        val selected = ScanFrameFusion.selectBestFrame(listOf(wrongSingle, repeatedOne, repeatedTwo))

        assertEquals("repeated-1.png", selected?.path)
    }

    @Test
    fun repeatedSpeciesWithCpAndSupportIsHighConfidence() {
        val pika = pokemon(PokemonConfig(cp = 621, name = "Pikachu", realName = "Pikachu"))
        val frames = listOf(
            frame("first.png", pika, cpQuality = 0.88),
            frame("second.png", pika, cpQuality = 0.86)
        )

        assertTrue(ScanFrameFusion.isHighConfidence(frames))
    }

    @Test
    fun reviewedAliasAgreementCanTriggerHighConfidenceEarlyExit() {
        val alias = evidence(authority = SpeciesAuthority.REVIEWED_ALIAS)
        val frames = listOf(
            frame("first.png", pokemon(), cpQuality = 0.88, speciesEvidence = alias),
            frame("second.png", pokemon(), cpQuality = 0.86, speciesEvidence = alias)
        )

        assertTrue(ScanFrameFusion.isHighConfidence(frames))
    }

    @Test
    fun safeFuzzyAgreementDoesNotTriggerHighConfidenceEarlyExit() {
        val fuzzy = pokemon(PokemonConfig(
            cp = 1488,
            hp = 132,
            maxHp = 132,
            name = "Poliwrath",
            realName = "Poliwrath",
            rawOcrText = "CP:1488|HP:132/132|Name:Poliwrat|NameHC:Poliwrat"
        ))
        val frames = listOf(
            frame(
                "first.png",
                fuzzy,
                cpQuality = 0.88,
                speciesEvidence = evidence("Poliwrath", SpeciesAuthority.SAFE_FUZZY)
            ),
            frame(
                "second.png",
                fuzzy,
                cpQuality = 0.86,
                speciesEvidence = evidence("Poliwrath", SpeciesAuthority.SAFE_FUZZY)
            )
        )

        assertFalse(ScanFrameFusion.isHighConfidence(frames))
    }

    @Test
    fun uncertainNoMatchAndConflictNeverTriggerHighConfidenceEarlyExit() {
        val blockedAuthorities = listOf(
            SpeciesAuthority.UNCERTAIN,
            SpeciesAuthority.NO_MATCH,
            SpeciesAuthority.CONFLICT
        )

        blockedAuthorities.forEach { authority ->
            val blocked = evidence(
                authority = authority,
                tuning = EvidenceTuning(
                    conflict = authority == SpeciesAuthority.CONFLICT,
                    observationsAgree = authority != SpeciesAuthority.CONFLICT
                )
            )
            val frames = listOf(
                frame("first.png", pokemon(), 0.88, blocked),
                frame("second.png", pokemon(), 0.86, blocked)
            )

            assertFalse(authority.name, ScanFrameFusion.isHighConfidence(frames))
        }
    }

    @Test
    fun closeMarginAndBadProfilesNeverTriggerHighConfidenceEarlyExit() {
        val blocked = listOf(
            evidence(tuning = EvidenceTuning(candidatesClose = true)),
            evidence(tuning = EvidenceTuning(profileStatus = SpeciesProfileStatus.MISSING)),
            evidence(tuning = EvidenceTuning(profileStatus = SpeciesProfileStatus.CONTRADICTORY)),
            evidence(tuning = EvidenceTuning(profileStatus = SpeciesProfileStatus.IMPOSSIBLE)),
            evidence(tuning = EvidenceTuning(profileStatus = SpeciesProfileStatus.INDETERMINATE))
        )

        blocked.forEach { speciesEvidence ->
            val frames = listOf(
                frame("first.png", pokemon(), 0.88, speciesEvidence),
                frame("second.png", pokemon(), 0.86, speciesEvidence)
            )

            assertFalse(speciesEvidence.toString(), ScanFrameFusion.isHighConfidence(frames))
        }
    }

    @Test
    fun matchingFirstAndThirdFramesCannotHideConflictingMiddleSpecies() {
        val pika = pokemon(PokemonConfig(name = "Pikachu", realName = "Pikachu"))
        val raichu = pokemon(PokemonConfig(name = "Raichu", realName = "Raichu"))
        val frames = listOf(
            frame("first.png", pika, 0.88, evidence("Pikachu")),
            frame("middle.png", raichu, 0.87, evidence("Raichu")),
            frame("third.png", pika, 0.86, evidence("Pikachu"))
        )

        assertFalse(ScanFrameFusion.isHighConfidence(frames))
    }

    @Test
    fun blockerReasonCodesCoverAuthorityMarginProfileAndDetailedRequest() {
        val fuzzy = evidence(authority = SpeciesAuthority.SAFE_FUZZY)
        val close = evidence(tuning = EvidenceTuning(candidatesClose = true))
        val missingProfile = evidence(tuning = EvidenceTuning(profileStatus = SpeciesProfileStatus.MISSING))

        assertEquals(
            listOf(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_AUTHORITY),
            ScanFrameFusion.earlyExitBlockReasons(listOf(frame("fuzzy.png", pokemon(), 0.88, fuzzy)))
        )
        assertEquals(
            listOf(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_MARGIN),
            ScanFrameFusion.earlyExitBlockReasons(listOf(frame("close.png", pokemon(), 0.88, close)))
        )
        assertEquals(
            listOf(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_PROFILE),
            ScanFrameFusion.earlyExitBlockReasons(
                listOf(frame("profile.png", pokemon(), 0.88, missingProfile))
            )
        )
        assertEquals(
            listOf(
                SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_AUTHORITY,
                SpeciesEvidenceReason.DETAILED_PASS_REQUESTED
            ),
            ScanFrameFusion.detailedPassReasons(fuzzy)
        )
    }

    @Test
    fun fusionKeepsPrimaryFieldsAndBackfillsSecondaryFields() {
        val authoritative = pokemon(PokemonConfig(
            cp = 501,
            hp = 80,
            maxHp = 100,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = null,
            stardust = null,
            weight = null,
            height = null,
            rawOcrText = "CP:501|HP:80/100|Name:Pikachu|NameHC:Pikachu"
        ))
        val detailed = pokemon(PokemonConfig(
            cp = 999,
            hp = 77,
            maxHp = 101,
            name = "Raichu",
            realName = "Raichu",
            candyName = "Pikachu",
            stardust = 3000,
            weight = 6.1f,
            height = 0.4f,
            rawOcrText = "CP:999|HP:77/101|Name:Raichu|Candy:Pikachu Candy|Dust:3000"
        ))
        val frames = listOf(frame("best.png", authoritative, cpQuality = 0.90))

        val fused = ScanFrameFusion.fuse(
            frames = frames,
            authoritative = authoritative,
            detailed = detailed,
            validCpList = ScanFrameFusion.validCpCandidates(frames),
            bestCpQuality = 0.90
        )

        assertEquals(501, fused.cp)
        assertEquals("Pikachu", fused.name)
        assertEquals("Pikachu", fused.realName)
        assertEquals(80, fused.hp)
        assertEquals(100, fused.maxHp)
        assertEquals("Pikachu", fused.candyName)
        assertEquals(3000, fused.stardust)
        assertEquals(6.1f, fused.weight)
        assertEquals(0.4f, fused.height)
        assertTrue(fused.rawOcrText.contains("CP:501"))
        assertTrue(fused.rawOcrText.contains("Name:Pikachu"))
        assertTrue(fused.rawOcrText.contains("Candy:Pikachu Candy"))
    }

    @Test
    fun weakNoisyFrameDoesNotOverrideStrongFrame() {
        val strong = pokemon(PokemonConfig(cp = 700, name = "Pikachu", realName = "Pikachu"))
        val noisy = pokemon(PokemonConfig(cp = 999, name = "Unknown", realName = "Unknown", hp = null, maxHp = null))
        val frames = listOf(
            frame("strong.png", strong, cpQuality = 0.90),
            frame("noisy.png", noisy, cpQuality = 0.10)
        )

        val fused = ScanFrameFusion.fuse(
            frames = frames,
            authoritative = strong,
            detailed = noisy,
            validCpList = ScanFrameFusion.validCpCandidates(frames),
            bestCpQuality = 0.90
        )

        assertEquals(700, fused.cp)
        assertEquals("Pikachu", fused.name)
        assertEquals("Pikachu", fused.realName)
    }

    @Test
    fun twoFrameFusionBackfillsDateAndSecondaryFieldsWithoutReplacingPrimaryFields() {
        val primary = pokemon(PokemonConfig(
            cp = 480,
            hp = 70,
            maxHp = 80,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = null,
            stardust = null,
            caughtDate = null,
            rawOcrText = "CP:480|HP:70/80|Name:Pikachu|NameHC:Pikachu"
        ))
        val supportingFrame = pokemon(PokemonConfig(
            cp = 480,
            hp = 70,
            maxHp = 80,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = null,
            stardust = 2200,
            caughtDate = supportingCaughtDate,
            rawOcrText = "CP:480|HP:70/80|Name:Pikachu|Date:2023-11-14"
        ))
        val detailed = pokemon(PokemonConfig(
            cp = 999,
            hp = 12,
            maxHp = 12,
            name = "Raichu",
            realName = "Raichu",
            candyName = "Pikachu",
            stardust = 5000,
            weight = 6.2f,
            height = 0.4f,
            caughtDate = detailedCaughtDate,
            rawOcrText = "CP:999|HP:12/12|Name:Raichu|Candy:Pikachu Candy|Weight:6.2|Height:0.4"
        ))
        val frames = listOf(
            frame("primary.png", primary, cpQuality = 0.90),
            frame("supporting.png", supportingFrame, cpQuality = 0.88)
        )

        val fused = ScanFrameFusion.fuse(
            frames = frames,
            authoritative = primary,
            detailed = detailed,
            validCpList = ScanFrameFusion.validCpCandidates(frames),
            bestCpQuality = 0.90
        )

        assertEquals(480, fused.cp)
        assertEquals(70, fused.hp)
        assertEquals(80, fused.maxHp)
        assertEquals("Pikachu", fused.name)
        assertEquals("Pikachu", fused.realName)
        assertEquals(supportingCaughtDate, fused.caughtDate)
        assertEquals("Pikachu", fused.candyName)
        assertEquals(2200, fused.stardust)
        assertEquals(6.2f, fused.weight)
        assertEquals(0.4f, fused.height)
        assertTrue(fused.rawOcrText.contains("CP:480"))
        assertTrue(fused.rawOcrText.contains("Candy:Pikachu Candy"))
    }

    @Test
    fun unknownPrimarySpeciesUsesKnownNameFromSecondFrame() {
        val primary = pokemon(PokemonConfig(
            cp = 510,
            hp = 61,
            maxHp = 70,
            name = "Unknown",
            realName = "Unknown",
            caughtDate = defaultCaughtDate,
            rawOcrText = "CP:510|HP:61/70|Name:Unknown|NameHC:"
        ))
        val knownFrame = pokemon(PokemonConfig(
            cp = 510,
            hp = 61,
            maxHp = 70,
            name = "Bulbasaur",
            realName = "Bulbasaur",
            caughtDate = defaultCaughtDate,
            rawOcrText = "CP:510|HP:61/70|Name:Bulbasaur|NameHC:Bulbasaur"
        ))
        val frames = listOf(
            frame("unknown.png", primary, cpQuality = 0.90),
            frame("known.png", knownFrame, cpQuality = 0.87)
        )

        val fused = ScanFrameFusion.fuse(
            frames = frames,
            authoritative = primary,
            detailed = primary,
            validCpList = ScanFrameFusion.validCpCandidates(frames),
            bestCpQuality = 0.90
        )

        assertEquals(510, fused.cp)
        assertEquals(61, fused.hp)
        assertEquals(70, fused.maxHp)
        assertEquals("Bulbasaur", fused.name)
        assertEquals("Bulbasaur", fused.realName)
        assertEquals(defaultCaughtDate, fused.caughtDate)
    }

    @Test
    fun weakSecondFrameCpAndUnknownNameDoNotOverrideStrongFrameFields() {
        val strong = pokemon(PokemonConfig(
            cp = 721,
            hp = 88,
            maxHp = 96,
            name = "Squirtle",
            realName = "Squirtle",
            caughtDate = defaultCaughtDate,
            rawOcrText = "CP:721|HP:88/96|Name:Squirtle|NameHC:Squirtle"
        ))
        val weak = pokemon(PokemonConfig(
            cp = 999,
            hp = 1,
            maxHp = 1,
            name = "Unknown",
            realName = "Unknown",
            caughtDate = weakCaughtDate,
            rawOcrText = "CP:999|HP:1/1|Name:Unknown|NameHC:"
        ))
        val frames = listOf(
            frame("strong.png", strong, cpQuality = 0.91),
            frame("weak.png", weak, cpQuality = 0.20)
        )

        val fused = ScanFrameFusion.fuse(
            frames = frames,
            authoritative = strong,
            detailed = weak,
            validCpList = ScanFrameFusion.validCpCandidates(frames),
            bestCpQuality = 0.91
        )

        assertEquals(721, fused.cp)
        assertEquals(88, fused.hp)
        assertEquals(96, fused.maxHp)
        assertEquals("Squirtle", fused.name)
        assertEquals("Squirtle", fused.realName)
        assertEquals(defaultCaughtDate, fused.caughtDate)
    }

    private fun frame(
        path: String,
        data: PokemonData,
        cpQuality: Double,
        speciesEvidence: SpeciesEvidence = evidence(data.realName ?: data.name)
    ): ScanFrameCandidate {
        return ScanFrameCandidate(path, data, cpQuality, speciesEvidence)
    }

    private val supportingCaughtDate = Date(1_700_086_400_000L)
    private val detailedCaughtDate = Date(1_700_172_800_000L)
    private val weakCaughtDate = Date(1_700_259_200_000L)
}

internal data class EvidenceTuning(
    val profileStatus: SpeciesProfileStatus = SpeciesProfileStatus.COMPATIBLE,
    val candidatesClose: Boolean = false,
    val conflict: Boolean = false,
    val observationsAgree: Boolean = true
)

internal val defaultCaughtDate = Date(1_700_000_000_000L)

internal fun evidence(
    species: String? = "Pikachu",
    authority: SpeciesAuthority = SpeciesAuthority.EXACT_CANONICAL,
    tuning: EvidenceTuning = EvidenceTuning()
): SpeciesEvidence {
    if (species.isNullOrBlank() || species.equals("Unknown", ignoreCase = true)) {
        return SpeciesEvidence.failClosed(tuning.profileStatus)
    }
    return SpeciesEvidence(
        selectedCanonicalSpecies = species,
        authority = authority,
        profileStatus = tuning.profileStatus,
        reasonCodes = emptyList(),
        observationsAgree = tuning.observationsAgree,
        authorityConflict = tuning.conflict,
        candidatesClose = tuning.candidatesClose
    )
}

internal data class PokemonConfig(
    val cp: Int? = 621,
    val hp: Int? = 84,
    val maxHp: Int? = 84,
    val name: String? = "Pikachu",
    val realName: String? = name,
    val candyName: String? = "Pikachu",
    val stardust: Int? = 2500,
    val weight: Float? = null,
    val height: Float? = null,
    val arcLevel: Float? = 0.5f,
    val caughtDate: Date? = defaultCaughtDate,
    val rawOcrText: String? = null
)

internal fun pokemon(config: PokemonConfig = PokemonConfig()): PokemonData {
    val cp = config.cp
    val hp = config.hp
    val maxHp = config.maxHp
    val name = config.name
    val realName = config.realName ?: name
    val rawOcr = config.rawOcrText ?: buildString {
        append("CP:").append(cp ?: "").append("|")
        append("HP:").append(hp ?: "").append("/").append(maxHp ?: "").append("|")
        append("Name:").append(name.orEmpty()).append("|")
        append("NameHC:").append(realName.orEmpty())
    }
    return PokemonData(
        cp = cp,
        hp = hp,
        maxHp = maxHp,
        name = name,
        realName = realName,
        candyName = config.candyName,
        megaEnergy = null,
        weight = config.weight,
        height = config.height,
        gender = null,
        stardust = config.stardust,
        arcLevel = config.arcLevel,
        caughtDate = config.caughtDate,
        rawOcrText = rawOcr
    )
}
