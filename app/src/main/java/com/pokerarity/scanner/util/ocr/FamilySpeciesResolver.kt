package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.PokemonData

/** The editable nickname never chooses between independently plausible family members. */
internal class FamilySpeciesResolver(
    private val snapshot: RecognitionSnapshot?
) {
    data class Observation(
        val candySpecies: String?,
        val exactCandyLabel: Boolean,
        val powerUpStardust: Int? = null,
        val anchoredPowerUpCost: Boolean = false,
        val types: Set<String>? = null,
        val evolutionCandyCost: Int? = null,
        /**
         * Phase 3A typed stardust evidence. When present it is the ONLY stardust
         * input of the numeric evaluation (the raw [powerUpStardust] is never
         * reparsed); when absent, pre-Phase-3A observations keep the legacy
         * raw-cost evaluation.
         */
        val stardustLevelEvidence: StardustLevelEvidence? = null
    )
    data class Result(
        val species: String?, val candidates: Set<String>, val reason: String,
        val formEvidence: Map<String, SameSpeciesFormEvidence> = emptyMap()
    )

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
                observation.types, observation.evolutionCandyCost,
                observation.powerUpStardustLevelEvidence))
    }

    fun resolve(pokemon: PokemonData, observed: Observation): Result =
        toResult(resolveWithEvaluation(pokemon, observed), snapshot?.metadata?.revisionId)

    /**
     * The one common candidate/profile evaluation path (plan section 5.2).
     * Candidates are form-profile ROWS (rows of one species share the canonical
     * name but can differ in types/stats/costs), every applicable supported
     * constraint is evaluated over the same rows, and the canonical species is
     * projected only after all constraints have run.
     */
    internal fun resolveWithEvaluation(pokemon: PokemonData, observed: Observation): CandidateEvaluation {
        // Conflicting anchored stardust observations are the numeric-conflict
        // fail-closed semantics: they never silently widen, support or eliminate.
        val pool = when {
            observed.stardustLevelEvidence is StardustLevelEvidence.Conflict ->
                emptyList<RecognitionSnapshot.Profile>() to "power_up_observations_conflict"
            else -> initialPool(snapshot, observed)
        }
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
            feasibilityEvaluation(pokemon, family, snapshot?.cpMultipliers ?: emptyMap()),
            powerUpCostEvaluation(pokemon, observed, family, snapshot?.cpMultipliers ?: emptyMap()),
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
    cpMultipliers: Map<Double, Double>
): ConstraintEvaluation {
    if (pokemon.maxHp == null) {
        return ConstraintEvaluation.notObserved(
            "cp_maxhp_feasibility", "max HP missing; CP without max HP is never a same-witness base")
    }
    val matched = family.filter { row ->
        ProfileTupleFeasibility.legalWitnesses(
            row.stats, pokemon.cp, pokemon.maxHp, cpMultipliers
        ).isNotEmpty()
    }.toSet()
    return ConstraintEvaluation(
        name = "cp_maxhp_feasibility", observed = true, status = ConstraintStatus.MATCHED,
        matched = matched, eliminated = family.toSet() - matched,
        detail = "same-witness CP/maxHP (max HP alone when CP is absent) must have at least one legal tuple")
}

private fun powerUpCostEvaluation(
    pokemon: PokemonData,
    observed: FamilySpeciesResolver.Observation,
    family: List<RecognitionSnapshot.Profile>,
    cpMultipliers: Map<Double, Double>
): ConstraintEvaluation {
    val typedEvidence = observed.stardustLevelEvidence
        ?: return when {
            // Pre-Phase-3A observations: the raw anchored cost with the witnessed-level
            // semantics. Identity preservation stays maximally permissive over cost
            // modifiers (full SUPPORTED_MODIFIERS set): a weak lucky/shadow/purified
            // signal cannot remove the true species. Inventory stardust never enters
            // this path — only the anchored POWER UP row cost does (Phase 2C provenance).
            observed.powerUpStardust == null || !observed.anchoredPowerUpCost ->
                ConstraintEvaluation.notObserved("power_up_cost", "no anchored power-up cost")
            pokemon.maxHp == null -> ConstraintEvaluation.unsupported("power_up_cost",
                "anchored cost observed but no max HP witness to anchor a level window; never applied as support")
            else -> legacyAnchoredPowerUpCostEvaluation(observed.powerUpStardust, pokemon, family, cpMultipliers)
        }
    return when {
        pokemon.maxHp == null -> ConstraintEvaluation.unsupported("power_up_cost",
            "anchored stardust levels observed but no max HP witness to anchor tuples; never applied as support")
        else -> typedPowerUpCostEvaluation(typedEvidence, pokemon, family, cpMultipliers)
    }
}

/**
 * Typed Phase 3A stardust evidence drives the tuple constraint: the underlying level
 * of every legal witness must lie in the anchored discrete level set. Evidence states
 * stay explicit — missing/unreadable never constrain, unsupported never supports or
 * eliminates, an invalid anchored cost contradicts every same-witness numeric row
 * (with its typed reason) instead of becoming a guessed level or a wildcard.
 */
