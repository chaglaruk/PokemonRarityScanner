package com.pokerarity.scanner

import android.app.Activity
import android.content.Intent
import com.pokerarity.scanner.service.ScreenCaptureManager
import com.pokerarity.scanner.service.ScreenCaptureService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ScreenCaptureManagerTest {

    @Test
    fun buildServiceIntent_carriesAutoCaptureFlag() {
        val managerClass = ScreenCaptureManager::class.java
        managerClass.getDeclaredField("resultCode").apply {
            isAccessible = true
            setInt(ScreenCaptureManager, Activity.RESULT_OK)
        }
        managerClass.getDeclaredField("resultData").apply {
            isAccessible = true
            set(ScreenCaptureManager, Intent("test-action"))
        }

        val context = RuntimeEnvironment.getApplication()
        val serviceIntent = ScreenCaptureManager.buildServiceIntent(context, autoCapture = true)

        assertNotNull(serviceIntent)
        assertEquals(Activity.RESULT_OK, serviceIntent!!.getIntExtra(ScreenCaptureService.EXTRA_RESULT_CODE, Activity.RESULT_CANCELED))
        assertEquals(true, serviceIntent.getBooleanExtra(ScreenCaptureService.EXTRA_AUTO_CAPTURE, false))

        ScreenCaptureManager.release()
    }
}
