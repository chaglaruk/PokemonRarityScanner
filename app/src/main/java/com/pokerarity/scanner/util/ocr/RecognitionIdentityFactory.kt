package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.ClassifierSpeciesMismatch
import com.pokerarity.scanner.data.model.RecognitionFormStatus
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.data.model.VisualFeatures

/**
 * Phase 2E pure builder of the explicit [RecognitionIdentity] contract.
 *
 * Species authority: the factory accepts a species as KNOWN only from the already
 * established hard recognition authority ([SpeciesEvidence.hasHardAuthority] —
 * independent family profile, exact canonical or reviewed alias) confirmed by the
 * accepted scan decision. Weak classifier / full-variant species suggestions are never
 * promoted to species authority; a disagreement is recorded diagnostically instead.
 *
 * Form authority: trusted owned form labels carried by the species resolver trace
 * (authoritative variant metadata within the locked species) plus the Phase 2D
 * snapshot's supported form rows. A single surviving trusted label is KNOWN; two or
 * more surviving labels, or a multi-row species without a distinguishing label, are
 * AMBIGUOUS with bounded alternatives; everything else is UNKNOWN. Absence of
 * non-base evidence never becomes KNOWN base form.
 *
 * Variant tri-state: TRUE only on positive trustworthy evidence; FALSE only where the
 * responsible detector has a reliable explicit-negative contract (Phase 2 trained shiny
 * demotion; a distinct OCR size-tag read excluding XXS/XXL); UNKNOWN otherwise. No
 * negative authority is manufactured for shadow, purified, lucky, costume, special form
 * or location card, whose current detectors prove presence only.
 */
internal object RecognitionIdentityFactory {

    /** Reason codes carried by an UNKNOWN form result. */
    const val FORM_REASON_NO_LABEL = "form_label_missing"

    /** Provenance code of a KNOWN form established by one trusted owned label. */
    const val FORM_PROVENANCE_OWNED_LABEL = "owned_form_label"
    const val FORM_PROVENANCE_OWNED_LABELS = "owned_form_labels"

    /** Classifier evidence sources recorded in a species mismatch. */
    const val MISMATCH_SOURCE_CLASSIFIER = "variant_classifier"
    const val MISMATCH_SOURCE_FULL_VARIANT = "full_variant_match"

    /** Distinct OCR size-tag values that positively exclude XXS (and accordingly XXL). */
    private val sizeTagsExcludingXxs = setOf("XS", "XL", "XXL")
    private val sizeTagsExcludingXxl = setOf("XXS", "XS", "XL")

    /** Upper bound for carried species reason codes; keeps diagnostics bounded. */
    private const val MAX_SPECIES_REASON_CODES = 8

    /**
     * All inputs are pre-collected runtime values; the factory performs no I/O, no
     * additional OCR/classifier pass and no snapshot reparsing.
     */
    internal data class Input(
        val speciesEvidence: SpeciesEvidence,
        val scanAccepted: Boolean,
        /** Species accepted by the Phase 2 authority gate; null when the gate blocked. */
        val lockedSpecies: String?,
        /** Species claimed by the resolved scoped/global variant classifier, if any. */
        val classifierSpecies: String?,
        /** Species of the applied full-variant winner, if a winner exists. */
        val fullMatchWinnerSpecies: String?,
        /** Resolver form candidates for the accepted species (trusted label evidence). */
        val formCandidates: List<FormCandidateDiagnostic>,
        /** Final merged compatibility features (positive promotions included). */
        val mergedFeatures: VisualFeatures,
        /** True only when the Phase 2 trained classifier demoted an existing shiny positive. */
        val phase2ShinyDemoted: Boolean,
        /** Distinct OCR size-tag read for this scan (XXS/XS/XL/XXL); null when not read. */
        val sizeTag: String?
    )

