package com.pokerarity.scanner

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pokerarity.scanner.util.ocr.AnchoredScreenText
import com.pokerarity.scanner.util.ocr.AnchoredScreenRecognizer
import com.pokerarity.scanner.util.ocr.anchoredRecognitionObservation
import com.pokerarity.scanner.util.ocr.ExtractionContext
import com.pokerarity.scanner.util.ocr.FieldRead
import com.pokerarity.scanner.util.ocr.MLKitOcrProvider
import com.pokerarity.scanner.util.ocr.RecognitionObservation
import com.pokerarity.scanner.util.ocr.RecognitionSnapshot
import com.pokerarity.scanner.util.ocr.StardustLevelEvidence
import com.pokerarity.scanner.util.ocr.StardustLevelWindowOracle
import com.pokerarity.scanner.util.ocr.StardustModifierContext
import com.pokerarity.scanner.util.ocr.TextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.File
import java.util.Locale

/**
 * Phase 3A integration boundary: the recognition snapshot's level domain is the only
 * legal-level authority, the per-frame observation carries the typed evidence built from
 * the anchored POWER UP field read, and inventory stardust structurally cannot become
 * level evidence. Evidence payloads stay bounded (codes/numbers, never OCR or paths).
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class StardustLevelWindowOracleIntegrationTest {

    private val snapshot: RecognitionSnapshot = RecognitionSnapshotTestSupport.loadRealSnapshot()

    private val parser = TextParser(ApplicationProvider.getApplicationContext<Context>()).also { parser ->
        val names = speciesFile().reader().use { reader ->
            Gson().fromJson<List<String>>(reader, object : TypeToken<List<String>>() {}.type)
        }
        TextParser::class.java.getDeclaredField("pokemonNames").apply { isAccessible = true }
            .set(parser, names.map { it.lowercase(Locale.ROOT) })
    }

    @Test
    fun realRecognitionSnapshotIsTheLegalLevelDomain() {
        val domain = snapshot.cpMultipliers.keys
        // The pinned snapshot authority: complete 1.0..51.0 half-level domain, no other table.
        assertEquals(101, domain.size)
        assertEquals(1.0, domain.minOrNull()!!, 0.0)
        assertEquals(51.0, domain.maxOrNull()!!, 0.0)

        val mid = StardustLevelWindowOracle.evaluate(
            FieldRead.read(1_000), StardustModifierContext.UNKNOWN, domain)
        assertEquals(generateSequence(9.0) { it + 0.5 }.takeWhile { it <= 11.5 + 1e-9 }.toList(),
            (mid as StardustLevelEvidence.Levels).levels)
    }

    @Test
    fun frameObservationCarriesTheTypedEvidenceFromTheAnchoredFieldRead() {
        val fields = anchoredFields(powerUpRead = FieldRead.read(1_000), powerUpCost = 1_000)
        val observation = anchoredRecognitionObservation(
            fields, frameIndex = 0, levelDomain = snapshot.cpMultipliers.keys)

        assertTrue(observation.powerUpStardust == 1_000)
        val evidence = observation.powerUpStardustLevelEvidence as StardustLevelEvidence.Levels
        assertEquals(9.0, evidence.minLevel!!, 0.0)
        assertEquals(11.5, evidence.maxLevel!!, 0.0)
    }

    @Test
    fun frameObservationCarriesExplicitMissingStateInsteadOfNullEvidence() {
        val fields = anchoredFields(powerUpRead = FieldRead.missing("action_not_detected"), powerUpCost = null)
        val observation = anchoredRecognitionObservation(
            fields, frameIndex = 0, levelDomain = snapshot.cpMultipliers.keys)

        assertTrue(observation.powerUpStardust == null)
        val evidence = observation.powerUpStardustLevelEvidence
        assertTrue(evidence is StardustLevelEvidence.Missing)
        assertTrue(evidence!!.reasonCodes.contains("action_not_detected"))
    }

    @Test
    fun productionWiringKeepsTheModifierContextUnknown() {
        val fields = anchoredFields(powerUpRead = FieldRead.read(200), powerUpCost = 200)
        val observation = anchoredRecognitionObservation(
            fields, frameIndex = 0, levelDomain = snapshot.cpMultipliers.keys)
        val evidence = observation.powerUpStardustLevelEvidence as StardustLevelEvidence.Levels

        // No trustworthy per-frame modifier provenance exists under the existing
        // contracts; the ambiguity across cost modifiers must be preserved honestly.
        assertTrue(evidence.reasonCodes.contains("cost_modifier_unknown"))
        assertTrue(evidence.modifierAmbiguous)
        assertTrue(evidence.reasonCodes.none { it.startsWith("modifier_established") })
    }

    @Test
    fun inventoryStardustVisibleOnScreenNeverBecomesLevelEvidence() {
        // The inventory balance "200" and its STARDUST label are visible, the anchored
        // POWER UP row independently reads 1,000: the level evidence must reflect only
        // the anchored row (9.0..11.5), never the inventory value.
        val result = extract(
            detailLines() + block("STARDUST", 560, 1120, 760, 1150),
            listOf(block("200", 610, 1070, 710, 1110), cost("1,000"))
        )
        assertEquals(1_000, result.powerUpCost)

        val observation = anchoredRecognitionObservation(
            result, frameIndex = 0, levelDomain = snapshot.cpMultipliers.keys)
        val evidence = observation.powerUpStardustLevelEvidence as StardustLevelEvidence.Levels
        assertEquals(
            generateSequence(9.0) { it + 0.5 }.takeWhile { it <= 11.5 + 1e-9 }.toList(),
            evidence.levels)
        // None of the 200-cost window (1.0..5.5) may leak in from the inventory value.
        assertFalse(evidence.levels.contains(1.0))
        assertFalse(evidence.levels.contains(2.5))
        assertFalse(evidence.levels.contains(5.5))
    }

    @Test
    fun inventoryValueWithoutAnAnchoredCostStaysNonLevelEvidence() {
        // Only an inventory-like number is visible (no anchored row cost): the typed
        // evidence is Unreadable/missing — never a level window.
        val result = extract(detailLines(), listOf(block("1,000", 610, 1070, 740, 1110)))
        assertTrue(result.powerUpCost == null)

        val observation = anchoredRecognitionObservation(
            result, frameIndex = 0, levelDomain = snapshot.cpMultipliers.keys)
        val evidence = observation.powerUpStardustLevelEvidence
        assertTrue(evidence is StardustLevelEvidence.Unreadable || evidence is StardustLevelEvidence.Missing)
        assertTrue(evidence !is StardustLevelEvidence.Levels)
    }

    @Test
    fun typedEvidencePayloadsStayBoundedAndNonSensitive() {
        val result = extract(detailLines(), listOf(cost("1,000")))
        val observation = anchoredRecognitionObservation(
            result, frameIndex = 0, levelDomain = snapshot.cpMultipliers.keys)
        val boundedCode = Regex("""^[a-z0-9_.]+$""")
        val serialized = observation.powerUpStardustLevelEvidence.toString()

        (observation.powerUpStardustLevelEvidence as StardustLevelEvidence.Levels).let { evidence ->
            evidence.reasonCodes.forEach { assertTrue(boundedCode.matches(it)) }
            evidence.interpretations.forEach { assertTrue(boundedCode.matches(it)) }
        }
        // No raw OCR text, screenshot path or local path can appear in the payload.
        assertFalse(serialized.contains("Eevee"))
        assertFalse(serialized.contains("1,000"))
        assertFalse(serialized.contains('/'))
        assertFalse(serialized.contains('\\'))
        assertFalse(serialized.contains(".json"))
    }

    // -- preserved-corpus anchors -------------------------------------------------------

    /**
     * The preserved Samsung S25 17-frame replay corpus (inputs staged from
     * PokemonScannerLab/differential/pokerarity) contains eight frames with an anchored
     * POWER UP stardust field. Their measured read states at the Phase 2F baseline
     * (replay_report_2f_b38d.json: fast and detailed passes agree) are pinned here as the
     * oracle's deterministic output — real-device-measured inputs, hand-derived windows.
     */
    @Test
    fun preservedCorpusPowerUpReadStatesProduceTypedWindows() {
        val domain = snapshot.cpMultipliers.keys

        fun window(cost: Int): StardustLevelEvidence.Levels =
            StardustLevelWindowOracle.evaluate(FieldRead.read(cost), StardustModifierContext.UNKNOWN, domain)
                as StardustLevelEvidence.Levels

        fun witnessed(from: Double, to: Double): List<Double> =
            generateSequence(from) { it + 0.5 }.takeWhile { it <= to + 1e-9 }.toList()

        // F01 purrloin clean: 1600, ordinary tier6 only.
        window(1_600).let {
            assertEquals(witnessed(13.0, 15.5), it.levels)
            assertEquals(listOf("tier6_normal"), it.interpretations)
            assertFalse(it.modifierAmbiguous)
        }
        // F02 trapinch shadow clean: 961, the Shadow float32 dual of tier3.
        window(961).let {
            assertEquals(witnessed(7.0, 9.5), it.levels)
            assertEquals(listOf("tier3_shadow"), it.interpretations)
        }
        // F03 totodile / F07 blitzle: 5000 — normal tier14 OR a lucky halved tier19:
        // two genuinely disjoint windows, honestly ambiguous.
        window(5_000).let {
            assertEquals(witnessed(29.0, 31.5) + witnessed(39.0, 41.5), it.levels)
            assertEquals(listOf("tier14_normal", "tier19_lucky"), it.interpretations)
            assertTrue(it.modifierAmbiguous)
        }
        // F04 nickit clean: 1000, ordinary tier4 only.
        window(1_000).let {
            assertEquals(witnessed(9.0, 11.5), it.levels)
            assertEquals(listOf("tier4_normal"), it.interpretations)
        }
        // F05 skwovet clean: 200, normal tier0 or lucky halved tier1.
        window(200).let {
            assertEquals(witnessed(1.0, 5.5), it.levels)
            assertEquals(listOf("tier0_normal", "tier1_lucky"), it.interpretations)
        }
        // F06 applin clean / X06 applin toast: 7000 — normal tier16 or lucky halved
        // tier23: disjoint windows again.
        window(7_000).let {
            assertEquals(witnessed(33.0, 35.5) + witnessed(47.0, 49.5), it.levels)
            assertEquals(listOf("tier16_normal", "tier23_lucky"), it.interpretations)
            assertTrue(it.modifierAmbiguous)
        }
        // N01/N02/X01..X03/X05/X07/X08: no anchored action row -> Missing.
        StardustLevelWindowOracle.evaluate(
            FieldRead.missing("action_not_detected"), StardustModifierContext.UNKNOWN, domain).let {
            assertTrue(it is StardustLevelEvidence.Missing)
        }
        // X04 shroomish card fade: field present but token unreadable -> Unreadable.
        StardustLevelWindowOracle.evaluate(
            FieldRead.unreadable("cost_token_unreadable", 1), StardustModifierContext.UNKNOWN, domain).let {
            assertTrue(it is StardustLevelEvidence.Unreadable)
        }
    }

    // -- helpers -----------------------------------------------------------------------

    private fun anchoredFields(powerUpRead: FieldRead<Int>, powerUpCost: Int?) = AnchoredScreenText.Fields(
        name = null, nameRaw = null, cp = 424, hp = 80 to 80, candy = "Eevee",
        powerUpCost = powerUpCost, types = setOf("normal"), detailScreen = true,
        hpRect = null, nameRect = null, candyRect = null, costRect = null,
        powerUpRead = powerUpRead
    )

    private fun extract(
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        elements: List<MLKitOcrProvider.RecognizedBlock> = emptyList()
    ) = AnchoredScreenText.extract(
        MLKitOcrProvider.Layout(lines, elements),
        parser,
        1080,
        2340,
        ExtractionContext(bar = Rect(310, 745, 770, 758))
    )

    private fun detailLines() = listOf(
        block("CP 424", 430, 210, 650, 260),
        block("Eevee", 320, 660, 760, 725),
        block("80 / 80 HP", 400, 790, 680, 830),
        block("NORMAL", 435, 935, 645, 965),
        block("WEIGHT", 100, 985, 290, 1020),
        block("HEIGHT", 790, 985, 980, 1020),
        block("EEVEE CANDY", 490, 1140, 800, 1180),
        block("POWER UP", 130, 1370, 410, 1420)
    )

    private fun cost(value: String, left: Int = 590, top: Int = 1370) =
        block(value, left, top, left + 130, top + 50)

    private fun block(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        MLKitOcrProvider.RecognizedBlock(text, Rect(left, top, right, bottom))

    private fun speciesFile(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            File(directory, "app/src/main/assets/data/pokemon_names.json").takeIf(File::isFile)?.let { return it }
            directory = directory.parentFile ?: return@repeat
        }
        error("pokemon_names.json not found")
    }
}
