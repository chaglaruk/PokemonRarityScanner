package com.pokerarity.scanner.data.model

/**
 * Visual features detected from color analysis of a Pokemon screenshot.
 */
data class VisualFeatures(
    val isShiny: Boolean = false,
    val isShadow: Boolean = false,
    val isLucky: Boolean = false,
    val hasCostume: Boolean = false,
    val isXXS: Boolean = false,
    val isXXL: Boolean = false,
    val hasLocationCard: Boolean = false,
    val confidence: Float = 0f
)
