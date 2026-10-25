package com.pokerarity.scanner.ui.main

import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.IvSolveMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanHistoryPresentationTest {

    @Test
    fun levelOnlyEstimateAppearsAlongsideUnknownIv() {
        val scan = history(
            ivSolveMode = IvSolveMode.INSUFFICIENT.name,
            ivLevelMin = 30f,
            ivLevelMax = 30.5f
        )

        assertEquals("IV Unknown | Level 30–30.5", historyIvLevelSummary(scan))
    }

    @Test
    fun resolvedIvAndBestBuddyLevelUseTheSharedSummaryFormatting() {
        val scan = history(
            ivSolveMode = IvSolveMode.EXACT.name,
            ivMin = 100,
            ivMax = 100,
            ivLevelMin = 50.5f,
            ivLevelMax = 50.5f
        )

        assertEquals("IV 100% | Level 50.5", historyIvLevelSummary(scan))
    }

    @Test
    fun legacyAndIncompleteLevelsRemainUnfabricated() {
        assertNull(historyIvLevelSummary(history()))

        val partial = history(
            ivSolveMode = IvSolveMode.INSUFFICIENT.name,
            ivLevelMin = 30f
        )
        assertEquals("IV Unknown", historyIvLevelSummary(partial))
    }

    private fun history(
        ivSolveMode: String? = null,
        ivMin: Int? = null,
        ivMax: Int? = null,
        ivLevelMin: Float? = null,
        ivLevelMax: Float? = null
    ) = ScanHistoryEntity(
        pokemonName = "Pikachu",
        cp = 475,
        hp = 52,
        caughtDate = null,
        rawOcrText = "",
        ivSolveMode = ivSolveMode,
        ivMin = ivMin,
        ivMax = ivMax,
        ivLevelMin = ivLevelMin,
        ivLevelMax = ivLevelMax
    )
}
