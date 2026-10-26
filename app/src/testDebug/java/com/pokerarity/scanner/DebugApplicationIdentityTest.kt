package com.pokerarity.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class DebugApplicationIdentityTest {
    @Test
    fun debugVariantUsesAnIsolatedApplicationId() {
        assertEquals("com.pokerarity.scanner.debug", BuildConfig.APPLICATION_ID)
    }
}
