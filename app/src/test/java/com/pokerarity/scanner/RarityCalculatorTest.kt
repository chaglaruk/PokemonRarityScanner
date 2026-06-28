package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.RarityCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class RarityCalculatorTest {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val fixedNow = dateFormat.parse("2026-06-26")!!
    private val calculator = RarityCalculator(
        context = ApplicationProvider.getApplicationContext(),
        currentDateProvider = { fixedNow }
    )

    @Test
    fun normal2017FlareonWithCaughtDateGetsAgeScoreNotBaseOnly() {
        val score = calculator.calculate(
            pokemon = flareon(caughtDate = dateFormat.parse("2017-11-05")),
            features = VisualFeatures()
        )

        assertTrue(score.breakdown.getValue("Age Score") in 21..22)
        assertTrue("2017 Flareon should not be base-only common", score.totalScore in 25..26)
    }

    @Test
    fun normalRecentFlareonWithoutVariantStaysBaseOnlyWhenDateMissing() {
        val score = calculator.calculate(
            pokemon = flareon(caughtDate = null),
            features = VisualFeatures()
        )

        assertEquals(0, score.breakdown.getValue("Age Score"))
        assertEquals(4, score.totalScore)
    }

    @Test
    fun suppressedFullMatchShinyDoesNotAddVariantScore() {
        val score = calculator.calculate(
            pokemon = flareon(caughtDate = null).copy(
                fullVariantMatch = FullVariantMatch(
                    finalSpecies = "Flareon",
                    finalSpriteKey = "136_00_shiny",
                    resolvedVariantClass = "base",
                    resolvedShiny = true,
                    variantConfidence = 0.94f,
                    shinyConfidence = 0.94f,
                    explanationMode = "exact_authoritative"
                )
            ),
            features = VisualFeatures(isShiny = false, confidence = 1.0f)
        )

        assertEquals(0, score.breakdown.getValue("Variant Score"))
        assertEquals(4, score.totalScore)
    }

    private fun flareon(caughtDate: Date?): PokemonData =
        PokemonData(
            cp = 1500,
            hp = 120,
            maxHp = 120,
            name = "Flareon",
            realName = "Flareon",
            candyName = "Eevee",
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
