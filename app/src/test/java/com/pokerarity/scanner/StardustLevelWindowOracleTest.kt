package com.pokerarity.scanner

import com.pokerarity.scanner.util.ocr.FieldRead
import com.pokerarity.scanner.util.ocr.FieldReadStatus
import com.pokerarity.scanner.util.ocr.PowerUpCostModifier
import com.pokerarity.scanner.util.ocr.StardustLevelEvidence
import com.pokerarity.scanner.util.ocr.StardustLevelWindowOracle
import com.pokerarity.scanner.util.ocr.StardustModifierContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3A oracle semantics over the recognition half-level domain (1.0..51.0).
 * These expectations describe UNDERLYING/base Pokémon levels from the POWER UP
 * stardust row. Best Buddy CP/HP witnessed-level compatibility is deliberately
 * tested separately in PowerUpStardustRulesParityTest and is not applied here.
 */
@Suppress("MagicNumber")
class StardustLevelWindowOracleTest {

    private val domain: Set<Double> = (0..100).map { 1.0 + it / 2.0 }.toSet()

    private fun evaluate(
        cost: Int,
        context: StardustModifierContext = StardustModifierContext.UNKNOWN
    ): StardustLevelEvidence =
        StardustLevelWindowOracle.evaluate(FieldRead.read(cost), context, domain)

    private fun levelsOf(evidence: StardustLevelEvidence): List<Double> =
        (evidence as StardustLevelEvidence.Levels).levels

    private fun halfLevels(from: Double, to: Double): List<Double> =
        generateSequence(from) { it + 0.5 }.takeWhile { it <= to + 1e-9 }.toList()

    @Test
    fun ordinaryCost200ProducesUnderlyingModifierAmbiguousSet() {
        // NORMAL tier0: 1.0..2.5. LUCKY tier1: 3.0..4.5.
        val evidence = evaluate(200) as StardustLevelEvidence.Levels
        assertEquals(halfLevels(1.0, 4.5), evidence.levels)
        assertEquals(1.0, evidence.minLevel!!, 0.0)
        assertEquals(4.5, evidence.maxLevel!!, 0.0)
        assertEquals(
            listOf(PowerUpCostModifier.NORMAL, PowerUpCostModifier.LUCKY),
            evidence.modifiersUsed
        )
        assertEquals(listOf("tier0_normal", "tier1_lucky"), evidence.interpretations)
        assertTrue(evidence.modifierAmbiguous)
        assertTrue(evidence.contiguous)
    }

    @Test
    fun ordinaryCost1000UsesUnderlyingTierOnly() {
        val evidence = evaluate(1_000) as StardustLevelEvidence.Levels
        assertEquals(halfLevels(9.0, 10.5), evidence.levels)
        assertEquals(listOf(PowerUpCostModifier.NORMAL), evidence.modifiersUsed)
        assertEquals(listOf("tier4_normal"), evidence.interpretations)
        assertFalse(evidence.levels.contains(11.0))
        assertFalse(evidence.levels.contains(11.5))
    }

    @Test
    fun ordinaryCost15000StopsAtUnderlyingLevel49Point5() {
        val evidence = evaluate(15_000) as StardustLevelEvidence.Levels
        assertEquals(listOf(49.0, 49.5), evidence.levels)
        assertEquals(49.5, evidence.maxLevel!!, 0.0)
        assertEquals(listOf("tier24_normal"), evidence.interpretations)
        assertFalse(evidence.levels.contains(50.0))
        assertFalse(evidence.levels.contains(50.5))
        assertFalse(evidence.levels.contains(51.0))
    }

    @Test
    fun luckyTerminalTierAlsoStopsAtUnderlying49Point5() {
        val evidence = evaluate(7_500) as StardustLevelEvidence.Levels
        assertEquals(listOf(49.0, 49.5), evidence.levels)
        assertEquals(listOf("tier24_lucky"), evidence.interpretations)
    }

    @Test
    fun disjointModifierCoincidencesStayDiscrete() {
        val evidence = evaluate(5_000) as StardustLevelEvidence.Levels
        assertEquals(
            halfLevels(29.0, 30.5) + halfLevels(39.0, 40.5),
            evidence.levels
        )
        assertEquals(listOf("tier14_normal", "tier19_lucky"), evidence.interpretations)
        assertFalse(evidence.contiguous)
        assertTrue(evidence.modifierAmbiguous)
    }

