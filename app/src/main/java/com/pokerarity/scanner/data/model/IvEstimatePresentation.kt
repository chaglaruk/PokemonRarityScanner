package com.pokerarity.scanner.data.model

private const val MIN_IV_PERCENT = 0
private const val MAX_IV_PERCENT = 100
private const val MIN_POKEMON_LEVEL = 1f
// Best Buddy can raise the effective (displayed) level to 51 without changing the underlying level cap.
private const val MAX_POKEMON_LEVEL = 51f
private const val POKEMON_LEVEL_STEP = 0.5f
private const val MAX_IV_LEVEL_ALTERNATIVES = 101

/** Parses persisted and Intent-backed solve modes without promoting unknown values. */
fun parseIvSolveMode(value: String?): IvSolveMode? =
    value?.let { runCatching { IvSolveMode.valueOf(it) }.getOrNull() }

/** One user-facing IV value shared by history rows and reopened result screens. */
fun formatIvEstimateText(
    solveMode: String?,
    ivMin: Int?,
    ivMax: Int?
): String? = when (parseIvSolveMode(solveMode)) {
    IvSolveMode.EXACT -> validIvBounds(ivMin, ivMax)
        ?.takeIf { it.first == it.second }
        ?.let { "${it.first}%" }
        ?: "Unknown"
    IvSolveMode.RANGE -> validIvBounds(ivMin, ivMax)?.let { (min, max) ->
        if (min == max) "$min%" else "$min%–$max%"
    } ?: "Unknown"
    IvSolveMode.INSUFFICIENT -> "Unknown"
    null -> null
}

/** Returns an exact IV percentage only when the persisted bounds agree and are valid. */
fun exactIvValue(solveMode: String?, ivMin: Int?, ivMax: Int?): Int? =
    validIvBounds(ivMin, ivMax)
        ?.takeIf { parseIvSolveMode(solveMode) == IvSolveMode.EXACT && it.first == it.second }
        ?.first

private fun validIvBounds(ivMin: Int?, ivMax: Int?): Pair<Int, Int>? {
    if (ivMin == null || ivMax == null) return null
    return if (
        ivMin in MIN_IV_PERCENT..MAX_IV_PERCENT &&
        ivMax in MIN_IV_PERCENT..MAX_IV_PERCENT &&
        ivMin <= ivMax
    ) {
        ivMin to ivMax
    } else {
        null
    }
}

/** One shared display value for estimate surfaces, including explicit unknown results. */
fun formatIvEstimateDisplayText(
    ivText: String?,
    ivExact: Int?,
    solveMode: IvSolveMode?
): String? = if (solveMode == IvSolveMode.INSUFFICIENT) {
    "Unknown"
} else {
    ivText?.takeIf { it.isNotBlank() }
        ?: ivExact?.takeIf { it in MIN_IV_PERCENT..MAX_IV_PERCENT }?.let { "$it%" }
        ?: solveMode?.let { "Unknown" }
}

/** Sorts/deduplicates a bounded set of valid effective level candidates. */
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
    if (encoded.isNullOrBlank()) return emptyList()
    val tokens = encoded.split(',')
    if (tokens.size > MAX_IV_LEVEL_ALTERNATIVES) return emptyList()
    val parsed = tokens.map { it.toFloatOrNull() ?: return emptyList() }
    return canonicalIvLevelAlternatives(parsed)
}

/** Formats candidate levels as contiguous runs separated by explicit alternatives. */
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

/** Shared IV and level line used by the result card and text-only share fallback. */
fun formatIvLevelSummary(
    ivText: String?,
    ivExact: Int?,
    solveMode: IvSolveMode?,
    levelMin: Float?,
    levelMax: Float?,
    levelAlternatives: List<Float> = emptyList()
): String? {
    val iv = formatIvEstimateDisplayText(ivText, ivExact, solveMode)
    val level = formatIvLevelEstimateText(levelMin, levelMax, levelAlternatives)
    return buildList {
        iv?.let { add("IV $it") }
        level?.let { add("Level $it") }
    }.joinToString(" | ").takeIf { it.isNotBlank() }
}

val Pokemon.ivEstimateDisplayText: String?
    get() = formatIvEstimateDisplayText(ivText, iv, ivSolveMode)

val Pokemon.ivLevelDisplayText: String?
    get() = formatIvLevelEstimateText(ivLevelMin, ivLevelMax, ivLevelAlternatives)

val Pokemon.ivLevelSummaryText: String?
    get() = formatIvLevelSummary(ivText, iv, ivSolveMode, ivLevelMin, ivLevelMax, ivLevelAlternatives)
