package com.pokerarity.scanner

import com.pokerarity.scanner.service.ScreenCaptureService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QaApplicationIdentityTest {
    @Test
    fun qaVariantScopesItsInternalBroadcastPermissionToItsPackage() {
        assertTrue(
            BuildConfig.APPLICATION_ID == "com.pokerarity.scanner.sprint6qa" ||
                BuildConfig.APPLICATION_ID.startsWith("com.pokerarity.scanner.sprint6qa."),
        )
        assertEquals(
            "${BuildConfig.APPLICATION_ID}.permission.INTERNAL_BROADCAST",
            ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION,
        )
    }

    @Test
    fun qaVariantDoesNotReadOrDownloadRemoteMetadataOverrides() {
        assertFalse(BuildConfig.REMOTE_METADATA_SYNC_ENABLED)
    }
}
