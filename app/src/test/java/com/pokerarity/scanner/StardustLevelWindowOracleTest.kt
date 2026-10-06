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
 * Phase 3A oracle semantics over the full recognition half-level domain (1.0..51.0).
 * Expected windows are hand-derived game-domain facts of the canonical tier rules
 * (each tier is a two-full-level base window; a witnessed level may sit one full level
 * above its base through an active Best Buddy bonus), NOT mirrors of the implementation.
 */
@Suppress("MagicNumber")
class StardustLevelWindowOracleTest {

    /** The recognition snapshot's legal half-level domain shape: 1.0 through 51.0. */
    private val domain: Set<Double> = (0..100).map { 1.0 + it / 2.0 }.toSet()

    private fun evaluate(
        cost: Int,
        context: StardustModifierContext = StardustModifierContext.UNKNOWN
    ): StardustLevelEvidence = StardustLevelWindowOracle.evaluate(
        FieldRead.read(cost), context, domain)

    private fun levelsOf(evidence: StardustLevelEvidence): List<Double> =
        (evidence as StardustLevelEvidence.Levels).levels

    private fun halfLevels(from: Double, to: Double): List<Double> =
        generateSequence(from) { it + 0.5 }.takeWhile { it <= to + 1e-9 }.toList()

    // -- Ordinary costs produce legal windows, never exact levels ----------------------

    @Test
    fun ordinaryCost200ProducesItsLowLevelWindowUnderUnknownModifiers() {
        // Normal tier0 (bases 1.0-2.5) plus the Lucky tier1 reading (400 halved,
        // bases 3.0-4.5): witnessed levels 1.0..5.5, honestly modifier-ambiguous.
        val evidence = evaluate(200) as StardustLevelEvidence.Levels
        assertEquals(halfLevels(1.0, 5.5), evidence.levels)
        assertEquals(1.0, evidence.minLevel!!, 0.0)
        assertEquals(5.5, evidence.maxLevel!!, 0.0)
        assertEquals(listOf(PowerUpCostModifier.NORMAL, PowerUpCostModifier.LUCKY), evidence.modifiersUsed)
        assertEquals(listOf("tier0_normal", "tier1_lucky"), evidence.interpretations)
        assertTrue(evidence.modifierAmbiguous)
        assertTrue(evidence.reasonCodes.contains("cost_modifier_unknown"))
        assertTrue(evidence.reasonCodes.contains("anchored_power_up_row"))
    }

    @Test
    fun ordinaryCost1000ProducesItsMidLevelWindow() {
        // 1000 is only the normal tier4 cost (no modifier coincidence): bases 9.0-10.5,
        // witnessed 9.0..11.5 through the possible Best Buddy one-level shift.
        val evidence = evaluate(1000) as StardustLevelEvidence.Levels
        assertEquals(halfLevels(9.0, 11.5), evidence.levels)
        assertEquals(listOf(PowerUpCostModifier.NORMAL), evidence.modifiersUsed)
        assertEquals(listOf("tier4_normal"), evidence.interpretations)
        assertFalse(evidence.modifierAmbiguous)
    }

    @Test
    fun ordinaryCost15000TerminatesAtTheSupportedPowerUpBoundary() {
        // The 15000 tier covers bases 49.0/49.5 only: witnessed 49.0..50.5, never 51.0,
        // and no 50.5/51.0 power-up tier exists.
        val evidence = evaluate(15_000) as StardustLevelEvidence.Levels
        assertEquals(listOf(49.0, 49.5, 50.0, 50.5), evidence.levels)
        assertEquals(50.5, evidence.maxLevel!!, 0.0)
        assertEquals(listOf("tier24_normal"), evidence.interpretations)
        assertFalse(levelsOf(evidence).contains(51.0))
    }

    @Test
    fun luckyMaxTierCostStaysWithinTheSameBoundary() {
        // 7500 = 15000 halved (Lucky): same tier-24 base window, witnessed 49.0..50.5.
        val evidence = evaluate(7_500) as StardustLevelEvidence.Levels
        assertEquals(halfLevels(49.0, 50.5), evidence.levels)
        assertEquals(listOf("tier24_lucky"), evidence.interpretations)
        assertFalse(evidence.levels.contains(51.0))
    }