private fun legacyAnchoredPowerUpCostEvaluation(
    cost: Int,
    pokemon: PokemonData,
    family: List<RecognitionSnapshot.Profile>,
    cpMultipliers: Map<Double, Double>
): ConstraintEvaluation {
    val witnessesByRow = family.associateWith { row ->
        ProfileTupleFeasibility.legalWitnesses(row.stats, pokemon.cp, pokemon.maxHp, cpMultipliers)
    }
    // The witnessed-level compatibility keeps the legacy Best Buddy {L, L-1} base
    // interpretation for the raw-cost path.
    val matched = family.filter { row ->
        witnessesByRow.getValue(row).any { witness ->
            PowerUpStardustRules.witnessedLevelCostMatches(cost, witness.effectiveLevel)
        }
    }.toSet()
    val eliminated = family.filter { row ->
        witnessesByRow.getValue(row).isNotEmpty() && row !in matched
    }.toSet()
    val unresolved = family.filter { row -> witnessesByRow.getValue(row).isEmpty() }.toSet()
    return ConstraintEvaluation("power_up_cost", observed = true, status = ConstraintStatus.MATCHED,
        matched = matched, eliminated = eliminated, unresolved = unresolved,
        detail = "cost must match a feasible level of the same witness")
}

private fun typedPowerUpCostEvaluation(
    evidence: StardustLevelEvidence,
    pokemon: PokemonData,
    family: List<RecognitionSnapshot.Profile>,
    cpMultipliers: Map<Double, Double>
): ConstraintEvaluation {
    val witnessesByRow = family.associateWith { row ->
        ProfileTupleFeasibility.legalWitnesses(row.stats, pokemon.cp, pokemon.maxHp, cpMultipliers)
    }
    val witnessedRows = family.filter { witnessesByRow.getValue(it).isNotEmpty() }.toSet()
    val unwitnessedRows = family.toSet() - witnessedRows
    return when (evidence) {
        is StardustLevelEvidence.Levels -> {
            val legalUnderlying = evidence.levels.toSet()
            val matched = witnessedRows.filter { row ->
                witnessesByRow.getValue(row).any { witness -> witness.underlyingLevel in legalUnderlying }
            }.toSet()
            ConstraintEvaluation("power_up_cost", observed = true, status = ConstraintStatus.MATCHED,
                matched = matched, eliminated = witnessedRows - matched, unresolved = unwitnessedRows,
                detail = "underlying level must lie in the anchored stardust level set on the same witness")
        }
        is StardustLevelEvidence.Invalid -> ConstraintEvaluation(
            "power_up_cost", observed = true, status = ConstraintStatus.MATCHED,
            matched = emptySet(), eliminated = witnessedRows, unresolved = unwitnessedRows,
            detail = "anchored stardust cost is no displayed power-up value (invalid): " +
                "contradicts every same-witness numeric row and never becomes a level"
        )
        is StardustLevelEvidence.Unsupported -> ConstraintEvaluation.unsupported("power_up_cost",
            "typed stardust evidence is unsupported under modeled mechanics: never support, never eliminate")
        is StardustLevelEvidence.Missing, is StardustLevelEvidence.Unreadable ->
            ConstraintEvaluation.notObserved("power_up_cost",
                "anchored power-up stardust not observed or unreadable")
        is StardustLevelEvidence.Conflict -> ConstraintEvaluation.unsupported("power_up_cost",
            "conflicting anchored stardust observations fail closed before evaluation")
    }
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
    val positiveBasis = evaluations.drop(1)
        .filter { it.observed && it.matched.any { row -> row.species in survivingSpecies } }
        .map { it.name }
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

private fun reasonFor(outcome: EvaluationOutcome, anyObserved: Boolean): String = when (outcome) {
    EvaluationOutcome.UNIQUE_SUPPORTED -> "independent_family_profile"
    EvaluationOutcome.AMBIGUOUS -> "family_profile_ambiguous"
    EvaluationOutcome.CONTRADICTION -> "family_profile_contradiction"
    EvaluationOutcome.INSUFFICIENT_EVIDENCE ->
        if (!anyObserved) "cp_or_power_up_cost_missing" else "identity_insufficient_evidence"
    EvaluationOutcome.UNSUPPORTED_MECHANIC -> "identity_unsupported_mechanic"
}

private fun toResult(evaluation: CandidateEvaluation, revision: String?): FamilySpeciesResolver.Result {
    val candidates = evaluation.survivingCandidates.map { it.species }.toSet()
    val forms = SameSpeciesFormDecision.fromEvaluation(evaluation, revision)
    return when (evaluation.outcome) {
        EvaluationOutcome.UNIQUE_SUPPORTED ->
            FamilySpeciesResolver.Result(evaluation.acceptedSpecies, candidates, evaluation.acceptanceReason!!, forms)
        EvaluationOutcome.CONTRADICTION ->
            FamilySpeciesResolver.Result(null, emptySet(), evaluation.acceptanceReason!!)
        else -> FamilySpeciesResolver.Result(null, candidates, evaluation.acceptanceReason!!, forms)
    }
}

// Evidence semantics for one supported constraint across the candidate rows.
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
