package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator

/** The editable nickname never chooses between independently plausible family members. */
internal class FamilySpeciesResolver(
    private val snapshot: RecognitionSnapshot?,
    private val calculator: RarityCalculator
) {
    data class Observation(
        val candySpecies: String?,
        val exactCandyLabel: Boolean,
        val powerUpStardust: Int? = null,
        val anchoredPowerUpCost: Boolean = false,
        val types: Set<String>? = null,
        val evolutionCandyCost: Int? = null
    )
    data class Result(val species: String?, val candidates: Set<String>, val reason: String)

    fun resolve(pokemon: PokemonData): Result {
        val observation = pokemon.recognitionObservation
            ?: return Result(null, emptySet(), "screen_observation_missing")
        val guardReason = when {
            !observation.detailScreen -> "detail_screen_unconfirmed"
            observation.numericConflict -> "numeric_observations_conflict"
            else -> null
        }
        return guardReason?.let { Result(null, emptySet(), it) }
            ?: resolve(pokemon, Observation(observation.candySpecies, observation.candySpecies != null,
                observation.powerUpStardust, observation.powerUpStardust != null,
                observation.types, observation.evolutionCandyCost))
    }

    fun resolve(pokemon: PokemonData, observed: Observation): Result =
        toResult(resolveWithEvaluation(pokemon, observed))

    /**
     * The one common candidate/profile evaluation path (plan section 5.2).
     * Candidates are form-profile ROWS (rows of one species share the canonical
     * name but can differ in types/stats/costs), every applicable supported
     * constraint is evaluated over the same rows, and the canonical species is
     * projected only after all constraints have run.
     */
    internal fun resolveWithEvaluation(pokemon: PokemonData, observed: Observation): CandidateEvaluation {
        val pool = initialPool(snapshot, observed)
        val family = pool.first
        if (family.isEmpty()) return CandidateEvaluation.insufficient(family, pool.second!!)
        val evaluations = listOf(
            ConstraintEvaluation(
                name = "candy_family", observed = true, status = ConstraintStatus.MATCHED,
                matched = family.toSet(),
                detail = "exact candy label selects the candidate pool; " +
                    "defines provenance, never positive support by itself"
            ),
            typeEvaluation(observed, family),
            feasibilityEvaluation(pokemon, family, calculator, snapshot?.cpMultipliers ?: emptyMap()),
            powerUpCostEvaluation(pokemon, observed, family, calculator, snapshot?.cpMultipliers ?: emptyMap()),
            evolveCostEvaluation(observed, family)
        )
        return evaluateOutcome(family, evaluations)
    }
}

/** Candy-gate: returns the candidate pool, or an empty list plus the guard reason. */
private fun initialPool(
    snapshot: RecognitionSnapshot?,
    observed: FamilySpeciesResolver.Observation
): Pair<List<RecognitionSnapshot.Profile>, String?> = when {
    // Fail closed when the recognition snapshot is unavailable: no family, no candidates.
    snapshot == null -> emptyList<RecognitionSnapshot.Profile>() to "recognition_snapshot_unavailable"
    observed.candySpecies?.takeIf { observed.exactCandyLabel } == null ->
        emptyList<RecognitionSnapshot.Profile>() to "candy_label_missing"
    else -> snapshot.forCandy(observed.candySpecies!!).takeIf { it.isNotEmpty() }?.let { it to null }
        ?: (emptyList<RecognitionSnapshot.Profile>() to "family_metadata_missing")
}

private fun typeEvaluation(
    observed: FamilySpeciesResolver.Observation,
    family: List<RecognitionSnapshot.Profile>
): ConstraintEvaluation {
    val types = observed.types
    if (types.isNullOrEmpty()) {
        return ConstraintEvaluation.notObserved("complete_type", "no complete type evidence")
    }
    val matched = family.filter { it.types == types }.toSet()
    return ConstraintEvaluation(
        name = "complete_type", observed = true, status = ConstraintStatus.MATCHED,
        matched = matched, eliminated = family.toSet() - matched,
        detail = "complete observed type set must equal the profile type set")
}

