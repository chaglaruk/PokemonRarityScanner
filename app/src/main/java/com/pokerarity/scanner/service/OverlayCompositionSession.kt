package com.pokerarity.scanner.service

import android.view.Choreographer
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers

/** Own the overlay's frame callbacks independently of a background activity's UI dispatcher. */
@OptIn(ExperimentalComposeUiApi::class)
internal class OverlayCompositionSession(private val view: ComposeView, lifecycle: Lifecycle) : AutoCloseable {
    private val recomposer: Recomposer = view.createLifecycleAwareWindowRecomposer(
        coroutineContext = Dispatchers.Main.immediate + AndroidUiFrameClock(Choreographer.getInstance()),
        lifecycle = lifecycle,
    )

    init {
        view.setParentCompositionContext(recomposer)
    }

    override fun close() {
        view.disposeComposition()
        recomposer.cancel()
    }
}
