package com.pokerarity.scanner.data.model

data class GlobalRarityLegacyDb(
    val version: Int,
    val generatedAt: String,
    val count: Int,
    val sourceSummary: Map<String, Int> = emptyMap(),
    val entries: List<GlobalRarityLegacyEntry>
)

data class GlobalRarityLegacyEntry(
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
    val firstSeen: String? = null,
    val lastSeen: String? = null,
    val lastKnownEvent: String? = null,
    val activeEventLabel: String? = null,
    val activeEventStart: String? = null,
    val activeEventEnd: String? = null,
    val liveAvailability: String = "unknown",
    val shinyAvailability: String = "unknown",
    val shinyOddsBucket: String? = null,
    val aliases: List<String> = emptyList(),
    val sourceIds: List<String> = emptyList(),
    val sourceNames: List<String> = emptyList()
)
