package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.RecognitionFormStatus
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.util.ocr.PowerUpCostModifier
import com.pokerarity.scanner.util.ocr.RecognitionObservation
import com.pokerarity.scanner.util.ocr.StardustLevelEvidence
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
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

    @Test
    fun calculateConnectsTypedStardustAndCpHpToUserFacingIvEstimate() {
        val snapshot = RecognitionSnapshotTestSupport.seedHolder()
        val pokemon = pikachuAtLevel30(snapshot)

        try {
            val score = calculator.calculate(pokemon, VisualFeatures())
            assertNotNull(score.ivEstimate)
            assertNotNull(score.ivSolve)
            assertTrue(score.ivSolve!!.ivSolveMode != IvSolveMode.INSUFFICIENT)
            assertTrue(score.ivSolve!!.ivSolveSignalsUsed.containsAll(listOf("CP", "MAX_HP", "POWER_UP_STARDUST")))
        } finally {
            RecognitionSnapshotTestSupport.resetHolder()
        }
    }

    private fun pikachuAtLevel30(snapshot: com.pokerarity.scanner.util.ocr.RecognitionSnapshot): PokemonData {
        val profile = snapshot.forSpecies("Pikachu").first()
        val level = 30.0
        val cpm = snapshot.cpMultipliers.getValue(level)
        val iv = 15
        val hp = kotlin.math.floor((profile.stats.sta + iv) * cpm).toInt().coerceAtLeast(10)
        val cp = kotlin.math.floor(
            (profile.stats.atk + iv) *
                kotlin.math.sqrt((profile.stats.def + iv).toDouble()) *
                kotlin.math.sqrt((profile.stats.sta + iv).toDouble()) * cpm * cpm / 10.0
        ).toInt().coerceAtLeast(10)
        return flareon(caughtDate = null).copy(
            cp = cp,
            hp = hp,
            maxHp = hp,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = "Pikachu",
            recognitionIdentity = pikachuIdentity(),
            recognitionObservation = RecognitionObservation(
                candySpecies = "Pikachu",
                powerUpStardust = 5000,
                types = setOf("electric"),
                detailScreen = true,
                powerUpStardustLevelEvidence = StardustLevelEvidence.Levels(
                    levels = listOf(level),
                    minLevel = level,
                    maxLevel = level,
                    modifiersUsed = listOf(PowerUpCostModifier.NORMAL),
                    interpretations = listOf("test_fixture"),
                    reasonCodes = listOf("anchored_power_up_row")
                )
            )
        )
    }

    private fun pikachuIdentity() = RecognitionIdentity(
        speciesStatus = RecognitionSpeciesStatus.KNOWN,
        canonicalSpecies = "Pikachu",
        speciesAuthority = "test_fixture",
        formStatus = RecognitionFormStatus.UNKNOWN,
        shiny = RecognitionTruth.UNKNOWN,
        shadow = RecognitionTruth.UNKNOWN,
        purified = RecognitionTruth.UNKNOWN,
        lucky = RecognitionTruth.UNKNOWN,
        costume = RecognitionTruth.UNKNOWN,
        specialForm = RecognitionTruth.UNKNOWN,
        xxs = RecognitionTruth.UNKNOWN,
        xxl = RecognitionTruth.UNKNOWN,
        locationCard = RecognitionTruth.UNKNOWN
    )

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
