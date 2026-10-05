package com.pokerarity.scanner.service

import android.content.Intent

/**
 * Phase 2F bounded request ownership (plan section 6.7).
 *
 * One process-local coordinator owns logical scan-request identity end to end:
 * - a [ScanRequestToken] names one attempt of one logical request under one
 *   projection/session epoch (opaque monotonic process-local ids, never wall-clock);
 * - at most ONE active logical pipeline and at most ONE pending logical request
 *   exist; additional user clicks coalesce into the reserved pending request
 *   (explicit [RequestAcceptance.Coalesced] outcome, first-identity policy);
 * - accepting a newer USER/AUTO request immediately terminates the older pipeline as
 *   [TerminalOutcome.STALE_SUPPRESSED] — from that instant the older pipeline can
 *   neither publish nor retry, so an old result can never overwrite newer intent,
 *   regardless of coroutine/mutex scheduling;
 * - retries keep the same requestId, increment attemptId, and are refused once a
 *   newer request has been accepted;
 * - every request ends in exactly one explicit [TerminalOutcome]; terminal
 *   transitions are atomic and idempotent, and [claimTerminal] is THE stale-result
 *   publication guard (linearization point).
 *
 * The coordinator is pure Kotlin (no framework calls, no I/O, no timing) so the whole
 * ownership matrix is testable as a deterministic state machine. All transitions are
 * serialized on one small monitor; operations are O(1) and add no OCR/classifier work.
 */
enum class RequestOrigin { USER, RETRY, AUTO }

enum class TerminalOutcome {
    SUCCESS_PUBLISHED,
    FINAL_FAILURE,
    STALE_SUPPRESSED,
    PROJECTION_INVALIDATED,
    STOPPED
}

/** Opaque ownership identity of one logical-request attempt under one projection epoch. */
data class ScanRequestToken(
    val requestId: Long,
    val attemptId: Int,
    val projectionEpoch: Long,
    val origin: RequestOrigin
)

/** Result of presenting a new logical request to the coordinator. */
sealed class RequestAcceptance {
    /** The request was accepted with its own identity. */
    data class Accepted(val token: ScanRequestToken) : RequestAcceptance()

    /** The request coalesced into the already-reserved pending request (explicit outcome). */
    data class Coalesced(val survivingToken: ScanRequestToken) : RequestAcceptance()

    /** The scanner is stopped; the request is refused (fail closed). */
    object RejectedStopped : RequestAcceptance()
}

/** Bounded ownership state snapshot for diagnostics; contains no sensitive values. */
data class RequestOwnershipSnapshot(
    val requestId: Long,
    val attemptId: Int,
    val projectionEpoch: Long,
    val origin: RequestOrigin,
    val terminalOutcome: TerminalOutcome?,
    val captureSequenceId: Long? = null,
    val coalescedRequests: Int = 0
)

class ScanRequestCoordinator(private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS) {

    init {
        require(maxAttempts >= 1)
    }

    private class State(
        // Tracks the LATEST accepted attempt of this logical request (retries mutate it).
        var token: ScanRequestToken,
        var terminal: TerminalOutcome? = null,
        var captureSequenceId: Long? = null,
        var coalescedRequests: Int = 0
    )

    /**
     * Bounded slot bookkeeping (one active execution slot, one pending reservation slot,
     * a bounded recent-terminal history for diagnostics).
     */
    private class OwnershipSlots {
        var active: State? = null
        var pending: State? = null
        val finished = linkedMapOf<Long, State>()

        fun latestLive(): State? {
            pending?.let { return it }
            return active?.takeIf { it.terminal == null }
        }

        fun findByRequestId(requestId: Long): State? =
            listOfNotNull(active, pending).firstOrNull { it.token.requestId == requestId }
                ?: finished[requestId]

        fun liveStates(): List<State> = listOfNotNull(active, pending).filter { it.terminal == null }

        fun clearSlot(state: State) {
            if (active === state) {
                active = null
                pending?.let { reserved ->
                    active = reserved
                    pending = null
                }
            } else if (pending === state) {
                pending = null
            } else {
                return
            }
            if (state.terminal != null) {
                finished[state.token.requestId] = state
                while (finished.size > MAX_TRACKED_FINISHED) {
                    finished.remove(finished.keys.first())
                }
            }
        }

        /** Moves already-terminal slot occupants into the bounded finished history. */
        fun retireTerminalStates() {
            listOfNotNull(active, pending).filter { it.terminal != null }.forEach { state ->
                clearSlot(state)
            }
        }

        fun clearAll() {
            active = null
            pending = null
        }
    }

    private var stopped = false
    private var projectionEpoch = 0L
    private var nextRequestId = 0L
    private val slots = OwnershipSlots()

    /** Current projection/session epoch; increments on projection teardown and session begin. */
    val currentProjectionEpoch: Long get() = synchronized(this) { projectionEpoch }

