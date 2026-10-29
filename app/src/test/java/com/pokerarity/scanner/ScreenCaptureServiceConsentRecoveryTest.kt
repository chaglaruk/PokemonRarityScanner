package com.pokerarity.scanner

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.Looper
import com.pokerarity.scanner.service.OverlayService
import com.pokerarity.scanner.service.ProjectionConsentRecoveryState
import com.pokerarity.scanner.service.RequestAcceptance
import com.pokerarity.scanner.service.RequestOrigin
import com.pokerarity.scanner.service.ScanRequests
import com.pokerarity.scanner.service.ScreenCaptureService
import com.pokerarity.scanner.ui.permission.ProjectionPermissionActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
class ScreenCaptureServiceConsentRecoveryTest : ScreenCaptureServiceTestSupport() {
    @Test
    fun stickyRestartWithoutIntentStopsStaleSessionAndRequestsFreshConsent() {
        ScanRequests.coordinator.onScannerLifecycle(started = true)
        val accepted = ScanRequests.coordinator.acceptRequest(RequestOrigin.USER)
        val token = (accepted as RequestAcceptance.Accepted).token

        val result = service.onStartCommand(null, 0, 42)

        assertEquals(Service.START_NOT_STICKY, result)
        assertTrue("The restarted service must stop without projection data", shadowOf(service).isStoppedBySelf)
        assertTrue("The obsolete foreground notification must be removed", shadowOf(service).notificationShouldRemoved)
        assertTrue(ScanRequests.coordinator.isStopped)
        assertFalse(ScanRequests.coordinator.isLiveRequest(token))
        assertTrue(
            shadowOf(service.application).broadcastIntents.any {
                it.action == ScreenCaptureService.ACTION_PROJECTION_REQUIRED
            }
        )

        // Model a successful fresh grant without invoking Robolectric's platform projection setup.
        installProjection()
        val consentIntent = Intent().apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, Activity.RESULT_OK)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, Intent())
        }
        assertEquals(Service.START_STICKY, service.onStartCommand(consentIntent, 0, 43))
        assertFalse(
            "Fresh projection consent must reopen scanner request ownership",
            ScanRequests.coordinator.isStopped
        )
        assertFalse(
            "Fresh projection consent must clear stale recovery state",
            ProjectionConsentRecoveryState.isPending(service)
        )
        assertTrue(ScanRequests.coordinator.acceptRequest(RequestOrigin.USER) is RequestAcceptance.Accepted)
    }

    @Test
    fun explicitProjectionRecoveryBroadcastLaunchesOnceWithoutPersistedMarker() {
        val overlayController = Robolectric.buildService(OverlayService::class.java)
        try {
            try {
                overlayController.create()
            } catch (_: android.content.res.Resources.NotFoundException) {
                // Recovery dispatch is registered before overlay resource inflation.
            }

            val overlay = overlayController.get()
            val recoveryIntent = Intent(ScreenCaptureService.ACTION_PROJECTION_REQUIRED).apply {
                setPackage(overlay.packageName)
                putExtra(ScreenCaptureService.EXTRA_PROJECTION_RECOVERY_PERSISTED, false)
            }
            overlay.sendBroadcast(recoveryIntent, ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION)
            drain()

            assertEquals(
                ProjectionPermissionActivity::class.java.name,
                shadowOf(service.application).nextStartedActivity?.component?.className
            )
            assertFalse(ProjectionConsentRecoveryState.isPending(overlay))

            overlay.sendBroadcast(recoveryIntent, ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION)
            drain()
            assertEquals(null, shadowOf(service.application).nextStartedActivity)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
            assertEquals(null, shadowOf(service.application).nextStartedActivity)
        } finally {
            overlayController.get().onDestroy()
        }
    }

    @Test
    fun persistedRecoveryPromotesOutstandingOneShotAndAcknowledgmentCancelsRetry() {
        val overlayController = Robolectric.buildService(OverlayService::class.java)
        try {
            try {
                overlayController.create()
            } catch (_: android.content.res.Resources.NotFoundException) {
                // Recovery dispatch is registered before overlay resource inflation.
            }

            val overlay = overlayController.get()
            val recoveryIntent = Intent(ScreenCaptureService.ACTION_PROJECTION_REQUIRED).apply {
                setPackage(overlay.packageName)
            }
            overlay.sendBroadcast(recoveryIntent.apply {
                putExtra(ScreenCaptureService.EXTRA_PROJECTION_RECOVERY_PERSISTED, false)
            }, ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION)
            drain()
            assertEquals(
                ProjectionPermissionActivity::class.java.name,
                shadowOf(service.application).nextStartedActivity?.component?.className
            )

            assertTrue(ProjectionConsentRecoveryState.markPending(overlay))
            overlay.sendBroadcast(recoveryIntent.apply {
                putExtra(ScreenCaptureService.EXTRA_PROJECTION_RECOVERY_PERSISTED, true)
            }, ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION)
            drain()
            assertEquals("The persisted event must not duplicate the outstanding screen", null,
                shadowOf(service.application).nextStartedActivity)

            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
            assertEquals(
                ProjectionPermissionActivity::class.java.name,
                shadowOf(service.application).nextStartedActivity?.component?.className
            )
            assertTrue(ProjectionConsentRecoveryState.isPending(overlay))

            assertTrue(OverlayService.acknowledgeProjectionPermissionActivity(overlay))
            drain()
            assertFalse(ProjectionConsentRecoveryState.isPending(overlay))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
            assertEquals(null, shadowOf(service.application).nextStartedActivity)
        } finally {
            overlayController.get().onDestroy()
        }
    }

    @Test
    fun freshProjectionGrantResetsFallbackSuppressionForLaterRecovery() {
        val overlayController = Robolectric.buildService(OverlayService::class.java)
        try {
            try {
                overlayController.create()
            } catch (_: android.content.res.Resources.NotFoundException) {
                // Recovery dispatch is registered before overlay resource inflation.
            }
            val overlay = overlayController.get()

            fun sendUnpersistedRecovery() {
                overlay.sendBroadcast(
                    Intent(ScreenCaptureService.ACTION_PROJECTION_REQUIRED).apply {
                        setPackage(overlay.packageName)
                        putExtra(ScreenCaptureService.EXTRA_PROJECTION_RECOVERY_PERSISTED, false)
                    },
                    ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION
                )
                drain()
            }

            sendUnpersistedRecovery()
            assertEquals(
                ProjectionPermissionActivity::class.java.name,
                shadowOf(service.application).nextStartedActivity?.component?.className
            )

            val freshGrant = Intent().apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, Activity.RESULT_OK)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, Intent())
            }
            assertEquals(Service.START_STICKY, service.onStartCommand(freshGrant, 0, 44))
            drain()
            assertFalse(ProjectionConsentRecoveryState.isPending(overlay))

            sendUnpersistedRecovery()
            assertEquals(
                "A later persistence failure must get its own immediate attempt",
                ProjectionPermissionActivity::class.java.name,
                shadowOf(service.application).nextStartedActivity?.component?.className
            )
        } finally {
            overlayController.get().onDestroy()
        }
    }

    @Test
    fun lateOverlayStartupReplaysProjectionConsentRequiredAfterStickyRestart() {
        ScanRequests.coordinator.onScannerLifecycle(started = true)
        service.onStartCommand(null, 0, 42)

        val overlayController = Robolectric.buildService(OverlayService::class.java)
        try {
            try {
                overlayController.create()
            } catch (_: android.content.res.Resources.NotFoundException) {
                // This test class omits app resources; recovery dispatch must precede overlay inflation.
            }
            val permissionIntent = shadowOf(service.application).nextStartedActivity
            assertEquals(
                ProjectionPermissionActivity::class.java.name,
                permissionIntent?.component?.className
            )
            assertTrue(
                "Attempting a background Activity launch is not delivery acknowledgment",
                ProjectionConsentRecoveryState.isPending(service)
            )

            // A successful startActivity() call can still be blocked by background-activity policy.
            // Repeated service callbacks must not enqueue duplicate screens immediately, but a
            // delayed retry must recover if no Activity acknowledges delivery.
            overlayController.get().onStartCommand(null, 0, 44)
            assertEquals(null, shadowOf(service.application).nextStartedActivity)

            val overlay = overlayController.get()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
            val retriedPermissionIntentAfterTimeout = shadowOf(service.application).nextStartedActivity
            assertEquals(
                ProjectionPermissionActivity::class.java.name,
                retriedPermissionIntentAfterTimeout?.component?.className
            )
            assertTrue(ProjectionConsentRecoveryState.isPending(service))

            // When the bounded pair is exhausted, a cooldown must schedule another cycle even if
            // ScreenCaptureService is already stopped and cannot send a second recovery broadcast.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
            assertEquals(null, shadowOf(service.application).nextStartedActivity)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
            val rearmedPermissionIntent = shadowOf(service.application).nextStartedActivity
            assertEquals(
                ProjectionPermissionActivity::class.java.name,
                rearmedPermissionIntent?.component?.className
            )

            assertTrue(OverlayService.acknowledgeProjectionPermissionActivity(overlay))
            assertFalse(
                "The pending marker clears only after the permission Activity receives the request",
                ProjectionConsentRecoveryState.isPending(service)
            )
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60))
            assertEquals(null, shadowOf(service.application).nextStartedActivity)

            ProjectionConsentRecoveryState.markPending(overlay)
            overlay.sendBroadcast(
                Intent(ScreenCaptureService.ACTION_PROJECTION_REQUIRED),
                ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION
            )
            drain()
            val retriedPermissionIntent = shadowOf(service.application).nextStartedActivity
            assertEquals(
                ProjectionPermissionActivity::class.java.name,
                retriedPermissionIntent?.component?.className
            )
        } finally {
            overlayController.get().onDestroy()
        }
    }

}
