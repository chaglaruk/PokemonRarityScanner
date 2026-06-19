package com.pokerarity.scanner.domain.collector

/**
 * Pure domain representation of a collector entry for context building.
 * Decoupled from the database entity.
 */
data class CollectionLookupEntry(
    val isXXL: Boolean = false,
    val isXXS: Boolean = false
)
