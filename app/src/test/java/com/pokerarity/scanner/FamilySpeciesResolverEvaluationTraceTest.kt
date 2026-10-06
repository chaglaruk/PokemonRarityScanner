package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.ConstraintStatus
import com.pokerarity.scanner.util.ocr.EvaluationOutcome
import com.pokerarity.scanner.util.ocr.FamilySpeciesResolver
import com.pokerarity.scanner.util.ocr.RecognitionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.File

/**
 * Phase 1A trace contract: a reviewer must be able to answer "why was this
 * species accepted?" (or rejected) purely from the candidate/constraint trace.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class FamilySpeciesResolverEvaluationTraceTest {

    private val profiles = listOf(
        File("src/main/assets/data/recognition_profiles.json"),
        File("app/src/main/assets/data/recognition_profiles.json")
    ).first { it.isFile }.reader().use(RecognitionSnapshot::load)
    private val resolver = FamilySpeciesResolver(profiles)

    private fun pokemon(cp: Int?, hp: Int?, maxHp: Int? = hp) = PokemonData(
        cp = cp, hp = hp, maxHp = maxHp, name = "nickname", realName = "nickname", candyName = null,
        megaEnergy = null, weight = null, height = null, stardust = null, caughtDate = null)

    private fun obs(candy: String, powerUp: Int? = null, types: Set<String>? = null, evolve: Int? = null) =
        FamilySpeciesResolver.Observation(
            candySpecies = candy, exactCandyLabel = true, powerUpStardust = powerUp,
            anchoredPowerUpCost = powerUp != null, types = types, evolutionCandyCost = evolve)

    @Test
    fun weedleAcceptanceIsTraceableToItsConstraints() {
        val evaluation = resolver.resolveWithEvaluation(pokemon(150, 61), obs("Weedle", evolve = 12))

        assertEquals(EvaluationOutcome.UNIQUE_SUPPORTED, evaluation.outcome)
        assertEquals("Weedle", evaluation.acceptedSpecies)
        assertEquals("independent_family_profile", evaluation.acceptanceReason)
        // Initial pool: every Weedle-family form row.
        assertTrue(evaluation.initialCandidates.any { it.species == "Weedle" })
        assertTrue(evaluation.initialCandidates.any { it.species == "Kakuna" })
        // The evolve constraint eliminated Kakuna and matched Weedle.
        val evolve = evaluation.evaluations.single { it.name == "evolve_cost" }
        assertTrue(evolve.observed)
        assertTrue(evolve.matched.any { it.species == "Weedle" })
        assertTrue(evolve.eliminated.any { it.species == "Kakuna" })
        // Unknown-metadata rows that were eliminated elsewhere never appear unresolved.
        val beedrillRows = evaluation.initialCandidates.filter { it.species == "Beedrill" }
        assertTrue(beedrillRows.isNotEmpty())
        assertTrue(beedrillRows.all { row -> evaluation.survivingCandidates.none { it == row } })
        // Survivors project to exactly the accepted species.
        assertEquals(listOf("Weedle"), evaluation.survivingCandidates.map { it.species }.distinct())
    }

    @Test
    fun unobservedConstraintsAreRecordedAsNotObserved() {
        val evaluation = resolver.resolveWithEvaluation(pokemon(150, 61), obs("Weedle", evolve = 12))
        val type = evaluation.evaluations.single { it.name == "complete_type" }
        assertFalse(type.observed)
        assertEquals(ConstraintStatus.NOT_OBSERVED, type.status)
        assertTrue(type.matched.isEmpty() && type.eliminated.isEmpty())
        val powerUp = evaluation.evaluations.single { it.name == "power_up_cost" }
        assertFalse(powerUp.observed)
        assertEquals(ConstraintStatus.NOT_OBSERVED, powerUp.status)
    }

    @Test
    fun unknownEvolveMetadataIsVisibleAsUnresolvedNeverMatched() {
        val template = profiles.forSpecies("Torchic").first()
        val known = template.copy(species = "Known", forms = setOf("Known"),
            candySpecies = "Synthetic", evolutionCandyCosts = setOf(50))
        val mystery = template.copy(species = "Mystery", forms = setOf("Mystery"),
            candySpecies = "Synthetic", evolutionCandyCosts = null)
        val synthetic = FamilySpeciesResolver(
            RecognitionSnapshot.fromRows(listOf(known, mystery), profiles.cpMultipliers))

        val evaluation = synthetic.resolveWithEvaluation(
            pokemon(null, null, maxHp = null), obs("Synthetic", types = setOf("fire"), evolve = 25))

        assertEquals(EvaluationOutcome.INSUFFICIENT_EVIDENCE, evaluation.outcome)
        assertNull(evaluation.acceptedSpecies)
        val evolve = evaluation.evaluations.single { it.name == "evolve_cost" }
        assertTrue(evolve.eliminated.any { it.species == "Known" })
        assertTrue(evolve.unresolved.any { it.species == "Mystery" })
        assertFalse(evolve.matched.any { it.species == "Mystery" })
        // The surviving unknown-metadata row stays visible in the survivor list.
        assertTrue(evaluation.survivingCandidates.any { it.species == "Mystery" })
    }

    @Test
    fun contradictionNamesTheEliminatingConstraint() {
        val evaluation = resolver.resolveWithEvaluation(
            pokemon(null, null, maxHp = null), obs("Torchic", types = setOf("fire"), evolve = 50))
        assertEquals(EvaluationOutcome.CONTRADICTION, evaluation.outcome)
        assertNull(evaluation.acceptedSpecies)
        val type = evaluation.evaluations.single { it.name == "complete_type" }
        assertTrue(type.matched.any { it.species == "Torchic" })
        val evolve = evaluation.evaluations.single { it.name == "evolve_cost" }
        assertTrue(evolve.eliminated.any { it.species == "Torchic" })
        assertTrue(evaluation.survivingCandidates.isEmpty())
    }

    // ------------------------------------------------------------------
    // PR #57 review: unknown metadata may coexist with an independently
    // established identity, but may never create it.
    // ------------------------------------------------------------------

    private fun syntheticFamily(vararg rows: RecognitionSnapshot.Profile): FamilySpeciesResolver =
        FamilySpeciesResolver(RecognitionSnapshot.fromRows(rows.toList(), profiles.cpMultipliers))

    private fun syntheticRow(
        species: String,
        stats: RecognitionSnapshot.ProfileStats = RecognitionSnapshot.ProfileStats(113, 86, 128),
        types: Set<String> = setOf("fire"),
        costs: Set<Int>?
    ) = profiles.forSpecies("Torchic").first().copy(
        species = species, forms = setOf(species), candySpecies = "Synthetic",
        stats = stats, types = types, evolutionCandyCosts = costs)

    /**
     * Case 1 (regression): unknown evolve metadata is the ONLY reason the candidate
     * survives — no independent constraint establishes it. The type matched both
     * rows, so the uniqueness would be created by the elimination of the known
     * candidate plus unknown metadata: must stay unresolved.
     */
    @Test
    fun review1_unknownEvolve_noIndependentIdentity_staysUnresolved() {
        val evaluation = syntheticFamily(
            syntheticRow("Known", costs = setOf(50)),
            syntheticRow("Mystery", costs = null)
        ).resolveWithEvaluation(pokemon(null, null, maxHp = null),
            obs("Synthetic", types = setOf("fire"), evolve = 25))

        assertNull(evaluation.acceptedSpecies)
        assertEquals(EvaluationOutcome.INSUFFICIENT_EVIDENCE, evaluation.outcome)
        val evolve = evaluation.evaluations.single { it.name == "evolve_cost" }
        assertTrue(evolve.eliminated.any { it.species == "Known" })
        assertTrue(evolve.unresolved.any { it.species == "Mystery" })
        assertFalse(evolve.matched.any { it.species == "Mystery" })
        // The type matched the survivor, but it also matched the eliminated row:
        // the basis is not exclusive, so it does not establish identity.
        assertTrue(evaluation.positiveBasis.contains("complete_type"))
        assertFalse(evaluation.positiveBasisExclusive)
    }

    /**
     * Case 2: complete type evidence ALONE identifies MysteryCandidate (the other
     * candidate is eliminated by type). Its unknown EVOLVE metadata must not veto
     * the established identity; EVOLVE stays unresolved with zero positive support.
     */
    @Test
    fun review2_unknownEvolve_independentTypeIdentity_isAccepted() {
        val evaluation = syntheticFamily(
            syntheticRow("Mystery", types = setOf("fire"), costs = null),
            syntheticRow("Other", types = setOf("water"), costs = setOf(50))
        ).resolveWithEvaluation(pokemon(null, null, maxHp = null),
            obs("Synthetic", types = setOf("fire"), evolve = 25))

        assertEquals("Mystery", evaluation.acceptedSpecies)
        assertEquals(EvaluationOutcome.UNIQUE_SUPPORTED, evaluation.outcome)
        assertEquals(listOf("complete_type"), evaluation.positiveBasis)
        assertTrue(evaluation.positiveBasisExclusive)
        val evolve = evaluation.evaluations.single { it.name == "evolve_cost" }
        assertTrue(evolve.observed)
        assertTrue(evolve.unresolved.any { it.species == "Mystery" })
        assertFalse(evolve.matched.any { it.species == "Mystery" })
    }

    /**
     * Case 3: same-witness CP/maxHP feasibility ALONE identifies MysteryCandidate
     * (verified against the real calculator: at CP320/maxHP60 the sta-200 profile
     * has no feasible level while the sta-100 profile is feasible at 15.5/16.0).
     * Unknown EVOLVE metadata coexists without contributing support.
     */
    @Test
    fun review3_unknownEvolve_independentNumericIdentity_isAccepted() {
        val evaluation = syntheticFamily(
            syntheticRow("Known", stats = RecognitionSnapshot.ProfileStats(113, 86, 200), costs = setOf(50)),
            syntheticRow("Mystery", stats = RecognitionSnapshot.ProfileStats(113, 86, 100), costs = null)
        ).resolveWithEvaluation(pokemon(320, 60), obs("Synthetic", evolve = 25))

        val feasibility = evaluation.evaluations.single { it.name == "cp_maxhp_feasibility" }
        assertTrue(feasibility.matched.any { it.species == "Mystery" })
        assertTrue(feasibility.eliminated.any { it.species == "Known" })
        assertEquals("Mystery", evaluation.acceptedSpecies)
        assertEquals(EvaluationOutcome.UNIQUE_SUPPORTED, evaluation.outcome)
        assertEquals(listOf("cp_maxhp_feasibility"), evaluation.positiveBasis)
        assertTrue(evaluation.positiveBasisExclusive)
        val evolve = evaluation.evaluations.single { it.name == "evolve_cost" }
        assertTrue(evolve.unresolved.any { it.species == "Mystery" })
        assertFalse(evolve.matched.any { it.species == "Mystery" })
    }

    /**
     * Case 4 (explicit): known-metadata candidates eliminated by EVOLVE and a
     * single unknown-metadata survivor is NOT accepted even though the canonical
     * surviving species count is one.
     */
    @Test
    fun review4_unknownMetadataDoesNotCreateUniqueIdentity() {
        val evaluation = syntheticFamily(
            syntheticRow("OnlyKnown", costs = setOf(50)),
            syntheticRow("OnlyMystery", costs = null)
        ).resolveWithEvaluation(pokemon(null, null, maxHp = null), obs("Synthetic", evolve = 25))

        assertEquals(1, evaluation.survivingCandidates.map { it.species }.distinct().size)
        assertNull(evaluation.acceptedSpecies)
        assertTrue(
            evaluation.outcome == EvaluationOutcome.INSUFFICIENT_EVIDENCE ||
                evaluation.outcome == EvaluationOutcome.UNSUPPORTED_MECHANIC)
        assertFalse(evaluation.positiveBasisExclusive)
    }

    /**
     * Case 5: a canonical species with two form rows — one positively matched,
     * one carrying unknown metadata — plus a second species. Identity follows the
     * positive basis; both rows of the accepted species survive (no row-to-species
     * collapse), and the unresolved row neither creates nor vetoes identity.
     * The contrast case shows the second species keeps the scan ambiguous.
     */
    @Test
    fun review5_multiRowSameSpecies_projectionFollowsIndependentIdentity() {
        val familyA = syntheticFamily(
            syntheticRow("S", stats = RecognitionSnapshot.ProfileStats(113, 86, 128), costs = setOf(25)),
            syntheticRow("S", stats = RecognitionSnapshot.ProfileStats(120, 90, 130), costs = null),
            syntheticRow("T", stats = RecognitionSnapshot.ProfileStats(110, 80, 120), costs = setOf(50))
        )
        val evaluation = familyA.resolveWithEvaluation(
            pokemon(null, null, maxHp = null), obs("Synthetic", types = setOf("fire"), evolve = 25))

        assertEquals("S", evaluation.acceptedSpecies)
        assertEquals(EvaluationOutcome.UNIQUE_SUPPORTED, evaluation.outcome)
        // Both S rows survive: the matched row anchors identity, the unknown-
        // metadata row is same-species and cannot flip it.
        assertEquals(2, evaluation.survivingCandidates.count { it.species == "S" })
        assertTrue(evaluation.survivingCandidates.none { it.species == "T" })
        val evolve = evaluation.evaluations.single { it.name == "evolve_cost" }
        assertTrue(evolve.matched.any {
            it.species == "S" && it.forms == setOf("S") && it.evolutionCandyCosts == setOf(25)
        })
        assertTrue(evolve.unresolved.any { it.species == "S" && it.evolutionCandyCosts == null })
        assertTrue(evolve.eliminated.any { it.species == "T" })

        // Contrast: when T also matches the observed cost, the basis alone keeps
        // both species and the unresolved S-row must not tip the decision.
        val ambiguous = syntheticFamily(
            syntheticRow("S", costs = setOf(25)),
            syntheticRow(
                "S2", stats = RecognitionSnapshot.ProfileStats(120, 90, 130),
                types = setOf("fire"), costs = null),
            syntheticRow("T", stats = RecognitionSnapshot.ProfileStats(110, 80, 120), costs = setOf(25))
        ).resolveWithEvaluation(pokemon(null, null, maxHp = null), obs("Synthetic", types = setOf("fire"), evolve = 25))
        assertNull(ambiguous.acceptedSpecies)
        assertEquals(EvaluationOutcome.AMBIGUOUS, ambiguous.outcome)
        assertTrue(ambiguous.survivingCandidates.any { it.species == "T" })
        assertFalse(ambiguous.positiveBasisExclusive)
    }
}
