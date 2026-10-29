package com.pokerarity.scanner.service

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectionConsentDenialHandlerTest {

    @After
    fun resetProcessState() {
        ScanRequests.resetForTest()
        OverlayStateStore.resetToIdle()
        ScreenCaptureManager.clearGrant()
    }

    @Test
    fun deniedConsentStopsScannerOwnershipAndBothServices() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val context = RecordingContext(appContext)
        val coordinator = ScanRequestCoordinator()
        ScanRequests.resetForTest(coordinator)
        coordinator.onScannerLifecycle(started = true)
        OverlayStateStore.dispatch(OverlayIntent.StartScan)
        ProjectionConsentRecoveryState.markPending(context)
        ScreenCaptureManager.handleResult(ActivityResult(Activity.RESULT_OK, Intent()))

        ProjectionConsentDenialHandler.stopScanner(context)

        assertTrue("Denied consent must leave request ownership stopped", coordinator.isStopped)
        assertEquals(OverlayState.Idle, OverlayStateStore.state.value)
        assertEquals(
            setOf(OverlayService::class.java.name, ScreenCaptureService::class.java.name),
            context.stoppedServices.mapNotNull { it.component?.className }.toSet()
        )
        assertFalse("Denied consent must clear a cached projection grant", ScreenCaptureManager.isGranted)
        assertFalse(
            "Denied consent must stop retrying a denied request",
            ProjectionConsentRecoveryState.isPending(context)
        )
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val stoppedServices = mutableListOf<Intent>()

        override fun stopService(name: Intent): Boolean {
            stoppedServices += name
            return true
        }
    }
}
