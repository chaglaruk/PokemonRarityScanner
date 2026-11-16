package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.FullVariantMatch

/** Scopes Shiny references only when the full matcher resolved a form for the locked species. */
internal object ShinyReferenceScope {
    fun resolvedFormSpriteKey(fullMatch: FullVariantMatch?, lockedSpecies: String?): String? = fullMatch
        ?.takeIf {
            !lockedSpecies.isNullOrBlank() &&
                it.resolvedForm &&
                it.finalSpecies.equals(lockedSpecies, ignoreCase = true) &&
                it.winnerSpecies.equals(lockedSpecies, ignoreCase = true)
        }
        ?.finalSpriteKey
        ?.takeIf { it.isNotBlank() }
}
