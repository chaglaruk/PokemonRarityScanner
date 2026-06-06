package com.pokerarity.scanner.data.model

enum class CollectionTier(
    val label: String,
    val minScore: Int,
    val maxScore: Int,
    val colorHex: String
) {
    COMMON("Common", 0, 14, "#A0A0A0"),
    UNCOMMON("Uncommon", 15, 29, "#4CAF50"),
    NOTABLE("Notable", 30, 44, "#2196F3"),
    RARE("Rare", 45, 59, "#9C27B0"),
    VERY_RARE("Very Rare", 60, 74, "#FF9800"),
    ULTRA_RARE("Ultra Rare", 75, 89, "#E91E63"),
    TROPHY("Trophy", 90, 100, "#FFD700");

    companion object {
        fun fromScore(score: Int): CollectionTier =
            entries.reversed().first { score.coerceIn(0, 100) >= it.minScore }
    }
}

enum class ScoreAxis(val key: String, val label: String, val maxPoints: Int) {
    BASE_SPECIES("baseSpecies", "Base Species", 20),
    AVAILABILITY("availability", "Availability", 22),
    VARIANT("variant", "Variant", 22),
    LEGACY("legacy", "Legacy", 15),
    COLLECTOR_CONTEXT("collectorContext", "Collector Context", 13),
    META("meta", "Meta", 8);
}

data class CollectionAxisScore(
    val axis: ScoreAxis,
    val score: Int,
    val maxScore: Int,
    val details: List<String> = emptyList()
)

enum class EventMatchLevel {
    EXACT,
    POSSIBLE,
    NONE
}

data class CollectionResult(
    val totalScore: Int,
    val tier: CollectionTier,
    val axes: List<CollectionAxisScore>,
    val trophyQualified: Boolean,
    val trophySignals: List<String>,
    val detectedSpecies: String,
    val costumeOrForm: String?,
    val eventName: String?,
    val eventWindow: String?,
    val firstReleased: String?,
    val currentStatus: String?,
    val legacyCatchLabel: String?,
    val tradeInfo: String?,
    val isEdited: Boolean,
    val eventMatchLevel: EventMatchLevel,
    val eventDateMismatchMessage: String?,
    val catalogVersion: String? = null
)