    fun build(input: Input): RecognitionIdentity {
        val species = buildSpecies(input)
        val form = buildForm(input, species)
        return RecognitionIdentity(
            speciesStatus = species.status,
            canonicalSpecies = species.canonicalSpecies,
            speciesAuthority = species.authority,
            speciesReasonCodes = species.reasonCodes,
            formStatus = form.status,
            knownForm = form.knownForm,
            formProvenance = form.provenance,
            ambiguousForms = form.ambiguousForms,
            formReasonCodes = form.reasonCodes,
            // Shiny tri-state: TRUE on any positive trustworthy evidence (visual
            // signatures, classifier promotions, OCR label — all folded into the merged
            // feature boolean); FALSE only through the Phase 2 trained classifier's
            // explicit negative contract (balanced per-species examples with a
            // demotion-grade margin); UNKNOWN when the detectors simply did not fire.
            shiny = when {
                input.mergedFeatures.isShiny -> RecognitionTruth.TRUE
                input.phase2ShinyDemoted -> RecognitionTruth.FALSE
                else -> RecognitionTruth.UNKNOWN
            },
            shadow = positiveOrUnknown(input.mergedFeatures.isShadow),
            // No purified detector exists anywhere in the current pipeline: the mechanic
            // is unsupported, so the tri-state can never honestly leave UNKNOWN.
            purified = RecognitionTruth.UNKNOWN,
            lucky = positiveOrUnknown(input.mergedFeatures.isLucky),
            costume = positiveOrUnknown(input.mergedFeatures.hasCostume),
            specialForm = positiveOrUnknown(input.mergedFeatures.hasSpecialForm),
            xxs = sizeTagTruth(input.sizeTag, sizeTagsExcludingXxs),
            xxl = sizeTagTruth(input.sizeTag, sizeTagsExcludingXxl),
            locationCard = positiveOrUnknown(input.mergedFeatures.hasLocationCard),
            classifierMismatch = buildMismatch(input, species.canonicalSpecies)
        )
    }

    private fun positiveOrUnknown(positive: Boolean): RecognitionTruth =
        if (positive) RecognitionTruth.TRUE else RecognitionTruth.UNKNOWN

    /** Trusted resolver form labels: bounded distinct display labels from variant metadata. */
    fun distinctTrustedFormLabels(
        formCandidates: List<FormCandidateDiagnostic>,
        lockedSpecies: String? = null
    ): List<String> =
        formCandidates
            .filter { candidate ->
                candidate.source == "authoritative_variant_db" &&
                    (lockedSpecies == null || candidate.species.equals(lockedSpecies, ignoreCase = true))
            }
            .map { it.form.trim() }
            .filter { it.isNotBlank() }
            // Defense in depth beyond the resolver's own label filter: only ordinary
            // form-label keywords may establish form identity; event/costume labels
            // must never become the canonical form.
            .filter(OrdinaryFormLabelFilter::matches)
            .distinctBy { it.lowercase().replace(Regex("[^a-z0-9]"), "") }
            .take(RecognitionIdentity.MAX_FORM_ALTERNATIVES)

    private data class SpeciesResult(
        val status: RecognitionSpeciesStatus,
        val canonicalSpecies: String?,
        val authority: String?,
        val reasonCodes: List<String>
    )

    private data class FormResult(
        val status: RecognitionFormStatus,
        val knownForm: String?,
        val provenance: String?,
        val ambiguousForms: List<String>,
        val reasonCodes: List<String>
    )

    private fun buildSpecies(input: Input): SpeciesResult {
        val evidence = input.speciesEvidence
        val canonical = evidence.selectedCanonicalSpecies
            ?.trim()
            ?.takeUnless { it.isBlank() || it.equals("Unknown", ignoreCase = true) }
        val gateAgrees = input.lockedSpecies != null &&
            canonical != null &&
            input.lockedSpecies.equals(canonical, ignoreCase = true)
        val known = input.scanAccepted && evidence.hasHardAuthority && gateAgrees
        return if (known) {
            SpeciesResult(
                status = RecognitionSpeciesStatus.KNOWN,
                canonicalSpecies = canonical,
                authority = speciesAuthorityCode(evidence.authority),
                reasonCodes = evidence.reasonCodes.take(MAX_SPECIES_REASON_CODES)
            )
        } else {
            SpeciesResult(
                status = RecognitionSpeciesStatus.UNKNOWN,
                canonicalSpecies = null,
                authority = null,
                reasonCodes = unknownSpeciesReasons(input, canonical, gateAgrees)
            )
        }
    }

    /** Bounded diagnostic reasons explaining why the species stayed UNKNOWN. */
    private fun unknownSpeciesReasons(
        input: Input,
        canonical: String?,
        gateAgrees: Boolean
    ): List<String> {
        val reasons = input.speciesEvidence.reasonCodes.take(MAX_SPECIES_REASON_CODES).toMutableList()
        if (!input.scanAccepted) reasons.add("scan_not_accepted")
        if (input.speciesEvidence.hasHardAuthority && canonical == null) reasons.add("species_value_missing")
        val hardEvidenceWithSpecies = input.speciesEvidence.hasHardAuthority && canonical != null
        when {
            hardEvidenceWithSpecies && input.lockedSpecies == null -> reasons.add("species_lock_missing")
            hardEvidenceWithSpecies && !gateAgrees -> reasons.add("gate_species_disagreement")
        }
        return reasons
    }