private fun feasibilityEvaluation(
    pokemon: PokemonData,
    family: List<RecognitionSnapshot.Profile>,
    calculator: RarityCalculator,
    cpMultipliers: Map<Double, Double>
): ConstraintEvaluation {
    if (pokemon.maxHp == null) {
        return ConstraintEvaluation.notObserved(
            "cp_maxhp_feasibility", "max HP missing; CP without max HP is never a same-witness base")
    }
    val matched = family.filter {
        calculator.matchingProfileLevels(pokemon, it.stats, cpMultipliers).isNotEmpty()
    }.toSet()
    return ConstraintEvaluation(
        name = "cp_maxhp_feasibility", observed = true, status = ConstraintStatus.MATCHED,
        matched = matched, eliminated = family.toSet() - matched,
        detail = "same-witness CP/maxHP (max HP alone when CP is absent) must have at least one feasible level")
}

private fun powerUpCostEvaluation(
    pokemon: PokemonData,
    observed: FamilySpeciesResolver.Observation,
    family: List<RecognitionSnapshot.Profile>,
    calculator: RarityCalculator,
    cpMultipliers: Map<Double, Double>
): ConstraintEvaluation = when {
    observed.powerUpStardust == null || !observed.anchoredPowerUpCost ->
        ConstraintEvaluation.notObserved("power_up_cost", "no anchored power-up cost")
    pokemon.maxHp == null -> ConstraintEvaluation.unsupported("power_up_cost",
        "anchored cost observed but no max HP witness to anchor a level window; never applied as support")
    else -> anchoredPowerUpCostEvaluation(observed.powerUpStardust, pokemon, family, calculator, cpMultipliers)
}

private fun anchoredPowerUpCostEvaluation(
    cost: Int,
    pokemon: PokemonData,
    family: List<RecognitionSnapshot.Profile>,
    calculator: RarityCalculator,
    cpMultipliers: Map<Double, Double>
): ConstraintEvaluation {
    val levelsByRow = family.associateWith { calculator.matchingProfileLevels(pokemon, it.stats, cpMultipliers) }
    val matched = family.filter { row ->
        levelsByRow.getValue(row).any { level -> PowerUpTiers.costMatches(cost, level) }
    }.toSet()
    val eliminated = family.filter { row ->
        levelsByRow.getValue(row).isNotEmpty() && row !in matched
    }.toSet()
    val unresolved = family.filter { row -> levelsByRow.getValue(row).isEmpty() }.toSet()
    return ConstraintEvaluation("power_up_cost", observed = true, status = ConstraintStatus.MATCHED,
        matched = matched, eliminated = eliminated, unresolved = unresolved,
        detail = "cost must match a feasible level of the same witness")
}

private fun evolveCostEvaluation(
    observed: FamilySpeciesResolver.Observation,
    family: List<RecognitionSnapshot.Profile>
): ConstraintEvaluation {
    val cost = observed.evolutionCandyCost
        ?: return ConstraintEvaluation.notObserved("evolve_cost",
            "no ordinary EVOLVE action observed; absence is never evidence against a candidate")
    val matched = family.filter { profile -> profile.evolutionCandyCosts?.contains(cost) == true }.toSet()
    val eliminated = family.filter { profile ->
        profile.evolutionCandyCosts != null && profile !in matched
    }.toSet()
    val unresolved = family.filter { profile -> profile.evolutionCandyCosts == null }.toSet()
    val status = if (matched.isEmpty() && eliminated.isEmpty()) ConstraintStatus.UNSUPPORTED
    else ConstraintStatus.MATCHED
    return ConstraintEvaluation("evolve_cost", observed = true, status = status,
        matched = matched, eliminated = eliminated, unresolved = unresolved,
        detail = "unknown evolution metadata stays unresolved and never becomes positive support")
}

