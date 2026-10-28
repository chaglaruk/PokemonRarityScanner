package com.pokerarity.scanner.ui.share

import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.model.ScanDecisionSupport
import com.pokerarity.scanner.data.model.ScanReviewCodes
import com.pokerarity.scanner.data.model.formatIvLevelEstimateText
import com.pokerarity.scanner.data.model.formatHpDisplayText
import com.pokerarity.scanner.data.model.ivEstimateDisplayText
import com.pokerarity.scanner.data.model.ivLevelDisplayText
import com.pokerarity.scanner.data.model.ivLevelSummaryText
import com.pokerarity.scanner.data.model.normalizeIvText
import com.pokerarity.scanner.data.model.pokemonFromScanExtras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResultSharePresentationTest {
    private data class ShareStats(
        val cp: Int? = 500,
        val hp: Int? = 51,
        val dateText: String? = null
    )

    @Test
    fun shareAndOverlayHpPresentationKeepsFaintedZeroDistinctFromUnknown() {
        assertEquals("0", formatHpDisplayText(0, "-"))
        assertEquals("0", formatHpDisplayText(0, "Unknown"))
        assertEquals("-", formatHpDisplayText(null, "-"))
        assertEquals("Unknown", formatHpDisplayText(null, "Unknown"))
    }

    @Test
    fun sharedTextCarriesTheSameKnownOrAmbiguousFormLabelAsTheResult() {
        assertTrue(
            ResultShareRenderer.appendFormPresentationToShareText(
                "I found Raichu in PokeRarityScanner.",
                "Form: Alolan"
            ).contains("(Form: Alolan)")
        )
        assertTrue(
            ResultShareRenderer.appendFormPresentationToShareText(
                "I found Palkia in PokeRarityScanner.",
                "Possible forms: Origin / Altered"
            ).contains("(Possible forms: Origin / Altered)")
        )
    }

    @Test
    fun cardAndTextShareUseTheSameIvRangeAndLevelPresentation() {
        val pokemon = pokemon(
            ivText = "37%–82%",
            solveMode = IvSolveMode.RANGE,
            levelMin = 15.5f,
            levelMax = 16.5f
        )

        assertEquals("15.5–16.5", pokemon.ivLevelDisplayText)
        assertEquals("IV 37%–82% | Level 15.5–16.5", pokemon.ivLevelSummaryText)
        assertEquals(
            "I found Pikachu with a rarity score of 42 in PokeRarityScanner " +
                "(IV 37%–82% | Level 15.5–16.5).",
            ResultShareRenderer.appendEstimateToShareText(
                "I found Pikachu with a rarity score of 42 in PokeRarityScanner.",
                pokemon.ivLevelSummaryText
            )
        )
    }

    @Test
    fun overlayRangeNormalizationMatchesTheSharedResultAndSharePresentation() {
        val overlayIvText = normalizeIvText("37%–82%")
        val overlayPokemon = pokemon(ivText = overlayIvText, solveMode = IvSolveMode.RANGE)

        assertEquals("37%–82%", overlayIvText)
        assertEquals("37%–82%", overlayPokemon.ivEstimateDisplayText)
        assertEquals("IV 37%–82%", overlayPokemon.ivLevelSummaryText)
    }

    @Test
    fun insufficientIvCanStillShareKnownLevelAndNullBoundsStayUnknown() {
        val pokemon = pokemon(
            solveMode = IvSolveMode.INSUFFICIENT,
            levelMin = 30f,
            levelMax = 30f
        )

        assertEquals("30", pokemon.ivLevelDisplayText)
        assertEquals("IV Unknown | Level 30", pokemon.ivLevelSummaryText)
        assertTrue(
            ResultShareRenderer.appendEstimateToShareText("Pikachu result.", pokemon.ivLevelSummaryText)
                .contains("IV Unknown | Level 30")
        )
        assertNull(formatIvLevelEstimateText(30f, null))
        assertNull(formatIvLevelEstimateText(31f, 30f))
        assertNull(formatIvLevelEstimateText(0f, 0f))
        assertNull(formatIvLevelEstimateText(Float.NaN, 30f))
        assertNull(formatIvLevelEstimateText(15.25f, 16.5f))
    }

    @Test
    fun insufficientIvRangeKeepsLevelWindowConsistentForCardAndShare() {
        val pokemon = pokemon(
            solveMode = IvSolveMode.INSUFFICIENT,
            levelMin = 30f,
            levelMax = 32.5f
        )

        assertEquals("30–32.5", pokemon.ivLevelDisplayText)
        assertEquals("IV Unknown | Level 30–32.5", pokemon.ivLevelSummaryText)
        assertEquals(
            "Pikachu result (IV Unknown | Level 30–32.5).",
            ResultShareRenderer.appendEstimateToShareText("Pikachu result.", pokemon.ivLevelSummaryText)
        )
    }

    @Test
    fun insufficientSolveModeOverridesOverlayFallbackTextInCardAndShare() {
        val pokemon = pokemon(
            ivText = "Hesaplanamadı",
            solveMode = IvSolveMode.INSUFFICIENT
        )

        assertEquals("Unknown", pokemon.ivEstimateDisplayText)
        assertEquals("IV Unknown", pokemon.ivLevelSummaryText)
        assertEquals(
            "Pikachu result (IV Unknown).",
            ResultShareRenderer.appendEstimateToShareText("Pikachu result.", pokemon.ivLevelSummaryText)
        )
    }

    @Test
    fun validBestBuddyEffectiveLevelsDisplayAndInvalidValuesStayHidden() {
        assertEquals("50", formatIvLevelEstimateText(50f, 50f))
        assertEquals("50.5", formatIvLevelEstimateText(50.5f, 50.5f))
        assertEquals("51", formatIvLevelEstimateText(51f, 51f))
        assertEquals("50–51", formatIvLevelEstimateText(50f, 51f))
        assertNull(formatIvLevelEstimateText(51.5f, 51.5f))
        assertNull(formatIvLevelEstimateText(50f, 52f))
        assertNull(formatIvLevelEstimateText(50.25f, 51f))
        assertNull(formatIvLevelEstimateText(51f, 50.5f))
        assertNull(formatIvLevelEstimateText(Float.NaN, 51f))
        assertNull(formatIvLevelEstimateText(50f, Float.POSITIVE_INFINITY))
    }

    @Test
    fun unknownLegacyEstimateKeepsTheExistingPlainShareText() {
        val pokemon = pokemon()

        assertNull(pokemon.ivLevelSummaryText)
        assertEquals(
            "I found Pikachu with a rarity score of 42 in PokeRarityScanner.",
            ResultShareRenderer.appendEstimateToShareText(
                "I found Pikachu with a rarity score of 42 in PokeRarityScanner.",
                pokemon.ivLevelSummaryText
            )
        )
    }

    @Test
    fun shareTextUsesTheResultCardScanReviewPresentation() {
        val support = ScanDecisionSupport(
            eventConfidenceCode = "",
            eventConfidenceLabel = "",
            eventConfidenceDetail = "",
            scanConfidenceScore = 72,
            scanConfidenceLabel = ScanReviewCodes.REVIEW,
            scanConfidenceDetail =
                "${ScanReviewCodes.DETAILS_PREFIX}${ScanReviewCodes.CP_PRESENT},${ScanReviewCodes.HP_MISSING}"
        )

        assertEquals(
            "Result (Recognition confidence: Review recommended • 72% - CP available, HP missing).",
            ResultShareRenderer.appendScanConfidenceToShareText("Result.", support)
        )
        assertEquals("Result.", ResultShareRenderer.appendScanConfidenceToShareText("Result.", null))
    }

    @Test
    fun textOnlyShareAppendsABoundedValuableSummary() {
        val summary = buildString {
            append("Pikachu looks valuable because ")
            repeat(100) { append("a detailed event explanation ") }
        }

        val shareText = ResultShareRenderer.appendValuableSummaryToShareText("Result.", summary)
        val appendedSummary = shareText.substringAfter("\n\n")

        assertTrue(shareText.startsWith("Result.\n\n"))
        assertTrue(appendedSummary.codePointCount(0, appendedSummary.length) <= 480)
        assertTrue(appendedSummary.endsWith("…"))
        assertEquals(
            "Result.",
            ResultShareRenderer.appendValuableSummaryToShareText("Result.", "  ")
        )
    }

    @Test
    fun textOnlyShareIncludesKnownCpHpAndCaughtDate() {
        val pokemon = pokemon(stats = ShareStats(dateText = "Jan 2, 2020"))

        val shareText = ResultShareRenderer.appendKnownStatsToShareText(
            "Result.", pokemon, "CP", "HP", "CAUGHT"
        )

        assertEquals("Result.\nCP 500 • HP 51 • CAUGHT Jan 2, 2020", shareText)
    }

    @Test
    fun textOnlyShareKeepsObservedZeroHpAndOmitsMissingStats() {
        val fainted = pokemon(stats = ShareStats(hp = 0, dateText = "Jan 2, 2020"))
        val faintedShare = ResultShareRenderer.appendKnownStatsToShareText(
            "Result.", fainted, "CP", "HP", "CAUGHT"
        )
        assertTrue(faintedShare.contains("Result.\nCP 500 • HP 0 • CAUGHT Jan 2, 2020"))

        val missing = pokemon(stats = ShareStats(cp = null, hp = null))
        val missingShare = ResultShareRenderer.appendKnownStatsToShareText(
            "Result.", missing, "CP", "HP", "CAUGHT"
        )
        assertEquals("Result.", missingShare)
    }

    @Test
    fun turkishTextShareDoesNotMixInTheEnglishSummary() {
        assertTrue(ResultShareRenderer.shouldAppendEnglishSummary("en"))
        assertFalse(ResultShareRenderer.shouldAppendEnglishSummary("tr"))
        assertEquals(
            "Pikachu sonucu.",
            ResultShareRenderer.buildShareText("Pikachu sonucu.", pokemon(), languageCode = "tr")
        )
    }

    private fun pokemon(
        stats: ShareStats = ShareStats(),
        ivText: String? = null,
        solveMode: IvSolveMode? = null,
        levelMin: Float? = null,
        levelMax: Float? = null
    ) = pokemonFromScanExtras(
        name = "Pikachu",
        cp = stats.cp,
        hp = stats.hp,
        ivText = ivText,
        ivSolveMode = solveMode,
        ivLevelMin = levelMin,
        ivLevelMax = levelMax,
        score = 42,
        tier = "RARE",
        isShiny = false,
        isLucky = false,
        hasCostume = false,
        hasSpecialForm = false,
        isShadow = false,
        dateText = stats.dateText
    )
}
