package com.pokerarity.scanner.data.model

data class VariantDecisionTrace(
    val classifierScope: String? = null,
    val classifierSpecies: String? = null,
    val classifierSpriteKey: String? = null,
    val classifierVariantType: String? = null,
    val classifierShiny: Boolean? = null,
    val classifierCostume: Boolean? = null,
    val classifierConfidence: Float? = null,
    val classifierScore: Float? = null,
    val classifierVariantMargin: Float? = null,
    val classifierBestBaseScore: Float? = null,
    val classifierBestNonBaseScore: Float? = null,
    val classifierBestNonBaseType: String? = null,
    val classifierRescueKind: String? = null,

    val fullVariantSpecies: String? = null,
    val fullVariantSpriteKey: String? = null,
    val fullVariantClass: String? = null,
    val fullVariantShiny: Boolean? = null,
    val fullVariantCostume: Boolean? = null,
    val fullVariantForm: Boolean? = null,
    val fullVariantEvent: String? = null,
    val fullVariantExplanationMode: String? = null,
    val fullVariantSpeciesConfidence: Float? = null,
    val fullVariantVariantConfidence: Float? = null,
    val fullVariantShinyConfidence: Float? = null,
    val fullVariantEventConfidence: Float? = null,
    val fullVariantDebug: String? = null
)
