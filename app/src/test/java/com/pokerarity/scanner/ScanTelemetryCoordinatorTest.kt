package com.pokerarity.scanner

import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTelemetryCoordinatorTest {

    @Test
    fun shouldUseTelemetry_requiresBuildEnablementAndUserConsent() {
        assertTrue(ScanTelemetryCoordinator.shouldUseTelemetry(repositoryEnabled = true, userConsent = true))
        assertFalse(ScanTelemetryCoordinator.shouldUseTelemetry(repositoryEnabled = false, userConsent = true))
        assertFalse(ScanTelemetryCoordinator.shouldUseTelemetry(repositoryEnabled = true, userConsent = false))
        assertFalse(ScanTelemetryCoordinator.shouldUseTelemetry(repositoryEnabled = false, userConsent = false))
    }
}
