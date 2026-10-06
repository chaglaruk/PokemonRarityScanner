// Purpose: Phase 2F deterministic pure ownership state-machine tests (plan 6.7 matrix).
package com.pokerarity.scanner.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The full bounded-ownership matrix from plan section 6.7, as a deterministic pure JVM
 * state-machine suite: no Android, no sleeps, no timing — only explicit state
 * transitions on [ScanRequestCoordinator].
 */
class ScanRequestCoordinatorTest {

    private fun coordinator() = ScanRequestCoordinator()

    private fun userToken(c: ScanRequestCoordinator): ScanRequestToken =
        (c.acceptRequest(RequestOrigin.USER) as RequestAcceptance.Accepted).token

    // 1. first user request becomes current
    @Test
    fun firstUserRequestBecomesCurrent() {
        val c = coordinator()
        val token = userToken(c)

        assertTrue(c.hasPublicationRights(token))
        assertTrue(c.isLiveRequest(token))
        assertEquals(RequestOrigin.USER, token.origin)
        assertEquals(1, token.attemptId)
    }

    // 2. second user request while active becomes the sole pending request
    @Test
    fun secondUserRequestWhileActiveBecomesSolePending() {
        val c = coordinator()
        val a = userToken(c)
        val b = userToken(c)

        assertNotEquals(a.requestId, b.requestId)
        assertFalse("older pipeline must lose publication rights immediately", c.hasPublicationRights(a))
        assertTrue(c.hasPublicationRights(b))
        assertEquals(TerminalOutcome.STALE_SUPPRESSED, c.snapshot(a)?.terminalOutcome)
    }

    // 3. burst requests remain bounded to one pending request
    @Test
    fun burstRequestsCoalesceIntoTheSinglePendingRequest() {
        val c = coordinator()
        userToken(c)
        val firstPending = userToken(c)
        repeat(5) {
            val acceptance = c.acceptRequest(RequestOrigin.USER)
            assertTrue(acceptance is RequestAcceptance.Coalesced)
            assertEquals(firstPending.requestId, (acceptance as RequestAcceptance.Coalesced).survivingToken.requestId)
        }
        assertEquals(5, c.snapshot(firstPending)?.coalescedRequests)
        assertEquals(2L, c.snapshot(firstPending)?.requestId)
    }

    // 4. pending promotion after active terminal
    @Test
    fun pendingPromotesAfterActiveTerminal() {
        val c = coordinator()
        val a = userToken(c)
        val b = userToken(c)

        assertTrue(c.claimTerminal(a, TerminalOutcome.FINAL_FAILURE).not())
        c.suppressAsStale(a)

        assertTrue("pending must be promoted once the active slot clears", c.hasPublicationRights(b))
        assertNull(c.snapshot(b)?.terminalOutcome)
    }

    // 5. newer accepted user request makes older non-terminal publication stale
    // 6. old request cannot publish overlay (publication claim is the guard)
    @Test
    fun olderPublicationClaimIsRejectedOnceNewerRequestIsAccepted() {
        val c = coordinator()
        val a = userToken(c)
        userToken(c)

        assertFalse(c.claimTerminal(a, TerminalOutcome.SUCCESS_PUBLISHED))
        c.suppressAsStale(a)
        assertEquals(TerminalOutcome.STALE_SUPPRESSED, c.snapshot(a)?.terminalOutcome)
    }

    // 7. old request cannot save / 8. cannot enqueue telemetry:
    // save/telemetry happen only after a successful claimTerminal, so the guard above
    // covers them structurally; asserted end-to-end in ScanManagerRequestOwnershipTest.

    // 9. old request cannot emit late error
    @Test
    fun oldRequestCannotRetryOrFailAfterNewerRequest() {
        val c = coordinator()
        val a = userToken(c)
        val b = userToken(c)

        assertNull("stale retry must be refused", c.acceptRetry(a))
        assertFalse(c.claimTerminal(a, TerminalOutcome.FINAL_FAILURE))
        assertTrue(c.hasPublicationRights(b))
    }

    // 10. retry keeps same requestId and increments attemptId
    @Test
    fun retryKeepsRequestIdAndIncrementsAttempt() {
        val c = coordinator()
        val a = userToken(c)

        val retry = c.acceptRetry(a)!!

        assertEquals(a.requestId, retry.requestId)
        assertEquals(a.attemptId + 1, retry.attemptId)
        assertEquals(RequestOrigin.RETRY, retry.origin)
        assertTrue(c.hasPublicationRights(retry))
    }

    // 11. retry with no newer request is allowed
    @Test
    fun retryWithoutNewerRequestProceeds() {
        val c = coordinator()
        val a = userToken(c)
        val retry = c.acceptRetry(a)!!

        assertTrue(c.hasPublicationRights(retry))
        assertEquals(a.requestId, retry.requestId)
    }

    // 12. retry loses to an already accepted newer user request
    @Test
    fun retryLosesToAcceptedNewerUserRequest() {
        val c = coordinator()
        val a = userToken(c)
        val retry1 = c.acceptRetry(a)!!
        userToken(c)

        assertNull("retry of a superseded request must be refused", c.acceptRetry(retry1))
        assertFalse(c.hasPublicationRights(retry1))
        assertTrue(c.isLiveRequest(a) || c.snapshot(a)?.terminalOutcome != null)
    }

