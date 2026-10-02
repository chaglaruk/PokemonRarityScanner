package com.pokerarity.scanner

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Looper
import android.os.Handler
import com.pokerarity.scanner.service.OverlayService
import com.pokerarity.scanner.service.ScreenCaptureService
import com.pokerarity.scanner.util.RateLimiter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    @After fun destroy() { service.onDestroy() }

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

    private fun drain() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

    private fun completedSequences() = shadowOf(service.application).broadcastIntents.count {
        it.action == ScreenCaptureService.ACTION_SCREENSHOT_READY
    }
}
