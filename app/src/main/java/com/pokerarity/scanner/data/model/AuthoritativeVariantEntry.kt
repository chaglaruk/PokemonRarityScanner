package com.pokerarity.scanner.data.model

data class AuthoritativeVariantDb(
    val version: Int,
    val generatedAt: String,
    val count: Int,
    val sourceSummary: Map<String, Int> = emptyMap(),
    val entries: List<AuthoritativeVariantEntry>
)

data class HistoricalEventAppearance(
    val eventLabel: String? = null,
    val startDate: String? = null,
    val endDate: String? = null
)

data class AuthoritativeVariantEntry(
    val species: String,
    val dex: Int,
    val formId: String,
    val variantId: String? = null,
    val spriteKey: String,
    val variantClass: String,
    val isShiny: Boolean,
    val isCostumeLike: Boolean,
    val variantLabel: String? = null,
    val eventLabel: String? = null,
    val eventStart: String? = null,
    val eventEnd: String? = null,
    val historicalEvents: List<HistoricalEventAppearance> = emptyList(),
    val gameMasterFormName: String? = null,
    val aliases: List<String> = emptyList(),
    val sourceIds: List<String> = emptyList()
)
