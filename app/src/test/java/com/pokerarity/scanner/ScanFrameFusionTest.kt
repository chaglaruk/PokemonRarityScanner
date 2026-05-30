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
            arcLevel = 0.5f,
            caughtDate = caughtDate,
            rawOcrText = rawOcrText
        )
    }

    private val defaultCaughtDate = Date(1_700_000_000_000L)
}
