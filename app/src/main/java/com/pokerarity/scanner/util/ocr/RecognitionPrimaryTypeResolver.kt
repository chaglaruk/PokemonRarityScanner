package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus

/** Resolves a primary display type only when the pinned profiles compatible with the identity agree. */
internal object RecognitionPrimaryTypeResolver {
    fun resolve(snapshot: RecognitionSnapshot, identity: RecognitionIdentity): String? {
        val species = identity.canonicalSpecies
            ?.takeIf { identity.speciesStatus == RecognitionSpeciesStatus.KNOWN && it.isNotBlank() }
            ?: return null
        val compatibleProfiles = SameSpeciesFormDecision.profilesSupportedByIdentity(
            snapshot.forSpecies(species),
            identity
        )
        val primaryTypes = compatibleProfiles.mapNotNull { it.types.firstOrNull() }.toSet()
        return primaryTypes.singleOrNull()
    }
}
