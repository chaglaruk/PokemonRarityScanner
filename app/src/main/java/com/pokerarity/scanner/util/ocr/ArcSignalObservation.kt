package com.pokerarity.scanner.util.ocr

/**
 * Phase 3C typed arc observation (plan §7.4), stage A+B: the pixel/curve observation of
 * the CP arc on a calibrated detail frame. This type carries NO Pokemon level semantics —
 * the forward model scores the visible arc signal (fill boundary / marker) along the
 * fitted ring as a continuous normalized parameter t:
 *
 *  - t = 0 at the arc's start (lower-left terminus, minimum representable level);
 *  - t = 1 at the arc's end (lower-right terminus, maximum representable level).
 *
 * Whether t maps to the UNDERLYING or the EFFECTIVE (Best Buddy) level is deliberately
 * NOT decided here: the development corpus cannot yet establish which semantics the
 * visible arc represents, so consumers must keep both tuple interpretations alive.
 */
data class ArcSignalObservation(
    val parameter: ParameterState,
    /** Fraction of arc angles where the fitted ring shows a distinct thin curve (0..1). */
    val ringCoverage: Float,
    /** Fraction of the observed arc run that is bright fill (0..1); null when unfitted. */
    val fillFraction: Float?,
    /** Bounded, non-sensitive reason/provenance codes. */
    val reasonCodes: List<String>
) {
    sealed interface ParameterState {
        /** Single plausible fill-boundary interval on the normalized parameter. */
        data class Interval(val min: Double, val max: Double) : ParameterState

        /** Genuinely discrete plausible boundaries; no arbitrary winner is chosen. */
        data class Alternatives(val values: List<Double>, val tolerance: Double) : ParameterState

        /** The signal could not be observed credibly (occlusion, no fill, weak fit). */
        data class Unknown(val reasonCode: String) : ParameterState

        /** The requested observation is not supported for this frame's evidence. */
        data class Unsupported(val reasonCode: String) : ParameterState
    }
}

/**
 * Phase 3C typed LEVEL evidence for the arc (the level-facing contract consumed by
 * Phase 3B tuple feasibility). Production Phase 3C keeps the position→level mapping
 * DISABLED (the development evidence does not yet establish a trustworthy mapping), so
 * the production fitter only ever produces [Unknown] with the measured parameter carried
 * as bounded metadata. [Range]/[Alternatives] exist for the mapper seam exercised by
 * tests and for a future slice with an evidence-backed mapping.
 *
 * Best Buddy disposition: a [Range] constrains the arc-relevant level WITHOUT deciding
 * whether the arc represents the underlying or the effective level; tuple feasibility
 * must keep both interpretations (see [ProfileTupleFeasibility.constrainToArcLevels]).
 */
sealed interface ArcLevelEvidence {
    /** Bounded, non-sensitive reason/provenance codes. */
    val reasonCodes: List<String>

    /** Inclusive legal level window (half-level units of the recognition snapshot domain). */
    data class Range(
        val minLevel: Double,
        val maxLevel: Double,
        override val reasonCodes: List<String>
    ) : ArcLevelEvidence

    /** Genuinely discrete plausible windows; no arbitrary winner. */
    data class Alternatives(
        val ranges: List<ClosedFloatingPointRange<Double>>,
        override val reasonCodes: List<String>
    ) : ArcLevelEvidence

    /** No credible level interpretation (mapping unestablished, weak signal). */
    data class Unknown(
        val parameter: ArcSignalObservation?,
        override val reasonCodes: List<String>
    ) : ArcLevelEvidence

    /** Arc evidence not supported for this frame (geometry unavailable/legacy). */
    data class Unsupported(override val reasonCodes: List<String>) : ArcLevelEvidence

    /** Independent credible arc observations disagree; never silently resolved. */
    data class Conflict(override val reasonCodes: List<String>) : ArcLevelEvidence
}
