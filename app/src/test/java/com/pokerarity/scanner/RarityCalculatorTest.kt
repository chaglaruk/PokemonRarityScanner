package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.RecognitionFormStatus
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionIdentityCompat
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowLog

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
    fun unknownSpecialFormIdentityCannotInflateRarity() {
        // A legacy visual/classifier flag can claim a form without typed current-form evidence.
        // Scoring must use the same identity-authorized flags shown in the result and History.
        val identity = pikachuIdentity().copy(canonicalSpecies = "Flareon")
        val pokemon = flareon(caughtDate = null).copy(recognitionIdentity = identity)
        val untrustedClassifierFeatures = VisualFeatures(hasSpecialForm = true, confidence = 0.98f)
        val authorizedFeatures = RecognitionIdentityCompat.toVisualFeatures(
            identity,
            confidence = untrustedClassifierFeatures.confidence
        )

        val untrusted = calculator.calculate(pokemon, untrustedClassifierFeatures)
        val authorized = calculator.calculate(pokemon, authorizedFeatures)

        assertFalse(authorizedFeatures.hasSpecialForm)
        assertTrue(untrusted.breakdown.getValue("Variant Score") > 0)
        assertEquals(0, authorized.breakdown.getValue("Variant Score"))
        assertEquals(4, authorized.totalScore)
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

    @Test
    fun cpValidationWarningDoesNotLogPokemonEvidence() {
        val calculator = RarityCalculator(ApplicationProvider.getApplicationContext())
        RarityCalculator::class.java.getDeclaredField("baseStats\$delegate")
            .apply { isAccessible = true }
            .set(calculator, lazyOf(mapOf("Pikachu" to RarityCalculator.BaseStats(112, 96, 111, 0.4, 6.0))))
        val privateSpecies = "Pikachu"
        val privateCp = 1234
        val privateMaximumHp = 56
        val pokemon = PokemonData(
            cp = privateCp,
            hp = 0,
            maxHp = privateMaximumHp,
            name = privateSpecies,
            realName = privateSpecies,
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 0.5f,
            caughtDate = null
        )

        ShadowLog.clear()
        calculator.validateAndFixCP(pokemon)

        val warningMessages = ShadowLog.getLogsForTag("RarityCalculator")
            .filter { it.type == android.util.Log.WARN }
            .map { it.msg }
        assertTrue("Expected the weak HP warning", warningMessages.isNotEmpty())
        assertFalse(warningMessages.any { it.contains(privateSpecies) })
        assertFalse(warningMessages.any { it.contains(privateCp.toString()) })
        assertFalse(warningMessages.any { it.contains(privateMaximumHp.toString()) })
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