    // 13. retry counter/attempt state does not leak between requests
    @Test
    fun attemptStateDoesNotLeakBetweenRequests() {
        val c = ScanRequestCoordinator(maxAttempts = 3)
        val a = userToken(c)
        val a2 = c.acceptRetry(a)!!
        val a3 = c.acceptRetry(a2)!!
        assertEquals(3, a3.attemptId)
        assertNull("max attempts reached", c.acceptRetry(a3))

        // A brand-new request starts fresh at attempt 1.
        c.suppressAsStale(a3)
        val b = userToken(c)
        assertEquals(1, b.attemptId)
        assertEquals(a.requestId + 1, b.requestId)
        val bRetry = c.acceptRetry(b)!!
        assertEquals(2, bRetry.attemptId)
    }

    // 14. old projection epoch screenshot is rejected
    @Test
    fun oldProjectionEpochScreenshotReadyIsRejected() {
        val c = coordinator()
        val a = userToken(c)

        c.onProjectionInvalidated()

        assertFalse(c.acceptScreenshotReady(a, captureSequenceId = 1L))
        assertFalse(c.hasPublicationRights(a))
        assertEquals(TerminalOutcome.PROJECTION_INVALIDATED, c.snapshot(a)?.terminalOutcome)
    }

    // 15. projection recreation invalidates old capture callbacks/results
    @Test
    fun projectionInvalidationAlsoClearsPendingRequests() {
        val c = coordinator()
        val a = userToken(c)
        val b = userToken(c)

        c.onProjectionInvalidated()

        // Exactly-once terminals: A was already stale-superseded by B's acceptance and
        // keeps that outcome; the live B is invalidated by the projection teardown.
        assertEquals(TerminalOutcome.STALE_SUPPRESSED, c.snapshot(a)?.terminalOutcome)
        assertEquals(TerminalOutcome.PROJECTION_INVALIDATED, c.snapshot(b)?.terminalOutcome)
        assertFalse(c.isLiveRequest(b))
        val fresh = userToken(c)
        assertTrue("new request carries the new epoch", fresh.projectionEpoch > a.projectionEpoch)
    }

    // 16. stop/restart invalidates old request ownership
    @Test
    fun stopInvalidatesOwnershipAndRestartStartsFreshSession() {
        val c = coordinator()
        val a = userToken(c)
        val oldEpoch = a.projectionEpoch

        c.onScannerLifecycle(started = false)

        assertTrue(c.isStopped)
        assertFalse(c.acceptScreenshotReady(a, 1L))
        assertFalse(c.hasPublicationRights(a))
        assertEquals(TerminalOutcome.STOPPED, c.snapshot(a)?.terminalOutcome)
        assertTrue(c.acceptRequest(RequestOrigin.USER) is RequestAcceptance.RejectedStopped)

        c.onScannerLifecycle(started = true)

        assertFalse(c.isStopped)
        val fresh = userToken(c)
        assertTrue(fresh.projectionEpoch > oldEpoch)
        assertTrue(c.hasPublicationRights(fresh))
        assertFalse("old request must stay invalid after restart", c.hasPublicationRights(a))
    }

    // 17. one request has exactly one terminal outcome
    // 18. duplicate terminal callbacks are idempotently rejected
    @Test
    fun terminalOutcomeIsExactlyOnceAndIdempotent() {
        val c = coordinator()
        val a = userToken(c)

        assertTrue(c.claimTerminal(a, TerminalOutcome.SUCCESS_PUBLISHED))
        assertFalse("second terminal claim must be rejected", c.claimTerminal(a, TerminalOutcome.SUCCESS_PUBLISHED))
        assertFalse("success then failure must be impossible", c.claimTerminal(a, TerminalOutcome.FINAL_FAILURE))
        assertEquals(TerminalOutcome.SUCCESS_PUBLISHED, c.snapshot(a)?.terminalOutcome)
    }

    // 19. screenshot-ready with wrong request/attempt metadata is rejected
    @Test
    fun screenshotReadyForUnknownRequestIsRejected() {
        val c = coordinator()
        val a = userToken(c)
        val rogue = a.copy(requestId = a.requestId + 100)

        assertFalse(c.acceptScreenshotReady(rogue, 1L))
        assertTrue(c.acceptScreenshotReady(a, 1L))
    }

    @Test
    fun oldAttemptCannotCapturePublishRetryOrSuppressTheCurrentRetry() {
        val c = coordinator()
        val attempt1 = userToken(c)
        val attempt2 = c.acceptRetry(attempt1)!!

        assertFalse("old attempt screenshot must be rejected", c.acceptScreenshotReady(attempt1, 1L))
        assertFalse("old attempt must lose publication rights", c.hasPublicationRights(attempt1))
        assertFalse(
            "old attempt cannot claim terminal publication",
            c.claimTerminal(attempt1, TerminalOutcome.SUCCESS_PUBLISHED)
        )
        assertNull("old attempt cannot schedule another retry", c.acceptRetry(attempt1))

        // A late callback from attempt 1 must not terminalize/clear attempt 2.
        c.suppressAsStale(attempt1)
        assertTrue(c.isLiveRequest(attempt2))
        assertTrue(c.hasPublicationRights(attempt2))
        assertTrue(c.acceptScreenshotReady(attempt2, 2L))
    }

