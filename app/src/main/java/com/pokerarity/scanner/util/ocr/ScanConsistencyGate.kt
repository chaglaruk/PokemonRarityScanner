package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.util.Log
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.PokemonFamilyRegistry
import com.pokerarity.scanner.data.repository.RarityCalculator

class ScanConsistencyGate(
    private val context: Context,
    private val rarityCalculator: RarityCalculator
) {

    private val textParser = TextParser(context)

    data class Decision(
        val pokemon: PokemonData,
        val shouldRetry: Boolean,
        val reason: String
    )

    fun evaluate(authoritative: PokemonData, candidate: PokemonData): Decision {
        val authoritativeSpecies = authoritative.realName ?: authoritative.name
        val candidateSpecies = candidate.realName ?: candidate.name
        val candySpecies = candidate.candyName ?: authoritative.candyName

        if (candidateSpecies.isNullOrBlank() || candidateSpecies.equals("Unknown", ignoreCase = true)) {
            return when {
                !authoritativeSpecies.isNullOrBlank() && !authoritativeSpecies.equals("Unknown", ignoreCase = true) ->
                    Decision(correctSpecies(candidate, authoritativeSpecies), false, "fallback_authoritative_species")
                !candySpecies.isNullOrBlank() ->
                    Decision(correctSpecies(candidate, candySpecies), false, "fallback_candy_species")
                else ->
                    Decision(candidate, true, "unknown_species")
            }
        }

        val authoritativeAnchor = hasStrongAuthoritativeAnchor(authoritative, authoritativeSpecies)
        val candidateFit = score(candidate, candidateSpecies)
        val authoritativeFit = score(candidate, authoritativeSpecies)

        if (!candySpecies.isNullOrBlank()) {
            if (PokemonFamilyRegistry.familySize(context, candySpecies) == 1 &&
                !candidateSpecies.equals(candySpecies, ignoreCase = true)
            ) {
                return Decision(
                    correctSpecies(candidate, candySpecies),
                    false,
                    "corrected_to_unique_candy_species"
                )
            }

            val candyFamilyMembers = PokemonFamilyRegistry.getFamilyMembers(context, candySpecies)
            val candidateMatchesCandyFamily = PokemonFamilyRegistry.isSameFamily(context, candidateSpecies, candySpecies)
            if (!candidateMatchesCandyFamily) {
                if (!authoritativeSpecies.isNullOrBlank() &&
                    PokemonFamilyRegistry.isSameFamily(context, authoritativeSpecies, candySpecies)
                ) {
                    return Decision(
                        correctSpecies(candidate, authoritativeSpecies),
                        false,
                        "corrected_to_authoritative_candy_family"
                    )
                }

                val bestCandyFamilyCandidate = candyFamilyMembers
                    .mapNotNull { species -> score(candidate, species)?.let { species to it } }
                    .maxByOrNull { it.second.score }

                if (bestCandyFamilyCandidate != null) {
                    val bestSpecies = bestCandyFamilyCandidate.first
                    val bestFit = bestCandyFamilyCandidate.second
                    val currentScore = candidateFit?.score ?: 0.0
                    if (bestFit.score >= 0.34 && bestFit.score >= currentScore + 0.08) {
                        return Decision(
                            correctSpecies(candidate, bestSpecies),
                            false,
                            "corrected_to_candy_family_best_fit"
                        )
                    }
                }

                return Decision(candidate, true, "cross_family_conflict")
            }
        }

        if (!authoritativeSpecies.isNullOrBlank() &&
            !authoritativeSpecies.equals(candidateSpecies, ignoreCase = true) &&
            !PokemonFamilyRegistry.isSameFamily(context, authoritativeSpecies, candidateSpecies) &&
            authoritativeAnchor
        ) {
            val authoritativeScore = authoritativeFit?.score ?: 0.0
            val candidateScore = candidateFit?.score ?: 0.0
            if (authoritativeScore >= candidateScore - 0.04 || candidateScore < 0.28) {
                return Decision(
                    correctSpecies(candidate, authoritativeSpecies),
                    false,
                    "restored_authoritative_species"
                )
            }
        }

        if (!authoritativeSpecies.isNullOrBlank() && candidateFit != null && authoritativeFit != null) {
            if (candidateFit.score < 0.20 && authoritativeFit.score >= candidateFit.score + 0.10) {
                return Decision(
                    correctSpecies(candidate, authoritativeSpecies),
                    false,
                    "replaced_low_fit_candidate"
                )
            }
        }

        return Decision(candidate, false, "accepted")
    }

    private fun score(pokemon: PokemonData, species: String?): RarityCalculator.SpeciesFit? {
        if (species.isNullOrBlank()) return null
        return rarityCalculator.scoreSpeciesFit(pokemon, species)
    }

    private fun correctSpecies(pokemon: PokemonData, species: String): PokemonData {
        if (pokemon.name.equals(species, ignoreCase = true) && pokemon.realName.equals(species, ignoreCase = true)) {
            return pokemon
        }
        Log.d("ScanConsistencyGate", "Correcting species to $species")
        return pokemon.copy(
            name = species,
            realName = species
        )
    }

    private fun hasStrongAuthoritativeAnchor(authoritative: PokemonData, species: String?): Boolean {
        if (species.isNullOrBlank()) return false
        val rawName = extractRawField(authoritative.rawOcrText, "Name")
        val rawFallback = extractRawField(authoritative.rawOcrText, "NameHC")
        val parsed = textParser.parseName(rawName) ?: textParser.parseName(rawFallback)
        return parsed.equals(species, ignoreCase = true)
    }

    private fun extractRawField(rawOcrText: String, key: String): String {
        return rawOcrText.split("|")
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(":")
            ?.trim()
            .orEmpty()
    }
}
