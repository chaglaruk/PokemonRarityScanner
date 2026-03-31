package com.pokerarity.scanner.util.vision

object FullVariantSeedSelection {
    fun chooseSpeciesSeed(
        finalSpecies: String,
        speciesMatch: VariantPrototypeClassifier.MatchResult?,
        resolvedMatch: VariantPrototypeClassifier.MatchResult?
    ): VariantPrototypeClassifier.MatchResult? {
        return if (resolvedMatch?.species.equals(finalSpecies, ignoreCase = true)) {
            resolvedMatch
        } else {
            speciesMatch
        }
    }
}
