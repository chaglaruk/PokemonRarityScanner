package com.pokerarity.scanner.data.model

/**
 * Phase 2E explicit recognition-result contract (plan section 6.6).
 *
 * One immutable result type that states, for a finished recognition pass:
 * - species: KNOWN (canonical species + bounded provenance) or UNKNOWN;
 * - form: KNOWN, AMBIGUOUS (bounded surviving alternatives) or UNKNOWN;
 * - variant flags: explicit [RecognitionTruth] tri-state instead of booleans that
 *   collapse "not detected" and "positively absent" into the same `false`.
 *
 * Contract rules:
 * - The KNOWN species is established exclusively by the hard recognition-authority
 *   pipeline (independent family profile / exact canonical / reviewed alias evidence).
 *   Weak variant classifiers never appear here as species authority.
 * - UNKNOWN species carries no species value at all; "Unknown" is never stored as an
 *   authoritative species inside the contract (compatibility UI may still display it).
 * - Absence of non-base form evidence is NOT base-form KNOWN; a base form can only be
 *   KNOWN when trusted explicit evidence positively establishes it.
 * - [RecognitionTruth.FALSE] is used only where the responsible detector has a reliable
 *   explicit-negative contract; a detector merely failing to fire is [RecognitionTruth.UNKNOWN].
 */
enum class RecognitionTruth { TRUE, FALSE, UNKNOWN }

enum class RecognitionSpeciesStatus { KNOWN, UNKNOWN }

enum class RecognitionFormStatus { KNOWN, AMBIGUOUS, UNKNOWN }

/**
 * Bounded record that weak classifier/variant evidence disagreed with the locked species.
 * The locked species is never rewritten from this; it exists for diagnostics only.
 */
data class ClassifierSpeciesMismatch(
    val reason: String,
    val source: String,
    val suggestedSpecies: String,
    val lockedSpecies: String
)

data class RecognitionIdentity(
    val speciesStatus: RecognitionSpeciesStatus,
    /** Canonical species with snapshot display casing; non-null only when KNOWN. */
    val canonicalSpecies: String? = null,
    /** Bounded provenance code of the hard authority behind a KNOWN species. */
    val speciesAuthority: String? = null,
    val speciesReasonCodes: List<String> = emptyList(),
    val formStatus: RecognitionFormStatus,
    /** Trusted form label/identifier; non-null only when KNOWN. */
    val knownForm: String? = null,
    /** Bounded provenance code of the form evidence. */
    val formProvenance: String? = null,
    /** Surviving plausible forms; non-empty only when AMBIGUOUS, bounded. */
    val ambiguousForms: List<String> = emptyList(),
    val formReasonCodes: List<String> = emptyList(),
    val shiny: RecognitionTruth,
    val shadow: RecognitionTruth,
    val purified: RecognitionTruth,
    val lucky: RecognitionTruth,
    val costume: RecognitionTruth,
    val specialForm: RecognitionTruth,
    val xxs: RecognitionTruth,
    val xxl: RecognitionTruth,
    val locationCard: RecognitionTruth,
    /** Weak-evidence disagreement with the locked species, if any. */
    val classifierMismatch: ClassifierSpeciesMismatch? = null
) {
    companion object {
        /** Maximum number of surviving form alternatives carried by the contract. */
        const val MAX_FORM_ALTERNATIVES = 5

        /** Mismatch reason when the scoped/global variant classifier claims another species. */
        const val MISMATCH_REASON_CLASSIFIER = "classifier_species_mismatch"

        /** Mismatch reason when the full-variant winner belongs to another species. */
        const val MISMATCH_REASON_FULL_VARIANT = "full_variant_species_mismatch"
    }
}

/**
 * One-way compatibility adapter from the explicit tri-state contract to the legacy
 * [VisualFeatures] booleans consumed by rarity/UI/storage code.
 *
 * Mapping: TRUE -> true; FALSE and UNKNOWN -> false. FALSE and UNKNOWN are deliberately
 * indistinguishable in the compatibility view, so a compatibility `false` can never be
 * read back as positive evidence of absence — the explicit identity stays the only place
 * where tri-state authority lives (no circular authority).
 */
object RecognitionIdentityCompat {

    fun toBoolean(truth: RecognitionTruth): Boolean = truth == RecognitionTruth.TRUE

    /**
     * Compatibility booleans for a finished identity. The returned features are for
     * legacy consumers (UI extras, history persistence) only; they must never be fed
     * back into identity construction.
     */
    fun toVisualFeatures(identity: RecognitionIdentity, confidence: Float = 0f): VisualFeatures =
        VisualFeatures(
            isShiny = toBoolean(identity.shiny),
            isShadow = toBoolean(identity.shadow),
            isPurified = toBoolean(identity.purified),
            isLucky = toBoolean(identity.lucky),
            hasCostume = toBoolean(identity.costume),
            hasSpecialForm = toBoolean(identity.specialForm),
            isXXS = toBoolean(identity.xxs),
            isXXL = toBoolean(identity.xxl),
            hasLocationCard = toBoolean(identity.locationCard),
            confidence = confidence
        )
}
