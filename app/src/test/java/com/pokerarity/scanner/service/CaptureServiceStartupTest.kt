package com.pokerarity.scanner.service

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.result.ActivityResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class CaptureServiceStartupTest {

    @After
    fun clearProjectionGrant() {
        ScreenCaptureManager.release()
        OverlayStateStore.resetToIdle()
    }

    @Test
    fun foregroundServiceStartFailureRequiresFreshGrantBeforeRetry() {
        grantProjection()
        var serviceStartAttempts = 0
        var overlayStartAttempts = 0

        val firstAttemptStarted = CaptureServiceStartup.runOrRollback(
            context = RuntimeEnvironment.getApplication(),
            startCaptureService = {
                serviceStartAttempts++
                error("foreground service start rejected")
            },
            startOverlay = { overlayStartAttempts++ }
        )

        assertFalse(firstAttemptStarted)
        assertEquals(1, serviceStartAttempts)
        assertEquals(0, overlayStartAttempts)
        assertFalse(ScreenCaptureManager.isGranted)
        assertEquals(
            ScanStartupPolicy.StartAction.REQUEST_PERMISSION,
            ScanStartupPolicy.startAction(overlayRunning = false, projectionGranted = ScreenCaptureManager.isGranted)
        )

        grantProjection()
        val retryStarted = CaptureServiceStartup.runOrRollback(
            context = RuntimeEnvironment.getApplication(),
            startCaptureService = { serviceStartAttempts++ },
            startOverlay = { overlayStartAttempts++ }
        )

        assertTrue(retryStarted)
        assertEquals(2, serviceStartAttempts)
        assertEquals(1, overlayStartAttempts)
        assertTrue(ScreenCaptureManager.isGranted)
    }

    @Test
    fun asynchronousOverlayStartFailureStopsCaptureAndRequiresFreshGrantOnRetry() {
        val stoppedServices = mutableListOf<String>()
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun stopService(name: Intent): Boolean {
                stoppedServices += name.component?.className.orEmpty()
                return true
            }
        }
        grantProjection()
        OverlayStateStore.dispatch(OverlayIntent.StartScan)

        CaptureServiceStartup.rollbackAfterOverlayAttachFailure(context)

        assertEquals(
            listOf(ScreenCaptureService::class.java.name),
            stoppedServices
        )
        assertFalse(ScreenCaptureManager.isGranted)
        assertEquals(OverlayState.Idle, OverlayStateStore.state.value)
        assertEquals(
            ScanStartupPolicy.StartAction.REQUEST_PERMISSION,
            ScanStartupPolicy.startAction(overlayRunning = false, projectionGranted = ScreenCaptureManager.isGranted)
        )
    }

    private fun grantProjection() {
        assertTrue(
            ScreenCaptureManager.handleResult(
                ActivityResult(Activity.RESULT_OK, Intent("test-projection-grant"))
            )
        )
    }
}
