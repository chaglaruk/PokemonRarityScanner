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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun unknownSpecialFormIdentityCannotInflateRarity() {
        val identity = pikachuIdentity().copy(canonicalSpecies = "Flareon")
        val pokemon = flareon(caughtDate = null).copy(recognitionIdentity = identity)
        val weakClassifierFeatures = VisualFeatures(hasSpecialForm = true, confidence = 0.98f)
        val authorizedFeatures = RecognitionIdentityCompat.toVisualFeatures(
            identity,
            confidence = weakClassifierFeatures.confidence
        )

        val unfilteredScore = calculator.calculate(pokemon, weakClassifierFeatures)
        val authorizedScore = calculator.calculate(pokemon, authorizedFeatures)

        assertFalse(authorizedFeatures.hasSpecialForm)
        assertTrue(unfilteredScore.breakdown.getValue("Variant Score") > 0)
        assertEquals(0, authorizedScore.breakdown.getValue("Variant Score"))
        assertEquals(4, authorizedScore.totalScore)
    }

    @Test
    fun verifiedSpecialFormIdentityContributesFormRarity() {
        val identity = pikachuIdentity().copy(
            canonicalSpecies = "Flareon",
            formStatus = RecognitionFormStatus.KNOWN,
            knownForm = "Alolan",
            specialForm = RecognitionTruth.TRUE
        )
        val features = RecognitionIdentityCompat.toVisualFeatures(identity)

        val score = calculator.calculate(
            pokemon = flareon(caughtDate = null).copy(recognitionIdentity = identity),
            features = features
        )

        assertTrue(features.hasSpecialForm)
        assertTrue(score.breakdown.getValue("Variant Score") > 0)
    }

    @Test
    fun unverifiedFormCannotCreateDateBackedFormExplanation() {
        val ambiguousSpecialFormIdentity = pikachuIdentity().copy(
            canonicalSpecies = "Charizard",
            formStatus = RecognitionFormStatus.AMBIGUOUS,
            ambiguousForms = listOf("Origin", "Altered"),
            specialForm = RecognitionTruth.TRUE
        )
        val score = calculator.calculate(
            pokemon = flareon(caughtDate = dateFormat.parse("2018-05-19"))
                .copy(
                    name = "Charizard",
                    realName = "Charizard",
                    candyName = "Charmander",
                    recognitionIdentity = ambiguousSpecialFormIdentity,
                    fullVariantMatch = FullVariantMatch(
                        finalSpecies = "Charizard",
                        finalSpriteKey = "006_51",
                        resolvedVariantClass = "form",
                        resolvedForm = true,
                        resolvedEventLabel = "Community Day: Charmander",
                        resolvedEventWindow = com.pokerarity.scanner.data.model.ReleaseWindow(
                            "2018-05-19",
                            "2018-05-19"
                        ),
                        variantConfidence = 0.9f,
                        eventConfidence = 0.9f,
                        explanationMode = "exact_authoritative"
                    )
                ),
            features = RecognitionIdentityCompat.toVisualFeatures(ambiguousSpecialFormIdentity)
        )

        assertFalse(score.explanation.any { it.contains("Event: Community Day: Charmander") })
        assertFalse(score.explanation.any { it.contains("Costume: Charizard form 51") })
        assertFalse(score.explanation.any { it.contains("Form: Charizard form 51") })
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
