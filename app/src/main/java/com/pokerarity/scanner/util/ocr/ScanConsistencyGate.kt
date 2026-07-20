package com.pokerarity.scanner.util.ocr

import android.content.Context
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.PokemonFamilyRegistry
import com.pokerarity.scanner.data.repository.RarityCalculator

internal class ScanConsistencyGate(
    private val context: Context,
    private val rarityCalculator: RarityCalculator
) {

    data class Decision(
        val pokemon: PokemonData,
        val shouldRetry: Boolean,
        val reason: String
    )

    fun evaluate(
        authoritative: PokemonData,
        candidate: PokemonData,
        speciesEvidence: SpeciesEvidence = SpeciesEvidence.failClosed()
    ): Decision {
        val authoritativeSpecies = speciesEvidence.selectedCanonicalSpecies
        val candidateSpecies = candidate.realName ?: candidate.name
        val candySpecies = candidate.candyName ?: authoritative.candyName
        val authoritativeDataSpecies = authoritative.realName ?: authoritative.name
        val hardAuthority = isHardAuthority(speciesEvidence, authoritativeSpecies, authoritativeDataSpecies)
        val hardSpecies = authoritativeSpecies?.takeIf { hardAuthority }
        val candidateUnknown = candidateIsUnknown(candidateSpecies)
        val crossFamilyWithCandy = !candidateUnknown &&
            !candySpecies.isNullOrBlank() &&
            isCrossFamily(candidateSpecies, candySpecies)
        val result = when {
            crossFamilyWithCandy ->
                Decision(candidate, true, SpeciesEvidenceReason.CROSS_FAMILY_CONFLICT)
            candidateUnknown && hardSpecies != null ->
                Decision(correctSpecies(candidate, hardSpecies), false, "fallback_authoritative_species")
            candidateUnknown ->
                Decision(candidate, true, SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_AUTHORITY)
            hardSpecies == null ->
                Decision(candidate, true, SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_AUTHORITY)
            hardSpecies.equals(candidateSpecies, ignoreCase = true) ->
                Decision(candidate, false, "accepted")
            isCrossFamily(hardSpecies, candidateSpecies) ->
                Decision(candidate, true, SpeciesEvidenceReason.CROSS_FAMILY_CONFLICT)
            else ->
                resolveFitDecision(candidate, candidateSpecies, hardSpecies)
        }
        return result
    }

    private fun isHardAuthority(
        speciesEvidence: SpeciesEvidence,
        authoritativeSpecies: String?,
        authoritativeDataSpecies: String?
    ): Boolean {
        val compatibleProfile = speciesEvidence.profileStatus == SpeciesProfileStatus.COMPATIBLE
        val notConflicted = !speciesEvidence.authorityConflict && !speciesEvidence.candidatesClose
        val matchesData = !authoritativeSpecies.isNullOrBlank() &&
            authoritativeSpecies.equals(authoritativeDataSpecies, ignoreCase = true)
        return speciesEvidence.hasHardAuthority && compatibleProfile &&
            speciesEvidence.observationsAgree && notConflicted && matchesData
    }

    private fun candidateIsUnknown(species: String?): Boolean =
        species.isNullOrBlank() || species.equals("Unknown", ignoreCase = true)

    private fun isCrossFamily(speciesA: String?, speciesB: String?): Boolean =
        !PokemonFamilyRegistry.isSameFamily(context, speciesA.orEmpty(), speciesB.orEmpty())

    private fun resolveFitDecision(
        candidate: PokemonData,
        candidateSpecies: String?,
        hardSpecies: String?
    ): Decision {
        val candidateFit = score(candidate, candidateSpecies)
        val authoritativeFit = score(candidate, hardSpecies)
        if (candidateFit != null && authoritativeFit != null) {
            val authoritativeScore = authoritativeFit.score
            val candidateScore = candidateFit.score
            if (authoritativeScore >= candidateScore - 0.08 || candidateScore < 0.45) {
                return Decision(
                    correctSpecies(candidate, hardSpecies),
                    false,
                    "restored_authoritative_family_species"
                )
            }
        }
        return Decision(candidate, true, SpeciesEvidenceReason.AUTHORITY_CONFLICT)
    }

    private fun score(pokemon: PokemonData, species: String?): RarityCalculator.SpeciesFit? {
        if (species.isNullOrBlank()) return null
        return rarityCalculator.scoreSpeciesFit(pokemon, species)
    }

    private fun correctSpecies(pokemon: PokemonData, species: String?): PokemonData {
        if (pokemon.name.equals(species, ignoreCase = true) && pokemon.realName.equals(species, ignoreCase = true)) {
            return pokemon
        }
        return pokemon.copy(
            name = species,
            realName = species
        )
    }
}
