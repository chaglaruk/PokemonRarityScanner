package com.pokerarity.scanner.service

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OwnedFrameHandoffTest {
    private val directory = Files.createTempDirectory("owned-frame").toFile()
    private fun token() =
        (ScanRequests.coordinator.acceptRequest(RequestOrigin.USER) as RequestAcceptance.Accepted).token
    private fun source() = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    @Before fun setup() {
        ScanRequests.resetForTest()
        ScanRequests.coordinator.onScannerLifecycle(true)
        OwnedFrameHandoff.enabled = true
    }
    @After fun cleanup() { OwnedFrameHandoff.invalidate(); OwnedFrameHandoff.enabled = false }

    @Test fun exactPixelsAndOneClaimWithIdempotentLeaseClose() {
        val token = token(); val bitmap = source()
        val path = OwnedFrameHandoff.capture(bitmap, File(directory, "one.png"), token, 17, 0)
        val leases = requireNotNull(OwnedFrameHandoff.claim(listOf(path), token, 17))
        assertFalse(leases.single().pixels.isMutable)
        assertEquals(Color.RED, leases.single().pixels.getPixel(0, 0))
        assertNull(OwnedFrameHandoff.claim(listOf(path), token, 17))
        OwnedFrameHandoff.awaitEvidence(path)
        OwnedFrameHandoff.finish(token)
        assertFalse(leases.single().pixels.isRecycled)
        leases.single().close(); leases.single().close()
        assertTrue(leases.single().pixels.isRecycled)
        bitmap.recycle()
    }
    @Test fun epochInvalidationRefusesNewReadsWithoutRecyclingAnActiveReader() {
        val token = token(); val source = source()
        val path = OwnedFrameHandoff.capture(source, File(directory, "epoch.png"), token, 17, 0)
        val leases = requireNotNull(OwnedFrameHandoff.claim(listOf(path), token, 17))
        OwnedFrameHandoff.awaitEvidence(path)
        ScanRequests.coordinator.onProjectionInvalidated()
        OwnedFrameHandoff.invalidate()
        assertNull(OwnedFrameHandoff.pixels(path, token))
        assertFalse(leases.single().pixels.isRecycled)
        leases.single().close(); assertTrue(leases.single().pixels.isRecycled)
        source.recycle()
    }
    @Test fun wrongAttemptAndSequenceCannotClaim() {
        val token = token(); val source = source()
        val path = OwnedFrameHandoff.capture(source, File(directory, "binding.png"), token, 17, 0)
        assertNull(OwnedFrameHandoff.claim(listOf(path), token.copy(attemptId = 2), 17))
        assertNull(OwnedFrameHandoff.claim(listOf(path), token, 18))
        OwnedFrameHandoff.awaitEvidence(path); source.recycle()
    }
    @Test fun thirdSourceRefusedAndEvidenceRemainsForBothFrames() {
        val token = token(); val source = source()
        val paths = (0..1).map { OwnedFrameHandoff.capture(source, File(directory, "$it.png"), token, 17, it) }
        try { OwnedFrameHandoff.capture(source, File(directory, "third.png"), token, 17, 0); fail() }
        catch (_: IllegalStateException) { }
        paths.forEach { OwnedFrameHandoff.awaitEvidence(it); assertTrue(File(it).isFile) }
        assertEquals(2L, OwnedFrameHandoff.metrics()["liveSourceFrames"])
        source.recycle()
    }
    @Test fun nativeTaskCopyRemainsValidAfterCallerPixelsAreRecycled() {
        val source = source(); val lease = OwnedFrameHandoff.taskCopy(source)
        source.recycle()
        assertEquals(Color.RED, lease.pixels.getPixel(0, 0))
        lease.close(); lease.close(); assertTrue(lease.pixels.isRecycled)
    }
    @Test fun outstandingNativeCopiesAreBoundedAndReturnSlots() {
        val source = source(); val leases = (1..4).map { OwnedFrameHandoff.taskCopy(source) }
        try { OwnedFrameHandoff.taskCopy(source); fail() } catch (_: IllegalStateException) { }
        leases.forEach { it.close() }
        assertEquals(0L, OwnedFrameHandoff.metrics()["liveTaskCopies"])
        OwnedFrameHandoff.taskCopy(source).close(); source.recycle()
    }
    @Test fun staleRequestCannotAddFrame() {
        val old = token(); token(); val source = source()
        try { OwnedFrameHandoff.capture(source, File(directory, "stale.png"), old, 17, 0); fail() }
        catch (_: IllegalStateException) { }
        source.recycle()
    }
    @Test fun pendingAttemptCanCaptureWhileStalePipelineReadersStillOwnPixels() {
        val source = source(); val a = token()
        val pathsA = (0..1).map {
            OwnedFrameHandoff.capture(source, File(directory, "a-$it.png"), a, 17, it)
        }
        val leasesA = requireNotNull(OwnedFrameHandoff.claim(pathsA, a, 17))
        pathsA.forEach(OwnedFrameHandoff::awaitEvidence)
        val b = token()
        val pathsB = (0..1).map {
            OwnedFrameHandoff.capture(source, File(directory, "b-$it.png"), b, 18, it)
        }
        assertEquals(4L, OwnedFrameHandoff.metrics()["liveSourceFrames"])
        assertNull(OwnedFrameHandoff.pixels(pathsA[0], a))
        assertFalse(leasesA[0].pixels.isRecycled)
        pathsB.forEach(OwnedFrameHandoff::awaitEvidence)
        leasesA.forEach { it.close() }; OwnedFrameHandoff.finish(b)
        assertEquals(0L, OwnedFrameHandoff.metrics()["liveSourceFrames"])
        source.recycle()
    }
    @Test fun detachedDetailedReaderOutlivesPipelineRootUntilItCloses() {
        val source = source(); val token = token()
        val path = OwnedFrameHandoff.capture(source, File(directory, "detached.png"), token, 17, 0)
        val pipeline = requireNotNull(OwnedFrameHandoff.claim(listOf(path), token, 17))
        val detailed = requireNotNull(OwnedFrameHandoff.pixels(path, token, "detailed"))
        OwnedFrameHandoff.awaitEvidence(path)
        pipeline.forEach { it.close() }; OwnedFrameHandoff.finish(token)
        assertFalse(detailed.isRecycled)
        assertTrue(OwnedFrameHandoff.releaseRead(detailed, "detailed"))
        assertTrue(detailed.isRecycled)
        assertEquals(0L, OwnedFrameHandoff.metrics()["liveReaderLeases"])
        source.recycle()
    }
    @Test fun expiryStopsManagedWorkAndPreservesReaderPixelsUntilRelease() {
        val source = source(); val token = token()
        val path = OwnedFrameHandoff.capture(source, File(directory, "timeout.png"), token, 17, 0)
        val pipeline = requireNotNull(OwnedFrameHandoff.claim(listOf(path), token, 17))
        OwnedFrameHandoff.awaitEvidence(path)
        val job = kotlinx.coroutines.Job()
        OwnedFrameHandoff.trackOwnerJob(token, job)
        OwnedFrameHandoff.expireBefore(Long.MAX_VALUE)
        assertTrue(job.isCancelled)
        assertEquals(TerminalOutcome.FINAL_FAILURE, ScanRequests.coordinator.snapshot(token)?.terminalOutcome)
        assertFalse(pipeline.single().pixels.isRecycled)
        pipeline.single().close(); assertTrue(pipeline.single().pixels.isRecycled)
        assertTrue(OwnedFrameHandoff.metrics()["expiryQueueSize"]!! <= 1)
        source.recycle()
    }
    @Test fun prestartCancellationCallbackCannotReleaseRootTwiceDuringPruning() {
        val source = source(); val a = token()
        val path = OwnedFrameHandoff.capture(source, File(directory, "prestart-a.png"), a, 17, 0)
        val pipeline = requireNotNull(OwnedFrameHandoff.claim(listOf(path), a, 17))
        OwnedFrameHandoff.awaitEvidence(path)
        val job = kotlinx.coroutines.Job()
        job.invokeOnCompletion { pipeline.forEach { it.close() }; OwnedFrameHandoff.finish(a) }
        OwnedFrameHandoff.trackOwnerJob(a, job)
        val b = token()
        val next = OwnedFrameHandoff.capture(source, File(directory, "prestart-b.png"), b, 18, 0)
        assertTrue(job.isCancelled)
        assertTrue(pipeline.single().pixels.isRecycled)
        OwnedFrameHandoff.awaitEvidence(next)
        assertEquals(1L, OwnedFrameHandoff.metrics()["liveSourceFrames"])
        source.recycle()
    }
    @Test fun completedRecognitionReleasesPixelsWithoutCancellingAnAcceptedSaveTail() {
        val source = source(); val token = token()
        val path = OwnedFrameHandoff.capture(source, File(directory, "success.png"), token, 17, 0)
        val pipeline = requireNotNull(OwnedFrameHandoff.claim(listOf(path), token, 17))
        OwnedFrameHandoff.awaitEvidence(path)
        pipeline.forEach { it.close() }; OwnedFrameHandoff.retireRecognitionRoots(token)
        assertEquals(0L, OwnedFrameHandoff.metrics()["liveSourceFrames"])
        assertTrue(ScanRequests.coordinator.claimTerminal(token, TerminalOutcome.SUCCESS_PUBLISHED))
        val job = kotlinx.coroutines.Job(); OwnedFrameHandoff.trackOwnerJob(token, job)
        OwnedFrameHandoff.expireBefore(Long.MAX_VALUE)
        assertFalse(job.isCancelled)
        OwnedFrameHandoff.awaitEvidence(path)
        OwnedFrameHandoff.finish(token); source.recycle()
    }
    @Test fun allocationPressureSignalsBackpressureBeforeAnExtraCopyIsAllocated() {
        val source = source(); val a = token()
        val paths = (0..1).map { OwnedFrameHandoff.capture(source, File(directory,"budget-a-$it.png"),a,17,it) }
        val pipeline = requireNotNull(OwnedFrameHandoff.claim(paths,a,17))
        paths.forEach(OwnedFrameHandoff::awaitEvidence)
        val b = token()
        val second = (0..1).map { OwnedFrameHandoff.capture(source,File(directory,"budget-b-$it.png"),b,18,it) }
        second.forEach(OwnedFrameHandoff::awaitEvidence)
        assertFalse(OwnedFrameHandoff.hasCapacityForAttempt(32,32))
        pipeline.forEach { it.close() }
        assertTrue(OwnedFrameHandoff.hasCapacityForAttempt(32,32))
        source.recycle()
    }
}
