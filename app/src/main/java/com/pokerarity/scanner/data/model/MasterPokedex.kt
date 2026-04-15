package com.pokerarity.scanner.data.model

data class MasterPokedex(
    val version: Int,
    val generatedAt: String,
    val entries: List<MasterPokedexEntry>
)

data class MasterPokedexEntry(
    val species: String,
    val spriteKey: String,
    val variantClass: String,
    val isShiny: Boolean,
    val isCostumeLike: Boolean,
    val variantLabel: String? = null,
    val eventLabel: String? = null,
    val eventStart: String? = null,
    val eventEnd: String? = null,
    val historicalEvents: List<HistoricalEventAppearance> = emptyList(),
    val aliases: List<String> = emptyList(),
    val signature: MasterPokedexSignature? = null
)

data class MasterPokedexSignature(
    val aHash: String? = null,
    val dHash: String? = null,
    val pHash: String? = null,
    val headPHash: String? = null,
    val edge: List<Float> = emptyList()
)
