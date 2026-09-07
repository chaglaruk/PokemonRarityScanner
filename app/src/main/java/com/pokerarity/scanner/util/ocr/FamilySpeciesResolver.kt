package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator

/** The editable nickname never chooses between independently plausible family members. */
internal class FamilySpeciesResolver(
    private val profiles: RecognitionProfiles,
    private val calculator: RarityCalculator
) {
    data class Observation(
        val candySpecies: String?,
        val exactCandyLabel: Boolean,
        val powerUpStardust: Int? = null,
        val anchoredPowerUpCost: Boolean = false,
        val types: Set<String>? = null
    )
    data class Result(val species: String?, val candidates: Set<String>, val reason: String)

    fun resolve(pokemon: PokemonData): Result {
        val observation = pokemon.recognitionObservation
            ?: return Result(null, emptySet(), "screen_observation_missing")
        if (!observation.detailScreen) return Result(null, emptySet(), "detail_screen_unconfirmed")
        if (observation.numericConflict) return Result(null, emptySet(), "numeric_observations_conflict")
        return resolve(pokemon, Observation(observation.candySpecies, observation.candySpecies != null,
            observation.powerUpStardust, observation.powerUpStardust != null, observation.types))
    }

    fun resolve(pokemon: PokemonData, observed: Observation): Result {
        if (!observed.exactCandyLabel || observed.candySpecies == null) return Result(null, emptySet(), "candy_label_missing")
        val family = profiles.forCandy(observed.candySpecies)
        if (family.isEmpty()) return Result(null, emptySet(), "family_metadata_missing")
        if (pokemon.maxHp == null) {
            // A uniquely typed family member can be identified after the HP label
            // scrolls offscreen. No numeric field is synthesized from that identity.
            val typed = family.filter { !observed.types.isNullOrEmpty() && it.types == observed.types }
                .map { it.species }.toSet()
            return if (pokemon.cp == null && typed.size == 1) Result(typed.single(), typed, "independent_family_profile")
                else Result(null, typed, "maximum_hp_missing")
        }
        val cost = observed.powerUpStardust.takeIf { observed.anchoredPowerUpCost }
        if (pokemon.cp == null && cost == null && observed.types.isNullOrEmpty()) {
            return Result(null, emptySet(), "cp_or_power_up_cost_missing")
        }
        val candidates = family.filter { profile ->
            (observed.types.isNullOrEmpty() || profile.types == observed.types) &&
                calculator.matchingProfileLevels(pokemon, profile.stats, profiles.cpMultipliers).any { level -> cost == null || costMatches(cost, level) }
        }.map { it.species }.toSet()
        return when (candidates.size) {
            0 -> Result(null, candidates, "family_profile_contradiction")
            1 -> Result(candidates.single(), candidates, "independent_family_profile")
            else -> Result(null, candidates, "family_profile_ambiguous")
        }
    }

    companion object {
        // Ordinary power-up tiers. Possible status discounts are included for every
        // candidate unless separately established, so a weak shiny/lucky detector
        // cannot remove the true species. Inventory stardust never enters this path.
        private val costs = listOf(200, 400, 600, 800, 1000, 1300, 1600, 1900, 2200, 2500,
            3000, 3500, 4000, 4500, 5000, 6000, 7000, 8000, 9000, 10000, 11000, 12000, 13000, 14000, 15000)
        private val modifiers = listOf(1.0, .5, .9, .45, 1.2)
        private fun costMatches(observed: Int, level: Double): Boolean {
            // The active Best Buddy bonus changes CP/HP, but not the underlying upgrade tier.
            return listOf(level, level - 1).filter { it >= 1 && it < 50 }.any { baseLevel ->
                val base = costs[((baseLevel - 1) / 2).toInt()]
                modifiers.any { kotlin.math.ceil(base * it).toInt() == observed }
            }
        }
    }
}
