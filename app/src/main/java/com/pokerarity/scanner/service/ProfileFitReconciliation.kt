package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.ProfileTupleFeasibility
import com.pokerarity.scanner.util.ocr.RecognitionSnapshot
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import com.pokerarity.scanner.util.ocr.SpeciesRefinerConfig
import kotlin.math.abs

private const val ARC_LEVELS_PER_UNIT = 49.0

/**
 * Generic profile-fit reconciliation for [ScanManager.profileStatus].
 *
 * The anchored observation path established its evidence against the
 * RecognitionProfiles rows, so reconciliation must check the same per-form
 * rows and float32 multiplier domain, never the legacy single baseStats row.
 * Paths without a recognition observation keep the legacy evaluator. This is
 * reconciliation evidence only: it maps feasibility to a profile status and
 * never creates a new hard-positive species authority.
 */
internal fun resolveProfileFit(
    pokemon: PokemonData,
    species: String,
    rarityCalculator: RarityCalculator
): SpeciesProfileStatus =
    if (pokemon.recognitionObservation != null) {
        resolveRecognitionProfileFit(pokemon, species, rarityCalculator)
    } else {
        resolveLegacyProfileFit(pokemon, species, rarityCalculator)
    }

private fun resolveLegacyProfileFit(
    pokemon: PokemonData,
    species: String,
    rarityCalculator: RarityCalculator
): SpeciesProfileStatus {
    val fit = rarityCalculator.evaluateSpeciesProfile(pokemon, species)
        ?: return SpeciesProfileStatus.INDETERMINATE
    return when {
        !fit.hpPossible -> SpeciesProfileStatus.IMPOSSIBLE
        !fit.jointCpHpPossible -> SpeciesProfileStatus.CONTRADICTORY
        pokemon.arcLevel?.let { !it.isFinite() || it !in 0f..1f } == true ->
            SpeciesProfileStatus.CONTRADICTORY
        fit.minJointArcDiff?.let { it >= SpeciesRefinerConfig.default().arcDiffThreshold } == true ->
            SpeciesProfileStatus.CONTRADICTORY
        else -> SpeciesProfileStatus.COMPATIBLE
    }
}

private fun resolveRecognitionProfileFit(
    pokemon: PokemonData,
    species: String,
    rarityCalculator: RarityCalculator
): SpeciesProfileStatus {
    // Fail closed when the snapshot is unavailable: no rows -> never a hard acceptance.
    val snapshot = rarityCalculator.recognitionSnapshot
    val rows = snapshot?.forSpecies(species).orEmpty()
    val cpMultipliers = snapshot?.cpMultipliers ?: emptyMap()
    val jointLevels = rows.flatMap { row ->
        ProfileTupleFeasibility
            .legalWitnesses(row.stats, pokemon.cp, pokemon.maxHp, cpMultipliers)
            .map { it.effectiveLevel }
    }
    val negative = when {
        rows.isEmpty() -> SpeciesProfileStatus.INDETERMINATE
        jointLevels.isEmpty() && !hasMaximumHpWitness(pokemon, rows, cpMultipliers) ->
            SpeciesProfileStatus.IMPOSSIBLE
        jointLevels.isEmpty() -> SpeciesProfileStatus.CONTRADICTORY
        arcOutsideValidDomain(pokemon.arcLevel) -> SpeciesProfileStatus.CONTRADICTORY
        else -> null
    }
    return negative ?: arcVerdict(pokemon.arcLevel, jointLevels)
}

private fun hasMaximumHpWitness(
    pokemon: PokemonData,
    rows: List<RecognitionSnapshot.Profile>,
    cpMultipliers: Map<Double, Double>
): Boolean = rows.any { row ->
    ProfileTupleFeasibility
        .legalWitnesses(row.stats, null, pokemon.maxHp, cpMultipliers)
        .isNotEmpty()
}

private fun arcOutsideValidDomain(arcLevel: Float?): Boolean =
    arcLevel != null && (!arcLevel.isFinite() || arcLevel !in 0f..1f)

private fun arcVerdict(arcLevel: Float?, jointLevels: List<Double>): SpeciesProfileStatus {
    if (arcLevel == null) return SpeciesProfileStatus.COMPATIBLE
    val estimatedLevel = arcLevel * ARC_LEVELS_PER_UNIT + 1.0
    val minJointArcDiff = jointLevels.minOf { abs(it - estimatedLevel) }
    return if (minJointArcDiff >= SpeciesRefinerConfig.default().arcDiffThreshold) {
        SpeciesProfileStatus.CONTRADICTORY
    } else {
        SpeciesProfileStatus.COMPATIBLE
    }
}
