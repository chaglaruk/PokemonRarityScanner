package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.RecognitionTruth

/** Diagnostic labels only: never classify an image or turn missing evidence into FALSE. */
internal data class VariantDiagnosticTruth(
    val rawFullMatchShiny: Boolean?,
    val finalTypedShiny: RecognitionTruth?,
    val finalShinyReason: String
) {
    companion object {
        fun build(raw: Boolean?, pokemonTruth: RecognitionTruth?,
            reportTruth: RecognitionTruth?): VariantDiagnosticTruth {
            val disagree = pokemonTruth != null && reportTruth != null && pokemonTruth != reportTruth
            val final = if (disagree) null else reportTruth ?: pokemonTruth
            val reason = when {
                disagree -> "diagnostic_identity_disagreement"
                final == RecognitionTruth.TRUE -> "typed_positive_evidence"
                final == RecognitionTruth.FALSE -> "typed_explicit_negative_evidence"
                final == RecognitionTruth.UNKNOWN -> "typed_insufficient_evidence"
                else -> "typed_identity_unavailable"
            }
            return VariantDiagnosticTruth(raw, final, reason)
        }
    }
}