private fun evaluateOutcome(
    family: List<RecognitionSnapshot.Profile>,
    evaluations: List<ConstraintEvaluation>
): CandidateEvaluation {
    val eliminatedRows = evaluations.flatMap { it.eliminated }.toSet()
    val surviving = family.filter { it !in eliminatedRows }
    val survivingSpecies = surviving.map { it.species }.distinct()
    // Positive basis: observed constraints (pool definition excluded) that matched
    // at least one surviving row of the candidate species. Unknown metadata never
    // belongs to the basis.
    val positiveBasis = positiveBasisFor(survivingSpecies, evaluations)
    // The identity may rest on the positive basis ALONE: applying only the basis
    // constraints must eliminate every other species. When it does, unknown
    // metadata merely coexists with an established identity; when it does not,
    // the uniqueness would depend on unknown metadata and stays unresolved.
    val basisEliminatedRows = evaluations.drop(1)
        .filter { it.name in positiveBasis }
        .flatMap { it.eliminated }.toSet()
    val basisOnlySpecies = family.filter { it !in basisEliminatedRows }.map { it.species }.distinct()
    val anyObserved = evaluations.drop(1).any { it.observed }
    val anyObservedUnsupported = evaluations.any { it.observed && it.status == ConstraintStatus.UNSUPPORTED }
    val outcome = when {
        survivingSpecies.isEmpty() -> EvaluationOutcome.CONTRADICTION
        survivingSpecies.size > 1 -> if (!anyObserved) {
            EvaluationOutcome.INSUFFICIENT_EVIDENCE
        } else {
            EvaluationOutcome.AMBIGUOUS
        }
        positiveBasis.isNotEmpty() && basisOnlySpecies == survivingSpecies -> EvaluationOutcome.UNIQUE_SUPPORTED
        anyObservedUnsupported -> EvaluationOutcome.UNSUPPORTED_MECHANIC
        else -> EvaluationOutcome.INSUFFICIENT_EVIDENCE
    }
    val acceptedSpecies = if (outcome == EvaluationOutcome.UNIQUE_SUPPORTED) survivingSpecies.single() else null
    return CandidateEvaluation(family, evaluations, surviving, outcome, acceptedSpecies,
        reasonFor(outcome, anyObserved), positiveBasis,
        positiveBasisExclusive = outcome == EvaluationOutcome.UNIQUE_SUPPORTED)
}

private fun positiveBasisFor(
    survivingSpecies: List<String>,
    evaluations: List<ConstraintEvaluation>
): List<String> = evaluations.drop(1)
    .filter { it.observed && it.matched.any { row -> row.species in survivingSpecies } }
    .map { it.name }

private fun reasonFor(outcome: EvaluationOutcome, anyObserved: Boolean): String = when (outcome) {
    EvaluationOutcome.UNIQUE_SUPPORTED -> "independent_family_profile"
    EvaluationOutcome.AMBIGUOUS -> "family_profile_ambiguous"
    EvaluationOutcome.CONTRADICTION -> "family_profile_contradiction"
    EvaluationOutcome.INSUFFICIENT_EVIDENCE ->
        if (!anyObserved) "cp_or_power_up_cost_missing" else "identity_insufficient_evidence"
    EvaluationOutcome.UNSUPPORTED_MECHANIC -> "identity_unsupported_mechanic"
}

private fun toResult(evaluation: CandidateEvaluation): FamilySpeciesResolver.Result {
    val candidates = evaluation.survivingCandidates.map { it.species }.toSet()
    return when (evaluation.outcome) {
        EvaluationOutcome.UNIQUE_SUPPORTED ->
            FamilySpeciesResolver.Result(evaluation.acceptedSpecies, candidates, evaluation.acceptanceReason!!)
        EvaluationOutcome.CONTRADICTION ->
            FamilySpeciesResolver.Result(null, emptySet(), evaluation.acceptanceReason!!)
        else -> FamilySpeciesResolver.Result(null, candidates, evaluation.acceptanceReason!!)
    }
}

// Ordinary power-up tiers. Possible status discounts are included for every
// candidate unless separately established, so a weak shiny/lucky detector
// cannot remove the true species. Inventory stardust never enters this path.
// Values are the canonical game tiers, intentionally explicit (detekt MagicNumber
// is acknowledged here once for the data table as a whole).
@Suppress("MagicNumber")
private val costs = listOf(
    200, 400, 600, 800, 1000, 1300, 1600, 1900, 2200, 2500,
    3000, 3500, 4000, 4500, 5000, 6000, 7000, 8000, 9000, 10000,
    11000, 12000, 13000, 14000, 15000
)
private const val SHADOW_MODIFIER = 1.2
private const val SHADOW_MODIFIER_FLOAT = 1.2f
private const val MAX_POWER_UP_LEVEL = 50.0
// Multipliers: normal, best-buddy half, purified, purified-buddy, shadow.
@Suppress("MagicNumber")
private val modifiers = listOf(1.0, .5, .9, .45, SHADOW_MODIFIER)

