package com.pokerarity.scanner.data.model

/**
 * Rarity tier classification for a Pokemon.
 * Tiers are ordered from least rare to most rare.
 */
enum class RarityTier(val label: String, val minScore: Int, val color: String) {
    COMMON("Common", 0, "#A0A0A0"),
    UNCOMMON("Uncommon", 21, "#4CAF50"),
    RARE("Rare", 51, "#2196F3"),
    EPIC("Epic", 101, "#9C27B0"),
    LEGENDARY("Legendary", 201, "#FF9800"),
    MYTHICAL("Mythical", 401, "#E91E63"),
    GOD_TIER("God Tier", 800, "#FFD700");

    companion object {
        fun fromScore(score: Int): RarityTier {
            return entries.reversed().first { score >= it.minScore }
        }
    }
}

data class RarityAxisScore(
    val key: String,
    val label: String,
    val score: Int,
    val maxScore: Int,
    val details: List<String> = emptyList()
)

enum class IvSolveMode {
    EXACT,
    RANGE,
    INSUFFICIENT
}

data class IvSolveDetails(
    val ivExact: Int? = null,
    val ivMin: Int? = null,
    val ivMax: Int? = null,
    val ivCandidateCount: Int = 0,
    val levelMin: Float? = null,
    val levelMax: Float? = null,
    val ivSolveMode: IvSolveMode = IvSolveMode.INSUFFICIENT,
    val ivSolveSignalsUsed: List<String> = emptyList()
)

/**
 * Complete rarity assessment for a scanned Pokemon.
 *
 * @param totalScore Overall rarity score
 * @param tier Human-readable category derived from totalScore
 * @param breakdown Points awarded per high-level axis
 * @param explanation Human-readable reasons for the score
 */
data class RarityScore(
    val totalScore: Int,
    val tier: RarityTier,
    val ivEstimate: String? = null, // e.g. "90% - 95%" or "100%"
    val ivSolve: IvSolveDetails? = null,
    val breakdown: Map<String, Int>,
    val explanation: List<String>,
    val axes: List<RarityAxisScore> = emptyList(),
    val confidence: Float = 1.0f,
    val decisionSupport: ScanDecisionSupport? = null
)
