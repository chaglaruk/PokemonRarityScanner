package com.pokerarity.scanner

import android.app.Application
import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import com.pokerarity.scanner.service.ScreenCaptureService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
class ScreenCaptureProjectionForegroundTypeTest {
    @Test
    fun revokedProjectionReturnsForegroundServiceToItsDeclaredWaitingType() {
        val controller = Robolectric.buildService(ScreenCaptureService::class.java)
        val service = controller.create().get()
        try {
            val activeProjection = fakeProjection(service)
            service.startForeground(
                NOTIFICATION_ID,
                Notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
            setField(service, "mediaProjection", activeProjection)

            assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                foregroundServiceType(service)
            )

            service.createProjectionCallback(activeProjection).onStop()

            assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                foregroundServiceType(service)
            )
        } finally {
            service.onDestroy()
        }
    }

    private fun foregroundServiceType(service: ScreenCaptureService): Int {
        val shadow = shadowOf(service)
        val getter = shadow.javaClass.getDeclaredMethod("getForegroundServiceType").apply {
            isAccessible = true
        }
        return getter.invoke(shadow) as Int
    }

    private fun fakeProjection(service: ScreenCaptureService): MediaProjection {
        @Suppress("UNCHECKED_CAST")
        val tokenClass = Class.forName("android.media.projection.IMediaProjection") as Class<Any>
        return ReflectionHelpers.callConstructor(
            MediaProjection::class.java,
            ClassParameter.from(Context::class.java, service),
            ClassParameter.from(tokenClass, ReflectionHelpers.createNullProxy(tokenClass))
        )
    }

    private fun setField(service: ScreenCaptureService, name: String, value: Any) {
        ScreenCaptureService::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }.set(service, value)
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
    }
}