private object PowerUpTiers {
    fun canonicalDisplayedCosts(base: Int, modifier: Double): Set<Int> {
    val exact = kotlin.math.ceil(base * modifier).toInt()
    if (modifier != SHADOW_MODIFIER) return setOf(exact)

    // Pokemon GO can render Shadow power-up costs from single-precision
    // multiplication. Some canonical tiers therefore appear one stardust
    // above the mathematically exact 1.2x value (for example 800 -> 961 and
    // 1600 -> 1921), while other tiers remain exact (2200 -> 2640,
    // 4000 -> 4800). Model those two canonical representations explicitly
    // instead of applying a general +/-1 tolerance to arbitrary OCR values.
    val float32 = kotlin.math.ceil((base.toFloat() * SHADOW_MODIFIER_FLOAT).toDouble()).toInt()
    return setOf(exact, float32)
}

@Suppress("MagicNumber")
    fun costMatches(observed: Int, level: Double): Boolean {
    // The active Best Buddy bonus changes CP/HP, but not the underlying upgrade tier.
    val candidateBaseLevels = listOf(level, level - 1).filter { it >= 1 && it < MAX_POWER_UP_LEVEL }
    return candidateBaseLevels.any { baseLevel ->
        val base = costs[((baseLevel - 1) / 2).toInt()]
        modifiers.any { modifier -> observed in canonicalDisplayedCosts(base, modifier) }
    }

}
/** Evidence semantics for one supported constraint across the candidate rows. */
}
internal enum class ConstraintStatus {
    /** Observed and consistent with the candidate row. */
    MATCHED,
    /** Observed and inconsistent with this row: eliminates the row only. */
    ELIMINATED,
    /** The evidence was not observed on this scan; never eliminates, never supports. */
    NOT_OBSERVED,
    /** Observed but not applicable here (no witness or unknown metadata); never eliminates or supports. */
    UNSUPPORTED,
    /** Observed values conflict with each other. */
    CONFLICTING
}

internal data class ConstraintEvaluation(
    val name: String,
    val observed: Boolean,
    val status: ConstraintStatus,
    /** Form-profile rows the constraint positively supports. */
    val matched: Set<RecognitionSnapshot.Profile> = emptySet(),
    /** Form-profile rows the constraint eliminates. */
    val eliminated: Set<RecognitionSnapshot.Profile> = emptySet(),
    /** Rows retained only because their metadata is unknown; never positive support. */
    val unresolved: Set<RecognitionSnapshot.Profile> = emptySet(),
    val detail: String? = null
) {
    companion object {
        fun notObserved(name: String, detail: String): ConstraintEvaluation =
            ConstraintEvaluation(name, observed = false, status = ConstraintStatus.NOT_OBSERVED, detail = detail)

        fun unsupported(name: String, detail: String): ConstraintEvaluation =
            ConstraintEvaluation(name, observed = true, status = ConstraintStatus.UNSUPPORTED, detail = detail)
    }
}

internal enum class EvaluationOutcome {
    UNIQUE_SUPPORTED,
    AMBIGUOUS,
    CONTRADICTION,
    INSUFFICIENT_EVIDENCE,
    UNSUPPORTED_MECHANIC
}

internal data class CandidateEvaluation(
    val initialCandidates: List<RecognitionSnapshot.Profile>,
    val evaluations: List<ConstraintEvaluation>,
    val survivingCandidates: List<RecognitionSnapshot.Profile>,
    val outcome: EvaluationOutcome,
    val acceptedSpecies: String?,
    val acceptanceReason: String?,
    /** Observed constraints that positively matched the surviving species (pool definition excluded). */
    val positiveBasis: List<String> = emptyList(),
    /** True when the positive basis alone, applied to the initial pool, already yields the accepted species. */
    val positiveBasisExclusive: Boolean = false
) {
    companion object {
        fun insufficient(pool: List<RecognitionSnapshot.Profile>, reason: String) = CandidateEvaluation(
            initialCandidates = pool, evaluations = emptyList(), survivingCandidates = pool,
            outcome = EvaluationOutcome.INSUFFICIENT_EVIDENCE, acceptedSpecies = null, acceptanceReason = reason)
    }
}