    // -- Adjacent-tier and off-by-one counterexamples ----------------------------------

    @Test
    fun adjacentTierCostsNeverShiftTheWindowByOneTier() {
        // Just below/above tier 0 (200): no interpretation anywhere.
        listOf(199, 201).forEach { assertTrue("cost=$it", evaluate(it) is StardustLevelEvidence.Invalid) }
        // Tier boundaries around 400 and 1000.
        listOf(399, 401, 999, 1_001).forEach {
            assertTrue("cost=$it", evaluate(it) is StardustLevelEvidence.Invalid)
        }
        // Around the terminal tier.
        listOf(14_999, 15_001).forEach {
            assertTrue("cost=$it", evaluate(it) is StardustLevelEvidence.Invalid)
        }
        // Witnessed-window edges: one half-level beyond each end of the 1000 window.
        val window = levelsOf(evaluate(1_000))
        assertFalse(window.contains(8.5))
        assertFalse(window.contains(12.0))
        // The 200 window ends exactly at 5.5; 6.0 belongs to the next tier only.
        val lowWindow = levelsOf(evaluate(200))
        assertTrue(lowWindow.contains(5.5))
        assertFalse(lowWindow.contains(6.0))
    }

    @Test
    fun shadowFloat32DualCostsResolveToTheirOwnTierOnly() {
        // 960 (exact) and 961 (float32) are Shadow tier-3 costs; 962 is nothing.
        val exact = evaluate(960) as StardustLevelEvidence.Levels
        assertEquals(listOf("tier3_shadow"), exact.interpretations)
        assertEquals(halfLevels(7.0, 9.5), exact.levels)
        val dual = evaluate(961) as StardustLevelEvidence.Levels
        assertEquals(listOf("tier3_shadow"), dual.interpretations)
        assertTrue(evaluate(962) is StardustLevelEvidence.Invalid)
    }

    // -- Invalid dust never becomes a level --------------------------------------------

    @Test
    fun impossibleCostIsInvalidAndNeverAGuessedLevel() {
        val evidence = evaluate(1_234)
        assertTrue(evidence is StardustLevelEvidence.Invalid)
        assertEquals(1_234, (evidence as StardustLevelEvidence.Invalid).observedCost)
        assertTrue(evidence.reasonCodes.contains("cost_not_a_displayed_power_up_value"))
    }

    @Test
    fun inventoryScaleBalanceCannotBecomeALevelWindowEvenIfMisrouted() {
        // An inventory stardust balance is not a power-up cost; even a caller that
        // wrongly routes it here gets Invalid, never a level window.
        assertTrue(evaluate(123_456) is StardustLevelEvidence.Invalid)
    }

    // -- Missing / unreadable / conflict / unsupported are distinct states -------------

    @Test
    fun missingFieldIsDistinctFromUnreadableField() {
        val missing = StardustLevelWindowOracle.evaluate(
            FieldRead.missing("action_not_detected"), StardustModifierContext.UNKNOWN, domain)
        val unreadable = StardustLevelWindowOracle.evaluate(
            FieldRead.unreadable("cost_token_unreadable", 2), StardustModifierContext.UNKNOWN, domain)

        assertTrue(missing is StardustLevelEvidence.Missing)
        assertTrue(unreadable is StardustLevelEvidence.Unreadable)
        assertTrue(missing.reasonCodes.contains("action_not_detected"))
        assertTrue(unreadable.reasonCodes.contains("cost_token_unreadable"))
        assertTrue(missing.reasonCodes.contains("anchored_power_up_row"))
        assertEquals(1, missing.reasonCodes.count { it == "anchored_power_up_row" })
        assertDifferentEvidenceStates(missing, unreadable)
    }

    @Test
    fun readStateWithoutAValueFailsClosedAsUnreadable() {
        val evidence = StardustLevelWindowOracle.evaluate(
            FieldRead(FieldReadStatus.READ, null, 1, "read"), StardustModifierContext.UNKNOWN, domain)
        assertTrue(evidence is StardustLevelEvidence.Unreadable)
        assertTrue(evidence.reasonCodes.contains("read_state_without_value"))
    }