    @Test
    fun shadowFloat32DualCostsResolveToUnderlyingTierOnly() {
        val exact = evaluate(960) as StardustLevelEvidence.Levels
        assertEquals(halfLevels(7.0, 8.5), exact.levels)
        assertEquals(listOf("tier3_shadow"), exact.interpretations)

        val dual = evaluate(961) as StardustLevelEvidence.Levels
        assertEquals(halfLevels(7.0, 8.5), dual.levels)
        assertEquals(listOf("tier3_shadow"), dual.interpretations)
        assertTrue(evaluate(962) is StardustLevelEvidence.Invalid)
    }

    @Test
    fun adjacentTierNoiseNeverBecomesAGuessedLevel() {
        listOf(199, 201, 399, 401, 999, 1_001, 14_999, 15_001).forEach {
            assertTrue("cost=" + it, evaluate(it) is StardustLevelEvidence.Invalid)
        }
    }

    @Test
    fun impossibleAndInventoryScaleCostsAreInvalid() {
        assertTrue(evaluate(1_234) is StardustLevelEvidence.Invalid)
        assertTrue(evaluate(123_456) is StardustLevelEvidence.Invalid)
    }

    @Test
    fun missingUnreadableConflictAndReadWithoutValueStayDistinct() {
        val missing = StardustLevelWindowOracle.evaluate(
            FieldRead.missing("action_not_detected"),
            StardustModifierContext.UNKNOWN,
            domain
        )
        val unreadable = StardustLevelWindowOracle.evaluate(
            FieldRead.unreadable("cost_token_unreadable", 2),
            StardustModifierContext.UNKNOWN,
            domain
        )
        val conflict = StardustLevelWindowOracle.evaluate(
            FieldRead.conflict("multiple_distinct_costs", 2),
            StardustModifierContext.UNKNOWN,
            domain
        )
        val malformedRead = StardustLevelWindowOracle.evaluate(
            FieldRead(FieldReadStatus.READ, null, 1, "read"),
            StardustModifierContext.UNKNOWN,
            domain
        )

        assertTrue(missing is StardustLevelEvidence.Missing)
        assertTrue(unreadable is StardustLevelEvidence.Unreadable)
        assertTrue(conflict is StardustLevelEvidence.Conflict)
        assertTrue(malformedRead is StardustLevelEvidence.Unreadable)
        assertEquals(2, (conflict as StardustLevelEvidence.Conflict).candidateCount)
    }

    @Test
    fun emptyRecognitionLevelDomainFailsClosed() {
        val evidence = StardustLevelWindowOracle.evaluate(
            FieldRead.read(200),
            StardustModifierContext.UNKNOWN,
            emptySet()
        )
        assertTrue(evidence is StardustLevelEvidence.Unsupported)
        assertTrue(evidence.reasonCodes.contains("level_domain_unavailable"))
    }

    @Test
    fun establishedLuckyNarrowsOnlyWithValidProvenance() {
        val context = StardustModifierContext.established(
            PowerUpCostModifier.LUCKY,
            "anchored_lucky_label"
        )
        val evidence = evaluate(100, context) as StardustLevelEvidence.Levels
        assertEquals(halfLevels(1.0, 2.5), evidence.levels)
        assertEquals(listOf("tier0_lucky"), evidence.interpretations)
        assertFalse(evidence.modifierAmbiguous)
        assertTrue(evidence.reasonCodes.contains("modifier_established_anchored_lucky_label"))
    }

    @Test
    fun missingBlankAndMalformedModifierProvenanceCannotNarrow() {
        val invalid = listOf(
            null,
            "",
            "   ",
            "C:\\Users\\name\\screen.png",
            "Lucky Pokémon 1,000",
            "x".repeat(80),
            "../private"
        )
        invalid.forEach { provenance ->
            val context = StardustModifierContext.established(
                PowerUpCostModifier.LUCKY,
                provenance
            )
            val evidence = evaluate(100, context)
            assertTrue("provenance=" + provenance, evidence is StardustLevelEvidence.Unsupported)
            assertTrue(evidence.reasonCodes.contains("modifier_provenance_invalid"))
        }
    }

    @Test
    fun modifierContextRepresentsOneModeledCostStateOnly() {
        val purified = StardustModifierContext.established(
            PowerUpCostModifier.PURIFIED,
            "trusted_purified_state"
        )
        val evidence = evaluate(180, purified) as StardustLevelEvidence.Levels
        assertEquals(listOf(PowerUpCostModifier.PURIFIED), evidence.modifiersUsed)
        assertEquals(listOf("tier0_purified"), evidence.interpretations)

        // Lucky+Purified is one explicit modeled state, not two booleans that can
        // accidentally coexist with Shadow.
        val luckyPurified = StardustModifierContext.established(
            PowerUpCostModifier.LUCKY_PURIFIED,
            "trusted_lucky_purified_state"
        )
        val combined = evaluate(90, luckyPurified) as StardustLevelEvidence.Levels
        assertEquals(listOf(PowerUpCostModifier.LUCKY_PURIFIED), combined.modifiersUsed)
    }

