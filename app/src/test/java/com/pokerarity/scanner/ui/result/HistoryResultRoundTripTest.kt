package com.pokerarity.scanner.ui.result

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.model.formatIvLevelSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
                    ivLevelMax = 16.5f
                )
            )
            val persistedHistory = database.scanHistoryDao().getById(historyId)!!

            // This is the same Intent mapper called when HistoryActivity launches ResultActivity.
            val resultIntent = HistoryResultIntentMapper.create(context, persistedHistory)
            assertEquals(ResultActivity::class.java.name, resultIntent.component?.className)
            assertEquals(historyId, resultIntent.getLongExtra(ResultActivity.EXTRA_HISTORY_ENTRY_ID, 0L))
            assertEquals("37%–82%", resultIntent.getStringExtra(ResultActivity.EXTRA_IV_ESTIMATE))

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
}
