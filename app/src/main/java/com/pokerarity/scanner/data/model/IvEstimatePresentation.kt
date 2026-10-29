package com.pokerarity.scanner.data.model

private const val MIN_IV_PERCENT = 0
private const val MAX_IV_PERCENT = 100

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

/** Shared IV and level line used by the result card and text-only share fallback. */
fun formatIvLevelSummary(
    ivText: String?,
    ivExact: Int?,
    solveMode: IvSolveMode?,
    level: IvLevelEstimate
): String? {
    val iv = formatIvEstimateDisplayText(ivText, ivExact, solveMode)
    return buildList {
        iv?.let { add("IV $it") }
        level.displayText?.let { add("Level $it") }
    }.joinToString(" | ").takeIf { it.isNotBlank() }
}

val Pokemon.ivEstimateDisplayText: String?
    get() = formatIvEstimateDisplayText(ivText, iv, ivSolveMode)

val Pokemon.ivLevelDisplayText: String?
    get() = IvLevelEstimate(ivLevelMin, ivLevelMax, ivLevelAlternatives).displayText

val Pokemon.ivLevelSummaryText: String?
    get() = formatIvLevelSummary(
        ivText,
        iv,
        ivSolveMode,
        IvLevelEstimate(ivLevelMin, ivLevelMax, ivLevelAlternatives)
    )