    @Test
    fun establishedShadowContradictingCostIsConflict() {
        val context = StardustModifierContext.established(
            PowerUpCostModifier.SHADOW,
            "trusted_shadow_state"
        )
        val evidence = evaluate(200, context)
        assertTrue(evidence is StardustLevelEvidence.Conflict)
        assertTrue(evidence.reasonCodes.contains("cost_conflicts_with_established_modifier"))
    }

    @Test
    fun establishedNormalExcludesLuckyCoincidence() {
        val context = StardustModifierContext.established(
            PowerUpCostModifier.NORMAL,
            "trusted_normal_state"
        )
        val evidence = evaluate(200, context) as StardustLevelEvidence.Levels
        assertEquals(halfLevels(1.0, 2.5), evidence.levels)
        assertEquals(listOf("tier0_normal"), evidence.interpretations)
    }

    @Test
    fun unknownModifierPreservesAmbiguity() {
        val evidence = evaluate(200) as StardustLevelEvidence.Levels
        assertTrue(evidence.modifierAmbiguous)
        assertTrue(evidence.reasonCodes.contains("cost_modifier_unknown"))
        assertTrue(evidence.reasonCodes.none { it.startsWith("modifier_established") })
    }

    @Test
    fun legalLevelsAreIntersectedWithProvidedRecognitionDomain() {
        val restricted = setOf(9.0, 9.5, 10.0)
        val evidence = StardustLevelWindowOracle.evaluate(
            FieldRead.read(1_000),
            StardustModifierContext.UNKNOWN,
            restricted
        )
        assertEquals(listOf(9.0, 9.5, 10.0), levelsOf(evidence))
        assertNull((evidence as StardustLevelEvidence.Levels).levels.firstOrNull { it !in restricted })
    }

    @Test
    fun levels50AndAboveNeverMatchAnyPowerUpCostAsUnderlyingLevels() {
        val reachable = (0..100).map { 1.0 + it / 2.0 }.toSet()
        (0..20_000).forEach { cost ->
            val evidence = StardustLevelWindowOracle.evaluate(
                FieldRead.read(cost),
                StardustModifierContext.UNKNOWN,
                reachable
            )
            if (evidence is StardustLevelEvidence.Levels) {
                assertFalse("cost=" + cost, evidence.levels.any { it >= 50.0 })
            }
        }
    }

    @Test
    fun fieldReadReasonCodesAreBoundedBeforeEnteringEvidence() {
        val evidence = StardustLevelWindowOracle.evaluate(
            FieldRead.missing("C:\\Users\\name\\raw screenshot 1000"),
            StardustModifierContext.UNKNOWN,
            domain
        )
        assertTrue(evidence is StardustLevelEvidence.Missing)
        assertTrue(evidence.reasonCodes.contains("field_read_reason_invalid"))
        assertFalse(evidence.toString().contains("Users"))
        assertFalse(evidence.toString().contains("1000"))
    }

    @Test
    fun evidenceCodesAndInterpretationsRemainBounded() {
        val boundedCode = Regex("""^[a-z0-9_.]{1,48}$""")
        listOf(
            evaluate(200),
            evaluate(1_234),
            StardustLevelWindowOracle.evaluate(
                FieldRead.missing("action_not_detected"),
                StardustModifierContext.UNKNOWN,
                domain
            ),
            StardustLevelWindowOracle.evaluate(
                FieldRead.unreadable("cost_out_of_supported_domain"),
                StardustModifierContext.UNKNOWN,
                domain
            )
        ).forEach { evidence ->
            evidence.reasonCodes.forEach { assertTrue(boundedCode.matches(it)) }
            if (evidence is StardustLevelEvidence.Levels) {
                evidence.interpretations.forEach { assertTrue(boundedCode.matches(it)) }
                evidence.levels.forEach { level ->
                    assertTrue(level.isFinite())
                    assertEquals(0.0, level % 0.5, 1e-9)
                }
            }
            assertFalse(evidence.toString().contains('/'))
            assertFalse(evidence.toString().contains('\\'))
        }
    }
}
