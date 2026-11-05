package com.pokerarity.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugApplicationIdentityTest {
    @Test
    fun debugVariantUsesAnIsolatedApplicationId() {
        assertEquals("com.pokerarity.scanner.debug", BuildConfig.APPLICATION_ID)
        assertTrue(BuildConfig.REMOTE_METADATA_SYNC_ENABLED)
    }
}
