package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.FullVariantMatch

/** Provides a signature key only for a resolved form of the already locked species. */
internal object ShinyReferenceScope {
    fun resolvedFormSpriteKey(fullMatch: FullVariantMatch?, lockedSpecies: String?): String? = fullMatch
        ?.takeIf {
            !lockedSpecies.isNullOrBlank() &&
                it.resolvedForm &&
                it.finalSpecies.equals(lockedSpecies, ignoreCase = true) &&
                it.winnerSpecies.equals(lockedSpecies, ignoreCase = true)
        }
        ?.finalSpriteKey
        ?.takeIf(String::isNotBlank)
}
