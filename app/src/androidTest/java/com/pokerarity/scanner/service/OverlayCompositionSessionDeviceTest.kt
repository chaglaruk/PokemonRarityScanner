package com.pokerarity.scanner.service

import android.graphics.PixelFormat
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.ui.main.MainActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Frames must progress while the app activity is stopped, and stop when the overlay is removed. */
@RunWith(AndroidJUnit4::class)
class OverlayCompositionSessionDeviceTest {
    private class OverlayOwner : SavedStateRegistryOwner {
        val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        init {
            savedState.performAttach()
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }
    }

    @Test fun backgroundActivityDoesNotParkOverlayAnimationsAndDetachCancelsEffects() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(Settings.canDrawOverlays(context))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val manager = context.getSystemService(WindowManager::class.java)
        val animated = CountDownLatch(1)
        val disposed = CountDownLatch(1)
        lateinit var view: ComposeView
        lateinit var owner: OverlayOwner
        lateinit var session: OverlayCompositionSession
        var initialized = false
        try {
            scenario.moveToState(Lifecycle.State.CREATED)
            instrumentation.runOnMainSync {
                owner = OverlayOwner()
                view = ComposeView(context).apply {
                    setViewTreeLifecycleOwner(owner)
                    setViewTreeSavedStateRegistryOwner(owner)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                }
                session = OverlayCompositionSession(view, owner.lifecycle)
                view.setContent {
                    val alpha = remember { Animatable(0f) }
                    Box(Modifier.graphicsLayer { this.alpha = alpha.value })
                    LaunchedEffect(Unit) {
                        try {
                            alpha.animateTo(1f, tween(100))
                            animated.countDown()
                            while (true) withFrameNanos { }
                        } finally { disposed.countDown() }
                    }
                }
                initialized = true
                manager.addView(view, WindowManager.LayoutParams(120, 120,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT))
            }
            assertTrue("Overlay animation parked behind a stopped activity", animated.await(8, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                manager.removeViewImmediate(view)
                session.close()
                owner.registry.currentState = Lifecycle.State.DESTROYED
            }
            assertTrue("Detached overlay retained its effects", disposed.await(2, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync {
                if (initialized) {
                    if (view.isAttachedToWindow) manager.removeViewImmediate(view)
                    session.close()
                    owner.registry.currentState = Lifecycle.State.DESTROYED
                }
            }
            scenario.close()
        }
    }
}