    private fun buildForm(input: Input, species: SpeciesResult): FormResult {
        val trustedLabels = distinctTrustedFormLabels(input.formCandidates, species.canonicalSpecies)
        return when {
            species.status != RecognitionSpeciesStatus.KNOWN -> FormResult(
                status = RecognitionFormStatus.UNKNOWN,
                knownForm = null,
                provenance = null,
                ambiguousForms = emptyList(),
                reasonCodes = listOf(FORM_REASON_NO_LABEL)
            )
            trustedLabels.size == 1 -> FormResult(
                status = RecognitionFormStatus.KNOWN,
                knownForm = trustedLabels.single(),
                provenance = FORM_PROVENANCE_OWNED_LABEL,
                ambiguousForms = emptyList(),
                reasonCodes = emptyList()
            )
            trustedLabels.size >= 2 -> FormResult(
                status = RecognitionFormStatus.AMBIGUOUS,
                knownForm = null,
                provenance = FORM_PROVENANCE_OWNED_LABELS,
                ambiguousForms = trustedLabels,
                reasonCodes = listOf("owned_form_labels_ambiguous")
            )
            else -> FormResult(
                status = RecognitionFormStatus.UNKNOWN,
                knownForm = null,
                provenance = null,
                ambiguousForms = emptyList(),
                // The Phase 2D snapshot's raw Game Master "forms" field mixes ordinary
                // forms with costumes/events and temporary evolutions. Without trusted
                // owned form-label evidence, Phase 2E must remain UNKNOWN rather than
                // manufacture canonical-form ambiguity from raw form-id multiplicity.
                reasonCodes = listOf(FORM_REASON_NO_LABEL)
            )
        }
    }

    private fun buildMismatch(input: Input, lockedSpecies: String?): ClassifierSpeciesMismatch? {
        if (lockedSpecies == null) return null
        val winnerMismatch = differingSpecies(input.fullMatchWinnerSpecies, lockedSpecies)?.let {
            ClassifierSpeciesMismatch(
                reason = RecognitionIdentity.MISMATCH_REASON_FULL_VARIANT,
                source = MISMATCH_SOURCE_FULL_VARIANT,
                suggestedSpecies = it,
                lockedSpecies = lockedSpecies
            )
        }
        return winnerMismatch ?: differingSpecies(input.classifierSpecies, lockedSpecies)?.let {
            ClassifierSpeciesMismatch(
                reason = RecognitionIdentity.MISMATCH_REASON_CLASSIFIER,
                source = MISMATCH_SOURCE_CLASSIFIER,
                suggestedSpecies = it,
                lockedSpecies = lockedSpecies
            )
        }
    }

    /** The suggested species when weak evidence names something other than the lock. */
    private fun differingSpecies(suggested: String?, lockedSpecies: String): String? =
        suggested?.trim()?.takeUnless { it.isBlank() || it.equals(lockedSpecies, ignoreCase = true) }

    private fun sizeTagTruth(sizeTag: String?, excluding: Set<String>): RecognitionTruth = when {
        sizeTag == null -> RecognitionTruth.UNKNOWN
        sizeTag in excluding -> RecognitionTruth.FALSE
        else -> RecognitionTruth.TRUE
    }

    private fun speciesAuthorityCode(authority: SpeciesAuthority): String = when (authority) {
        SpeciesAuthority.INDEPENDENT_PROFILE -> SpeciesEvidenceReason.INDEPENDENT_PROFILE
        SpeciesAuthority.EXACT_CANONICAL -> SpeciesEvidenceReason.EXACT
        SpeciesAuthority.REVIEWED_ALIAS -> SpeciesEvidenceReason.REVIEWED_ALIAS
        SpeciesAuthority.SAFE_FUZZY -> SpeciesEvidenceReason.SAFE_FUZZY
        SpeciesAuthority.UNCERTAIN -> SpeciesEvidenceReason.UNCERTAIN
        SpeciesAuthority.NO_MATCH -> SpeciesEvidenceReason.NO_MATCH
        SpeciesAuthority.CONFLICT -> SpeciesEvidenceReason.AUTHORITY_CONFLICT
    }
}

/**
 * Ordinary form-label filter used by the contract builder: only regional/ordinary
 * form keywords may establish form identity; costume/event labels never do.
 */
private object OrdinaryFormLabelFilter {
    private val keywords = listOf("alolan", "galarian", "hisuian", "paldean", "origin", "altered", "form")

    fun matches(label: String): Boolean {
        val normalized = label.lowercase().replace(Regex("[^a-z0-9]"), "")
        return keywords.any(normalized::contains) && !normalized.contains("costume")
    }
}
