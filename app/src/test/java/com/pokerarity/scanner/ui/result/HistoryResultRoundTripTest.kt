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
import com.pokerarity.scanner.data.model.toUiPokemon
import com.pokerarity.scanner.data.model.valuableSummary
import com.pokerarity.scanner.data.model.formatIvLevelSummary
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
    fun historyNavigationResultAndSaveKeepIvRangeAndLevels() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val historyId = database.scanHistoryDao().insert(
                ScanHistoryEntity(
                    pokemonName = "Raichu",
                    cp = 475,
                    hp = 52,
                    caughtDate = null,
                    rawOcrText = "history-round-trip",
                    ivMin = 37,
                    ivMax = 82,
                    ivCandidateCount = 19,
                    ivSolveMode = IvSolveMode.RANGE.name,
                    ivLevelMin = 15.5f,
                    ivLevelMax = 16.5f,
                    recognitionIdentityJson = RecognitionIdentityHistoryCodec.encode(knownAlolanIdentity())
                )
            )
            val persistedHistory = database.scanHistoryDao().getById(historyId)!!

            // This is the same Intent mapper called when HistoryActivity launches ResultActivity.
            val resultIntent = HistoryResultIntentMapper.create(context, persistedHistory)
            assertEquals(ResultActivity::class.java.name, resultIntent.component?.className)
            assertEquals(historyId, resultIntent.getLongExtra(ResultActivity.EXTRA_HISTORY_ENTRY_ID, 0L))
            assertEquals("37%–82%", resultIntent.getStringExtra(ResultActivity.EXTRA_IV_ESTIMATE))
            val resultIdentity = RecognitionIdentityHistoryCodec.decode(
                resultIntent.getStringExtra(ResultActivity.EXTRA_RECOGNITION_IDENTITY)
            )
            assertEquals(knownAlolanIdentity(), resultIdentity)
            assertEquals("Form: Alolan", RecognitionIdentityHistoryCodec.formPresentation(resultIdentity))

            // ResultActivity reads these fields for the result card and applies the same values on Save.
            val result = ScanIvEstimateExtras.fromIntent(resultIntent)
            assertEquals(IvSolveMode.RANGE, result.solveMode)
            assertEquals(
                "IV 37%–82% | Level 15.5–16.5",
                formatIvLevelSummary(result.text, null, result.solveMode, result.levelMin, result.levelMax)
            )
            repeat(2) {
                val current = database.scanHistoryDao().getById(historyId)!!
                val saved = ResultHistorySaveMapper.fromIntent(resultIntent, current, null)
                database.scanHistoryDao().update(saved)
            }
            val persistedSave = database.scanHistoryDao().getById(historyId)!!

            assertEquals(1, database.scanHistoryDao().count())
            assertEquals(historyId, persistedSave.id)
            assertEquals(37, persistedSave.ivMin)
            assertEquals(82, persistedSave.ivMax)
            assertEquals(19, persistedSave.ivCandidateCount)
            assertEquals("RANGE", persistedSave.ivSolveMode)
            assertEquals(15.5f, persistedSave.ivLevelMin)
            assertEquals(16.5f, persistedSave.ivLevelMax)
            assertEquals(
                "Form: Alolan",
                RecognitionIdentityHistoryCodec.formPresentation(
                    RecognitionIdentityHistoryCodec.decode(persistedSave.recognitionIdentityJson)
                )
            )
        } finally {
            database.close()
        }
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
                    scanConfidenceScore = 82,
                    scanConfidenceLabel = "High confidence",
                    scanConfidenceDetail = "Name and CP matched"
                )
            )
            val stored = database.scanHistoryDao().getById(id)!!
            val resultIntent = HistoryResultIntentMapper.create(context, stored)

            val support = resultIntent.readScanDecisionSupport()
            assertEquals("DATE_BACKED_EVENT", support?.eventConfidenceCode)
            assertEquals("Spring event", support?.eventConfidenceLabel)
            assertEquals("The catch date matches Spring event timing.", support?.eventConfidenceDetail)
            assertEquals("High confidence", support?.scanConfidenceLabel)

            val result = com.pokerarity.scanner.data.model.pokemonFromScanExtras(
                name = resultIntent.getStringExtra(ResultActivity.EXTRA_POKEMON_NAME).orEmpty(),
                cp = resultIntent.readScanCp(),
                hp = resultIntent.getIntExtra(ResultActivity.EXTRA_HP, 0),
                score = resultIntent.getIntExtra(ResultActivity.EXTRA_SCORE, 0),
                tier = resultIntent.getStringExtra(ResultActivity.EXTRA_TIER).orEmpty(),
                isShiny = resultIntent.getBooleanExtra(ResultActivity.EXTRA_IS_SHINY, false),
                isLucky = resultIntent.getBooleanExtra(ResultActivity.EXTRA_IS_LUCKY, false),
                hasCostume = resultIntent.getBooleanExtra(ResultActivity.EXTRA_HAS_COSTUME, false),
                hasSpecialForm = resultIntent.getBooleanExtra(ResultActivity.EXTRA_HAS_SPECIAL_FORM, false),
                isShadow = resultIntent.getBooleanExtra(ResultActivity.EXTRA_IS_SHADOW, false),
                dateText = resultIntent.getStringExtra(ResultActivity.EXTRA_DATE),
                decisionSupport = support
            )
            assertTrue(result.valuableSummary().contains("The catch date matches Spring event timing."))

            database.scanHistoryDao().update(ResultHistorySaveMapper.fromIntent(resultIntent, stored, null))
            val reread = database.scanHistoryDao().getById(id)!!
            assertEquals("DATE_BACKED_EVENT", reread.eventConfidenceCode)
            assertEquals("Spring event", reread.eventConfidenceLabel)
            assertEquals("The catch date matches Spring event timing.", reread.eventConfidenceDetail)
            assertEquals("High confidence", reread.toUiPokemon().decisionSupport?.scanConfidenceLabel)
            assertTrue(reread.toUiPokemon().valuableSummary().contains("The catch date matches Spring event timing."))
        } finally {
            database.close()
        }
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
