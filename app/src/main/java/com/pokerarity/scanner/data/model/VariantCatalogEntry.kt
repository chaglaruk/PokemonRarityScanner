package com.pokerarity.scanner.data.model

data class VariantCatalog(
    val version: Int,
    val generatedAt: String,
    val count: Int,
    val speciesCount: Int,
    val entries: List<VariantCatalogEntry>
)

data class VariantCatalogEntry(
    val dex: Int,
    val species: String,
    val formId: String,
    val variantId: String? = null,
    val assetKey: String,
    val spriteKey: String,
    val isShiny: Boolean,
    val variantClass: String,
    val isCostumeLike: Boolean,
    val eventTags: List<String> = emptyList(),
    val primaryEventLabel: String? = null,
    val hasEventMetadata: Boolean = false,
    val releaseWindow: ReleaseWindow? = null,
    val variantLabel: String? = null,
    val gameMasterCostumeForms: List<String> = emptyList(),
    val gameMasterFormName: String? = null,
    val assetPath: String? = null
)

data class ReleaseWindow(
    val firstSeen: String? = null,
    val lastSeen: String? = null
)
