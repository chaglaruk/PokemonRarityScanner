package com.pokerarity.scanner.util.ocr

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

    private fun ordinaryLabel(identifier: String, species: String): String? {
        val separator = identifier.lastIndexOf('_')
        if (separator < 0) return null
        val prefix = identifier.substring(0, separator).filter(Char::isLetterOrDigit)
        val canonical = species.filter(Char::isLetterOrDigit)
        return if (prefix.equals(canonical, ignoreCase = true)) {
            suffixLabels[identifier.substring(separator + 1)]
        } else null
    }
}
