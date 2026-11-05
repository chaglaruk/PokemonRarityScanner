package com.pokerarity.scanner

import com.pokerarity.scanner.service.ScreenCaptureService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QaApplicationIdentityTest {
    @Test
    fun qaVariantScopesItsInternalBroadcastPermissionToItsPackage() {
        assertTrue(BuildConfig.APPLICATION_ID.startsWith("com.pokerarity.scanner.sprint6qa"))
        assertFalse(BuildConfig.REMOTE_METADATA_SYNC_ENABLED)
        assertTrue(
            BuildConfig.QA_SOURCE_SHA.isEmpty() ||
                Regex("[a-fA-F0-9]{40}").matches(BuildConfig.QA_SOURCE_SHA),
        )
        assertEquals(
            "${BuildConfig.APPLICATION_ID}.permission.INTERNAL_BROADCAST",
            ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION,
        )
    }
}
