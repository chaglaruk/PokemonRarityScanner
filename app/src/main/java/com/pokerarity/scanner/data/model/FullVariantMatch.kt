package com.pokerarity.scanner.data.model

data class FullVariantMatch(
    val finalSpecies: String,
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
