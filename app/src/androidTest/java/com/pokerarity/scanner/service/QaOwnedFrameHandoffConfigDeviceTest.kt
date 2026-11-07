package com.pokerarity.scanner.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pokerarity.scanner.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QaOwnedFrameHandoffConfigDeviceTest {

    @Test
    fun qaBuildActivatesOnlyTheBoundedOwnedFramePath() {
        assertTrue(BuildConfig.DEBUG)
        assertTrue(BuildConfig.QA_OWNED_FRAME_HANDOFF_ENABLED)
        assertTrue(OwnedFrameHandoff.active())
        assertTrue(OwnedFrameHandoff.metrics().getValue("liveSourceBytes") <= 48L * 1024 * 1024)
    }
}
