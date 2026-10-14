package com.pokerarity.scanner.data.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.data.repository.ScanHistoryMapper
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class PartialHistoryPresentationTest {
    private fun legacy(cp: Int? = 500, shiny: Boolean = false) = ScanHistoryEntity(
        id = 42, timestamp = Date(123456789), pokemonName = "Pikachu", cp = cp,
        hp = 60, caughtDate = null, rawOcrText = "synthetic", isShiny = shiny,
        rarityScore = 42, rarityTier = "RARE"
    )

    private fun identity(shiny: RecognitionTruth) = RecognitionIdentity(
        speciesStatus = RecognitionSpeciesStatus.KNOWN, canonicalSpecies = "Pikachu",
        speciesAuthority = "exact_canonical", formStatus = RecognitionFormStatus.UNKNOWN,
        shiny = shiny, shadow = RecognitionTruth.UNKNOWN, purified = RecognitionTruth.UNKNOWN,
        lucky = RecognitionTruth.UNKNOWN, costume = RecognitionTruth.UNKNOWN,
        specialForm = RecognitionTruth.UNKNOWN, xxs = RecognitionTruth.UNKNOWN,
        xxl = RecognitionTruth.UNKNOWN, locationCard = RecognitionTruth.UNKNOWN
    )

    private fun data(cp: Int? = 500, shiny: RecognitionTruth? = null) = PokemonData(
        cp = cp, hp = 60, maxHp = 60, name = "Pikachu", realName = "Pikachu",
        candyName = "Pikachu", megaEnergy = null, weight = null, height = null,
        stardust = null, caughtDate = null, rawOcrText = "synthetic",
        recognitionIdentity = shiny?.let(::identity)
    )

    private val score = RarityScore(totalScore = 42, tier = RarityTier.RARE,
        breakdown = mapOf("species" to 42), explanation = listOf("Species rarity"))

    @Test fun knownLegacyCpAndRarityStayUnchanged() {
        val row = legacy()
        val ui = row.toUiPokemon()
        assertEquals(500, ui.cp)
        assertEquals("500", ui.cpDisplayText)
        assertEquals(row.id, ui.sourceId)
        assertEquals(row.rarityScore, ui.rarityScore)
        assertEquals(row.rarityTier, ui.rarityTierCode)
    }

    @Test fun missingCpStaysNullAndNeverDisplaysZero() {
        val ui = legacy(cp = null).toUiPokemon()
        assertNull(ui.cp)
        assertEquals("Unknown", ui.cpDisplayText)
    }

    @Test fun nonpositiveLegacyValuesArePreservedButNotDisplayedAsMeasured() {
        listOf(0, -1).forEach { recorded ->
            val row = legacy(cp = recorded)
            assertEquals(recorded, row.cp)
            assertNull(row.toUiPokemon().cp)
            assertEquals("Unknown", row.toUiPokemon().cpDisplayText)
        }
    }

    @Test fun typedTrueIsStoredAndShownAsPositive() {
        val row = ScanHistoryMapper.toEntity(data(shiny = RecognitionTruth.TRUE), VisualFeatures(isShiny = true), score)
        assertEquals("TRUE", row.shinyTruth)
        assertEquals(RecognitionTruth.TRUE, row.toUiPokemon().shinyTruth)
        assertTrue("SHINY" in row.toUiPokemon().tags)
        assertEquals("Shiny", row.toUiPokemon().shinyDisplayText)
    }

    @Test fun typedFalseStaysDistinctFromUnknown() {
        val row = ScanHistoryMapper.toEntity(data(shiny = RecognitionTruth.FALSE), VisualFeatures(), score)
        assertEquals("FALSE", row.shinyTruth)
        assertEquals(RecognitionTruth.FALSE, row.toUiPokemon().shinyTruth)
        assertEquals("Not shiny", row.toUiPokemon().shinyDisplayText)
        assertFalse("SHINY" in row.toUiPokemon().tags)
    }

    @Test fun typedUnknownDoesNotTurnIntoFalse() {
        val row = ScanHistoryMapper.toEntity(data(cp = null, shiny = RecognitionTruth.UNKNOWN), VisualFeatures(), score)
        assertEquals("UNKNOWN", row.shinyTruth)
        assertEquals(RecognitionTruth.UNKNOWN, row.toUiPokemon().shinyTruth)
        assertEquals("Shiny unknown", row.toUiPokemon().shinyDisplayText)
        assertNull(row.cp)
        assertNull(row.toUiPokemon().cp)
    }

    @Test fun legacyFalseNeverBecomesVerifiedNonShiny() {
        val row = legacy(shiny = false)
        assertNull(row.shinyTruth)
        assertEquals(RecognitionTruth.UNKNOWN, row.recognizedShiny)
        assertEquals("Shiny unknown", row.toUiPokemon().shinyDisplayText)
        assertFalse(row.toUiPokemon().analysis.any { it.title == "Shiny variant" })
    }

    @Test fun legacyPositiveIsPreservedWithoutInventingNegativeEvidence() {
        assertEquals(RecognitionTruth.TRUE, legacy(shiny = true).recognizedShiny)
        val row = ScanHistoryMapper.toEntity(data(), VisualFeatures(), score)
        assertNull(row.shinyTruth)
        assertEquals(RecognitionTruth.UNKNOWN, row.recognizedShiny)
    }

    @Test fun unsupportedTypedEncodingStaysUnknownEvenWithLegacyPositive() {
        val row = legacy(shiny = true).copy(shinyTruth = "FUTURE_UNSUPPORTED")
        assertEquals(RecognitionTruth.UNKNOWN, row.recognizedShiny)
        assertFalse("SHINY" in row.toUiPokemon().tags)
    }

    @Test fun explicitUnknownAndFalseOverrideContradictoryCompatibilityPositive() {
        listOf(RecognitionTruth.UNKNOWN, RecognitionTruth.FALSE).forEach { truth ->
            val row = ScanHistoryMapper.toEntity(data(shiny = truth), VisualFeatures(isShiny = true), score)
            assertEquals(truth, row.recognizedShiny)
            assertFalse("SHINY" in row.toUiPokemon().tags)
            assertEquals(score.totalScore, row.rarityScore)
            assertEquals(score.tier.name, row.rarityTier)
        }
    }

    @Test fun historyConversionCannotPromoteUnknownSpeciesOrVariantAuthority() {
        val unknown = identity(RecognitionTruth.UNKNOWN).copy(
            speciesStatus = RecognitionSpeciesStatus.UNKNOWN, canonicalSpecies = null, speciesAuthority = null
        )
        val source = data().copy(name = null, realName = null, recognitionIdentity = unknown)
        val row = ScanHistoryMapper.toEntity(source, VisualFeatures(), score)
        assertEquals("Unknown", row.toUiPokemon().name)
        assertFalse("SHINY" in row.toUiPokemon().tags)
        assertSame(unknown, source.recognitionIdentity)
        assertEquals(RecognitionSpeciesStatus.UNKNOWN, source.recognitionIdentity?.speciesStatus)
        assertNull(source.recognitionIdentity?.canonicalSpecies)
    }

    @Test fun identicalRecognizedInputsKeepRarityAndIdentityAfterHistoryMapping() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val calculator = RarityCalculator(context, currentDateProvider = { Date(1782432000000) })
        val source = data(shiny = RecognitionTruth.TRUE)
        val features = RecognitionIdentityCompat.toVisualFeatures(source.recognitionIdentity!!)
        val before = calculator.calculate(source, features)
        val snapshot = source.copy()
        val row = ScanHistoryMapper.toEntity(source, features, before)
        row.toUiPokemon()
        val after = calculator.calculate(source, features)
        assertEquals(before, after)
        assertEquals(snapshot, source)
        assertEquals(before.totalScore, row.rarityScore)
        assertEquals(before.tier.name, row.rarityTier)
    }
}
