package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.RecognitionFormStatus
import com.pokerarity.scanner.data.model.RecognitionIdentity

/** Same-frame form witnesses; consumed only after the canonical species has been locked. */
data class SameSpeciesFormEvidence(
    val species: String,
    val snapshotRevision: String,
    val alternatives: List<String>,
    val reasonCode: String
)

/** Interprets supported ordinary form identifiers from the existing snapshot, never species. */
internal object SameSpeciesFormDecision {
    // Supported identifier grammar, not a species/form registry. All membership and
    // statistics still come from the pinned RecognitionSnapshot. Other identifiers
    // (including event/costume IDs) remain unsupported until their semantics are known.
    private val suffixLabels = mapOf(
        "NORMAL" to "Normal Form", "ALOLA" to "Alolan Form", "GALARIAN" to "Galarian Form",
        "HISUIAN" to "Hisuian Form", "PALDEA" to "Paldean Form",
        "ORIGIN" to "Origin Form", "ALTERED" to "Altered Form",
        "HEAT" to "Heat Form", "WASH" to "Wash Form", "FROST" to "Frost Form",
        "FAN" to "Fan Form", "MOW" to "Mow Form"
    )

    fun fromEvaluation(evaluation: CandidateEvaluation, revision: String?): Map<String, SameSpeciesFormEvidence> {
        if (revision.isNullOrBlank()) return emptyMap()
        return evaluation.survivingCandidates.groupBy { it.species }.mapValues { (species, rows) ->
            val supported = evaluation.evaluations.drop(1).any { constraint ->
                constraint.observed && constraint.status == ConstraintStatus.MATCHED &&
                    constraint.matched.containsAll(rows)
            }
            val labels = rows.flatMap { row -> row.forms.map { ordinaryLabel(it, species) } }
            val reason = when {
                !supported -> "form_independent_evidence_missing"
                labels.any { it == null } -> "form_identifier_unsupported"
                else -> "same_species_profile_witness"
            }
            SameSpeciesFormEvidence(species, revision,
                if (reason == "same_species_profile_witness") {
                    labels.filterNotNull().distinct().sorted()
                } else emptyList(),
                reason)
        }
    }

    /**
     * Return only snapshot rows supported by the locked identity's form result.
     * UNKNOWN deliberately keeps the full species pool; a bounded form result must
     * resolve every stated label through the same ordinary-form semantics used by
     * [fromEvaluation], or the caller receives no numeric candidates.
     */
    fun profilesSupportedByIdentity(
        profiles: List<RecognitionSnapshot.Profile>,
        identity: RecognitionIdentity
    ): List<RecognitionSnapshot.Profile> = when (identity.formStatus) {
        RecognitionFormStatus.UNKNOWN -> profiles
        RecognitionFormStatus.KNOWN -> identity.knownForm
            ?.let { profilesForLabels(profiles, listOf(it)) }
            .orEmpty()
        RecognitionFormStatus.AMBIGUOUS -> profilesForLabels(profiles, identity.ambiguousForms)
    }

    private fun profilesForLabels(
        profiles: List<RecognitionSnapshot.Profile>,
        labels: List<String>
    ): List<RecognitionSnapshot.Profile> {
        val supportedLabels = labels.mapNotNull { label ->
            canonicalOrdinaryLabel(label, profiles.firstOrNull()?.species.orEmpty())
        }
        return if (labels.isNotEmpty() && supportedLabels.size == labels.size) {
            val distinctSupportedLabels = supportedLabels.toSet()
            profiles.filter { profile ->
                profile.forms.any { identifier ->
                    ordinaryLabel(identifier, profile.species) in distinctSupportedLabels
                }
            }
        } else {
            emptyList()
        }
    }

    /**
     * Identity labels can come from owned variant labels (for example "Alolan")
     * or this snapshot's own evidence labels ("Alolan Form"). Map both through
     * the existing supported suffix vocabulary; do not parse raw form IDs here.
     */
    private fun canonicalOrdinaryLabel(label: String, species: String): String? {
        val normalized = label.filter(Char::isLetterOrDigit).lowercase()
            .replace(species.filter(Char::isLetterOrDigit).lowercase(), "")
        return suffixLabels.values.singleOrNull { value ->
            val canonical = value.filter(Char::isLetterOrDigit).lowercase()
            normalized == canonical || normalized == canonical.removeSuffix("form")
        }
    }

    private fun ordinaryLabel(identifier: String, species: String): String? {
        val parts = identifier.split('_')
        val canonical = species.filter(Char::isLetterOrDigit)
        val speciesEnd = (1 until parts.size).firstOrNull { end ->
            parts.take(end).joinToString("").filter(Char::isLetterOrDigit)
                .equals(canonical, ignoreCase = true)
        } ?: return null

        // Snapshot form identifiers can include a subtype after the ordinary-form
        // authority token (for example TAUROS_PALDEA_COMBAT or
        // DARMANITAN_GALARIAN_ZEN). Interpret only the first token after the exact
        // canonical species prefix. Later subtype tokens remain alternatives within
        // that region and are never treated as another form authority.
        return suffixLabels[parts[speciesEnd]]
    }
}
