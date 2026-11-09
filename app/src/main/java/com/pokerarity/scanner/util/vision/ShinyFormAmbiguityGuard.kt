package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.VisualFeatures

/** Final Shiny gate after visual, prototype, and trained-classifier evidence merge. */
internal object ShinyFormAmbiguityGuard {
    data class Result(
        val features: VisualFeatures,
        val phase2ShinyDemoted: Boolean
    )

    fun apply(
        features: VisualFeatures,
        phase2ShinyDemoted: Boolean,
        unresolvedForms: Boolean
    ): Result = if (unresolvedForms) {
        // A different form's normal appearance can resemble a Shiny reference.
        // Neither a positive nor an explicit negative is justified here.
        Result(features.copy(isShiny = false), phase2ShinyDemoted = false)
    } else {
        Result(features, phase2ShinyDemoted)
    }

    /** Skip every visual Shiny path when the species-level reference is ambiguous. */
    fun <T> runDetectorDecision(
        unresolvedForms: Boolean,
        unresolvedValue: T,
        decision: () -> T
    ): T = if (unresolvedForms) unresolvedValue else decision()
}
