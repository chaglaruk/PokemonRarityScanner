package com.pokerarity.scanner.ui.result

import android.content.Intent
import com.pokerarity.scanner.data.model.IvSolveDetails
import com.pokerarity.scanner.data.model.IvSolveMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanResultIvIntentExtrasTest {
    @Test
    fun overlayIntentKeepsDisconnectedLevelsWithoutInventingBounds() {
        val intent = Intent()
        val solve = IvSolveDetails(
            ivMin = 37,
            ivMax = 82,
            ivCandidateCount = 19,
            levelAlternatives = listOf(30f, 33f),
            ivSolveMode = IvSolveMode.RANGE,
            ivSolveSignalsUsed = listOf("CP", "MAX_HP")
        )

        intent.putScanIvSolveExtras(solve, "37%–82%")

        assertEquals("RANGE", intent.getStringExtra(ResultActivity.EXTRA_IV_SOLVE_MODE))
        assertEquals(19, intent.getIntExtra(ResultActivity.EXTRA_IV_CANDIDATE_COUNT, -1))
        assertEquals(37, intent.getIntExtra(ResultActivity.EXTRA_IV_MIN, -1))
        assertEquals(82, intent.getIntExtra(ResultActivity.EXTRA_IV_MAX, -1))
        assertEquals("37%–82%", intent.getStringExtra(ResultActivity.EXTRA_IV_ESTIMATE))
        assertEquals(listOf(30f, 33f), intent.getFloatArrayExtra(
            ResultActivity.EXTRA_IV_LEVEL_ALTERNATIVES
        )?.toList())
        assertFalse(intent.hasExtra(ResultActivity.EXTRA_IV_LEVEL_MIN))
        assertFalse(intent.hasExtra(ResultActivity.EXTRA_IV_LEVEL_MAX))
        assertEquals(
            listOf("CP", "MAX_HP"),
            intent.getStringArrayListExtra(ResultActivity.EXTRA_IV_SIGNALS)
        )
    }
}
