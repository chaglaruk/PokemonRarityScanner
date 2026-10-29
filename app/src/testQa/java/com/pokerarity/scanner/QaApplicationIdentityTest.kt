package com.pokerarity.scanner

import com.pokerarity.scanner.service.ScreenCaptureService
import org.junit.Assert.assertEquals
import org.junit.Test

class QaApplicationIdentityTest {
    @Test
    fun qaVariantScopesItsInternalBroadcastPermissionToItsPackage() {
        assertEquals("com.pokerarity.scanner.sprint6qa", BuildConfig.APPLICATION_ID)
        assertEquals(
            "${BuildConfig.APPLICATION_ID}.permission.INTERNAL_BROADCAST",
            ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION,
        )
    }
}
