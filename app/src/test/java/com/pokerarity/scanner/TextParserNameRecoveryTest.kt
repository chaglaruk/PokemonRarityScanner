package com.pokerarity.scanner

import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.util.ocr.SpeciesNameAcceptanceSource
import com.pokerarity.scanner.util.ocr.SpeciesNameDecision
import com.pokerarity.scanner.util.ocr.TextParser
import com.pokerarity.scanner.util.ocr.acceptedSpeciesOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.text.Normalizer

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class TextParserNameRecoveryTest {

    private val parser = TextParser(
        ApplicationProvider.getApplicationContext(),
        RecognitionSnapshotTestSupport.canonicalSpecies()
    )

    @Test
    fun parseName_recoversNumericOcrConfusion() {
        assertEquals("Porygon", parser.parseName("Poryg0n"))
        assertEquals("Espeon", parser.parseName("Espe0n"))
        assertNull(parser.parseName("Porygon2"))
        assertTrue(parser.decideSpeciesName("Porygon2") is SpeciesNameDecision.Uncertain)
        assertEquals("Gyarados", parser.parseName("Gyarados100"))
        assertEquals("Slowpoke", parser.parseName("Slowpoke100"))
    }

    @Test
    fun parseName_recoversCommonGlyphConfusion() {
        assertEquals("Gyarados", parser.parseName("Gvarados"))
    }

    @Test
    fun anchoredTitleRecoversUniqueAdjacentTranspositionsAcrossCanonicalSpecies() {
        val namesByKey = RecognitionSnapshotTestSupport.canonicalSpecies()
            .mapNotNull { name -> transpositionKey(name)?.let { it to name } }
            .groupBy({ it.first }, { it.second })
        val generated = mutableMapOf<String, MutableSet<String>>()
        namesByKey.forEach { (sourceKey, sourceNames) ->
            (0 until sourceKey.lastIndex)
                .filter { index -> sourceKey[index] != sourceKey[index + 1] }
                .forEach { index ->
                    val chars = sourceKey.toCharArray()
                    val previous = chars[index]
                    chars[index] = chars[index + 1]
                    chars[index + 1] = previous
                    val observed = chars.concatToString()
                    if (observed !in namesByKey) {
                        generated.getOrPut(observed) { linkedSetOf() }.addAll(sourceNames)
                    }
                }
        }

        val unique = generated.filterValues { it.size == 1 }
        var beforeCorrect = 0
        var beforeWrong = 0
        var beforeUnknown = 0
        var afterCorrect = 0
        var afterWrong = 0
        var afterUnknown = 0
        var newlyCorrect = 0
        var newlyWrong = 0
        unique.forEach { (observed, expectedNames) ->
            val expected = expectedNames.single()
            val before = parser.decideSpeciesName(observed).acceptedSpeciesOrNull()
            val after = parser.decideAnchoredSpeciesName(observed).acceptedSpeciesOrNull()
            when {
                before == null -> beforeUnknown++
                before?.equals(expected, ignoreCase = true) == true -> beforeCorrect++
                else -> beforeWrong++
            }
            when {
                after == null -> afterUnknown++
                after?.equals(expected, ignoreCase = true) == true -> afterCorrect++
                else -> afterWrong++
            }
            if (before == null && after?.equals(expected, ignoreCase = true) == true) newlyCorrect++
            if (before == null && after != null && !after.equals(expected, ignoreCase = true)) newlyWrong++
        }

        println(
            "ANCHORED_TRANSPOSITION unique=${unique.size} " +
                "beforeCorrect=$beforeCorrect beforeWrong=$beforeWrong beforeUnknown=$beforeUnknown " +
                "afterCorrect=$afterCorrect afterWrong=$afterWrong afterUnknown=$afterUnknown " +
                "newlyCorrect=$newlyCorrect newlyWrong=$newlyWrong"
        )
        assertTrue("anchored recovery must improve automatic coverage", newlyCorrect > 0)
        assertEquals("must not add a confidently wrong species", 0, newlyWrong)
        assertEquals("must not alter existing accepted decisions", beforeWrong, afterWrong)
        assertTrue("UNKNOWN count must decrease", afterUnknown < beforeUnknown)
    }

    @Test
    fun anchoredTranspositionRecoveryDoesNotChangeGenericOrAmbiguousReads() {
        listOf("Pikacxu", "Nidoran", "Pikachu7", "", "!!!!").forEach { input ->
            assertEquals(input, parser.decideSpeciesName(input), parser.decideAnchoredSpeciesName(input))
        }
        assertNull(parser.parseName("Bulbasuar"))
        val anchored = parser.decideAnchoredSpeciesName("Bulbasuar") as SpeciesNameDecision.Accepted
        assertEquals("Bulbasaur", anchored.species)
        assertEquals("unique_anchored_adjacent_transposition", anchored.diagnostics.reasonCodes.single())
    }

    @Test
    fun terminalNAsLeRecoveryIsCanonicalAndCarriesReviewedAliasProvenance() {
        assertEquals("Vaporeon", parser.parseName("Vaporeole"))
        assertEquals("Jolteon", parser.parseName("Jolteole"))

        val decision = parser.decideSpeciesName("Vaporeole") as SpeciesNameDecision.Accepted
        assertEquals("Vaporeon", decision.species)
        assertEquals(SpeciesNameAcceptanceSource.REVIEWED_ALIAS, decision.source)
        assertNull(parser.parseName("Pikachole"))
    }

    @Test
    fun nidoranGenderMarkersRemainDistinctAndAmbiguousMarkersStayUncertain() {
        val explicitGenderInputs = listOf(
            "Nidoran♀" to "Nidoran-f",
            "Nidoran ♂" to "Nidoran-m",
            "Nidoran F" to "Nidoran-f",
            "Nidoran-M" to "Nidoran-m",
            "nidoran-f" to "Nidoran-f",
            "nidoran-m" to "Nidoran-m"
        )

        explicitGenderInputs.forEach { (input, expected) ->
            assertEquals(input, expected, parser.parseName(input))
            assertEquals(input, expected, parser.parseStrongSpeciesName(input))
        }

        listOf("Nidoran", "Nidoran?", "Nidoran x", "Nidoran-f/m?").forEach { input ->
            assertNull(input, parser.parseName(input))
            assertTrue(
                "$input should remain uncertain",
                parser.decideSpeciesName(input) is SpeciesNameDecision.Uncertain
            )
        }

        mapOf(
            "Nidorina" to "Nidorina",
            "Nidorino" to "Nidorino",
            "Nidoqueen" to "Nidoqueen",
            "Nidoking" to "Nidoking"
        ).forEach { (input, expected) ->
            assertEquals(input, expected, parser.parseName(input))
        }
    }

    @Test
    fun canonicalKeysDoNotCollapseUnicodeGenderForms() {
        val glyphCanonicalParser = TextParser(
            ApplicationProvider.getApplicationContext(),
            listOf("Nidoran♀", "Nidoran♂")
        )

        assertEquals("Nidoran♀", glyphCanonicalParser.parseName("Nidoran♀"))
        assertEquals("Nidoran♂", glyphCanonicalParser.parseName("Nidoran♂"))
        assertNull(glyphCanonicalParser.parseName("Nidoran"))
        assertTrue(glyphCanonicalParser.decideSpeciesName("Nidoran") is SpeciesNameDecision.Uncertain)
    }

    @Test
    fun parseStrongSpeciesName_doesNotLockNicknameLikeFuzzySpecies() {
        assertEquals("Porygon", parser.parseStrongSpeciesName("Poryg0n"))
        assertEquals("Espeon", parser.parseStrongSpeciesName("Espeon"))
        assertEquals("Espeon", parser.parseStrongSpeciesName("Espeon100"))
        assertEquals("Slowpoke", parser.parseStrongSpeciesName("Slowpoke100"))
        assertNull(parser.parseStrongSpeciesName("ELECTRIC"))
    }

    @Test
    fun parseName_ignoresTypeAndUiLabels() {
        assertNull(parser.parseName("TALLEST"))
        assertNull(parser.parseName("FLYING"))
        assertNull(parser.parseName("ELECTRIC"))
        assertNull(parser.parseName("NORMAL"))
        assertNull(parser.parseName("WEATHER BONUS"))
    }

    @Test
    fun parseName_ignoresPokemonGoSizeRecordAndSuccessLabels() {
        assertNull(parser.parseName("NEW SIZE RECORD"))
        assertNull(parser.parseName("SIZE RECORD"))
        assertNull(parser.parseName("RECORD XL"))
        assertNull(parser.parseName("SUCCESS"))
        assertNull(parser.parseName("XXL"))
        assertNull(parser.parseName("XXS"))
        assertNull(parser.parseName("XS"))
        assertNull(parser.parseName("XL"))
        assertNull(parser.parseName("TINY"))
        assertNull(parser.parseName("GIGANTIC"))
        assertNull(parser.parseName("SHORTEST"))
    }

    @Test
    fun parseName_ignoresPowerUpAndCandyUiLabels() {
        assertNull(parser.parseName("POWER UP"))
        assertNull(parser.parseName("CANDY"))
        assertNull(parser.parseName("STARDUST"))
    }

    @Test
    fun parseName_ignoresCpAndHpLabels() {
        assertNull(parser.parseName("CP"))
        assertNull(parser.parseName("HP"))
        assertNull(parser.parseName("CP 1500"))
    }

    @Test
    fun rankNameCandidates_ignoresCompactNonSpeciesUiTokens() {
        assertTrue(parser.rankNameCandidates("newrecordxl").isEmpty())
        assertTrue(parser.rankNameCandidates("sizerecordxxl").isEmpty())
        assertTrue(parser.rankNameCandidates("missing").isEmpty())
        assertTrue(parser.rankNameCandidates("not-run").isEmpty())
        assertNull(parser.parseName("newrecordxl"))
        assertNull(parser.parseStrongSpeciesName("sizerecordxxs"))
        assertNull(parser.parseName("missing"))
        assertNull(parser.parseName("not-run"))
    }

    @Test
    fun parseName_keepsRealSpeciesNearBlockedUiWords() {
        assertEquals("Slowpoke", parser.parseName("Slowpoke XL"))
    }

    @Test
    fun parseCandyName_rescuesExactSpeciesBeforeCandyTokenInNoisyLiveText() {
        assertEquals(
            "Slowpoke",
            parser.parseCandyNameLoose("5,076 UST 211 SLOWPOKE CANDY S7 SLOWPOKE CANDY XL")
        )
        assertEquals(
            "Farfetch'd",
            parser.parseCandyNameLoose("5,076 UST 1,514 FARFETCH'D CANDY 219 FARFETCH'D CANDY XL")
        )
        assertEquals(
            "Snorlax",
            parser.parseCandyNameLoose("5,076 UST 2,618 SNORLAX CANDY 281 SNORLAX CANDY XL")
        )
    }

    private fun transpositionKey(name: String): String? {
        val key = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace("♀", "f")
            .replace("♂", "m")
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
        return key.takeIf { it.length >= 6 && it.all { character -> character in 'a'..'z' } }
    }
}
