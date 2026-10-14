package com.pokerarity.scanner.ui.result

import android.content.Intent
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.data.model.pokemonFromScanExtras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PartialHistoryExtrasTest {
    @Test fun knownIntegerExtraRetainsItsKeyAndValue() {
        val intent = Intent().putScanCp(500)
        assertTrue(intent.hasExtra(ResultActivity.EXTRA_CP))
        assertEquals(500, intent.readScanCp())
    }

    @Test fun absentCpIsNullWithoutNumericSubstitute() {
        assertNull(Intent().readScanCp())
        val intent = Intent().putScanCp(500).putScanCp(null)
        assertFalse(intent.hasExtra(ResultActivity.EXTRA_CP))
        assertNull(intent.readScanCp())
    }

    @Test fun legacyZeroAndUnsupportedCpAreNotMeasuredValues() {
        listOf(0, -1).forEach { cp ->
            assertNull(Intent().putExtra(ResultActivity.EXTRA_CP, cp).readScanCp())
        }
    }

    @Test fun typedStatesSurviveHistoryDetailAndOverlayExtras() {
        RecognitionTruth.values().forEach { truth ->
            val intent = Intent().putScanShinyTruth(truth)
                .putExtra(ResultActivity.EXTRA_IS_SHINY, truth == RecognitionTruth.TRUE)
            assertEquals(truth, intent.readScanShinyTruth())
        }
    }

    @Test fun legacyFalseExtraIsUnknownAndLegacyPositiveIsKept() {
        assertEquals(RecognitionTruth.UNKNOWN, Intent().readScanShinyTruth())
        assertEquals(RecognitionTruth.UNKNOWN,
            Intent().putExtra(ResultActivity.EXTRA_IS_SHINY, false).readScanShinyTruth())
        assertEquals(RecognitionTruth.TRUE,
            Intent().putExtra(ResultActivity.EXTRA_IS_SHINY, true).readScanShinyTruth())
    }

    @Test fun invalidAndNullTypedExtraDoNotAcquireLegacyPositiveAuthority() {
        val invalid = Intent().putExtra(ResultActivity.EXTRA_SHINY_TRUTH, "unsupported")
            .putExtra(ResultActivity.EXTRA_IS_SHINY, true)
        assertEquals(RecognitionTruth.UNKNOWN, invalid.readScanShinyTruth())
        val absentValue = Intent().putExtra(ResultActivity.EXTRA_SHINY_TRUTH, null as String?)
            .putExtra(ResultActivity.EXTRA_IS_SHINY, true)
        assertEquals(RecognitionTruth.UNKNOWN, absentValue.readScanShinyTruth())
    }

    @Test fun removingTypedMetadataRetainsLegacyPositiveCompatibility() {
        val intent = Intent().putExtra(ResultActivity.EXTRA_IS_SHINY, true)
            .putScanShinyTruth(RecognitionTruth.FALSE).putScanShinyTruth(null)
        assertFalse(intent.hasExtra(ResultActivity.EXTRA_SHINY_TRUTH))
        assertEquals(RecognitionTruth.TRUE, intent.readScanShinyTruth())
    }

    @Test fun legacyFactoryCallStaysCompatibleAndExplicitUnknownDoesNotClaimShiny() {
        fun ui(shiny: Boolean, truth: RecognitionTruth? = null) = pokemonFromScanExtras(
            name = "Pikachu", cp = null, hp = null, score = 42, tier = "RARE",
            isShiny = shiny, isLucky = false, hasCostume = false, hasSpecialForm = false,
            isShadow = false, dateText = null, shinyTruth = truth
        )
        assertNull(ui(false).cp)
        assertEquals("Unknown", ui(false).cpDisplayText)
        assertEquals(RecognitionTruth.UNKNOWN, ui(false).shinyTruth)
        assertEquals(RecognitionTruth.TRUE, ui(true).shinyTruth)
        assertFalse("SHINY" in ui(true, RecognitionTruth.UNKNOWN).tags)
        assertEquals(42, ui(true, RecognitionTruth.UNKNOWN).rarityScore)
    }
}
