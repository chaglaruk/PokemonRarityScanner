package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.FullVariantCandidate

object FullVariantScoring {
    private const val STRONG_SECONDARY_COSTUME_PROMOTION = 0.16f

    fun rankScore(candidate: FullVariantCandidate, finalSpecies: String): Float {
        var score = 1f - candidate.matchScore.coerceIn(0f, 1f)
        if (candidate.species.equals(finalSpecies, ignoreCase = true)) {
            score += 0.25f
        }
        if (candidate.source == "authoritative_species_date") {
            score += 0.20f
        }
        if (candidate.source == "authoritative_live_species_event") {
            score += 0.18f
        }
        if (candidate.eventLabel != null) {
            score += 0.05f
        }
        if (candidate.source == "classifier_species" && candidate.variantClass != "base") {
            score += 0.10f
            score += candidate.classifierConfidence.coerceIn(0f, 1f) * 0.15f
        }
        if (candidate.source == "classifier_species_authoritative_remap" && candidate.variantClass != "base") {
            score += 0.16f
            score += candidate.classifierConfidence.coerceIn(0f, 1f) * 0.12f
        }
        if (candidate.source == "classifier_species_secondary_authoritative_remap" && candidate.variantClass != "base") {
            score += 0.14f
            score += candidate.classifierConfidence.coerceIn(0f, 1f) * 0.10f
        }
        if (candidate.source == "classifier_global_authoritative_remap" && candidate.variantClass != "base") {
            score += 0.08f
            score += candidate.classifierConfidence.coerceIn(0f, 1f) * 0.08f
        }
        if (candidate.source == "classifier_family_authoritative_remap" && candidate.variantClass != "base") {
            score += 0.12f
            score += candidate.classifierConfidence.coerceIn(0f, 1f) * 0.10f
        }
        if (isStrongSecondaryCostumeCandidate(candidate)) {
            score += STRONG_SECONDARY_COSTUME_PROMOTION
        }
        if (!candidate.rescueKind.isNullOrBlank()) {
            score -= if (isStrongSecondaryCostumeCandidate(candidate)) 0.03f else 0.10f
        }
        if (
            candidate.source == "authoritative_family_costume_support" &&
            candidate.species.equals(finalSpecies, ignoreCase = true) &&
            candidate.isCostumeLike
        ) {
            score += 0.18f
        }
        return score
    }

    private fun isStrongSecondaryCostumeCandidate(candidate: FullVariantCandidate): Boolean {
        return candidate.source == "classifier_species_secondary_non_base" &&
            candidate.variantClass == "costume" &&
            candidate.isCostumeLike &&
            candidate.classifierConfidence >= 0.40f &&
            candidate.matchScore <= 0.50f
    }
}
