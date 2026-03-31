package com.pokerarity.scanner.data.model

data class EventPokemonSeedFile(
    val version: Int = 1,
    val generatedAt: String? = null,
    val events: List<EventPokemonSeed> = emptyList(),
)

data class EventPokemonSeed(
    val baseName: String,
    val eventName: String,
    val eventBonusScore: Int,
    val spriteKey: String? = null,
    val variantToken: String? = null,
    val eventStart: String? = null,
    val eventEnd: String? = null,
    val source: String? = null,
)
