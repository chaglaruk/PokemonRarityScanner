package com.pokerarity.scanner.util.perf

/** Closed vocabulary prevents accidentally recording OCR, paths or scan/user content. */
enum class ScanTimingStage {
    REQUEST_ACCEPTED, CAPTURE_DEFERRED, CAPTURE_START, FRAME_ACQUIRE, FRAME_ACQUIRED,
    FRAME_COPY, PNG_STORAGE, FRAME_READY, CAPTURE_READY, PROCESSING_QUEUED, PROCESSING_START,
    DECODE, SCALE, CP_CROP_QUALITY, ROUTING, GEOMETRY, CALIBRATION_LOOKUP, CALIBRATION_VALIDATE,
    FRAME_OCR, OCR_WARM_UP, OCR_LAYOUT, OCR_TEXT, OCR_BLOCKS, RESOURCE_RECOVERY, FIELD_PARSE, PROFILE_RESOLVE,
    TUPLE_FEASIBILITY, DETAILED_LAUNCH, DETAILED_WORK, DETAILED_COMPLETION, DETAILED_WAIT,
    DETAILED_LAUNCH_TO_COMPLETION, FUSION, CONSISTENCY_GATE, VARIANT_CLASSIFIER, VISUAL_DETECTOR,
    PHASE2_CLASSIFIER, IDENTITY_AUTHORITY, RARITY, DECISION_COMPLETE, TERMINAL_CLAIM,
    PUBLICATION_QUEUED, MAIN_PUBLICATION, OVERLAY_RECEIVED, OVERLAY_CREATE, OVERLAY_WINDOW_ADDED,
    COMPOSE_FIRST_DRAW, ANDROID_VIEW_FIRST_DRAW, COMPOSE_FRAME_DELIVERY, OVERLAY_DISPOSE, DIAGNOSTIC_EXPORT,
    PERSISTENCE_ENQUEUED, PERSISTENCE_SAVE, TELEMETRY_ENQUEUE, PROCESSING_FINISHED
}

data class ScanTimingOwner(val requestId: Long, val attemptId: Int, val projectionEpoch: Long)
data class ScanTimingEvent(
    val stage: ScanTimingStage,
    val startNanos: Long,
    val endNanos: Long,
    val frameIndex: Int?,
    val captureSequenceId: Long?,
    val outcome: String = "completed",
    val inputWidth: Int? = null,
    val inputHeight: Int? = null
)

data class ScanTimingSnapshot(
    val owner: ScanTimingOwner,
    val clock: String = "android_elapsed_realtime_nanos",
    val schemaVersion: Int = 1,
    val events: List<ScanTimingEvent>,
    val droppedEvents: Int,
    val visibilitySemantics: String = "first_draw_is_not_confirmed_visible_pixels"
)

/** Thread-safe bounded diagnostic data only. It cannot grant publication or retry rights. */
class ScanTimingTrace(val owner: ScanTimingOwner, private val capacity: Int = DEFAULT_CAPACITY) {
    init { require(capacity > 0) }
    private val events = ArrayDeque<ScanTimingEvent>()
    private var dropped = 0

    @Synchronized fun record(event: ScanTimingEvent) {
        require(event.endNanos >= event.startNanos)
        if (events.size == capacity) {
            dropped++
            return
        }
        events.addLast(event)
    }

    @Synchronized fun snapshot(): ScanTimingSnapshot = ScanTimingSnapshot(
        owner = owner, events = events.toList(), droppedEvents = dropped
    )

    companion object { const val DEFAULT_CAPACITY = 256 }
}
