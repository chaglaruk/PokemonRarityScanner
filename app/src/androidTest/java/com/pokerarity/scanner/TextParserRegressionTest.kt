package com.pokerarity.scanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.util.ocr.TextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TextParserRegressionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val parser = TextParser(context)
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Test
    fun parseCpPrefersExplicitAnchor() {
        assertEquals(1278, parser.parseCP("0 CP1278 1"))
    }

    @Test
    fun parseHpRejectsImpossiblePairs() {
        assertNull(parser.parseHPPair("43 /743 HP"))
        assertNull(parser.parseHPPair("93/793 HP"))
        assertEquals(82 to 82, parser.parseHPPair("82/82HP"))
    }

    @Test
    fun parseDateSupportsCompactBadgeTokens() {
        assertEquals("2018-11-08", fmt.format(parser.parseDate("2018 1108")))
        assertEquals("2018-08-30", fmt.format(parser.parseDate("2018 3008")))
        assertEquals("2018-10-21", fmt.format(parser.parseDate("2018 21110")))
    }
}
