package com.pokerarity.scanner

import android.os.SystemClock
import androidx.compose.runtime.State
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.ui.main.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityStartupTest {

    @Test
    fun coldStartPreparesSecureDataBeforeCollectionIsShown() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val startupStateField = MainActivity::class.java.getDeclaredField("startupState").apply {
            isAccessible = true
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var startupReady = false
        val deadline = SystemClock.uptimeMillis() + STARTUP_TIMEOUT_MILLIS
        try {
            while (!startupReady && SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val state = startupStateField.get(activity) as? State<*>
                    startupReady = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                        state?.value != null
                }
                if (!startupReady) SystemClock.sleep(POLL_INTERVAL_MILLIS)
            }
            assertTrue(
                "MainActivity did not prepare secure startup data before timeout",
                startupReady
            )
        } finally {
            scenario.close()
        }
    }

    private companion object {
        const val STARTUP_TIMEOUT_MILLIS = 30_000L
        const val POLL_INTERVAL_MILLIS = 100L
    }
}