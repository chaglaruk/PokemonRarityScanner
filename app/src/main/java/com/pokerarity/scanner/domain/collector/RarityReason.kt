package com.pokerarity.scanner.domain.collector

enum class RarityReasonCategory {
    SPECIES,
    VARIANT,
    IV,
    COLLECTION_GAP,
    DUPLICATE,
    UNCERTAINTY
}

enum class RarityReason(val title: String, val category: RarityReasonCategory) {
    SHINY("Shiny", RarityReasonCategory.VARIANT),
    COSTUME("Costume", RarityReasonCategory.VARIANT),
    SPECIAL_BACKGROUND("Special Background", RarityReasonCategory.VARIANT),
    LOCATION_BACKGROUND("Location Background", RarityReasonCategory.VARIANT),
    SHADOW("Shadow", RarityReasonCategory.VARIANT),
    LUCKY("Lucky", RarityReasonCategory.VARIANT),
    OLD_POKEMON("Old Pokemon", RarityReasonCategory.VARIANT),
    HIGH_IV("High IV", RarityReasonCategory.IV),
    PERFECT_IV("Perfect IV (100%)", RarityReasonCategory.IV),
    NUNDO("Nundo (0%)", RarityReasonCategory.IV),
    FIRST_OF_KIND("First of Kind", RarityReasonCategory.COLLECTION_GAP),
    DUPLICATE("Duplicate", RarityReasonCategory.DUPLICATE),
    LEGENDARY_OR_MYTHICAL("Legendary / Mythical", RarityReasonCategory.SPECIES),
    LOW_CONFIDENCE_REVIEW("Low Confidence Scan", RarityReasonCategory.UNCERTAINTY),
    UNKNOWN_SPECIES_REVIEW("Unknown Species", RarityReasonCategory.UNCERTAINTY),
    XXL("XXL Size", RarityReasonCategory.VARIANT),
    XXS("XXS Size", RarityReasonCategory.VARIANT)
}