    val isStopped: Boolean get() = synchronized(this) { stopped }

    /**
     * Scanner lifecycle boundary. Starting begins a fresh ownership session (old
     * callbacks from before the restart can no longer publish); stopping invalidates
     * all outstanding ownership so retries cannot resurrect a stopped request.
     */
    fun onScannerLifecycle(started: Boolean) {
        val lifecycleOutcome: Unit = synchronized(this) {
            if (started) {
                slots.liveStates().forEach { it.terminal = TerminalOutcome.STOPPED }
                slots.retireTerminalStates()
                slots.clearAll()
                stopped = false
                projectionEpoch++
            } else {
                if (!stopped) {
                    slots.liveStates().forEach { it.terminal = TerminalOutcome.STOPPED }
                    slots.retireTerminalStates()
                    stopped = true
                }
            }
        }
        return lifecycleOutcome
    }

    /**
     * Projection teardown/recreation: the epoch advances and every live request ends as
     * [TerminalOutcome.PROJECTION_INVALIDATED]. Capture callbacks/results from before
     * the recreation can no longer validate against the new epoch.
     */
    fun onProjectionInvalidated() = synchronized(this) {
        projectionEpoch++
        slots.liveStates().forEach { it.terminal = TerminalOutcome.PROJECTION_INVALIDATED }
        slots.retireTerminalStates()
        slots.clearAll()
    }

    /**
     * Accept a new logical request ([RequestOrigin.USER] or AUTO). Becomes the active
     * request on an idle scanner; otherwise it occupies the single pending slot and the
     * older live pipeline is terminated as STALE_SUPPRESSED immediately (newer intent is
     * visible before old publication). When a pending request is already reserved, the
     * request coalesces into it (first-identity policy; no orphan request ids).
     */
    fun acceptRequest(origin: RequestOrigin): RequestAcceptance = synchronized(this) {
        if (stopped) return RequestAcceptance.RejectedStopped
        slots.pending?.let { reserved ->
            reserved.coalescedRequests++
            return RequestAcceptance.Coalesced(reserved.token)
        }
        val token = ScanRequestToken(++nextRequestId, attemptId = 1, projectionEpoch, origin)
        val state = State(token)
        val liveActive = slots.active?.takeIf { it.terminal == null }
        if (liveActive != null) {
            // Newer intent accepted: the older pipeline loses publication rights NOW.
            liveActive.terminal = TerminalOutcome.STALE_SUPPRESSED
            slots.pending = state
        } else {
            slots.active = state
        }
        return RequestAcceptance.Accepted(token)
    }

    /**
     * Accept a retry of [token]: SAME requestId, attemptId+1. Refused when a newer
     * request has been accepted (user intent wins over stale retry), when the request
     * already terminated, when the projection epoch moved, when attempts are exhausted,
     * or when the scanner is stopped. A refused retry never rebroadcasts.
     */
    fun acceptRetry(token: ScanRequestToken): ScanRequestToken? = synchronized(this) {
        if (stopped) return null
        val latest = slots.latestLive() ?: return null
        if (latest.token.requestId != token.requestId) return null
        if (latest.terminal != null) return null
        if (token.projectionEpoch != projectionEpoch) return null
        if (token.attemptId >= maxAttempts) return null
        val retryToken = token.copy(attemptId = token.attemptId + 1, origin = RequestOrigin.RETRY)
        latest.token = retryToken
        return retryToken
    }

    /**
     * Present a completed capture (screenshot-ready) for [token]. Accepted only for the
     * latest live request under the current epoch - captures belonging to superseded
     * requests or to an old projection generation are rejected fail-closed.
     */
    fun acceptScreenshotReady(token: ScanRequestToken, captureSequenceId: Long?): Boolean = synchronized(this) {
        if (stopped) return false
        val latest = slots.latestLive() ?: return false
        if (latest.token.requestId != token.requestId) return false
        if (token.projectionEpoch != projectionEpoch) return false
        latest.captureSequenceId = captureSequenceId
        return true
    }

    /** True only when [token] is the latest live request under the current epoch. */
    fun hasPublicationRights(token: ScanRequestToken): Boolean = synchronized(this) {
        if (stopped) return false
        val latest = slots.latestLive() ?: return false
        return latest.token.requestId == token.requestId &&
            latest.terminal == null &&
            token.projectionEpoch == projectionEpoch
    }

    /**
     * THE stale-result publication guard. Atomically claims the request's terminal
     * publication (linearization point): the first accepted terminal claim wins; any
     * later claim for the same or another outcome is rejected, so a request can never
     * publish success and failure, and cannot publish twice. Returns false when the
     * request already lost ownership (newer request accepted, epoch moved, or stopped).
     */
    fun claimTerminal(token: ScanRequestToken, outcome: TerminalOutcome): Boolean = synchronized(this) {
        require(outcome == TerminalOutcome.SUCCESS_PUBLISHED || outcome == TerminalOutcome.FINAL_FAILURE)
        if (stopped) return false
        val latest = slots.latestLive() ?: return false
        if (latest.token.requestId != token.requestId || latest.terminal != null) return false
        latest.terminal = outcome
        slots.clearSlot(latest)
        return true
    }

