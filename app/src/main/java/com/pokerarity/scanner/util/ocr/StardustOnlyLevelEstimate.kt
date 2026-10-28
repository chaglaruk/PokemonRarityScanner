package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.canonicalIvLevelAlternatives

private const val HALF_LEVEL_STEP = 0.5
private const val MAX_UNDERLYING_LEVEL = 49.5

internal data class StardustOnlyLevelEstimate(
    val alternatives: List<Float>,
    val contiguousBounds: Pair<Float, Float>?
)

/** Preserves valid stardust-derived effective levels when IV solving has no species domain. */
internal fun stardustOnlyLevelEstimate(stardust: StardustLevelEvidence?): StardustOnlyLevelEstimate {
    val levels = (stardust as? StardustLevelEvidence.Levels)?.levels
    val validLevels = levels
        ?.takeIf { it.isNotEmpty() }
        ?.takeIf { candidates -> candidates.all { it.isFinite() } }
        ?.takeIf { candidates -> candidates.all { it >= 1.0 && it <= MAX_UNDERLYING_LEVEL } }
        ?.takeIf { candidates -> candidates.all { it % HALF_LEVEL_STEP == 0.0 } }
    val alternatives = canonicalIvLevelAlternatives(
        validLevels
            ?.flatMap { listOf(it, it + 1.0) }
            ?.distinct()
            ?.sorted()
            ?.map { it.toFloat() }
            .orEmpty()
    )
    val bounds = alternatives.takeIf { candidates ->
        candidates.isNotEmpty() && candidates.zipWithNext().all { (first, next) ->
            next - first == HALF_LEVEL_STEP.toFloat()
        }
    }?.let { candidates -> candidates.first() to candidates.last() }
    return StardustOnlyLevelEstimate(alternatives, bounds)
}
