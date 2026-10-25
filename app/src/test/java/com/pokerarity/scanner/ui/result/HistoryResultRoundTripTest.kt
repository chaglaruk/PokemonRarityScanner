package com.pokerarity.scanner.ui.result

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.model.formatIvLevelSummary
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
    fun historyNavigationResultAndSaveKeepIvRangeAndLevels() {
        val history = ScanHistoryEntity(
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

        val resultIntent = HistoryResultIntentMapper.create(context, history)
        assertEquals(ResultActivity::class.java.name, resultIntent.component?.className)
        assertEquals("37%–82%", resultIntent.getStringExtra(ResultActivity.EXTRA_IV_ESTIMATE))

        // ResultActivity reads these fields to build the screen and uses the same value
        // object when Save creates its next ScanHistoryEntity.
        val result = ScanIvEstimateExtras.fromIntent(resultIntent)
        assertEquals(IvSolveMode.RANGE, result.solveMode)
        assertEquals(
            "IV 37%–82% | Level 15.5–16.5",
            formatIvLevelSummary(result.text, null, result.solveMode, result.levelMin, result.levelMax)
        )
        val saved = result.applyTo(ScanHistoryEntity(
            pokemonName = "Raichu",
            cp = resultIntent.readScanCp(),
            hp = resultIntent.getIntExtra(ResultActivity.EXTRA_HP, 0),
            caughtDate = null,
            rawOcrText = "Recognition Save Snapshot"
        ))

        assertEquals(37, saved.ivMin)
        assertEquals(82, saved.ivMax)
        assertEquals(19, saved.ivCandidateCount)
        assertEquals("RANGE", saved.ivSolveMode)
        assertEquals(15.5f, saved.ivLevelMin)
        assertEquals(16.5f, saved.ivLevelMax)
    }

    @Test
    fun historyNavigationAndSavePreserveUnknownAndNullableValues() {
        val legacyHistory = ScanHistoryEntity(
            pokemonName = "Pikachu",
            cp = null,
            hp = null,
            caughtDate = null,
            rawOcrText = "legacy-history"
        )
        val legacyIntent = HistoryResultIntentMapper.create(context, legacyHistory)

        listOf(
            ResultActivity.EXTRA_IV_ESTIMATE,
            ResultActivity.EXTRA_IV_SOLVE_MODE,
            ResultActivity.EXTRA_IV_MIN,
            ResultActivity.EXTRA_IV_MAX,
            ResultActivity.EXTRA_IV_CANDIDATE_COUNT,
            ResultActivity.EXTRA_IV_LEVEL_MIN,
            ResultActivity.EXTRA_IV_LEVEL_MAX
        ).forEach { assertFalse("nullable field stays absent: $it", legacyIntent.hasExtra(it)) }
        val legacyResult = ScanIvEstimateExtras.fromIntent(legacyIntent)
        val legacySaved = legacyResult.applyTo(legacyHistory.copy(id = 0))
        assertNull(legacySaved.ivMin)
        assertNull(legacySaved.ivMax)
        assertNull(legacySaved.ivCandidateCount)
        assertNull(legacySaved.ivSolveMode)
        assertNull(legacySaved.ivLevelMin)
        assertNull(legacySaved.ivLevelMax)

        val insufficient = legacyHistory.copy(
            ivCandidateCount = 0,
            ivSolveMode = IvSolveMode.INSUFFICIENT.name
        )
        val insufficientIntent = HistoryResultIntentMapper.create(context, insufficient)
        assertEquals("Unknown", insufficientIntent.getStringExtra(ResultActivity.EXTRA_IV_ESTIMATE))
        val insufficientResult = ScanIvEstimateExtras.fromIntent(insufficientIntent)
        assertEquals(
            "IV Unknown",
            formatIvLevelSummary(
                insufficientResult.text,
                null,
                insufficientResult.solveMode,
                insufficientResult.levelMin,
                insufficientResult.levelMax
            )
        )
        val insufficientSaved = insufficientResult.applyTo(insufficient.copy(id = 0))
        assertEquals(0, insufficientSaved.ivCandidateCount)
        assertEquals("INSUFFICIENT", insufficientSaved.ivSolveMode)
        assertNull(insufficientSaved.ivMin)
        assertNull(insufficientSaved.ivMax)
        assertTrue(insufficientResult.levelMin == null && insufficientResult.levelMax == null)
    }
}
