package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
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
            data = pokemon(cp = 42, hp = null, maxHp = null, name = "Unknown", realName = "Unknown", caughtDate = null),
            cpQuality = 0.95
        )
        val strongFrame = frame(
            path = "strong.png",
            data = pokemon(cp = 621, name = "Pikachu", realName = "Pikachu", caughtDate = defaultCaughtDate),
            cpQuality = 0.60
        )

        val selected = ScanFrameFusion.selectBestFrame(listOf(weakFrame, strongFrame))

        assertEquals("strong.png", selected?.path)
    }

    @Test
    fun oneWrongSingleFrameSpeciesDoesNotBeatRepeatedBetterSpecies() {
        val wrongSingle = frame(
            path = "wrong.png",
            data = pokemon(cp = 3100, name = "Mewtwo", realName = "Mewtwo"),
            cpQuality = 0.95
        )
        val repeatedOne = frame(
            path = "repeated-1.png",
            data = pokemon(cp = 621, name = "Pikachu", realName = "Pikachu"),
            cpQuality = 0.88
        )
        val repeatedTwo = frame(
            path = "repeated-2.png",
            data = pokemon(cp = 621, name = "Pikachu", realName = "Pikachu"),
            cpQuality = 0.86
        )

        val selected = ScanFrameFusion.selectBestFrame(listOf(wrongSingle, repeatedOne, repeatedTwo))

        assertEquals("repeated-1.png", selected?.path)
    }

    @Test
    fun repeatedSpeciesWithCpAndSupportIsHighConfidence() {
        val frames = listOf(
            frame("first.png", pokemon(cp = 621, name = "Pikachu", realName = "Pikachu"), cpQuality = 0.88),
            frame("second.png", pokemon(cp = 621, name = "Pikachu", realName = "Pikachu"), cpQuality = 0.86)
        )

        assertTrue(ScanFrameFusion.isHighConfidence(frames))
    }

    @Test
    fun repeatedSpeciesWithoutSupportSignalIsNotHighConfidence() {
        val frames = listOf(
            frame(
                "first.png",
                pokemon(cp = 621, name = "Pikachu", realName = "Pikachu", hp = null, maxHp = null, caughtDate = null, arcLevel = null),
                cpQuality = 0.88
            ),
            frame(
                "second.png",
                pokemon(cp = 621, name = "Pikachu", realName = "Pikachu", hp = null, maxHp = null, caughtDate = null, arcLevel = null),
                cpQuality = 0.86
            )
        )

        assertFalse(ScanFrameFusion.isHighConfidence(frames))
    }

    @Test
    fun singleFrameFallbackStillSelectsBestFrame() {
        val onlyFrame = frame(
            path = "only.png",
            data = pokemon(cp = 621, name = "Pikachu", realName = "Pikachu"),
            cpQuality = 0.88
        )

        val selected = ScanFrameFusion.selectBestFrame(listOf(onlyFrame))

        assertEquals("only.png", selected?.path)
    }

    @Test
    fun unknownAndSingleWeakFramesDoNotTriggerHighConfidenceExit() {
        val unknown = frame(
            path = "unknown.png",
            data = pokemon(cp = 621, name = "Unknown", realName = "Unknown", hp = null, maxHp = null, caughtDate = null, arcLevel = null),
            cpQuality = 0.90
        )
        val singleKnown = frame(
            path = "single.png",
            data = pokemon(cp = 621, name = "Pikachu", realName = "Pikachu", hp = 84, maxHp = 84, caughtDate = null, arcLevel = null),
            cpQuality = 0.90
        )

        assertFalse(ScanFrameFusion.isHighConfidence(listOf(unknown)))
        assertFalse(ScanFrameFusion.isHighConfidence(listOf(singleKnown)))
    }

    @Test
    fun cpCandidatesGatheredOnlyFromAcceptableCpQualityFrames() {
        val frames = listOf(
            frame("low.png", pokemon(cp = 111), cpQuality = 0.54),
            frame("min.png", pokemon(cp = 222), cpQuality = ScanFrameFusion.CP_QUALITY_MIN),
            frame("missing.png", pokemon(cp = null), cpQuality = 0.90)
        )

        val candidates = ScanFrameFusion.validCpCandidates(frames)

        assertEquals(listOf(222), candidates)
    }

    @Test
    fun detailedPassRequiredWhenCpIsMissing() {
        val shouldRun = ScanFrameFusion.shouldRunDetailedPass(
            pokemon = pokemon(cp = null),
            cpQuality = 0.90,
            topTextConfidence = 0.95
        )

        assertTrue(shouldRun)
    }

    @Test
    fun detailedPassRequiredWhenSpeciesIsUnknown() {
        val shouldRun = ScanFrameFusion.shouldRunDetailedPass(
            pokemon = pokemon(name = "Unknown", realName = "Unknown"),
            cpQuality = 0.90,
            topTextConfidence = 0.95
        )

        assertTrue(shouldRun)
    }

    @Test
    fun detailedPassSkippedWhenCpNameDateAndHpAreReliable() {
        val shouldRun = ScanFrameFusion.shouldRunDetailedPass(
            pokemon = pokemon(cp = 621, name = "Pikachu", realName = "Pikachu", caughtDate = defaultCaughtDate),
            cpQuality = 0.90,
            topTextConfidence = 0.95
        )

        assertFalse(shouldRun)
    }

    @Test
    fun detailedPassRequiredWhenCpQualityBelowMinimum() {
        val shouldRun = ScanFrameFusion.shouldRunDetailedPass(
            pokemon = pokemon(cp = 621, name = "Pikachu", realName = "Pikachu", hp = 84, caughtDate = defaultCaughtDate),
            cpQuality = 0.50,
            topTextConfidence = 0.95
        )

        assertTrue(shouldRun)
    }

    @Test
    fun detailedPassRequiredWhenTextConfidenceBelowThreshold() {
        val shouldRun = ScanFrameFusion.shouldRunDetailedPass(
            pokemon = pokemon(cp = 621, name = "Pikachu", realName = "Pikachu", hp = 84, caughtDate = defaultCaughtDate),
            cpQuality = 0.90,
            topTextConfidence = 0.85
        )

        assertTrue(shouldRun)
    }

    @Test
    fun detailedPassSkippedWhenAllSignalsAboveThresholds() {
        val shouldRun = ScanFrameFusion.shouldRunDetailedPass(
            pokemon = pokemon(
                cp = 621,
                name = "Pikachu",
                realName = "Pikachu",
                hp = 84,
                caughtDate = defaultCaughtDate
            ),
            cpQuality = ScanFrameFusion.CP_QUALITY_MIN,
            topTextConfidence = 0.86
        )

        assertFalse(shouldRun)
    }

    @Test
    fun fusionKeepsPrimaryFieldsAndBackfillsSecondaryFields() {
        val authoritative = pokemon(
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
        )
        val detailed = pokemon(
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
        )
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
        val strong = pokemon(cp = 700, name = "Pikachu", realName = "Pikachu")
        val noisy = pokemon(cp = 999, name = "Unknown", realName = "Unknown", hp = null, maxHp = null)
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
        val primary = pokemon(
            cp = 480,
            hp = 70,
            maxHp = 80,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = null,
            stardust = null,
            caughtDate = null,
            rawOcrText = "CP:480|HP:70/80|Name:Pikachu|NameHC:Pikachu"
        )
        val supportingFrame = pokemon(
            cp = 480,
            hp = 70,
            maxHp = 80,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = null,
            stardust = 2200,
            caughtDate = supportingCaughtDate,
            rawOcrText = "CP:480|HP:70/80|Name:Pikachu|Date:2023-11-14"
        )
        val detailed = pokemon(
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
        )
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
        val primary = pokemon(
            cp = 510,
            hp = 61,
            maxHp = 70,
            name = "Unknown",
            realName = "Unknown",
            caughtDate = defaultCaughtDate,
            rawOcrText = "CP:510|HP:61/70|Name:Unknown|NameHC:"
        )
        val knownFrame = pokemon(
            cp = 510,
            hp = 61,
            maxHp = 70,
            name = "Bulbasaur",
            realName = "Bulbasaur",
            caughtDate = defaultCaughtDate,
            rawOcrText = "CP:510|HP:61/70|Name:Bulbasaur|NameHC:Bulbasaur"
        )
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
        val strong = pokemon(
            cp = 721,
            hp = 88,
            maxHp = 96,
            name = "Squirtle",
            realName = "Squirtle",
            caughtDate = defaultCaughtDate,
            rawOcrText = "CP:721|HP:88/96|Name:Squirtle|NameHC:Squirtle"
        )
        val weak = pokemon(
            cp = 999,
            hp = 1,
            maxHp = 1,
            name = "Unknown",
            realName = "Unknown",
            caughtDate = weakCaughtDate,
            rawOcrText = "CP:999|HP:1/1|Name:Unknown|NameHC:"
        )
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

    private fun frame(path: String, data: PokemonData, cpQuality: Double): ScanFrameCandidate {
        return ScanFrameCandidate(path = path, data = data, cpQuality = cpQuality)
    }

    private fun pokemon(
        cp: Int? = 621,
        hp: Int? = 84,
        maxHp: Int? = 84,
        name: String? = "Pikachu",
        realName: String? = name,
        candyName: String? = "Pikachu",
        stardust: Int? = 2500,
        weight: Float? = null,
        height: Float? = null,
        arcLevel: Float? = 0.5f,
        caughtDate: Date? = defaultCaughtDate,
        rawOcrText: String = "CP:${cp ?: ""}|HP:${hp ?: ""}/${maxHp ?: ""}|Name:${name.orEmpty()}|NameHC:${realName.orEmpty()}"
    ): PokemonData {
        return PokemonData(
            cp = cp,
            hp = hp,
            maxHp = maxHp,
            name = name,
            realName = realName,
            candyName = candyName,
            megaEnergy = null,
            weight = weight,
            height = height,
            gender = null,
            stardust = stardust,
            arcLevel = arcLevel,
            caughtDate = caughtDate,
            rawOcrText = rawOcrText
        )
    }

    private val defaultCaughtDate = Date(1_700_000_000_000L)
    private val supportingCaughtDate = Date(1_700_086_400_000L)
    private val detailedCaughtDate = Date(1_700_172_800_000L)
    private val weakCaughtDate = Date(1_700_259_200_000L)
}
