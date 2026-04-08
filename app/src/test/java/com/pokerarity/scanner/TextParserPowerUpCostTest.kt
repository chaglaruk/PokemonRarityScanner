package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.util.ocr.TextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TextParserPowerUpCostTest {

    private val parser = TextParser(ApplicationProvider.getApplicationContext())

    @Test
    fun dedicatedCandyPreferred_whenValid() {
        val parsed = parser.parsePowerUpCandyCost("1", "2")
        assertEquals(1, parsed)
    }

    @Test
    fun fallbackCandyUsed_whenDedicatedMissing() {
        val parsed = parser.parsePowerUpCandyCost("", "Cost 3")
        assertEquals(3, parsed)
    }

    @Test
    fun dedicatedStardustPreferred_whenValid() {
        val parsed = parser.parsePowerUpStardust("200", "600")
        assertEquals(200, parsed)
    }

    @Test
    fun fallbackStardustUsed_whenDedicatedMissing() {
        val parsed = parser.parsePowerUpStardust("", "Power up 1,600")
        assertEquals(1600, parsed)
    }

    @Test
    fun rowPairExtractsBothCosts_whenDedicatedCropsMissDigits() {
        val parsed = parser.parsePowerUpCostPair("1,900 2")
        assertEquals(1900 to 2, parsed)
    }

    @Test
    fun rowPairSupportsHighCandyBuckets() {
        val parsed = parser.parsePowerUpCostPair("13,000 17")
        assertEquals(13000 to 17, parsed)
    }

    @Test
    fun splitThousandsTokensAreMerged_forRowFallback() {
        val parsed = parser.parsePowerUpCostPair("2 200 2")
        assertEquals(2200 to 2, parsed)
    }

    @Test
    fun noisyMergedRowStillRecoversDustAndCandy() {
        val parsed = parser.parsePowerUpCostPair("5800 31", "1800 31")
        assertEquals(800 to 1, parsed)
    }

    @Test
    fun brokenNumericNoise_isRejected() {
        val candy = parser.parsePowerUpCandyCost("11", "99")
        val dust = parser.parsePowerUpStardust("70 000 0, 500", "81 8")
        assertNull(candy)
        assertNull(dust)
    }

    @Test
    fun stateAdjustedDustValuesRemainParseable() {
        assertEquals(14000, parser.parsePowerUpStardust("14,000"))
        assertEquals(240, parser.parsePowerUpStardust("240"))
    }

    @Test
    fun fallbackNoiseDoesNotInventCanonicalPair() {
        val parsed = parser.parsePowerUpCostPairStrict("70 000 94 7700", "111 5")
        assertNull(parsed)
    }
}
