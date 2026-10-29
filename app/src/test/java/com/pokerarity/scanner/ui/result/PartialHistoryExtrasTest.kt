package com.pokerarity.scanner.ui.result

import android.content.Intent
import com.pokerarity.scanner.data.model.RarityAnalysisItem
import com.pokerarity.scanner.data.model.RecognitionFormStatus
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus
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

    @Test fun typedHpDistinguishesObservedZeroFromUnknownAndLegacyZero() {
        val zeroHp = Intent().putScanHp(0)
        assertTrue(zeroHp.hasExtra(ResultActivity.EXTRA_HP))
        assertTrue(zeroHp.getBooleanExtra(ResultActivity.EXTRA_HP_KNOWN, false))
        assertEquals(0, zeroHp.readScanHp())

        val unknownHp = Intent().putScanHp(null)
        assertFalse(unknownHp.getBooleanExtra(ResultActivity.EXTRA_HP_KNOWN, true))
        assertNull(unknownHp.readScanHp())

        assertNull("legacy zero remains the old missing sentinel", Intent()
            .putExtra(ResultActivity.EXTRA_HP, 0).readScanHp())
        assertEquals(52, Intent().putExtra(ResultActivity.EXTRA_HP, 52).readScanHp())
        assertNull(Intent().putScanHp(-1).readScanHp())
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

    @Test fun scanExtrasMergeIdentityVariantsWithoutDuplicatingFormExplanation() {
        val pokemon = pokemonFromScanExtras(
            name = "Raichu",
            cp = 500,
            hp = 80,
            score = 42,
            tier = "RARE",
            isShiny = false,
            isLucky = false,
            hasCostume = false,
            hasSpecialForm = false,
            isShadow = false,
            dateText = null,
            analysisOverride = listOf(RarityAnalysisItem("Form: Alolan", null, true)),
            recognitionIdentity = recognitionIdentityWithFormAndSize(),
        )

        assertEquals(1, pokemon.analysis.count { it.title.startsWith("Form:") })
        assertTrue(pokemon.analysis.any { it.title == "XXS size" && it.isPositive })
        assertTrue("XXS" in pokemon.tags)
    }

    private fun recognitionIdentityWithFormAndSize() = RecognitionIdentity(
        speciesStatus = RecognitionSpeciesStatus.UNKNOWN,
        formStatus = RecognitionFormStatus.UNKNOWN,
        shiny = RecognitionTruth.UNKNOWN,
        shadow = RecognitionTruth.UNKNOWN,
        purified = RecognitionTruth.UNKNOWN,
        lucky = RecognitionTruth.UNKNOWN,
        costume = RecognitionTruth.UNKNOWN,
        specialForm = RecognitionTruth.TRUE,
        xxs = RecognitionTruth.TRUE,
        xxl = RecognitionTruth.UNKNOWN,
        locationCard = RecognitionTruth.UNKNOWN,
    )
}
