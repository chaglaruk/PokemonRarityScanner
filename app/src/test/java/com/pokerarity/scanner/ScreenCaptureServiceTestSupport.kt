package com.pokerarity.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Looper
import com.pokerarity.scanner.service.OverlayService
import com.pokerarity.scanner.service.ProjectionConsentRecoveryState
import com.pokerarity.scanner.service.ScanRequests
import com.pokerarity.scanner.service.ScreenCaptureService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Before
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration

abstract class ScreenCaptureServiceTestSupport {
    protected lateinit var service: ScreenCaptureService

    @Before fun prepareProjection() {
        service = Robolectric.buildService(ScreenCaptureService::class.java).create().get()
        ProjectionConsentRecoveryState.clear(service)
        installProjection()
    }

    protected fun installProjection() {
        // Fake only the platform consent token; exercise the real receiver and capture callbacks.
        val projection = fakeProjection()
        val reader = ImageReader.newInstance(64, 64, PixelFormat.RGBA_8888, 2)
        val display = (service.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .createVirtualDisplay("capture-test", 64, 64, 160, reader.surface, 0)
        setField("mediaProjection", projection)
        setField("imageReader", reader)
        setField("virtualDisplay", display)
        assertTrue(display != null)
    }

    protected fun fakeProjection(): MediaProjection {
        @Suppress("UNCHECKED_CAST")
        val tokenClass = Class.forName("android.media.projection.IMediaProjection") as Class<Any>
        return ReflectionHelpers.callConstructor(
            MediaProjection::class.java,
            ClassParameter.from(Context::class.java, service),
            ClassParameter.from(tokenClass, ReflectionHelpers.createNullProxy(tokenClass))
        )
    }

    @After fun destroy() {
        service.onDestroy()
        ProjectionConsentRecoveryState.clear(service)
        ScanRequests.resetForTest()
    }

    protected fun tearDownProjection() {
        ScreenCaptureService::class.java.getDeclaredMethod("tearDown").apply { isAccessible = true }.invoke(service)
    }

    protected fun request() {
        captureReceiver().onReceive(service, Intent(OverlayService.ACTION_CAPTURE_REQUESTED))
    }

    protected fun captureReceiver() = ScreenCaptureService::class.java.getDeclaredField("captureReceiver").apply {
        isAccessible = true
    }.get(service) as BroadcastReceiver

    protected fun capturing() = ScreenCaptureService::class.java.getDeclaredField("isCapturing").apply {
        isAccessible = true
    }.getBoolean(service)

    protected fun setField(name: String, value: Any) {
        ScreenCaptureService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
    }

    protected fun field(name: String): Any? = ScreenCaptureService::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(service)

    protected fun contextField(context: Any, name: String): Any = requireNotNull(
        context.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(context)
    ) { "capture context field $name was null" }

    protected fun resizeProjection(width: Int, height: Int) {
        ScreenCaptureService::class.java.getDeclaredMethod(
            "handleCapturedContentResize",
            MediaProjection::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(service, field("mediaProjection"), width, height)
    }

    protected fun assertReaderOpen(reader: ImageReader) {
        try {
            reader.acquireLatestImage()?.close()
        } catch (failure: IllegalStateException) {
            throw AssertionError("reader must remain open while its capture context owns it", failure)
        }
    }

    protected fun assertReaderClosed(reader: ImageReader) {
        val closed = runCatching { reader.acquireLatestImage()?.close() }.isFailure
        assertTrue("retired reader must close when its capture context releases it", closed)
    }

    @Suppress("DEPRECATION") // getRealSize is the API 28-compatible readback for this virtual display.
    protected fun assertVirtualDisplayOutputMatches(reader: ImageReader) {
        val display = field("virtualDisplay") as VirtualDisplay
        assertSame(reader.surface, display.surface)
        val size = Point()
        display.display.getRealSize(size)
        assertEquals(reader.width, size.x)
        assertEquals(reader.height, size.y)
    }

    protected fun drain() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

    protected fun completedSequences() = shadowOf(service.application).broadcastIntents.count {
        it.action == ScreenCaptureService.ACTION_SCREENSHOT_READY
    }}
