package com.pokerarity.scanner.data.model

private const val MIN_POKEMON_LEVEL = 1f
// Best Buddy can raise the effective (displayed) level to 51 without changing the underlying level cap.
private const val MAX_POKEMON_LEVEL = 51f
private const val POKEMON_LEVEL_STEP = 0.5f
private const val MAX_IV_LEVEL_ALTERNATIVES = 101

/** Level bounds and discrete candidates carried together for consistent result presentation. */
data class IvLevelEstimate(
    val minimum: Float?,
    val maximum: Float?,
    val alternatives: List<Float> = emptyList()
) {
    val displayText: String?
        get() = formatIvLevelEstimateText(minimum, maximum, alternatives)
}

/** Sorts and deduplicates a bounded set of valid effective-level candidates. */
fun canonicalIvLevelAlternatives(levels: List<Float>): List<Float> {
    if (levels.isEmpty() || levels.size > MAX_IV_LEVEL_ALTERNATIVES || levels.any { !isLegalPokemonLevel(it) }) {
        return emptyList()
    }
    return levels.distinct().sorted()
}

/** Stores only bounded numeric candidates; malformed legacy values remain unavailable. */
fun encodeIvLevelAlternatives(levels: List<Float>): String? =
    canonicalIvLevelAlternatives(levels).takeIf { it.isNotEmpty() }?.joinToString(",")

fun decodeIvLevelAlternatives(encoded: String?): List<Float> {
    val tokens = encoded?.takeIf { it.isNotBlank() }?.split(',')
        ?.takeIf { it.size <= MAX_IV_LEVEL_ALTERNATIVES }
        ?: return emptyList()
    val parsed = tokens.mapNotNull(String::toFloatOrNull)
    return parsed.takeIf { it.size == tokens.size }?.let(::canonicalIvLevelAlternatives).orEmpty()
}

/** Formats candidates as contiguous level runs separated by explicit alternatives. */
fun formatIvLevelEstimateText(
    levelMin: Float?,
    levelMax: Float?,
    levelAlternatives: List<Float> = emptyList()
): String? {
    val levels = canonicalIvLevelAlternatives(levelAlternatives)
    if (levels.isNotEmpty()) {
        val ranges = mutableListOf<String>()
        var first = levels.first()
        var previous = first
        levels.drop(1).forEach { level ->
            if (level - previous != POKEMON_LEVEL_STEP) {
                ranges += formatLevelRange(first, previous)
                first = level
            }
            previous = level
        }
        ranges += formatLevelRange(first, previous)
        return ranges.joinToString(" or ")
    }
    return levelMin?.takeIf(::isLegalPokemonLevel)?.let { minimum ->
        levelMax?.takeIf { isLegalPokemonLevel(it) && minimum <= it }?.let { maximum ->
            formatLevelRange(minimum, maximum)
        }
    }
}

private fun formatLevelRange(minimum: Float, maximum: Float): String {
    val minText = formatPokemonLevel(minimum)
    return if (minimum == maximum) minText else minText + "–" + formatPokemonLevel(maximum)
}

private fun isLegalPokemonLevel(level: Float): Boolean =
    level.isFinite() && level >= MIN_POKEMON_LEVEL && level <= MAX_POKEMON_LEVEL &&
        level % POKEMON_LEVEL_STEP == 0f

private fun formatPokemonLevel(level: Float): String = level.toString().removeSuffix(".0")
