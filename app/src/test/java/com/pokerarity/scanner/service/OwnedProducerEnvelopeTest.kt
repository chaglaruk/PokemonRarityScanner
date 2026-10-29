package com.pokerarity.scanner.service

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.Image
import android.media.ImageReader
import android.media.OwnedTestImage
import android.os.SystemClock
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, shadows = [OwnedReaderShadow::class])
class OwnedProducerEnvelopeTest {
    private class ProducerContext(base: Context) : ContextWrapper(base) {
        private val alias = File(base.cacheDir, "producer-alias/..").also { it.parentFile!!.mkdirs() }
        var envelope: Intent? = null
        override fun getCacheDir() = alias
        override fun sendBroadcast(intent: Intent, receiverPermission: String?) { envelope = intent }
    }

    /** Producer return path must identify its own registry entry even through a cache alias.
     * Exercises captureLatestFrame and finishCaptureSequence, not a hand-built ready Intent.
     */
    @Test fun realProducerEnvelopeAdmitsItsCapturedFramesThroughCacheAlias() {
        ScanRequests.resetForTest()
        OwnedFrameHandoff.enabled = true
        val application = RuntimeEnvironment.getApplication()
        application.resources.displayMetrics.apply { widthPixels = 64; heightPixels = 128 }
        val base = ProducerContext(application)
        val producer = ScreenCaptureService()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply { isAccessible = true }.invoke(producer, base)
        val token = (ScanRequests.coordinator.acceptRequest(RequestOrigin.USER) as RequestAcceptance.Accepted).token
        val reader = Shadow.newInstanceOf(ImageReader::class.java)
        val method = ScreenCaptureService::class.java.getDeclaredMethod("captureLatestFrame", ImageReader::class.java,
            java.lang.Long.TYPE, Integer.TYPE, ScanRequestToken::class.java).apply { isAccessible = true }
        val paths = mutableListOf<String>()
        var leases: List<OwnedFrameHandoff.Lease>? = null
        try {
            repeat(2) { index ->
                OwnedReaderShadow.next = OwnedTestImage()
                paths += requireNotNull(method.invoke(producer, reader, 19L, index, token) as String?)
            }
            val type = ScreenCaptureService::class.java.declaredClasses.single { it.simpleName == "CaptureContext" }
            val state = type.declaredConstructors.single { it.parameterCount == 9 }.apply { isAccessible = true }
                .newInstance(reader, 0L, 19L, SystemClock.elapsedRealtime(), paths, token, false, null, false)
            ScreenCaptureService::class.java.getDeclaredMethod("finishCaptureSequence", type)
                .apply { isAccessible = true }.invoke(producer, state)
            val intent = requireNotNull(base.envelope)
            assertTrue("Producer lost owned transport in its actual envelope",
                intent.getBooleanExtra(ScreenCaptureService.EXTRA_OWNED_FRAMES, false))
            val ownedPaths = intent.getStringArrayListExtra(ScreenCaptureService.EXTRA_SCREENSHOT_PATHS)!!
            ScanRequests.coordinator.acceptScreenshotReady(token,19L)
            leases = OwnedFrameHandoff.claim(ownedPaths, token, 19L)
            assertTrue("Actual envelope paths cannot be claimed", leases != null)
        } finally {
            paths.forEach { OwnedFrameHandoff.awaitEvidence(File(it).canonicalPath) }
            leases?.forEach { it.close() }
            OwnedFrameHandoff.invalidate(); OwnedFrameHandoff.enabled = false
            ScanRequests.resetForTest()
        }
    }

}

@Implements(ImageReader::class)
class OwnedReaderShadow {
    companion object { var next: Image? = null }
    @Implementation fun acquireLatestImage(): Image? = next.also { next = null }
}
