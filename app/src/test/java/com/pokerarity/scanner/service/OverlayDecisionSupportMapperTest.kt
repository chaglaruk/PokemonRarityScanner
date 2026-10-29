package com.pokerarity.scanner.service

import android.content.Intent
import com.pokerarity.scanner.ui.result.ResultActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverlayDecisionSupportMapperTest {

    @Test
    fun parsesRecognitionSummaryAndPreservesLegacyWhyNotExactSeparately() {
        val intent = Intent()
            .putExtra(ResultActivity.EXTRA_RECOGNITION_SUMMARY, "Species evidence needs review.")
            .putExtra(ResultActivity.EXTRA_WHY_NOT_EXACT, "IV range remains open.")
            .putExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_LABEL, "Review recommended")

        val support = OverlayDecisionSupportMapper.fromIntent(intent)

        assertNotNull(support)
        assertEquals("Species evidence needs review.", support?.recognitionSummary)
        assertEquals("IV range remains open.", support?.whyNotExact)
        assertEquals("Review recommended", support?.scanConfidenceLabel)
    }
}