    @Test
    fun screenshotReadyRequiresPositiveCaptureSequenceId() {
        val c = coordinator()
        val a = userToken(c)

        assertFalse(c.acceptScreenshotReady(a, 0L))
        assertFalse(c.acceptScreenshotReady(a, -1L))
        assertTrue(c.acceptScreenshotReady(a, 1L))
    }

    // 20. unowned new-runtime screenshot fails closed (ScanManager-level receiver test
    // covers the missing-metadata path; the coordinator refuses unknown ids as above).

    // 21. same request's valid capture can proceed through ScanManager
    @Test
    fun validCaptureOfCurrentRequestIsAccepted() {
        val c = coordinator()
        val a = userToken(c)

        assertTrue(c.acceptScreenshotReady(a, captureSequenceId = 7L))
        assertEquals(7L, c.snapshot(a)?.captureSequenceId)
        assertTrue(c.hasPublicationRights(a))
    }

    // 22. latest valid request publishes exactly once
    @Test
    fun latestRequestPublishesExactlyOnce() {
        val c = coordinator()
        val a = userToken(c)
        val b = userToken(c)

        c.suppressAsStale(a)
        assertTrue(c.claimTerminal(b, TerminalOutcome.SUCCESS_PUBLISHED))
        assertFalse(c.claimTerminal(b, TerminalOutcome.SUCCESS_PUBLISHED))
        assertEquals(TerminalOutcome.SUCCESS_PUBLISHED, c.snapshot(b)?.terminalOutcome)
    }

    @Test
    fun pendingTerminalDoesNotDropTheSupersededActiveRequestHistory() {
        val c = coordinator()
        val a = userToken(c)
        val b = userToken(c)

        // B is the latest live request while A still occupies the stale active slot.
        assertTrue(c.claimTerminal(b, TerminalOutcome.FINAL_FAILURE))

        assertEquals(TerminalOutcome.STALE_SUPPRESSED, c.snapshot(a)?.terminalOutcome)
        assertEquals(TerminalOutcome.FINAL_FAILURE, c.snapshot(b)?.terminalOutcome)

        val fresh = userToken(c)
        assertTrue(c.hasPublicationRights(fresh))
        assertEquals(TerminalOutcome.STALE_SUPPRESSED, c.snapshot(a)?.terminalOutcome)
    }

    @Test
    fun autoRequestIsOwnedAndSupersedesLikeUser() {
        val c = coordinator()
        val auto = (c.acceptRequest(RequestOrigin.AUTO) as RequestAcceptance.Accepted).token

        assertEquals(RequestOrigin.AUTO, auto.origin)
        assertTrue(c.hasPublicationRights(auto))

        val user = userToken(c)
        assertFalse("newer user request supersedes auto pipeline", c.hasPublicationRights(auto))
        assertTrue(c.hasPublicationRights(user))
    }

    @Test
    fun pendingRequestCaptureIsAcceptedWhileOldPipelineStillExecuting() {
        val c = coordinator()
        userToken(c)
        val b = userToken(c)

        // B is pending while the old pipeline is (logically) still executing.
        assertTrue(c.acceptScreenshotReady(b, captureSequenceId = 2L))
        assertTrue(c.hasPublicationRights(b))
    }

    @Test
    fun screenshotReadyAfterTerminalIsRejected() {
        val c = coordinator()
        val a = userToken(c)
        c.suppressAsStale(a)

        assertFalse(c.acceptScreenshotReady(a, 1L))
    }

    @Test
    fun suppressedRequestReleasesSlotForSubsequentFreshRequests() {
        val c = coordinator()
        val a = userToken(c)
        val b = userToken(c)
        c.suppressAsStale(a)

        // B is the promoted active request; per matrix item 2 a further click while a
        // request is ACTIVE supersedes it (only clicks while PENDING coalesce), so the
        // pending slot stays bounded at one.
        val c2Token = userToken(c)
        assertNotEquals(b.requestId, c2Token.requestId)
        assertFalse(c.hasPublicationRights(b))
        assertTrue(c.hasPublicationRights(c2Token))
        assertEquals(TerminalOutcome.STALE_SUPPRESSED, c.snapshot(b)?.terminalOutcome)
    }

    @Test
    fun projectionEpochMatchesRequirementForPublicationRights() {
        val c = coordinator()
        val a = userToken(c)
        val epochAtAcceptance = a.projectionEpoch

        // Simulate the epoch advancing without invalidation semantics (session begin).
        c.onScannerLifecycle(started = true)

        assertFalse(
            "publication rights must require the current epoch",
            c.hasPublicationRights(a.copy(projectionEpoch = epochAtAcceptance))
        )
    }
}