    @Test
    fun conflictingObservationsNeverSilentlyBecomeValid() {
        // Same-frame: two distinct cost tokens on the anchored row arrive as CONFLICT.
        val sameFrame = StardustLevelWindowOracle.evaluate(
            FieldRead.conflict("multiple_distinct_costs", 2), StardustModifierContext.UNKNOWN, domain)
        assertTrue(sameFrame is StardustLevelEvidence.Conflict)
        assertEquals(2, (sameFrame as StardustLevelEvidence.Conflict).candidateCount)
        assertTrue(sameFrame.reasonCodes.contains("multiple_distinct_costs"))

        // Multi-frame: disagreeing frame reads (200 vs 1300) have no merged representation
        // in the typed input — the only honest carrier is CONFLICT, which stays Conflict
        // and never becomes a level window. (Fusion additionally fails the scan closed.)
        val frameA = FieldRead.read(200)
        val frameB = FieldRead.read(1_300)
        assertTrue(frameA.value != frameB.value)
        assertTrue(StardustLevelWindowOracle.evaluate(frameA, StardustModifierContext.UNKNOWN, domain)
            is StardustLevelEvidence.Levels)
        assertTrue(StardustLevelWindowOracle.evaluate(frameB, StardustModifierContext.UNKNOWN, domain)
            is StardustLevelEvidence.Levels)
        assertTrue(StardustLevelWindowOracle.evaluate(
            FieldRead.conflict("multiple_distinct_costs", 2), StardustModifierContext.UNKNOWN, domain)
            is StardustLevelEvidence.Conflict)
    }

    @Test
    fun unsupportedIsDistinctFromInvalid() {
        // Lucky+Shadow combined cost (0.6x) is not a modeled mechanic: refused, not guessed.
        val combined = evaluate(
            240,
            StardustModifierContext(
                established = StardustModifierContext.EstablishedStardustModifiers(
                    lucky = true, shadow = true),
                provenanceCode = "test_both"))
        assertTrue(combined is StardustLevelEvidence.Unsupported)
        assertTrue(combined.reasonCodes.contains("lucky_shadow_cost_unsupported"))

        // Without the recognition snapshot's level domain the oracle cannot interpret honestly.
        val noDomain = StardustLevelWindowOracle.evaluate(
            FieldRead.read(200), StardustModifierContext.UNKNOWN, emptySet())
        assertTrue(noDomain is StardustLevelEvidence.Unsupported)
        assertTrue(noDomain.reasonCodes.contains("level_domain_unavailable"))

        // Unsupported is a different state than an impossible value.
        assertTrue(evaluate(1_234) is StardustLevelEvidence.Invalid)
        assertDifferentEvidenceStates(combined, evaluate(1_234))
    }

    // -- Modifier semantics ------------------------------------------------------------

    @Test
    fun establishedModifierNarrowsTheInterpretationOnlyWithProvenance() {
        // Trustworthy Lucky provenance: 100 is the halved tier-0 cost, witnessed 1.0..3.5.
        val lucky = evaluate(
            100,
            StardustModifierContext(
                established = StardustModifierContext.EstablishedStardustModifiers(lucky = true),
                provenanceCode = "anchored_lucky_label"))
        assertTrue(lucky is StardustLevelEvidence.Levels)
        assertEquals(halfLevels(1.0, 3.5), lucky.let { levelsOf(it) })
        assertEquals(listOf("tier0_lucky"), (lucky as StardustLevelEvidence.Levels).interpretations)
        assertFalse(lucky.modifierAmbiguous)
        assertTrue(lucky.reasonCodes.contains("modifier_established_anchored_lucky_label"))
        assertFalse(lucky.reasonCodes.contains("cost_modifier_unknown"))
    }

    @Test
    fun establishedShadowContextContradictingTheCostIsAConflictNotALevel() {
        // A trustworthily non-Shadow context sees 200 as impossible for its state; the
        // cost IS legal elsewhere, so the honest result is a conflict of observations.
        val shadow = evaluate(
            200,
            StardustModifierContext(
                established = StardustModifierContext.EstablishedStardustModifiers(shadow = true),
                provenanceCode = "test_shadow"))
        assertTrue(shadow is StardustLevelEvidence.Conflict)
        assertTrue(shadow.reasonCodes.contains("cost_conflicts_with_established_modifier"))
    }

