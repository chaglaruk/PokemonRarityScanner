package com.pokerarity.scanner.data.model

enum class CostumeEvidenceKind {
    NONE,
    COTTONEE_SPRING_2024_FLOWER_CROWN
}

/**
 * Visual features detected from color analysis of a Pokemon screenshot.
 */
data class VisualFeatures(
    val isShiny: Boolean = false,
    val isShadow: Boolean = false,
    val isPurified: Boolean = false,
    val isLucky: Boolean = false,
    val hasCostume: Boolean = false,
    val costumeEvidenceKind: CostumeEvidenceKind = CostumeEvidenceKind.NONE,
    val hasSpecialForm: Boolean = false,
    val isXXS: Boolean = false,
    val isXXL: Boolean = false,
    val hasLocationCard: Boolean = false,
    val confidence: Float = 0f
)
