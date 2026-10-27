package com.pokerarity.scanner

import com.pokerarity.scanner.service.ScanStartupPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanStartupPolicyTest {

    @Test
    fun manualStartDoesNotAutoCaptureImmediately() {
        assertFalse(ScanStartupPolicy.autoCaptureForManualStart())
    }

    @Test
    fun scannerUiIsActiveOnlyWhileOverlayAndProjectionAreBothRunning() {
        assertTrue(ScanStartupPolicy.isScannerSessionActive(overlayRunning = true, projectionGranted = true))
        assertFalse(ScanStartupPolicy.isScannerSessionActive(overlayRunning = true, projectionGranted = false))
        assertFalse(ScanStartupPolicy.isScannerSessionActive(overlayRunning = false, projectionGranted = true))
    }

    @Test
    fun startActionRecoversOverlayWithoutRequestingAnotherProjectionGrant() {
        assertEquals(
            ScanStartupPolicy.StartAction.STOP,
            ScanStartupPolicy.startAction(overlayRunning = true, projectionGranted = true)
        )
        assertEquals(
            ScanStartupPolicy.StartAction.REQUEST_PERMISSION,
            ScanStartupPolicy.startAction(overlayRunning = true, projectionGranted = false)
        )
        assertEquals(
            ScanStartupPolicy.StartAction.RESUME_OVERLAY,
            ScanStartupPolicy.startAction(overlayRunning = false, projectionGranted = true)
        )
        assertEquals(
            ScanStartupPolicy.StartAction.REQUEST_PERMISSION,
            ScanStartupPolicy.startAction(overlayRunning = false, projectionGranted = false)
        )
    }

    @Test
    fun stablePokemonScreenRequiresLowMotionAnd500ms() {
        assertFalse(
            ScanStartupPolicy.shouldAutoTriggerFromStableScreen(
                validPokemonScreen = true,
                stableForMs = 420L,
                motionScore = 0.03f
            )
        )
        assertFalse(
            ScanStartupPolicy.shouldAutoTriggerFromStableScreen(
                validPokemonScreen = true,
                stableForMs = 540L,
                motionScore = 0.18f
            )
        )
        assertTrue(
            ScanStartupPolicy.shouldAutoTriggerFromStableScreen(
                validPokemonScreen = true,
                stableForMs = 540L,
                motionScore = 0.03f
            )
        )
    }
}
