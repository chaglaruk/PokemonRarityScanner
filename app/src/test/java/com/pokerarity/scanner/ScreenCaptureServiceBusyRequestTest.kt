package com.pokerarity.scanner

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Looper
import android.os.Handler
import android.graphics.Point
import com.pokerarity.scanner.service.OverlayService
import com.pokerarity.scanner.service.RequestAcceptance
import com.pokerarity.scanner.service.RequestOrigin
import com.pokerarity.scanner.service.ScanRequests
import com.pokerarity.scanner.service.ScanRequestToken
import com.pokerarity.scanner.service.ScreenCaptureService
import com.pokerarity.scanner.service.TerminalOutcome
import com.pokerarity.scanner.service.parseOwnership
import com.pokerarity.scanner.service.putOwnershipExtras
import com.pokerarity.scanner.util.RateLimiter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
class ScreenCaptureServiceBusyRequestTest {
    private lateinit var service: ScreenCaptureService

    @Before fun prepareProjection() {
        service = Robolectric.buildService(ScreenCaptureService::class.java).create().get()
        installProjection()
    }

    private fun installProjection() {
        // Fake only the platform consent token; exercise the real receiver and capture callbacks.
        @Suppress("UNCHECKED_CAST")
        val tokenClass = Class.forName("android.media.projection.IMediaProjection") as Class<Any>
        val projection = ReflectionHelpers.callConstructor(MediaProjection::class.java,
            ClassParameter.from(Context::class.java, service),
            ClassParameter.from(tokenClass, ReflectionHelpers.createNullProxy(tokenClass)))
        val reader = ImageReader.newInstance(64, 64, PixelFormat.RGBA_8888, 2)
        val display = (service.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .createVirtualDisplay("capture-test", 64, 64, 160, reader.surface, 0)
        setField("mediaProjection", projection)
        setField("imageReader", reader)
        setField("virtualDisplay", display)
        assertTrue(display != null)
    }

    @After fun destroy() {
        service.onDestroy()
        ScanRequests.resetForTest()
    }

    // ── Phase 2F: request-ownership extensions (behavior preserved, now owned) ──

    @Test
    fun bareRequestsAreGivenSynthesizedOwnershipAndCarryItToScreenshotReady() {
        request()
        drain()
        val ready = shadowOf(service.application).broadcastIntents
            .last { it.action == ScreenCaptureService.ACTION_SCREENSHOT_READY }
        val ownership = ready.parseOwnership()
        assertNotNull("screenshot-ready must carry ownership metadata", ownership)
        assertTrue(ownership?.requestId ?: 0L > 0L)
        assertEquals(1, ownership?.attemptId)
    }

    @Test
    fun ownedRequestMetadataFlowsThroughCaptureToScreenshotReady() {
        val accepted = ScanRequests.coordinator.acceptRequest(RequestOrigin.USER)
        val token = (accepted as RequestAcceptance.Accepted).token
        val receiver = ScreenCaptureService::class.java.getDeclaredField("captureReceiver").apply {
            isAccessible = true
        }.get(service) as BroadcastReceiver
        receiver.onReceive(
            service,
            Intent(OverlayService.ACTION_CAPTURE_REQUESTED).apply { putOwnershipExtras(token) }
        )
        drain()
        val ready = shadowOf(service.application).broadcastIntents
            .last { it.action == ScreenCaptureService.ACTION_SCREENSHOT_READY }
        val carried = ready.parseOwnership()
        assertNotNull(carried)
        assertEquals(token.requestId, carried?.requestId)
        assertEquals(token.projectionEpoch, carried?.projectionEpoch)
        assertEquals(1L, ready.getLongExtra("extra_capture_sequence_id", -1L))
    }

    @Test
    fun partiallyOwnedCaptureRequestFailsClosedInsteadOfSynthesizingANewRequest() {
        val receiver = ScreenCaptureService::class.java.getDeclaredField("captureReceiver").apply {
            isAccessible = true
        }.get(service) as BroadcastReceiver
        receiver.onReceive(
            service,
            Intent(OverlayService.ACTION_CAPTURE_REQUESTED).apply {
                putExtra("extra_request_id", 42L)
            }
        )
        drain()

        assertEquals(0, completedSequences())
        assertFalse(capturing())
    }

    @Test
    fun invalidOriginInOwnedCaptureRequestFailsClosed() {
        val token = (ScanRequests.coordinator.acceptRequest(RequestOrigin.USER)
            as RequestAcceptance.Accepted).token
        val receiver = ScreenCaptureService::class.java.getDeclaredField("captureReceiver").apply {
            isAccessible = true
        }.get(service) as BroadcastReceiver
        receiver.onReceive(
            service,
            Intent(OverlayService.ACTION_CAPTURE_REQUESTED).apply {
                putOwnershipExtras(token)
                putExtra("extra_request_origin", "NOT_A_REAL_ORIGIN")
            }
        )
        drain()

        assertEquals(0, completedSequences())
        assertFalse(capturing())
        assertTrue(ScanRequests.coordinator.isLiveRequest(token))
    }

    @Test
    fun projectionLossInvalidatesAcceptedRequestsAndAdvancesTheEpoch() {
        val token = (ScanRequests.coordinator.acceptRequest(RequestOrigin.USER)
            as RequestAcceptance.Accepted).token
        val epochBefore = token.projectionEpoch

        tearDownProjection()

        assertEquals(
            TerminalOutcome.PROJECTION_INVALIDATED,
            ScanRequests.coordinator.snapshot(token)?.terminalOutcome
        )
        assertTrue(ScanRequests.coordinator.currentProjectionEpoch > epochBefore)
    }

    @Test
    fun rateLimitedOwnedRequestEndsExplicitlyInsteadOfRemainingLive() {
        val limiter = ScreenCaptureService::class.java.getDeclaredField("captureRateLimiter").apply {
            isAccessible = true
        }.get(service) as RateLimiter
        repeat(10) { assertTrue(limiter.canProcess()) }

        val token = (ScanRequests.coordinator.acceptRequest(RequestOrigin.USER)
            as RequestAcceptance.Accepted).token
        val receiver = ScreenCaptureService::class.java.getDeclaredField("captureReceiver").apply {
            isAccessible = true
        }.get(service) as BroadcastReceiver
        receiver.onReceive(
            service,
            Intent(OverlayService.ACTION_CAPTURE_REQUESTED).apply { putOwnershipExtras(token) }
        )
        drain()

        assertEquals(TerminalOutcome.FINAL_FAILURE, ScanRequests.coordinator.snapshot(token)?.terminalOutcome)
        assertFalse(ScanRequests.coordinator.isLiveRequest(token))
        assertEquals(0, completedSequences())
    }

    @Test
    fun projectionUnavailableOwnedRequestEndsExplicitly() {
        val token = (ScanRequests.coordinator.acceptRequest(RequestOrigin.USER)
            as RequestAcceptance.Accepted).token
        // Force ensureProjectionReady() down the no-grant path without tearing down the
        // coordinator first. onDestroy still owns/release the remaining fake resources.
        ScreenCaptureService::class.java.getDeclaredField("imageReader").apply {
            isAccessible = true
        }.set(service, null)

        val receiver = ScreenCaptureService::class.java.getDeclaredField("captureReceiver").apply {
            isAccessible = true
        }.get(service) as BroadcastReceiver
        receiver.onReceive(
            service,
            Intent(OverlayService.ACTION_CAPTURE_REQUESTED).apply { putOwnershipExtras(token) }
        )
        drain()

        assertEquals(TerminalOutcome.FINAL_FAILURE, ScanRequests.coordinator.snapshot(token)?.terminalOutcome)
        assertFalse(ScanRequests.coordinator.isLiveRequest(token))
        assertEquals(0, completedSequences())
    }

    @Test fun idleRequestCompletesOneCapture() {
        request()
        assertTrue(capturing())
        drain()
        assertEquals(1, completedSequences())
    }

    @Test fun validRequestWhileBusyCompletesASecondCapture() {
        request()
        assertTrue(capturing())
        request()
        assertEquals(0, completedSequences())
        drain()
        assertEquals("The second valid request must survive active capture ownership", 2, completedSequences())
    }

    @Test fun severalBusyRequestsCoalesceIntoOneDeferredSequence() {
        repeat(8) { request() }
        drain()
        assertEquals(2, completedSequences())
        assertFalse(capturing())
    }

    @Test fun rateLimitedBusyRequestDoesNotBecomePendingWork() {
        val limiter = ScreenCaptureService::class.java.getDeclaredField("captureRateLimiter").apply {
            isAccessible = true
        }.get(service) as RateLimiter
        repeat(9) { assertTrue(limiter.canProcess()) }
        request() // Tenth permitted request owns capture.
        request() // Eleventh must be rejected before it can occupy the pending slot.
        drain()
        assertEquals(1, completedSequences())
    }

    @Test fun projectionLossClearsPendingAndOldCallbacksCannotCaptureAfterReinitialization() {
        request()
        request()
        tearDownProjection()
        installProjection()
        drain()
        assertEquals(0, completedSequences())
        assertFalse(capturing())
        request()
        drain()
        assertEquals(1, completedSequences())
    }

    @Test fun capturedContentResizeReplacesOutputAndRetainsTheActiveReaderUntilCaptureEnds() {
        request()
        val oldReader = field("imageReader") as ImageReader
        val context = field("activeCaptureContext") ?: error("capture context missing")
        val ownership = contextField(context, "ownership") as ScanRequestToken
        resizeProjection(96, 80)

        val resizedReader = field("imageReader") as ImageReader
        assertNotSame(oldReader, resizedReader)
        assertEquals(96, resizedReader.width)
        assertEquals(80, resizedReader.height)
        assertVirtualDisplayOutputMatches(resizedReader)
        assertSame(oldReader, contextField(context, "reader"))
        assertReaderOpen(oldReader)
        val retryOwnership = contextField(context, "resizeRetryOwnership") as ScanRequestToken
        assertEquals(ownership.requestId, retryOwnership.requestId)
        assertEquals(ownership.attemptId + 1, retryOwnership.attemptId)
        assertTrue(ScanRequests.coordinator.isLiveRequest(retryOwnership))

        // The old sequence is discarded, then the same logical request recaptures from
        // the replacement reader using its new attempt token.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(21))
        val retryContext = field("activeCaptureContext") ?: error("resize retry context missing")
        assertSame(resizedReader, contextField(retryContext, "reader"))
        assertEquals(retryOwnership, contextField(retryContext, "ownership"))
        assertEquals(0, completedSequences())
        drain()

        assertReaderClosed(oldReader)
        assertReaderOpen(resizedReader)
        assertEquals(1, completedSequences())
        val ready = shadowOf(service.application).broadcastIntents
            .single { it.action == ScreenCaptureService.ACTION_SCREENSHOT_READY }
        assertEquals(retryOwnership, ready.parseOwnership())
        assertEquals(2L, ready.getLongExtra("extra_capture_sequence_id", -1L))
        assertFalse(ScanRequests.coordinator.acceptScreenshotReady(ownership, 1L))
        assertFalse(capturing())
    }

    @Test fun firstCapturedContentSizeAlignsTheOutputWithoutInvalidatingTheProjection() {
        val oldReader = field("imageReader") as ImageReader
        val epochBefore = ScanRequests.coordinator.currentProjectionEpoch

        resizeProjection(96, 80)

        val resizedReader = field("imageReader") as ImageReader
        assertEquals(96, resizedReader.width)
        assertEquals(80, resizedReader.height)
        assertVirtualDisplayOutputMatches(resizedReader)
        assertReaderClosed(oldReader)
        assertEquals(epochBefore, ScanRequests.coordinator.currentProjectionEpoch)
    }

    @Test fun resizedOutputKeepsTheReservedPendingRequestAndUsesTheNewReader() {
        request()
        request()
        val pending = field("pendingOwnership") as ScanRequestToken
        val oldReader = field("imageReader") as ImageReader
        resizeProjection(96, 80)

        val resizedReader = field("imageReader") as ImageReader
        assertVirtualDisplayOutputMatches(resizedReader)
        assertTrue(ScanRequests.coordinator.isLiveRequest(pending))
        assertReaderOpen(oldReader)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(180))
        shadowOf(Looper.getMainLooper()).idle()

        val nextContext = field("activeCaptureContext") ?: error("reserved capture did not start")
        assertSame(resizedReader, contextField(nextContext, "reader"))
        assertTrue(ScanRequests.coordinator.isLiveRequest(pending))

        drain()
        assertReaderClosed(oldReader)
        assertEquals(1, completedSequences())
        val ready = shadowOf(service.application).broadcastIntents
            .single { it.action == ScreenCaptureService.ACTION_SCREENSHOT_READY }
        assertEquals(pending, ready.parseOwnership())
        assertEquals(2L, ready.getLongExtra("extra_capture_sequence_id", -1L))
    }

