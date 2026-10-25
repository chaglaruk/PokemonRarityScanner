package com.pokerarity.scanner.data.model

/** Parses persisted and Intent-backed solve modes without promoting unknown values. */
fun parseIvSolveMode(value: String?): IvSolveMode? =
    value?.let { runCatching { IvSolveMode.valueOf(it) }.getOrNull() }

/** One user-facing IV value shared by history rows and reopened result screens. */
fun formatIvEstimateText(
    solveMode: String?,
    ivMin: Int?,
    ivMax: Int?
): String? = when (parseIvSolveMode(solveMode)) {
    IvSolveMode.EXACT -> (ivMin ?: ivMax)?.let { "$it%" }
    IvSolveMode.RANGE -> if (ivMin != null && ivMax != null) {
        if (ivMin == ivMax) "$ivMin%" else "$ivMin%–$ivMax%"
    } else null
    IvSolveMode.INSUFFICIENT -> "Unknown"
    null -> null
}

/** One shared display value for estimate surfaces, including explicit unknown results. */
fun formatIvEstimateDisplayText(
    ivText: String?,
    ivExact: Int?,
    solveMode: IvSolveMode?
): String? = ivText?.takeIf { it.isNotBlank() }
    ?: ivExact?.takeIf { it in 0..100 }?.let { "$it%" }
    ?: solveMode?.let { "Unknown" }

/** Formats only complete, legal level bounds; partial or invalid bounds remain unknown. */
fun formatIvLevelEstimateText(levelMin: Float?, levelMax: Float?): String? {
    if (levelMin == null || levelMax == null) return null
    if (!levelMin.isFinite() || !levelMax.isFinite() ||
        levelMin < 1f || levelMax > 50f || levelMin > levelMax
    ) return null

    fun format(level: Float) = level.toString().removeSuffix(".0")
    return if (levelMin == levelMax) format(levelMin) else format(levelMin) + "–" + format(levelMax)
}

/** Shared IV and level line used by the result card and text-only share fallback. */
fun formatIvLevelSummary(
    ivText: String?,
    ivExact: Int?,
    solveMode: IvSolveMode?,
    levelMin: Float?,
    levelMax: Float?
): String? {
    val iv = formatIvEstimateDisplayText(ivText, ivExact, solveMode)
    val level = formatIvLevelEstimateText(levelMin, levelMax)
    return buildList {
        iv?.let { add("IV $it") }
        level?.let { add("Level $it") }
    }.joinToString(" | ").takeIf { it.isNotBlank() }
}

val Pokemon.ivEstimateDisplayText: String?
    get() = formatIvEstimateDisplayText(ivText, iv, ivSolveMode)

val Pokemon.ivLevelDisplayText: String?
    get() = formatIvLevelEstimateText(ivLevelMin, ivLevelMax)

val Pokemon.ivLevelSummaryText: String?
    get() = formatIvLevelSummary(ivText, iv, ivSolveMode, ivLevelMin, ivLevelMax)