    /**
     * Record that [token]'s pipeline ended without publication rights (its result was
     * suppressed as stale). Idempotent; always releases the state's ownership slot so a
     * pending request can be promoted.
     */
    fun suppressAsStale(token: ScanRequestToken) = synchronized(this) {
        val state = slots.findByRequestId(token.requestId) ?: return@synchronized
        if (state.terminal == null) {
            state.terminal = TerminalOutcome.STALE_SUPPRESSED
        }
        slots.clearSlot(state)
    }

    /** Bounded ownership snapshot for diagnostics; null when the token is no longer tracked. */
    fun snapshot(token: ScanRequestToken): RequestOwnershipSnapshot? = synchronized(this) {
        val state = slots.findByRequestId(token.requestId) ?: return null
        return RequestOwnershipSnapshot(
            requestId = state.token.requestId,
            attemptId = state.token.attemptId,
            projectionEpoch = state.token.projectionEpoch,
            origin = state.token.origin,
            terminalOutcome = state.terminal,
            captureSequenceId = state.captureSequenceId,
            coalescedRequests = state.coalescedRequests
        )
    }

    /** True when [token] is a live (accepted, non-terminal) active or pending request. */
    fun isLiveRequest(token: ScanRequestToken): Boolean = synchronized(this) {
        if (stopped) return false
        val state = slots.findByRequestId(token.requestId) ?: return false
        return state.terminal == null
    }

    companion object {
        /** 1 initial attempt + [com.pokerarity.scanner.util.ScanError.MAX_RETRIES] retries. */
        const val DEFAULT_MAX_ATTEMPTS = 3

        /** Bounded history of recently finished requests for ownership diagnostics. */
        const val MAX_TRACKED_FINISHED = 16
    }
}

/** Process-wide owner of the scan-request coordinator. */
object ScanRequests {
    @Volatile
    var coordinator: ScanRequestCoordinator = ScanRequestCoordinator()
        private set

    /** Test seam: replace the process-wide coordinator (Robolectric tests must reset). */
    fun resetForTest(coordinator: ScanRequestCoordinator = ScanRequestCoordinator()) {
        this.coordinator = coordinator
    }
}

// ── Broadcast envelope helpers (internal capture/scan contract) ──────────

/** Attaches bounded request-ownership metadata to an internal broadcast. */
fun Intent.putOwnershipExtras(token: ScanRequestToken) {
    putExtra(EXTRA_REQUEST_ID, token.requestId)
    putExtra(EXTRA_ATTEMPT_ID, token.attemptId)
    putExtra(EXTRA_PROJECTION_EPOCH, token.projectionEpoch)
    putExtra(EXTRA_REQUEST_ORIGIN, token.origin.name)
}

fun Intent.putCaptureSequenceExtra(captureSequenceId: Long) {
    putExtra(EXTRA_CAPTURE_SEQUENCE_ID, captureSequenceId)
}

/**
 * Parses ownership metadata from an internal broadcast; null when absent or malformed
 * (callers must fail closed on new runtime paths).
 */
fun Intent.parseOwnership(): ScanRequestToken? {
    val extras = extras ?: return null
    val requestId = extras.getLong(EXTRA_REQUEST_ID, -1L)
    val attemptId = extras.getInt(EXTRA_ATTEMPT_ID, -1)
    val epoch = extras.getLong(EXTRA_PROJECTION_EPOCH, -1L)
    val origin = extras.getString(EXTRA_REQUEST_ORIGIN)
        ?.let { runCatching { RequestOrigin.valueOf(it) }.getOrNull() }
        ?: RequestOrigin.USER
    return ScanRequestToken(requestId, attemptId, epoch, origin)
        ?.takeIf { token -> token.requestId > 0L && token.attemptId > 0 && token.projectionEpoch >= 0L }
}

fun Intent.parseCaptureSequenceId(): Long? {
    val value = extras?.getLong(EXTRA_CAPTURE_SEQUENCE_ID, -1L) ?: -1L
    return value.takeIf { it > 0L }
}

internal const val EXTRA_REQUEST_ID = "extra_request_id"
internal const val EXTRA_ATTEMPT_ID = "extra_attempt_id"
internal const val EXTRA_PROJECTION_EPOCH = "extra_projection_epoch"
internal const val EXTRA_REQUEST_ORIGIN = "extra_request_origin"
internal const val EXTRA_CAPTURE_SEQUENCE_ID = "extra_capture_sequence_id"
