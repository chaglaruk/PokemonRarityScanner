package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.FullVariantCandidate

object FullVariantConstraints {
    fun keep(candidate: FullVariantCandidate, finalSpecies: String): Boolean {
        return candidate.species.equals(finalSpecies, ignoreCase = true) ||
            candidate.source == "authoritative_species_date"
    }
}
