package com.pokerarity.scanner

import com.pokerarity.scanner.service.ScanStartupPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanStartupPolicyTest {

    @Test
    fun manualStartDoesNotAutoCaptureImmediately() {
        assertFalse(ScanStartupPolicy.autoCaptureForManualStart())
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
