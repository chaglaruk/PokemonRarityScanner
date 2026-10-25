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
