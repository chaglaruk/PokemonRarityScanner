package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.util.ocr.SpeciesNameDecision
import com.pokerarity.scanner.util.ocr.TextParser
import com.pokerarity.scanner.util.ocr.acceptedSelectionScore
import com.pokerarity.scanner.util.ocr.acceptedSpeciesOrNull
import com.pokerarity.scanner.util.ocr.decideDynamicOcrSpeciesName
import com.pokerarity.scanner.util.ocr.decideStaticOcrSpeciesName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class OcrSpeciesNameDecisionConsistencyTest {
    private val parser = TextParser(ApplicationProvider.getApplicationContext<Context>()).also { parser ->
        TextParser::class.java.getDeclaredField("pokemonNames").apply { isAccessible = true }
            .set(
                parser,
                listOf(
                    "bulbasaur", "gyarados", "ho-oh", "nidoran-f", "nidoran-m",
                    "poliwrath", "poliwag", "metapod", "metang"
                )
            )
    }

    @Test
    fun dynamicAndStaticPoliciesUseTheSameFailClosedDecision() {
        val observations = listOf(
            "Bulbasaur", "Gyarados2024", "HoOh", "H0-0h", "Nidoran",
            "Poliwrat", "metapo", "Rocky", "WEATHER BONUS"
        )
        observations.forEach { observation ->
            val dynamicDecision = parser.decideDynamicOcrSpeciesName(observation)
            val staticDecision = parser.decideStaticOcrSpeciesName(observation)
            assertEquals(dynamicDecision, staticDecision)
            assertEquals(dynamicDecision.acceptedSpeciesOrNull(), staticDecision.acceptedSpeciesOrNull())
        }
    }

    @Test
    fun strongerAcceptanceProvenanceAlwaysOutranksWeakerEvidence() {
        val exact = parser.decideSpeciesName("Bulbasaur")
        val reviewed = parser.decideSpeciesName("HoOh")
        val safeFuzzy = parser.decideSpeciesName("Poliwrat")

        assertTrue(exact.acceptedSelectionScore(0f) > reviewed.acceptedSelectionScore(1f))
        assertTrue(reviewed.acceptedSelectionScore(0f) > safeFuzzy.acceptedSelectionScore(1f))
        assertTrue(safeFuzzy.acceptedSelectionScore(1f) <= 1f)
    }

    @Test
    fun rawRankedCandidatesDoNotImplyAcceptance() {
        assertTrue(parser.rankNameCandidates("Nidoran").isNotEmpty())
        val decision = parser.decideSpeciesName("Nidoran")
        assertTrue(decision is SpeciesNameDecision.Uncertain)
        assertEquals(null, decision.acceptedSpeciesOrNull())
    }
}
