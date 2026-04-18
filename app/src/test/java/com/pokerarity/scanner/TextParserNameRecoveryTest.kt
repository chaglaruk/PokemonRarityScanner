package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.util.ocr.TextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TextParserNameRecoveryTest {

    private val parser = TextParser(ApplicationProvider.getApplicationContext())

    @Test
    fun parseName_recoversNumericOcrConfusion() {
        assertEquals("Porygon", parser.parseName("Poryg0n"))
        assertEquals("Espeon", parser.parseName("Espe0n"))
    }

    @Test
    fun parseName_recoversCommonGlyphConfusion() {
        assertEquals("Gyarados", parser.parseName("Gvarados"))
    }

    @Test
    fun parseStrongSpeciesName_doesNotLockNicknameLikeFuzzySpecies() {
        assertEquals("Porygon", parser.parseStrongSpeciesName("Poryg0n"))
        assertEquals("Espeon", parser.parseStrongSpeciesName("Espeon"))
        assertNull(parser.parseStrongSpeciesName("ELECTRIC"))
    }
}
