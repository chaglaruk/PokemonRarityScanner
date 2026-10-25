package com.pokerarity.scanner.ui.share

import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.model.formatIvLevelEstimateText
import com.pokerarity.scanner.data.model.ivLevelDisplayText
import com.pokerarity.scanner.data.model.ivLevelSummaryText
import com.pokerarity.scanner.data.model.pokemonFromScanExtras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResultSharePresentationTest {
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

    private fun pokemon(
        ivText: String? = null,
        solveMode: IvSolveMode? = null,
        levelMin: Float? = null,
        levelMax: Float? = null
    ) = pokemonFromScanExtras(
        name = "Pikachu",
        cp = 500,
        hp = 51,
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
        dateText = null
    )
}
