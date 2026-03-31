package com.pokerarity.scanner.data.model

data class FullVariantCandidate(
    val species: String,
    val spriteKey: String,
    val variantClass: String,
    val isShiny: Boolean,
    val isCostumeLike: Boolean,
    val eventLabel: String? = null,
    val eventStart: String? = null,
    val eventEnd: String? = null,
    val matchScore: Float,
    val rescueKind: String? = null,
    val source: String,
    val classifierConfidence: Float = 1f
)