    @Test
    fun establishedNormalContextRemovesModifierCoincidences() {
        // 200 with proven NORMAL cost state: only the normal tier-0 window survives; the
        // Lucky tier-1 reading (halved 400) is excluded by the established context.
        val normal = evaluate(
            200,
            StardustModifierContext(
                established = StardustModifierContext.EstablishedStardustModifiers(),
                provenanceCode = "test_normal"))
        assertTrue(normal is StardustLevelEvidence.Levels)
        assertEquals(halfLevels(1.0, 3.5), normal.let { levelsOf(it) })
        assertEquals(listOf("tier0_normal"), (normal as StardustLevelEvidence.Levels).interpretations)
    }

    @Test
    fun unknownModifierStateIsPreservedNotInferred() {
        // Unknown context keeps every surviving interpretation; nothing narrows it to a
        // single scalar level, and no weak visual evidence is consulted by the oracle.
        val evidence = evaluate(200)
        assertTrue(evidence is StardustLevelEvidence.Levels)
        assertTrue((evidence as StardustLevelEvidence.Levels).levels.size > 1)
        assertTrue(evidence.modifierAmbiguous)
        assertTrue(evidence.reasonCodes.contains("cost_modifier_unknown"))
    }

    // -- Level domain authority --------------------------------------------------------

    @Test
    fun legalLevelsAreIntersectedWithTheProvidedRecognitionDomain() {
        // The domain is an input: a restricted recognition domain constrains the window,
        // and no internal table may extend it.
        val restricted = setOf(9.0, 9.5, 10.0)
        val evidence = StardustLevelWindowOracle.evaluate(
            FieldRead.read(1_000), StardustModifierContext.UNKNOWN, restricted)
        assertEquals(listOf(9.0, 9.5, 10.0), levelsOf(evidence))
        assertNull((evidence as StardustLevelEvidence.Levels).levels.firstOrNull { it !in restricted })
    }

    @Test
    fun domainLevelAboveTheSupportedPowerUpBoundaryNeverMatchesAnyCost() {
        // 51.0 is inside the recognition domain but outside every power-up tier base:
        // no cost may legalize it through the oracle.
        val reachable = (0..100).map { 1.0 + it / 2.0 }.toSet()
        (0..20_000).forEach { cost ->
            val evidence = StardustLevelWindowOracle.evaluate(
                FieldRead.read(cost), StardustModifierContext.UNKNOWN, reachable)
            // Non-window states (invalid etc.) trivially contain no level.
            if (evidence is StardustLevelEvidence.Levels) {
                assertFalse("cost=$cost", evidence.levels.contains(51.0))
            }
        }
    }

    // -- Bounded, non-sensitive evidence payloads --------------------------------------

    @Test
    fun evidenceCarriesBoundedCodesOnly() {
        val boundedCode = Regex("""^[a-z0-9_.]+$""")
        listOf(
            evaluate(200),
            evaluate(1_234),
            StardustLevelWindowOracle.evaluate(
                FieldRead.missing("action_not_detected"), StardustModifierContext.UNKNOWN, domain),
            StardustLevelWindowOracle.evaluate(
                FieldRead.unreadable("cost_out_of_supported_domain"), StardustModifierContext.UNKNOWN, domain),
            StardustLevelWindowOracle.evaluate(
                FieldRead.conflict("multiple_distinct_costs", 2), StardustModifierContext.UNKNOWN, domain)
        ).forEach { evidence ->
            evidence.reasonCodes.forEach { assertTrue("code=$it", boundedCode.matches(it)) }
            if (evidence is StardustLevelEvidence.Levels) {
                evidence.interpretations.forEach { assertTrue(boundedCode.matches(it)) }
                evidence.levels.forEach { level ->
                    assertTrue(level.isFinite())
                    assertEquals(0.0, level % 0.5, 1e-9)
                }
            }
            // No path separators, no whitespace payloads anywhere in the evidence string.
            assertFalse(evidence.toString().contains('/'))
            assertFalse(evidence.toString().contains('\\'))
        }
    }

    private fun assertDifferentEvidenceStates(first: Any, second: Any) {
        assertFalse(first::class == second::class)
    }
}