    @Test fun destructionClearsActiveAndPendingCapture() {
        request()
        request()
        service.onDestroy()
        drain()
        assertEquals(0, completedSequences())
        assertFalse(capturing())
    }

    @Test fun emptyCaptureReleasesOwnershipRunsPendingOnceAndAllowsNextIdleRequest() {
        request()
        request()
        drain() // The platform reader supplies no frames in this fixture.
        assertEquals(2, completedSequences())
        assertFalse(capturing())
        request()
        drain()
        assertEquals(3, completedSequences())
    }

    @Test fun requestImmediatelyBeforeCompletionIsNotLost() {
        request()
        Handler(Looper.getMainLooper()).postDelayed({ request() }, 179)
        drain()
        assertEquals(2, completedSequences())
    }

    @Test fun requestImmediatelyAfterCompletionStartsExactlyOnce() {
        request()
        Handler(Looper.getMainLooper()).postDelayed({ request() }, 181)
        drain()
        assertEquals(2, completedSequences())
    }

    @Test fun requestBetweenReleaseAndDeferredDrainCoalescesWithoutDoubleExecution() {
        request()
        request()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        // Completion at180 was queued by the100ms callback. This request will
        // run after completion, but before completion's newly posted drain.
        Handler(Looper.getMainLooper()).postDelayed({ request() }, 80)
        drain()
        assertEquals(2, completedSequences())
        assertFalse(capturing())
    }

