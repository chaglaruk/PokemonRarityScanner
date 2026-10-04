package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import kotlin.math.abs

/**
 * Runtime controller for persistent screen calibration (Phase 2B, plan 6.3).
 *
 * Per detail-routed frame:
 *  1. pre-OCR: resolve the persisted record for the display signature and decide whether a
 *     calibrated bar rect may seed the extractor (only when the frame's scroll state matches);
 *  2. post-OCR: validate the live HP-bar anchor against the record, update health counters,
 *     perform targeted/full rebuilds when justified, and persist the resulting healthy record.
 *
 * A record only ever enters the extractor as a FALLBACK anchor when live detection failed,
 * and only after a same-scroll-state compatibility check — a stale calibration can never
 * silently become trusted geometry or bypass routing. Health accounting skips frames whose
 * evidence is insufficient to judge the layout (no live bar, or no scroll-state confirmation),
 * so a single transient weak anchor cannot destroy an otherwise compatible calibration while
 * repeated genuine validation failures still invalidate it.
 */
class ScreenCalibrationManager(
    private val store: ScreenCalibrationStore,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /**
     * Pre-OCR lookup. Returns the persisted record (when compatible) plus the seed bar rect
     * when the frame's scroll state matches the calibrated scroll state.
     */
    fun resolveForFrame(signature: DisplayGeometrySignature, geometry: FrameGeometry): FrameResolution {
        val lookupStart = System.nanoTime()
        val record = store.load(signature.stableKey)
        val lookupMs = elapsedSince(lookupStart)
        val base = FrameResolution(
            resolution = CalibrationResolution.NOT_APPLICABLE,
            signatureKey = signature.stableKey,
            schemaRevision = record?.schemaRevision,
            record = record,
            reasonCodes = listOf("calibration_record_loaded"),
            lookupMs = lookupMs
        )
        return when {
            record == null -> FrameResolution(
                resolution = CalibrationResolution.UNAVAILABLE,
                signatureKey = signature.stableKey,
                reasonCodes = listOf(CalibrationMissReason.NO_RECORD.code),
                lookupMs = lookupMs
            )
            !record.isSchemaCurrent() -> {
                store.clear(signature.stableKey)
                base.copy(
                    resolution = CalibrationResolution.SCHEMA_INVALIDATED,
                    record = null,
                    reasonCodes = listOf(CalibrationMissReason.SCHEMA_MISMATCH.code)
                )
            }
            else -> base.copy(seededBarRect = seedBarRectOrNull(record, geometry))
        }
    }

    /**
     * Post-OCR validation + health update. [geometry.liveBarRect] is the bar the recognizer
     * located live (null when detection failed and a calibrated seed may have been used).
     */
    fun onFrameGeometryObserved(
        signature: DisplayGeometrySignature,
        pre: FrameResolution,
        geometry: FrameGeometry
    ): FrameResolution {
        val pendingRebuild = pre.resolution == CalibrationResolution.UNAVAILABLE ||
            pre.resolution == CalibrationResolution.SCHEMA_INVALIDATED
        // No live anchor: a seeded frame consumed calibration (no health change); an
        // unseeded frame simply lacks evidence. Neither validates nor damages the record.
        val noLiveBar = pre.seededBarRect != null
        val withLookup = when {
            pendingRebuild -> rebuildCalibration(signature, pre, geometry, extraReasons = listOf("full_autoconfig"))
            pre.record == null -> pre
            geometry.liveBarRect == null -> pre.copy(
                resolution = if (noLiveBar) CalibrationResolution.HIT_SEEDED else pre.resolution,
                reasonCodes = pre.reasonCodes + listOfNotNull("seeded_bar_used".takeIf { noLiveBar })
            )
            else -> validatedOutcome(pre, signature, geometry)
        }
        return withLookup.copy(lookupMs = withLookup.lookupMs ?: pre.lookupMs)
    }

    private fun validatedOutcome(
        pre: FrameResolution,
        signature: DisplayGeometrySignature,
        geometry: FrameGeometry
    ): FrameResolution {
        val record = pre.record ?: return pre.copy(reasonCodes = pre.reasonCodes + "missing_record")
        val validationStart = System.nanoTime()
        val verdict = validateBar(record, geometry)
        val validationMs = elapsedSince(validationStart)
        val outcome = when (verdict.outcome) {
            ValidationOutcome.HEALTHY -> {
                val updated = record.withValidation(success = true, atMs = clock())
                store.save(updated)
                FrameResolution(
                    resolution = CalibrationResolution.HIT_VALIDATED,
                    signatureKey = signature.stableKey,
                    schemaRevision = updated.schemaRevision,
                    record = updated,
                    reasonCodes = listOf("bar_anchor_validated"),
                    validationMs = validationMs
                )
            }
            ValidationOutcome.DRIFTED -> degradedOutcome(record, signature, verdict, validationMs)
            ValidationOutcome.MAJOR_DRIFT -> rebuildCalibration(
                signature = signature,
                pre = pre,
                geometry = geometry,
                extraReasons = verdict.reasons + "major_drift_invalidated",
                validationMs = validationMs
            )
            ValidationOutcome.NOT_JUDGEABLE -> pre.copy(
                reasonCodes = pre.reasonCodes + verdict.reasons,
                validationMs = validationMs
            )
        }
        return outcome.copy(validationMs = outcome.validationMs ?: validationMs)
    }

    private fun degradedOutcome(
        record: ScreenCalibrationRecord,
        signature: DisplayGeometrySignature,
        verdict: BarVerdict,
        validationMs: Long
    ): FrameResolution {
        val updated = record.withValidation(success = false, atMs = clock())
        val thresholdReached =
            updated.consecutiveValidationFailures >= CalibrationTolerances.REBUILD_AFTER_CONSECUTIVE_FAILURES
        return if (thresholdReached) {
            store.clear(signature.stableKey)
            FrameResolution(
                resolution = CalibrationResolution.INVALIDATED,
                signatureKey = signature.stableKey,
                schemaRevision = updated.schemaRevision,
                reasonCodes = verdict.reasons + "consecutive_failure_threshold",
                validationMs = validationMs
            )
        } else {
            store.save(updated)
            FrameResolution(
                resolution = CalibrationResolution.HIT_DEGRADED,
                signatureKey = signature.stableKey,
                schemaRevision = updated.schemaRevision,
                record = updated,
                reasonCodes = verdict.reasons,
                validationMs = validationMs
            )
        }
    }

    /**
     * Full geometry autoconfig: clears the stale/incompatible state and rebuilds a record
     * from the frame's coherent live anchor evidence when available.
     */
    private fun rebuildCalibration(
        signature: DisplayGeometrySignature,
        pre: FrameResolution,
        geometry: FrameGeometry,
        extraReasons: List<String>,
        validationMs: Long? = null
    ): FrameResolution {
        val buildStart = System.nanoTime()
        store.clear(signature.stableKey)
        val record = buildRecord(signature, geometry)
        val rebuilt = record != null
        // A fresh signature (or a schema-stale record) builds a brand-new calibration;
        // an invalidated live record is replaced in place.
        val freshStart = pre.resolution == CalibrationResolution.UNAVAILABLE ||
            pre.resolution == CalibrationResolution.SCHEMA_INVALIDATED
        val resolution = when {
            rebuilt && freshStart -> CalibrationResolution.REBUILT
            rebuilt -> CalibrationResolution.INVALIDATED_REBUILT
            pre.resolution == CalibrationResolution.UNAVAILABLE -> CalibrationResolution.UNAVAILABLE
            else -> CalibrationResolution.INVALIDATED
        }
        if (rebuilt) store.save(requireNotNull(record))
        return FrameResolution(
            resolution = resolution,
            signatureKey = signature.stableKey,
            schemaRevision = record?.schemaRevision,
            record = record,
            reasonCodes = pre.reasonCodes + extraReasons + listOfNotNull(
                "calibration_persisted".takeIf { rebuilt },
                "insufficient_anchor_evidence".takeIf { !rebuilt }
            ),
            validationMs = validationMs ?: elapsedSince(buildStart)
        )
    }

    private fun validateBar(record: ScreenCalibrationRecord, geometry: FrameGeometry): BarVerdict {
        val liveBar = geometry.liveBarRect
        val live = liveBar?.let { NormalizedRect.fromRect(it, geometry.frameWidth, geometry.frameHeight) }
        val scrollConfirmed = geometry.detailCard != null &&
            abs(geometry.detailCard.top - record.detailCard.top) <= CalibrationTolerances.SCROLL_STATE_RATIO
        return when {
            live == null -> BarVerdict(ValidationOutcome.NOT_JUDGEABLE, listOf("live_bar_out_of_bounds"))
            !scrollConfirmed -> BarVerdict(ValidationOutcome.NOT_JUDGEABLE, listOf("scroll_state_unconfirmed"))
            else -> driftVerdict(record, live)
        }
    }

    private fun driftVerdict(record: ScreenCalibrationRecord, live: NormalizedRect): BarVerdict {
        val leftDelta = abs(live.left - record.hpBar.left)
        val topDelta = abs(live.top - record.hpBar.top)
        val reasons = buildList {
            if (leftDelta > CalibrationTolerances.BAR_LEFT_RATIO) add("bar_left_drift")
            if (topDelta > CalibrationTolerances.BAR_TOP_RATIO) add("bar_top_drift")
        }
        return when {
            reasons.isEmpty() -> BarVerdict(ValidationOutcome.HEALTHY, reasons)
            leftDelta > CalibrationTolerances.MAJOR_DRIFT_RATIO ||
                topDelta > CalibrationTolerances.MAJOR_DRIFT_RATIO ->
                BarVerdict(ValidationOutcome.MAJOR_DRIFT, reasons)
            else -> BarVerdict(ValidationOutcome.DRIFTED, reasons)
        }
    }

    /**
     * Seed eligibility: the persisted bar may only seed the extractor when the live frame's
     * detail-card top matches the calibrated scroll state, so a scrolled/shifted frame never
     * receives geometry positioned for another scroll offset.
     */
    private fun seedBarRectOrNull(record: ScreenCalibrationRecord, geometry: FrameGeometry): Rect? {
        val sameScrollState = geometry.detailCard != null &&
            abs(geometry.detailCard.top - record.detailCard.top) <= CalibrationTolerances.SCROLL_STATE_RATIO
        return record.hpBar.toRect(geometry.frameWidth, geometry.frameHeight).takeIf { sameScrollState }
    }

    /** Full geometry autoconfig from coherent live evidence: bar inside the detected card. */
    private fun buildRecord(
        signature: DisplayGeometrySignature,
        geometry: FrameGeometry
    ): ScreenCalibrationRecord? {
        val bar = geometry.liveBarRect?.let { NormalizedRect.fromRect(it, geometry.frameWidth, geometry.frameHeight) }
        val card = geometry.detailCard
        val nameBand = bar?.let(::derivedNameBand)
        val coherent = bar != null && card != null && bar.top > card.top && nameBand != null
        if (!coherent) return null
        val now = clock()
        return ScreenCalibrationRecord(
            signature = signature,
            hpBar = requireNotNull(bar),
            detailCard = requireNotNull(card),
            nameBand = requireNotNull(nameBand),
            createdAtMs = now,
            lastValidatedAtMs = now,
            validationSuccesses = 1,
            consecutiveValidationFailures = 0
        )
    }

    private fun elapsedSince(startNanos: Long): Long =
        ((System.nanoTime() - startNanos) / NANOS_PER_MS).coerceAtLeast(0)

    private data class BarVerdict(
        val outcome: ValidationOutcome,
        val reasons: List<String>
    )

    private enum class ValidationOutcome {
        HEALTHY,
        DRIFTED,
        MAJOR_DRIFT,
        NOT_JUDGEABLE
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}

/** Name band derived from the calibrated bar, mirroring the extractor's ratios. */
private fun derivedNameBand(bar: NormalizedRect): NormalizedRect = NormalizedRect(
    left = AnchoredScreenText.NAME_LEFT_RATIO,
    top = (bar.top - AnchoredScreenText.NAME_TOP_OFFSET_RATIO).coerceAtLeast(0f),
    right = AnchoredScreenText.NAME_RIGHT_RATIO,
    bottom = bar.top
)
