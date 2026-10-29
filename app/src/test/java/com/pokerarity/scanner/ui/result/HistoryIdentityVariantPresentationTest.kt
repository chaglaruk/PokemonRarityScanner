package com.pokerarity.scanner.ui.result

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.RecognitionFormStatus
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionIdentityHistoryCodec
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.data.model.toUiPokemon
import com.pokerarity.scanner.data.model.valuableSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryIdentityVariantPresentationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun confirmedVariantStatesStayVisibleAcrossHistoryResultValueSummaryAndSave() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val identity = confirmedVariantIdentity()
        try {
            val id = database.scanHistoryDao().insert(
                ScanHistoryEntity(
                    pokemonName = "Pikachu",
                    cp = 640,
                    hp = 60,
                    caughtDate = null,
                    rawOcrText = "variant-round-trip",
                    recognitionIdentityJson = RecognitionIdentityHistoryCodec.encode(identity)
                )
            )
            val stored = database.scanHistoryDao().getById(id)!!

            assertVariantPresentation(stored.toUiPokemon())

            val resultIntent = HistoryResultIntentMapper.create(context, stored)
            assertEquals(
                identity,
                RecognitionIdentityHistoryCodec.decode(
                    resultIntent.getStringExtra(ResultActivity.EXTRA_RECOGNITION_IDENTITY)
                )
            )
            val reopenedResult = ResultIntentPokemonMapper.create(resultIntent)
            assertVariantPresentation(reopenedResult)
            val valueSummary = reopenedResult.valuableSummary()
            assertTrue(valueSummary.contains("purified", ignoreCase = true))
            assertTrue(valueSummary.contains("xxs", ignoreCase = true))
            assertTrue(valueSummary.contains("xxl", ignoreCase = true))
            assertTrue(valueSummary.contains("special background", ignoreCase = true))

            database.scanHistoryDao().update(ResultHistorySaveMapper.fromIntent(resultIntent, stored, null))
            val saved = database.scanHistoryDao().getById(id)!!
            assertEquals(identity, RecognitionIdentityHistoryCodec.decode(saved.recognitionIdentityJson))
            assertVariantPresentation(saved.toUiPokemon())
        } finally {
            database.close()
        }
    }

    @Test
    fun falseAndUnknownStatesDoNotBecomePositivePresentation() {
        val identity = confirmedVariantIdentity().copy(
            purified = RecognitionTruth.FALSE,
            xxs = RecognitionTruth.UNKNOWN,
            xxl = RecognitionTruth.FALSE,
            locationCard = RecognitionTruth.UNKNOWN
        )
        val scan = ScanHistoryEntity(
            pokemonName = "Pikachu",
            cp = 640,
            hp = 60,
            caughtDate = null,
            rawOcrText = "negative-variant-round-trip",
            recognitionIdentityJson = RecognitionIdentityHistoryCodec.encode(identity)
        )
        val pokemon = scan.toUiPokemon()
        val reopenedResult = ResultIntentPokemonMapper.create(HistoryResultIntentMapper.create(context, scan))

        POSITIVE_VARIANT_TAGS.forEach { tag -> assertFalse("unexpected tag: $tag", tag in pokemon.tags) }
        assertFalse(pokemon.analysis.any { it.title in POSITIVE_VARIANT_TITLES && it.isPositive })
        POSITIVE_VARIANT_TAGS.forEach { tag -> assertFalse("unexpected result tag: $tag", tag in reopenedResult.tags) }
        assertFalse(reopenedResult.analysis.any { it.title in POSITIVE_VARIANT_TITLES && it.isPositive })
    }

    private fun assertVariantPresentation(pokemon: com.pokerarity.scanner.data.model.Pokemon) {
        POSITIVE_VARIANT_TAGS.forEach { tag -> assertTrue("missing tag: $tag", tag in pokemon.tags) }
        POSITIVE_VARIANT_TITLES.forEach { title ->
            assertTrue("missing positive analysis: $title", pokemon.analysis.any { it.title == title && it.isPositive })
        }
    }

    private fun confirmedVariantIdentity() = RecognitionIdentity(
        speciesStatus = RecognitionSpeciesStatus.KNOWN,
        canonicalSpecies = "Pikachu",
        speciesAuthority = "species_exact_authority",
        formStatus = RecognitionFormStatus.UNKNOWN,
        shiny = RecognitionTruth.UNKNOWN,
        shadow = RecognitionTruth.UNKNOWN,
        purified = RecognitionTruth.TRUE,
        lucky = RecognitionTruth.UNKNOWN,
        costume = RecognitionTruth.UNKNOWN,
        specialForm = RecognitionTruth.UNKNOWN,
        xxs = RecognitionTruth.TRUE,
        xxl = RecognitionTruth.TRUE,
        locationCard = RecognitionTruth.TRUE
    )

    private companion object {
        val POSITIVE_VARIANT_TAGS = listOf("PURIFIED", "XXS", "XXL", "BACKGROUND")
        val POSITIVE_VARIANT_TITLES = listOf(
            "Purified Pokemon",
            "XXS size",
            "XXL size",
            "Special background"
        )
    }
}
