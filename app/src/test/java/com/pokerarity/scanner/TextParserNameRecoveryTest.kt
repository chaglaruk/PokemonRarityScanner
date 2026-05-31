package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.util.ocr.TextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class TextParserNameRecoveryTest {

    private val parser = TextParser(ApplicationProvider.getApplicationContext())

    @Test
    fun parseName_recoversNumericOcrConfusion() {
        assertEquals("Porygon", parser.parseName("Poryg0n"))
        assertEquals("Espeon", parser.parseName("Espe0n"))
        assertEquals("Porygon2", parser.parseName("Porygon2"))
        assertEquals("Gyarados", parser.parseName("Gyarados100"))
        assertEquals("Slowpoke", parser.parseName("Slowpoke100"))
    }

    @Test
    fun parseName_recoversCommonGlyphConfusion() {
        assertEquals("Gyarados", parser.parseName("Gvarados"))
    }

    @Test
    fun parseStrongSpeciesName_doesNotLockNicknameLikeFuzzySpecies() {
        assertEquals("Porygon", parser.parseStrongSpeciesName("Poryg0n"))
        assertEquals("Espeon", parser.parseStrongSpeciesName("Espeon"))
        assertEquals("Espeon", parser.parseStrongSpeciesName("Espeon100"))
        assertEquals("Slowpoke", parser.parseStrongSpeciesName("Slowpoke100"))
        assertNull(parser.parseStrongSpeciesName("ELECTRIC"))
    }

    @Test
    fun parseName_ignoresTypeAndUiLabels() {
        assertNull(parser.parseName("TALLEST"))
        assertNull(parser.parseName("FLYING"))
        assertNull(parser.parseName("ELECTRIC"))
        assertNull(parser.parseName("NORMAL"))
        assertNull(parser.parseName("WEATHER BONUS"))
    }

    @Test
    fun parseName_ignoresPokemonGoSizeRecordAndSuccessLabels() {
        assertNull(parser.parseName("NEW SIZE RECORD"))
        assertNull(parser.parseName("SIZE RECORD"))
        assertNull(parser.parseName("RECORD XL"))
        assertNull(parser.parseName("SUCCESS"))
        assertNull(parser.parseName("XXL"))
        assertNull(parser.parseName("XXS"))
        assertNull(parser.parseName("XS"))
        assertNull(parser.parseName("XL"))
        assertNull(parser.parseName("TINY"))
        assertNull(parser.parseName("GIGANTIC"))
        assertNull(parser.parseName("SHORTEST"))
    }

    @Test
    fun parseName_ignoresPowerUpAndCandyUiLabels() {
        assertNull(parser.parseName("POWER UP"))
        assertNull(parser.parseName("CANDY"))
        assertNull(parser.parseName("STARDUST"))
    }

    @Test
    fun parseName_ignoresCpAndHpLabels() {
        assertNull(parser.parseName("CP"))
        assertNull(parser.parseName("HP"))
        assertNull(parser.parseName("CP 1500"))
    }

    @Test
    fun rankNameCandidates_ignoresCompactNonSpeciesUiTokens() {
        assertTrue(parser.rankNameCandidates("newrecordxl").isEmpty())
        assertTrue(parser.rankNameCandidates("sizerecordxxl").isEmpty())
        assertNull(parser.parseName("newrecordxl"))
        assertNull(parser.parseStrongSpeciesName("sizerecordxxs"))
    }

    @Test
    fun parseName_keepsRealSpeciesNearBlockedUiWords() {
        assertEquals("Slowpoke", parser.parseName("Slowpoke XL"))
    }
}
