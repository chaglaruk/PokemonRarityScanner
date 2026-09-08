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
        val types: Set<String>? = null,
        val evolutionCandyCost: Int? = null
    )
    data class Result(val species: String?, val candidates: Set<String>, val reason: String)

    fun resolve(pokemon: PokemonData): Result {
        val observation = pokemon.recognitionObservation
            ?: return Result(null, emptySet(), "screen_observation_missing")
        if (!observation.detailScreen) return Result(null, emptySet(), "detail_screen_unconfirmed")
        if (observation.numericConflict) return Result(null, emptySet(), "numeric_observations_conflict")
        return resolve(pokemon, Observation(observation.candySpecies, observation.candySpecies != null,
            observation.powerUpStardust, observation.powerUpStardust != null, observation.types, observation.evolutionCandyCost))
    }

    fun resolve(pokemon: PokemonData, observed: Observation): Result {
        if (!observed.exactCandyLabel || observed.candySpecies == null) return Result(null, emptySet(), "candy_label_missing")
        val family = profiles.forCandy(observed.candySpecies)
        if (family.isEmpty()) return Result(null, emptySet(), "family_metadata_missing")
        if (pokemon.maxHp == null) {
            // A uniquely typed family member can be identified after the HP label
            // scrolls offscreen. No numeric field is synthesized from that identity.
            val typedProfiles = family.filter { !observed.types.isNullOrEmpty() && it.types == observed.types }
            val typed = typedProfiles.map { it.species }.toSet()
            if (pokemon.cp == null && typed.size == 1) return Result(typed.single(), typed, "independent_family_profile")
            // A visible ordinary evolution action and its exact candy price can
            // separate same-type family members even after both numbers scroll off.
            // Unknown metadata remains possible and may never grant authority.
            val evolutionCost = observed.evolutionCandyCost
            if (pokemon.cp == null && evolutionCost != null && evolutionCost in 0..1000) {
                val possible = typedProfiles.filter { it.evolutionCandyCosts == null || evolutionCost in it.evolutionCandyCosts }
                val candidates = possible.map { it.species }.toSet()
                if (candidates.size == 1 && possible.none { it.evolutionCandyCosts == null }) {
                    return Result(candidates.single(), candidates, "independent_family_profile")
                }
                return Result(null, candidates, "evolution_family_ambiguous_or_unsupported")
            }
            return Result(null, typed, "maximum_hp_missing")
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
        private const val SHADOW_MODIFIER = 1.2
        private const val SHADOW_MODIFIER_FLOAT = 1.2f
        private val modifiers = listOf(1.0, .5, .9, .45, SHADOW_MODIFIER)

        private fun canonicalDisplayedCosts(base: Int, modifier: Double): Set<Int> {
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

        private fun costMatches(observed: Int, level: Double): Boolean {
            // The active Best Buddy bonus changes CP/HP, but not the underlying upgrade tier.
            return listOf(level, level - 1).filter { it >= 1 && it < 50 }.any { baseLevel ->
                val base = costs[((baseLevel - 1) / 2).toInt()]
                modifiers.any { modifier -> observed in canonicalDisplayedCosts(base, modifier) }
            }
        }
    }
}
