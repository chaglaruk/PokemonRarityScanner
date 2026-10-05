package com.pokerarity.scanner.data.model

data class FullVariantMatch(
    val finalSpecies: String,
    /**
     * Species of the candidate that actually won variant resolution; null when no
     * candidate won. [finalSpecies] is the locked seed species echo, so this field is
     * the only explicit record of a cross-species winner (Phase 2E mismatch input).
     */
    val winnerSpecies: String? = null,
    val finalSpriteKey: String? = null,
    val resolvedVariantClass: String = "base",
    val resolvedShiny: Boolean = false,
    val resolvedCostume: Boolean = false,
    val resolvedForm: Boolean = false,
    val resolvedEventLabel: String? = null,
    val resolvedEventWindow: ReleaseWindow? = null,
    val speciesConfidence: Float = 0f,
    val variantConfidence: Float = 0f,
    val shinyConfidence: Float = 0f,
    val eventConfidence: Float = 0f,
    val explanationMode: String = "generic_species_only",
    val candidates: List<FullVariantCandidate> = emptyList(),
    val debugSummary: String = ""
)
