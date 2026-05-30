package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.RarityCalculator
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class RarityAgeScoringTest {

    private val fixedNow = Date(1_700_000_000_000L)
    private val calculator = RarityCalculator(
        context = ApplicationProvider.getApplicationContext(),
        currentDateProvider = { fixedNow }
    )

    @Test
    fun noCaughtDateHasNoAgeBonus() {
        assertEquals(0, ageScore(caughtDate = null))
    }

    @Test
    fun underOneYearHasNoAgeBonus() {
        assertEquals(0, ageScore(caughtDate = daysBeforeNow(364)))
    }

    @Test
    fun oneYearUsesOneYearTier() {
        assertEquals(6, ageScore(caughtDate = daysBeforeNow(365)))
    }

    @Test
    fun threeYearsUsesThreeYearTier() {
        assertEquals(12, ageScore(caughtDate = daysBeforeNow(1_095)))
    }

    @Test
    fun fiveYearsUsesFiveYearTier() {
        assertEquals(16, ageScore(caughtDate = daysBeforeNow(1_825)))
    }

    @Test
    fun sevenYearsUsesSevenYearTier() {
        assertEquals(20, ageScore(caughtDate = daysBeforeNow(2_555)))
    }

    @Test
    fun futureCaughtDateHasNoPositiveAgeBonus() {
        assertEquals(0, ageScore(caughtDate = daysAfterNow(1)))
    }

    private fun ageScore(caughtDate: Date?): Int {
        return calculator.calculate(
            pokemon = pokemon(caughtDate),
            features = VisualFeatures()
        ).breakdown.getValue("Age Score")
    }

    private fun pokemon(caughtDate: Date?): PokemonData {
        return PokemonData(
            cp = 500,
            hp = 50,
            maxHp = 50,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = "Pikachu",
            megaEnergy = null,
            weight = null,
            height = null,
            gender = null,
            stardust = null,
            arcLevel = 0.5f,
            caughtDate = caughtDate,
            rawOcrText = ""
        )
    }

    private fun daysBeforeNow(days: Int): Date {
        return Date(fixedNow.time - days * DAY_MS)
    }

    private fun daysAfterNow(days: Int): Date {
        return Date(fixedNow.time + days * DAY_MS)
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
