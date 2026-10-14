# Debug scan timing protocol

Instrumentation observes the existing pipeline. It grants no recognition, request,
retry, species, variant or IV authority, and does not change the OCR provider,
thresholds, storage format, fallback conditions or model policy.

All new timestamps use Android `SystemClock.elapsedRealtimeNanos`. Legacy pipeline
duration fields now use milliseconds derived from that same source. Calibration
duration timing receives that clock explicitly; its persistent wall-clock dates and
diagnostic IDs remain wall dates/IDs, never performance timestamps. Image timestamps,
Choreographer frame times, uptime and host/wall time are not converted or mixed into
these spans. Compose callbacks are observed by reading the elapsed clock at callback
delivery, not using the callback's frameTimeNanos as an elapsed timestamp.

Each bounded snapshot owns a requestId, attemptId and projectionEpoch. Each event
inherits that owner and records frameIndex/captureSequenceId where applicable; null
frame means attempt-wide work. Coroutine context propagation preserves the binding
across dispatcher switches; detached detailed and Main publication jobs receive an
explicit binding. The diagnostic store is not another ownership coordinator.

Logical REQUEST_ACCEPTED, CAPTURE_START, FRAME_READY/CAPTURE_READY,
PROCESSING_START, DECISION_COMPLETE, MAIN_PUBLICATION, Android view first draw and
Compose first draw are distinct marks. A missing mark stays missing. DETAILED_WORK,
DETAILED_WAIT and DETAILED_LAUNCH_TO_COMPLETION overlap: never sum them. Likewise,
parent spans include child work. Use endpoints/interval unions for critical-path
elapsed time, and report individual resource recovery/OCR calls separately.

First draw and one delivered Compose frame do not prove nontransparent pixels,
successful animation, an unobscured display or first actual visible pixels. Qualified
visibility requires passive physical display screenshots/video, with capture start
and completion brackets in the same elapsed clock or a validated external conversion.
Report bounds, sample count, screenshot overhead and uncertainty. Do not estimate
warm P50/P95 from two historical observations or compare component/replay timings
as though they were fresh capture-to-visible-card latency.

Only debug builds collect these traces. Release builds cannot enable collection.
No new logcat or runtime telemetry fields are emitted. Events use a closed stage
vocabulary and contain no paths, OCR, species, screen pixels, user/device identifiers
or credentials. Limits: eight recent attempts, 256 events per attempt (excess events
counted and discarded), eight local cache files, one background writer and eight
queued writes. Output is `cache/debug_scan_timing` in the app's local sandbox. These
files are neither exported nor uploaded by the telemetry pipeline. Traces with
dropped events cannot establish a complete timing baseline.

Optional export snapshots run outside the Main drawing critical path. This does
not relocate existing synchronous OCR diagnostic export; its cost is measured as
DIAGNOSTIC_EXPORT. The historical “overlay dispatched” message occurs after that
work and is not a visibility timestamp. Persistence enqueue is also distinct from
database completion.

Qualification must pair identical inputs/outcomes and ownership with instrumentation
enabled and disabled, retain genuine image hashes, test cancellation/dispatcher
binding/bounds, and keep raw evidence outside Git. A memory sample is not a peak;
CPU duration is a different explicitly labeled quantity, not an elapsed timestamp.
Storage/in-memory or scheduling optimizations need separate matched correctness,
bitmap-lifetime, cancellation, memory and visible-latency evidence before promotion.

Completed background saves request another timing snapshot after their span closes.
Cache updates use AtomicFile so readers see the previous or completed JSON, rather
than a partially overwritten file. Processing finish still does not await database
completion or prove visibility.
