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
import com.pokerarity.scanner.data.model.ScanDecisionSupport
import com.pokerarity.scanner.data.model.toUiPokemon
import com.pokerarity.scanner.data.model.valuableSummary
import com.pokerarity.scanner.data.model.formatIvLevelSummary
import com.pokerarity.scanner.data.model.ivLevelDisplayText
import com.pokerarity.scanner.data.model.ivLevelSummaryText
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
    fun stardustOnlyDisconnectedLevelsSurviveHistoryResultSaveAndReopen() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val historyId = database.scanHistoryDao().insert(
                ScanHistoryEntity(
                    pokemonName = "Pikachu",
                    cp = 470,
                    hp = null,
                    caughtDate = null,
                    rawOcrText = "stardust-only-levels",
                    ivCandidateCount = 0,
                    ivSolveMode = IvSolveMode.INSUFFICIENT.name,
                    ivLevelAlternatives = "30.0,31.0,33.0,34.0"
                )
            )
            val current = database.scanHistoryDao().getById(historyId)!!
            val intent = HistoryResultIntentMapper.create(context, current)
            assertEquals(IvSolveMode.INSUFFICIENT.name, intent.getStringExtra(ResultActivity.EXTRA_IV_SOLVE_MODE))
            assertEquals(
                listOf(30f, 31f, 33f, 34f),
                intent.getFloatArrayExtra(ResultActivity.EXTRA_IV_LEVEL_ALTERNATIVES)?.toList()
            )
            val displayed = ResultIntentPokemonMapper.create(intent)
            assertEquals("IV Unknown | Level 30 or 31 or 33 or 34", displayed.ivLevelSummaryText)

            repeat(2) {
                val saved = database.scanHistoryDao().getById(historyId)!!
                database.scanHistoryDao().update(ResultHistorySaveMapper.fromIntent(intent, saved, null))
            }
            val reread = database.scanHistoryDao().getById(historyId)!!
            assertNull(reread.ivLevelMin)
            assertNull(reread.ivLevelMax)
            assertEquals("30.0,31.0,33.0,34.0", reread.ivLevelAlternatives)
            assertEquals(1, database.scanHistoryDao().count())

            val reopened = ResultIntentPokemonMapper.create(HistoryResultIntentMapper.create(context, reread))
            assertEquals("30 or 31 or 33 or 34", reopened.ivLevelDisplayText)
            assertEquals("IV Unknown | Level 30 or 31 or 33 or 34", reopened.ivLevelSummaryText)
        } finally {
            database.close()
        }
    }

    private suspend fun insertRoundTripRecord(database: AppDatabase) = database.scanHistoryDao().insert(
        ScanHistoryEntity(
            pokemonName = "Raichu",
            cp = 475,
            hp = 52,
            caughtDate = DateParseUtils.parseIsoDate("2019-07-12"),
            rawOcrText = "history-round-trip",
            ivMin = 37,
            ivMax = 82,
            ivCandidateCount = 19,
            ivSolveMode = IvSolveMode.RANGE.name,
            ivLevelMin = null,
            ivLevelMax = null,
            ivLevelAlternatives = "30.0,33.0",
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
        assertEquals(listOf(30f, 33f), resultIntent.getFloatArrayExtra(
            ResultActivity.EXTRA_IV_LEVEL_ALTERNATIVES
        )?.toList())
        assertEquals("Jul 12, 2019", resultIntent.getStringExtra(ResultActivity.EXTRA_DATE))
        assertTrue(resultIntent.getBooleanExtra(ResultActivity.EXTRA_HAS_SPECIAL_FORM, false))
        val identity = RecognitionIdentityHistoryCodec.decode(
            resultIntent.getStringExtra(ResultActivity.EXTRA_RECOGNITION_IDENTITY)
        )
        assertEquals(knownAlolanIdentity(), identity)
        assertEquals("Form: Alolan", RecognitionIdentityHistoryCodec.formPresentation(identity))
    }

    private fun assertResultCardAndShare(resultPokemon: com.pokerarity.scanner.data.model.Pokemon) {
        assertEquals("Form: Alolan", resultPokemon.formPresentationText)
        assertTrue(resultPokemon.tags.contains("FORM"))
        assertEquals("Jul 12, 2019", resultPokemon.caughtDate)
        assertEquals(listOf(30f, 33f), resultPokemon.ivLevelAlternatives)
        assertEquals("30 or 33", resultPokemon.ivLevelDisplayText)
        assertEquals(
            "IV 37%–82% | Level 30 or 33",
            formatIvLevelSummary(
                resultPokemon.ivText,
                null,
                resultPokemon.ivSolveMode,
                resultPokemon.ivLevelMin,
                resultPokemon.ivLevelMax,
                resultPokemon.ivLevelAlternatives
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
            "IV 37%–82% | Level 30 or 33",
            formatIvLevelSummary(
                result.text, null, result.solveMode, result.levelMin, result.levelMax, result.levelAlternatives
            )
        )
        assertEquals(listOf(30f, 33f), result.levelAlternatives)
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
        assertNull(saved.ivLevelMin)
        assertNull(saved.ivLevelMax)
        assertEquals("30.0,33.0", saved.ivLevelAlternatives)
        assertEquals(listOf(30f, 33f), saved.toUiPokemon().ivLevelAlternatives)
        assertEquals("30 or 33", saved.toUiPokemon().ivLevelDisplayText)
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
