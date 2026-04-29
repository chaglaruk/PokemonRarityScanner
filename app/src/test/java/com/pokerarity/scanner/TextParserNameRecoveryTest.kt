package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.util.ocr.TextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
