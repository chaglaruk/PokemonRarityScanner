package com.pokerarity.scanner.data.model

/**
 * Rarity tier classification for a Pokemon.
 * Tiers are ordered from least rare to most rare.
 */
enum class RarityTier(val label: String, val minScore: Int, val color: String) {
    COMMON("Common", 0, "#9E9E9E"),
    UNCOMMON("Uncommon", 15, "#4CAF50"),
    RARE("Rare", 30, "#2196F3"),
    EPIC("Epic", 50, "#9C27B0"),
    LEGENDARY("Legendary", 75, "#FF9800"),
    MYTHICAL("Mythical", 90, "#F44336");

    companion object {
        fun fromScore(score: Int): RarityTier {
            return entries.reversed().first { score >= it.minScore }
        }
    }
}

/**
 * Complete rarity assessment for a scanned Pokemon.
 *
 * @param totalScore Overall rarity score (0-100)
 * @param tier Human-readable category derived from totalScore
 * @param breakdown Points awarded per category (Base, Shiny, Costume, Form, Age, Event)
 * @param explanation Human-readable reasons for the score
 */
data class RarityScore(
    val totalScore: Int,
    val tier: RarityTier,
    val ivEstimate: String? = null, // e.g. "90% - 95%" or "100%"
    val breakdown: Map<String, Int>,
    val explanation: List<String>
)
