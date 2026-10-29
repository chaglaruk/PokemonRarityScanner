package com.pokerarity.scanner.ui.result

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.model.RecognitionFormStatus
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionIdentityHistoryCodec
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityAnalysisHistoryCodec
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.ScanDecisionSupport
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.model.encodeExplanationItem
import com.pokerarity.scanner.data.repository.ScanHistoryMapper
import com.pokerarity.scanner.data.model.toUiPokemon
import com.pokerarity.scanner.data.model.valuableSummary
import com.pokerarity.scanner.data.model.formatIvLevelSummary
import com.pokerarity.scanner.data.model.formatHpDisplayText
import com.pokerarity.scanner.ui.share.ResultShareRenderer
import com.pokerarity.scanner.util.DateParseUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryResultRoundTripTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun historyListShowsOnlyAnExplicitlyKnownSpecialForm() {
        val known = ScanHistoryEntity(
            pokemonName = "Raichu",
            cp = 475,
            hp = 52,
            caughtDate = null,
            rawOcrText = "known-form",
            recognitionIdentityJson = RecognitionIdentityHistoryCodec.encode(knownAlolanIdentity())
        ).toUiPokemon()
        assertTrue(known.tags.contains("FORM"))
        assertTrue(known.analysis.any { it.title == "Special form" && it.isPositive })

        val unknown = ScanHistoryEntity(
            pokemonName = "Raichu",
            cp = 475,
            hp = 52,
            caughtDate = null,
            rawOcrText = "unknown-form",
            recognitionIdentityJson = RecognitionIdentityHistoryCodec.encode(
                knownAlolanIdentity().copy(specialForm = RecognitionTruth.UNKNOWN)
            )
        ).toUiPokemon()
        assertFalse(unknown.tags.contains("FORM"))
        assertFalse(unknown.analysis.any { it.title == "Special form" && it.isPositive })
    }

    @Test
    fun historyNavigationResultAndSaveKeepIvRangeAndLevels() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val historyId = insertRoundTripRecord(database)
            val persistedHistory = database.scanHistoryDao().getById(historyId)!!
            val resultIntent = HistoryResultIntentMapper.create(context, persistedHistory)
            assertHistoryResultIntent(resultIntent, historyId)
            val resultPokemon = ResultIntentPokemonMapper.create(resultIntent)
            assertResultCardAndShare(resultPokemon)
            saveReopenedResult(database, historyId, resultIntent)
        } finally {
            database.close()
        }
    }

    @Test
    fun rarityExplanationSurvivesHistoryReopenAndSave() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val id = insertRarityExplanationRecord(database)

            val persisted = database.scanHistoryDao().getById(id)!!
            val expectedBreakdown = linkedMapOf("older_catch" to 14, "species" to 40)
            assertEquals(expectedBreakdown,
                RarityAnalysisHistoryCodec.decode(persisted.rarityAnalysisJson)?.breakdown)
            assertCollectionDetailKeepsPersistedRarityExplanation(persisted)
            val resultIntent = HistoryResultIntentMapper.create(context, persisted)
            assertEquals(listOf("older_catch", "species"),
                resultIntent.getStringArrayListExtra(ResultActivity.EXTRA_BREAKDOWN_KEYS))
            assertEquals(listOf(14, 40),
                resultIntent.getIntegerArrayListExtra(ResultActivity.EXTRA_BREAKDOWN_VALUES))
            val reopened = ResultIntentPokemonMapper.create(resultIntent)
            assertTrue(reopened.analysis.any {
                it.title == "Older catch" && it.detail == "2019-07-12"
            })
            assertTrue(reopened.valuableSummary().contains("older catch from 2019-07-12"))

            database.scanHistoryDao().update(ResultHistorySaveMapper.fromIntent(resultIntent, persisted, null))
            val saved = database.scanHistoryDao().getById(id)!!
            assertEquals(expectedBreakdown,
                RarityAnalysisHistoryCodec.decode(saved.rarityAnalysisJson)?.breakdown)
            val reopenedAfterSave = ResultIntentPokemonMapper.create(
                HistoryResultIntentMapper.create(context, saved)
            )
            assertTrue(reopenedAfterSave.analysis.any {
                it.title == "Older catch" && it.detail == "2019-07-12"
            })
        } finally {
            database.close()
        }
    }

    @Test
    fun historyNavigationKeepsFaintedZeroHpAsObservedThroughResultAndSave() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val historyId = insertRoundTripRecord(database, hp = 0)
            val history = database.scanHistoryDao().getById(historyId)!!
            val resultIntent = HistoryResultIntentMapper.create(context, history)

            assertEquals(0, resultIntent.getIntExtra(ResultActivity.EXTRA_HP, -1))
            assertTrue(resultIntent.getBooleanExtra(ResultActivity.EXTRA_HP_KNOWN, false))
            val resultPokemon = ResultIntentPokemonMapper.create(resultIntent)
            assertEquals(0, resultPokemon.hp)
            assertEquals("0", formatHpDisplayText(resultPokemon.hp, "Unknown"))
            assertEquals("0", formatHpDisplayText(resultPokemon.hp, "-"))

            val reopened = ResultHistorySaveMapper.fromIntent(resultIntent, history, null)
            database.scanHistoryDao().update(reopened)
            assertEquals(0, database.scanHistoryDao().getById(historyId)!!.hp)
        } finally {
            database.close()
        }
    }

    @Test
    fun legacyHistoryWithoutExplanationDoesNotPresentItsScoreAsAReason() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val id = database.scanHistoryDao().insert(
                ScanHistoryEntity(
                    pokemonName = "Raichu",
                    cp = 475,
                    hp = 52,
                    caughtDate = null,
                    rawOcrText = "legacy-rarity-history",
                    rarityScore = 54,
                    rarityTier = "RARE",
                    rarityAnalysisJson = null
                )
            )

            val stored = database.scanHistoryDao().getById(id)!!
            assertNull(stored.rarityAnalysisJson)
            val resultIntent = HistoryResultIntentMapper.create(context, stored)
            val reopened = ResultIntentPokemonMapper.create(resultIntent)
            val summary = reopened.valuableSummary()
            assertTrue(summary.contains("no strong rarity signal was confirmed"))
            assertFalse(summary.contains("calculated rarity score", ignoreCase = true))

            val saved = ResultHistorySaveMapper.fromIntent(resultIntent, stored, null)
            database.scanHistoryDao().update(saved)
            val reopenedAgain = ResultIntentPokemonMapper.create(
                HistoryResultIntentMapper.create(context, database.scanHistoryDao().getById(id)!!)
            )
            assertNull(database.scanHistoryDao().getById(id)!!.rarityAnalysisJson)
            assertFalse(reopenedAgain.valuableSummary().contains("calculated rarity score", ignoreCase = true))
        } finally {
            database.close()
        }
    }

    private suspend fun insertRoundTripRecord(database: AppDatabase, hp: Int = 52) = database.scanHistoryDao().insert(
        ScanHistoryEntity(
            pokemonName = "Raichu",
            cp = 475,
            hp = hp,
            caughtDate = DateParseUtils.parseIsoDate("2019-07-12"),
            rawOcrText = "history-round-trip",
            ivMin = 37,
            ivMax = 82,
            ivCandidateCount = 19,
            ivSolveMode = IvSolveMode.RANGE.name,
            ivLevelMin = 15.5f,
            ivLevelMax = 16.5f,
            scanConfidenceScore = 85,
            scanConfidenceLabel = "scan.review",
            scanConfidenceDetail = "fields:cp_present,hp_missing,date_present",
            recognitionIdentityJson = RecognitionIdentityHistoryCodec.encode(knownAlolanIdentity())
        )
    )

    private fun assertHistoryResultIntent(resultIntent: android.content.Intent, historyId: Long) {
        assertEquals(ResultActivity::class.java.name, resultIntent.component?.className)
        assertEquals(historyId, resultIntent.getLongExtra(ResultActivity.EXTRA_HISTORY_ENTRY_ID, 0L))
        assertEquals("37%–82%", resultIntent.getStringExtra(ResultActivity.EXTRA_IV_ESTIMATE))
        assertEquals("Jul 12, 2019", resultIntent.getStringExtra(ResultActivity.EXTRA_DATE))
        assertTrue(resultIntent.getBooleanExtra(ResultActivity.EXTRA_HAS_SPECIAL_FORM, false))
        val identity = RecognitionIdentityHistoryCodec.decode(
            resultIntent.getStringExtra(ResultActivity.EXTRA_RECOGNITION_IDENTITY)
        )
        assertEquals(knownAlolanIdentity(), identity)
        assertEquals("Form: Alolan", RecognitionIdentityHistoryCodec.formPresentation(identity))
    }

    private fun assertCollectionDetailKeepsPersistedRarityExplanation(history: ScanHistoryEntity) {
        val collectionDetail = history.toUiPokemon()
        assertTrue(collectionDetail.analysis.any {
            it.title == "Older catch" && it.detail == "2019-07-12"
        })
        assertTrue(collectionDetail.analysis.any { it.title == "Special form" })
        assertTrue(collectionDetail.analysis.any { it.title == "Purified Pokemon" })
        assertTrue(collectionDetail.analysis.any { it.title == "XXS size" })
        assertTrue(collectionDetail.analysis.any { it.title == "XXL size" })
        assertTrue(collectionDetail.analysis.any { it.title == "Special background" })
        assertTrue(collectionDetail.valuableSummary().contains("older catch from 2019-07-12"))
    }

    private suspend fun insertRarityExplanationRecord(database: AppDatabase): Long {
        val scan = PokemonData(
            cp = 475,
            hp = 52,
            maxHp = 60,
            name = "Raichu",
            realName = "Raichu",
            candyName = "Raichu",
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = DateParseUtils.parseIsoDate("2019-07-12"),
            recognitionIdentity = knownAlolanIdentity().copy(
                purified = RecognitionTruth.TRUE,
                xxs = RecognitionTruth.TRUE,
                xxl = RecognitionTruth.TRUE,
                locationCard = RecognitionTruth.TRUE
            )
        )
        val rarity = RarityScore(
            totalScore = 54,
            tier = RarityTier.RARE,
            breakdown = linkedMapOf("older_catch" to 14, "species" to 40),
            explanation = listOf(
                encodeExplanationItem("Older catch", "2019-07-12"),
                encodeExplanationItem("Species rarity", "Raichu")
            )
        )
        return database.scanHistoryDao().insert(ScanHistoryMapper.toEntity(scan, VisualFeatures(), rarity))
    }

    private fun assertResultCardAndShare(resultPokemon: com.pokerarity.scanner.data.model.Pokemon) {
        assertEquals("Form: Alolan", resultPokemon.formPresentationText)
        assertTrue(resultPokemon.tags.contains("FORM"))
        assertEquals("Jul 12, 2019", resultPokemon.caughtDate)
        assertEquals(
            "IV 37%–82% | Level 15.5–16.5",
            formatIvLevelSummary(
                resultPokemon.ivText,
                null,
                resultPokemon.ivSolveMode,
                resultPokemon.ivLevelMin,
                resultPokemon.ivLevelMax
            )
        )
        val shared = ResultShareRenderer.appendScanConfidenceToShareText(
            "Reopened scan",
            resultPokemon.decisionSupport
        )
        assertTrue(shared.contains("Recognition confidence: Review recommended"))
        assertTrue(shared.contains("85% - CP available, HP missing, date available"))
    }

    private suspend fun saveReopenedResult(
        database: AppDatabase,
        historyId: Long,
        resultIntent: android.content.Intent
    ) {
        val result = ScanIvEstimateExtras.fromIntent(resultIntent)
        assertEquals(IvSolveMode.RANGE, result.solveMode)
        assertEquals(
            "IV 37%–82% | Level 15.5–16.5",
            formatIvLevelSummary(result.text, null, result.solveMode, result.levelMin, result.levelMax)
        )
        repeat(2) {
            val current = database.scanHistoryDao().getById(historyId)!!
            database.scanHistoryDao().update(ResultHistorySaveMapper.fromIntent(resultIntent, current, null))
        }
        val saved = database.scanHistoryDao().getById(historyId)!!
        assertEquals(1, database.scanHistoryDao().count())
        assertEquals(historyId, saved.id)
        assertEquals(37, saved.ivMin)
        assertEquals(82, saved.ivMax)
        assertEquals(19, saved.ivCandidateCount)
        assertEquals("RANGE", saved.ivSolveMode)
        assertEquals(15.5f, saved.ivLevelMin)
        assertEquals(16.5f, saved.ivLevelMax)
        assertEquals(
            "2019-07-12",
            DateParseUtils.formatDate(saved.caughtDate!!, DateParseUtils.ISO_DATE_FORMATTER)
        )
        assertEquals(
            "Form: Alolan",
            RecognitionIdentityHistoryCodec.formPresentation(
                RecognitionIdentityHistoryCodec.decode(saved.recognitionIdentityJson)
            )
        )
    }

    @Test
    fun historyNavigationAndSavePreserveUnknownAndNullableValues() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val legacyId = database.scanHistoryDao().insert(
                ScanHistoryEntity(
                    pokemonName = "Pikachu",
                    cp = null,
                    hp = null,
                    caughtDate = null,
                    rawOcrText = "legacy-history"
                )
            )
            val persistedLegacy = database.scanHistoryDao().getById(legacyId)!!
            verifyLegacyRoundTrip(database, persistedLegacy)
            verifyInsufficientRoundTrip(database, persistedLegacy)
        } finally {
            database.close()
        }
    }

    @Test
    fun eventDecisionContextSurvivesHistoryResultSharePresentationAndSave() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val id = database.scanHistoryDao().insert(
                ScanHistoryEntity(
                    pokemonName = "Pikachu",
                    cp = 640,
                    hp = 60,
                    caughtDate = null,
                    rawOcrText = "event-history-round-trip",
                    eventConfidenceCode = "DATE_BACKED_EVENT",
                    eventConfidenceLabel = "Spring event",
                    eventConfidenceDetail = "The catch date matches Spring event timing.",
                    mismatchGuardTitle = "Event blocked by catch date",
                    mismatchGuardDetail = "The caught date conflicts with the event window.",
                    recognitionSummary = "Event metadata was suppressed because it conflicts with the catch date.",
                    scanConfidenceScore = 82,
                    scanConfidenceLabel = "High confidence",
                    scanConfidenceDetail = "Name and CP matched"
                )
            )
            val stored = database.scanHistoryDao().getById(id)!!
            val resultIntent = HistoryResultIntentMapper.create(context, stored)

            val support = resultIntent.readScanDecisionSupport()
            assertDecisionSupport(support)

            // Exercise the same Intent-to-card mapping that ResultActivity uses.
            val result = ResultIntentPokemonMapper.create(resultIntent)
            assertDecisionSupport(result.decisionSupport)
            assertTrue(result.valuableSummary().contains("caught date conflicts with the event window"))

            database.scanHistoryDao().update(ResultHistorySaveMapper.fromIntent(resultIntent, stored, null))
            val reread = database.scanHistoryDao().getById(id)!!
            assertPersistedDecisionSupport(reread)
            assertTrue(reread.toUiPokemon().valuableSummary().contains("caught date conflicts with the event window"))
        } finally {
            database.close()
        }
    }

    private fun assertDecisionSupport(support: ScanDecisionSupport?) {
        assertEquals("DATE_BACKED_EVENT", support?.eventConfidenceCode)
        assertEquals("Spring event", support?.eventConfidenceLabel)
        assertEquals("The catch date matches Spring event timing.", support?.eventConfidenceDetail)
        assertEquals("Event blocked by catch date", support?.mismatchGuardTitle)
        assertEquals("The caught date conflicts with the event window.", support?.mismatchGuardDetail)
        assertEquals(
            "Event metadata was suppressed because it conflicts with the catch date.",
            support?.recognitionSummary
        )
        assertEquals("High confidence", support?.scanConfidenceLabel)
    }

    private fun assertPersistedDecisionSupport(history: ScanHistoryEntity) {
        assertEquals("DATE_BACKED_EVENT", history.eventConfidenceCode)
        assertEquals("Spring event", history.eventConfidenceLabel)
        assertEquals("The catch date matches Spring event timing.", history.eventConfidenceDetail)
        assertEquals("Event blocked by catch date", history.mismatchGuardTitle)
        assertEquals("The caught date conflicts with the event window.", history.mismatchGuardDetail)
        assertEquals(
            "Event metadata was suppressed because it conflicts with the catch date.",
            history.recognitionSummary
        )
        assertDecisionSupport(history.toUiPokemon().decisionSupport)
    }

    private suspend fun verifyLegacyRoundTrip(database: AppDatabase, history: ScanHistoryEntity) {
        val resultIntent = HistoryResultIntentMapper.create(context, history)
        listOf(
            ResultActivity.EXTRA_IV_ESTIMATE,
            ResultActivity.EXTRA_IV_SOLVE_MODE,
            ResultActivity.EXTRA_IV_MIN,
            ResultActivity.EXTRA_IV_MAX,
            ResultActivity.EXTRA_IV_CANDIDATE_COUNT,
            ResultActivity.EXTRA_IV_LEVEL_MIN,
            ResultActivity.EXTRA_IV_LEVEL_MAX
        ).forEach { assertFalse("nullable field stays absent: $it", resultIntent.hasExtra(it)) }
        assertFalse(resultIntent.getBooleanExtra(ResultActivity.EXTRA_HAS_SPECIAL_FORM, false))
        assertNull(resultIntent.getStringExtra(ResultActivity.EXTRA_DATE))
        val resultPokemon = ResultIntentPokemonMapper.create(resultIntent)
        assertFalse(resultPokemon.tags.contains("FORM"))
        assertNull(resultPokemon.formPresentationText)
        assertEquals("Unknown", resultPokemon.caughtDate)

        val saved = ResultHistorySaveMapper.fromIntent(resultIntent, history, null)
        database.scanHistoryDao().update(saved)
        val reread = database.scanHistoryDao().getById(history.id)!!
        assertNull(reread.ivMin)
        assertNull(reread.ivMax)
        assertNull(reread.ivCandidateCount)
        assertNull(reread.ivSolveMode)
        assertNull(reread.ivLevelMin)
        assertNull(reread.ivLevelMax)
    }

    private suspend fun verifyInsufficientRoundTrip(database: AppDatabase, legacyHistory: ScanHistoryEntity) {
        val historyId = database.scanHistoryDao().insert(
            legacyHistory.copy(
                id = 0,
                ivCandidateCount = 0,
                ivSolveMode = IvSolveMode.INSUFFICIENT.name,
                ivLevelMin = 30f,
                ivLevelMax = 32.5f
            )
        )
        val history = database.scanHistoryDao().getById(historyId)!!
        val resultIntent = HistoryResultIntentMapper.create(context, history)
        assertEquals("Unknown", resultIntent.getStringExtra(ResultActivity.EXTRA_IV_ESTIMATE))
        assertEquals(30f, resultIntent.getFloatExtra(ResultActivity.EXTRA_IV_LEVEL_MIN, Float.NaN))
        assertEquals(32.5f, resultIntent.getFloatExtra(ResultActivity.EXTRA_IV_LEVEL_MAX, Float.NaN))

        val result = ScanIvEstimateExtras.fromIntent(resultIntent)
        assertEquals(
            "IV Unknown | Level 30–32.5",
            formatIvLevelSummary(result.text, null, result.solveMode, result.levelMin, result.levelMax)
        )
        val saved = ResultHistorySaveMapper.fromIntent(resultIntent, history, null)
        database.scanHistoryDao().update(saved)
        val reread = database.scanHistoryDao().getById(historyId)!!
        assertEquals(0, reread.ivCandidateCount)
        assertEquals("INSUFFICIENT", reread.ivSolveMode)
        assertNull(reread.ivMin)
        assertNull(reread.ivMax)
        assertEquals(30f, reread.ivLevelMin)
        assertEquals(32.5f, reread.ivLevelMax)
        assertEquals(2, database.scanHistoryDao().count())
    }

    private fun knownAlolanIdentity() = RecognitionIdentity(
        speciesStatus = RecognitionSpeciesStatus.KNOWN,
        canonicalSpecies = "Raichu",
        speciesAuthority = "species_exact_authority",
        formStatus = RecognitionFormStatus.KNOWN,
        knownForm = "Alolan",
        formProvenance = "authoritative_variant_db",
        shiny = RecognitionTruth.UNKNOWN,
        shadow = RecognitionTruth.UNKNOWN,
        purified = RecognitionTruth.UNKNOWN,
        lucky = RecognitionTruth.UNKNOWN,
        costume = RecognitionTruth.UNKNOWN,
        specialForm = RecognitionTruth.TRUE,
        xxs = RecognitionTruth.UNKNOWN,
        xxl = RecognitionTruth.UNKNOWN,
        locationCard = RecognitionTruth.UNKNOWN
    )
}
