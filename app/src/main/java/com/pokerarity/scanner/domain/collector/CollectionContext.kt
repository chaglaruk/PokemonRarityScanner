package com.pokerarity.scanner.domain.collector

data class CollectionContext(
    val duplicateCountForVariantKey: Int = 0,
    val isFirstOfKind: Boolean = false,
    val hasSameSpecies: Boolean = false,
    val hasSameShiny: Boolean = false,
    val hasSameCostume: Boolean = false,
    val hasSameBackground: Boolean = false,
    val hasSameXXL: Boolean = false,
    val hasSameXXS: Boolean = false
)
