package com.pokerarity.scanner.data.model

private const val MIN_IV_PERCENT = 0
private const val MAX_IV_PERCENT = 100
private const val MIN_POKEMON_LEVEL = 1f
private const val MAX_POKEMON_LEVEL = 50f
private const val POKEMON_LEVEL_STEP = 0.5f

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
    ?: ivExact?.takeIf { it in MIN_IV_PERCENT..MAX_IV_PERCENT }?.let { "$it%" }
    ?: solveMode?.let { "Unknown" }

/** Formats only complete, legal level bounds; partial or invalid bounds remain unknown. */
fun formatIvLevelEstimateText(levelMin: Float?, levelMax: Float?): String? =
    levelMin?.takeIf(::isLegalPokemonLevel)?.let { minimum ->
        levelMax?.takeIf { isLegalPokemonLevel(it) && minimum <= it }?.let { maximum ->
            val minText = formatPokemonLevel(minimum)
            if (minimum == maximum) minText else minText + "–" + formatPokemonLevel(maximum)
        }
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
