package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.EvaluationOutcome
import com.pokerarity.scanner.util.ocr.FamilySpeciesResolver
import com.pokerarity.scanner.util.ocr.PowerUpCostModifier
import com.pokerarity.scanner.util.ocr.RecognitionObservation
import com.pokerarity.scanner.util.ocr.RecognitionSnapshot
import com.pokerarity.scanner.util.ocr.StardustLevelEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

/**
 * Phase 3B resolver integration: the common candidate/profile evaluation consumes the
 * Phase 3A typed stardust evidence (underlying/base levels) through the bounded tuple
 * authority, keeps every evidence state explicit, and never loosens the Phase 1/2
 * acceptance semantics. Fixture expectations come from the published game formulas
 * against the real pinned snapshot CPM values (see ProfileTupleFeasibilityTest).
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class FamilySpeciesResolverTypedStardustTest {


    // Real pinned CPM values on purpose-built subset domains.
    private val realCpm = mapOf(
        30.0 to 0.7317000031471252, 30.5 to 0.7347410111373763, 33.0 to 0.7497610449790955,
        49.0 to 0.8353000283241272, 49.5 to 0.8378037559315699, 50.0 to 0.8403000235557556,
        50.5 to 0.8428037290347484, 51.0 to 0.845300018787384
    )

    private fun row(species: String, atk: Int, def: Int, sta: Int) = RecognitionSnapshot.Profile(
        species = species,
        forms = setOf(species),
        stats = RecognitionSnapshot.ProfileStats(atk, def, sta),
        types = setOf("normal"),
        familyId = "FAMILY_FIXTURE",
        candySpecies = "Alpha",
        evolutionCandyCosts = null
    )

    /** Fixture A: maxHp 52 + cp 554 is witnessed only at effective 33.0 (the 5000 gap). */
    private val gapResolver = FamilySpeciesResolver(
        RecognitionSnapshot.fromRows(
            listOf(row("Alpha", atk = 100, def = 100, sta = 70), row("Beta", atk = 85, def = 100, sta = 70)),
            realCpm.filterKeys { it in setOf(30.0, 30.5, 33.0) }
        ))

    /** Fixture B: maxHp 50 + cp 656 is witnessed only at effective 50.0 and 51.0. */
    private val buddyResolver = FamilySpeciesResolver(
        RecognitionSnapshot.fromRows(
            listOf(row("Gamma", atk = 100, def = 100, sta = 55)),
            realCpm.filterKeys { it in setOf(49.0, 49.5, 50.0, 50.5, 51.0) }
        ))

    private fun levels(levels: List<Double>): StardustLevelEvidence.Levels = StardustLevelEvidence.Levels(
        levels = levels,
        minLevel = levels.min(),
        maxLevel = levels.max(),
        modifiersUsed = listOf(PowerUpCostModifier.NORMAL),
        interpretations = listOf("fixture_window"),
        reasonCodes = listOf("anchored_power_up_row", "cost_modifier_unknown")
    )

    private val levels5000 = levels(listOf(29.0, 29.5, 30.0, 30.5) + listOf(39.0, 39.5, 40.0, 40.5))

    private fun pokemon(cp: Int?, maxHp: Int, stardust: Int? = null): PokemonData = PokemonData(
        cp = cp, hp = maxHp, maxHp = maxHp, name = "nickname", realName = "nickname",
        candyName = null, megaEnergy = null, weight = null, height = null,
        stardust = stardust, caughtDate = null)

    private fun observation(
        evidence: StardustLevelEvidence? = null,
        rawCost: Int? = null
    ) = FamilySpeciesResolver.Observation(
        candySpecies = "Alpha", exactCandyLabel = true,
        powerUpStardust = rawCost, anchoredPowerUpCost = rawCost != null,
        types = null, evolutionCandyCost = null, stardustLevelEvidence = evidence)

    private fun evaluation(
        resolver: FamilySpeciesResolver,
        pokemon: PokemonData,
        observed: FamilySpeciesResolver.Observation
    ) = resolver.resolveWithEvaluation(pokemon, observed)

    private fun constraint(evaluation: com.pokerarity.scanner.util.ocr.CandidateEvaluation, name: String) =
        evaluation.evaluations.single { it.name == name }

    // -- tuple pruning and the disjoint-window gap --------------------------------------

    @Test
    fun tupleFeasibilityPrunesFormRowsBeforeSpeciesProjection() {
        // CP 554 / maxHp 52 has a legal tuple only for Alpha (effective 33.0);
        // Beta has none anywhere and is pruned as a ROW before projection.
        val result = evaluation(gapResolver, pokemon(554, 52), observation())
        assertEquals(EvaluationOutcome.UNIQUE_SUPPORTED, result.outcome)
        assertEquals("Alpha", result.acceptedSpecies)
    }

    @Test
    fun stardustWindowGapRejectsTheOnlyLegalWitness() {
        // Alpha's only tuple sits at underlying 33.0/32.0 — inside the 29.0..40.5
        // span of the 5000 windows but in the GAP between them: the whole family
        // becomes numerically contradicted instead of surviving on min..max.
        val result = evaluation(gapResolver, pokemon(554, 52), observation(levels5000))
        assertEquals(EvaluationOutcome.CONTRADICTION, result.outcome)
        assertTrue(result.survivingCandidates.isEmpty())
        assertNull(gapResolver.resolve(pokemon(554, 52), observation(levels5000)).species)
    }

    @Test
    fun stardustWindowKeepsWitnessesInsideTheLegalSet() {
        // CP 470 has legal tuples inside the first 5000 window (underlying 29.0..30.5
        // incl. Best Buddy bases) for both fixture rows: the window keeps them alive
        // and the cost constraint stays honest positive support for the survivors.
        val result = evaluation(gapResolver, pokemon(470, 52), observation(levels5000))
        assertTrue("Alpha" in result.survivingCandidates.map { it.species })
        assertTrue("Alpha" in constraint(result, "power_up_cost").matched.map { it.species })
        assertTrue(result.positiveBasis.contains("power_up_cost"))
    }

    // -- Phase 3A evidence-state semantics ----------------------------------------------

    @Test
    fun invalidDustContradictsWitnessedRowsInsteadOfGuessingALevel() {
        // An anchored read that is no displayed power-up value (962-style) eliminates
        // every same-witness numeric row with its typed reason — it is never treated
        // as a wildcard level and never silently ignored.
        val result = evaluation(gapResolver, pokemon(554, 52), observation(
            StardustLevelEvidence.Invalid(962, listOf("anchored_power_up_row", "cost_not_a_displayed_power_up_value"))))
        assertEquals(EvaluationOutcome.CONTRADICTION, result.outcome)
        assertTrue("Alpha" in constraint(result, "power_up_cost").eliminated.map { it.species })
        assertTrue(constraint(result, "power_up_cost").detail!!.contains("invalid"))
    }

    @Test
    fun missingAndUnreadableStardustNeverConstrainOrSupport() {
        val withoutEvidence = evaluation(gapResolver, pokemon(554, 52), observation())
        val missing = evaluation(gapResolver, pokemon(554, 52),
            observation(StardustLevelEvidence.Missing(listOf("anchored_power_up_row", "action_not_detected"))))
        val unreadable = evaluation(gapResolver, pokemon(554, 52),
            observation(StardustLevelEvidence.Unreadable(listOf("anchored_power_up_row", "cost_token_unreadable"))))

        // Widening/unchanged: same accepted species and outcome as no evidence at all,
        // and the cost constraint is not observed (never positive support).
        assertEquals(withoutEvidence.outcome, missing.outcome)
        assertEquals(withoutEvidence.acceptedSpecies, missing.acceptedSpecies)
        assertEquals(withoutEvidence.outcome, unreadable.outcome)
        assertEquals(withoutEvidence.acceptedSpecies, unreadable.acceptedSpecies)
        assertEquals(false, constraint(missing, "power_up_cost").observed)
        assertEquals(false, constraint(unreadable, "power_up_cost").observed)
        assertFalse(withoutEvidence.positiveBasis.contains("power_up_cost"))
    }

    @Test
    fun unsupportedStardustNeverBecomesPositiveSupportOrElimination() {
        val result = evaluation(gapResolver, pokemon(554, 52), observation(
            StardustLevelEvidence.Unsupported(listOf("anchored_power_up_row", "lucky_shadow_cost_unsupported"))))
        // The row survives on its own numeric evidence; the unsupported cost neither
        // supports nor eliminates it.
        assertEquals(EvaluationOutcome.UNIQUE_SUPPORTED, result.outcome)
        assertEquals("Alpha", result.acceptedSpecies)
        val cost = constraint(result, "power_up_cost")
        assertTrue(cost.observed)
        assertTrue(cost.matched.isEmpty() && cost.eliminated.isEmpty())
        assertFalse(result.positiveBasis.contains("power_up_cost"))
    }

    @Test
    fun conflictingStardustObservationsFailClosed() {
        val conflict = StardustLevelEvidence.Conflict(
            2, listOf("anchored_power_up_row", "multiple_distinct_costs"))
        val result = gapResolver.resolve(pokemon(554, 52), observation(conflict))
        assertNull(result.species)
        assertTrue(result.candidates.isEmpty())
        assertEquals("power_up_observations_conflict", result.reason)
    }

    // -- Best Buddy semantics -----------------------------------------------------------

    @Test
    fun bestBuddyWitnessesKeepHighLevelObservationsAlive() {
        // CP 656 / maxHp 50 is witnessed only at effective 50.0 and 51.0; effective
        // 51.0 exists only as underlying 50.0 + Best Buddy. Without stardust the row
        // must survive (the buddy interpretation is always possible).
        val result = evaluation(buddyResolver, pokemon(656, 50), observation())
        assertEquals(EvaluationOutcome.UNIQUE_SUPPORTED, result.outcome)
        assertEquals("Gamma", result.acceptedSpecies)
        assertTrue(constraint(result, "cp_maxhp_feasibility").matched.isNotEmpty())
    }

    @Test
    fun stardust15000SurvivesOnlyThroughTheBestBuddyWitness() {
        // The 15000 window (49.0/49.5) excludes effective-51.0 (underlying 50.0) and
        // the normal effective-50.0 reading (underlying 50.0); only the Best Buddy
        // witness (underlying 49.0, effective 50.0) keeps the row alive.
        val levels15000 = levels(listOf(49.0, 49.5))
        val result = evaluation(buddyResolver, pokemon(656, 50), observation(levels15000))
        assertEquals(EvaluationOutcome.UNIQUE_SUPPORTED, result.outcome)
        assertEquals("Gamma", result.acceptedSpecies)
        assertTrue(constraint(result, "power_up_cost").matched.isNotEmpty())
    }

    @Test
    fun stardustWindowCannotInventUnderlyingLevelsAtOrAbove50() {
        // A window that stops below 50.0 (15000) can never pair with the effective-51.0
        // witness, because its underlying level would be 50.0+: if the ONLY witness
        // were effective 51.0 the row must be contradicted, not rescued at 50+.
        val only51Domain = FamilySpeciesResolver(
            RecognitionSnapshot.fromRows(
                listOf(row("Gamma", atk = 100, def = 100, sta = 55)),
                realCpm.filterKeys { it == 51.0 }
            ))
        val result = evaluation(only51Domain, pokemon(656, 50), observation(levels(listOf(49.0, 49.5))))
        assertEquals(EvaluationOutcome.CONTRADICTION, result.outcome)
    }

    // -- legacy path and inventory stardust ---------------------------------------------

    @Test
    fun legacyRawCostPathStillRejectsNoncanonicalCosts() {
        // Observations predating the typed evidence keep the witnessed-level raw-cost
        // evaluation: 962 matches no canonical tier and contradicts the witness.
        val result = evaluation(gapResolver, pokemon(554, 52), observation(rawCost = 962))
        assertEquals(EvaluationOutcome.CONTRADICTION, result.outcome)
    }

    @Test
    fun inventoryStardustBalanceNeverConstrainsTuples() {
        // The inventory balance lives on PokemonData, never in the observation: with
        // no anchored cost the scan outcome is identical whether or not the wallet
        // happens to hold a power-up-like number.
        val withWallet = gapResolver.resolve(pokemon(554, 52, stardust = 962), observation())
        val withoutWallet = gapResolver.resolve(pokemon(554, 52, stardust = null), observation())
        assertEquals(withoutWallet, withWallet)
        assertEquals("Alpha", withWallet.species)
    }
}