    private fun tearDownProjection() {
        ScreenCaptureService::class.java.getDeclaredMethod("tearDown").apply { isAccessible = true }.invoke(service)
    }

    private fun request() {
        val receiver = ScreenCaptureService::class.java.getDeclaredField("captureReceiver").apply {
            isAccessible = true
        }.get(service) as BroadcastReceiver
        receiver.onReceive(service, Intent(OverlayService.ACTION_CAPTURE_REQUESTED))
    }

    private fun capturing() = ScreenCaptureService::class.java.getDeclaredField("isCapturing").apply {
        isAccessible = true
    }.getBoolean(service)

    private fun setField(name: String, value: Any) {
        ScreenCaptureService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
    }

    private fun field(name: String): Any? = ScreenCaptureService::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(service)

    private fun contextField(context: Any, name: String): Any = requireNotNull(
        context.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(context)
    ) { "capture context field $name was null" }

    private fun resizeProjection(width: Int, height: Int) {
        ScreenCaptureService::class.java.getDeclaredMethod(
            "handleCapturedContentResize",
            MediaProjection::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(service, field("mediaProjection"), width, height)
    }

    private fun assertReaderOpen(reader: ImageReader) {
        try {
            reader.acquireLatestImage()?.close()
        } catch (failure: IllegalStateException) {
            throw AssertionError("reader must remain open while its capture context owns it", failure)
        }
    }

    private fun assertReaderClosed(reader: ImageReader) {
        val closed = runCatching { reader.acquireLatestImage()?.close() }.isFailure
        assertTrue("retired reader must close when its capture context releases it", closed)
    }

    @Suppress("DEPRECATION") // getRealSize is the API 28-compatible readback for this virtual display.
    private fun assertVirtualDisplayOutputMatches(reader: ImageReader) {
        val display = field("virtualDisplay") as VirtualDisplay
        assertSame(reader.surface, display.surface)
        val size = Point()
        display.display.getRealSize(size)
        assertEquals(reader.width, size.x)
        assertEquals(reader.height, size.y)
    }

    private fun drain() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

    private fun completedSequences() = shadowOf(service.application).broadcastIntents.count {
        it.action == ScreenCaptureService.ACTION_SCREENSHOT_READY
    }
}
