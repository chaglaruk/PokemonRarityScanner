package com.pokerarity.scanner.util.perf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import com.pokerarity.scanner.service.ScanRequestToken
import java.util.concurrent.atomic.AtomicBoolean

internal val LocalOverlayTimingOwner = staticCompositionLocalOf<ScanRequestToken?> { null }

/** Observe actual Compose frame delivery and drawing; neither asserts visible display pixels. */
@Composable
fun overlayTimingModifier(token: ScanRequestToken?): Modifier {
    if (token == null || !DebugScanTiming.isEnabled()) return Modifier
    val context = LocalContext.current
    val drawn = remember(token) { AtomicBoolean(false) }
    LaunchedEffect(token) {
        withFrameNanos {
            // Ignore frameTimeNanos: Choreographer's clock is not our elapsed clock.
            DebugScanTiming.withOwner(token) { DebugScanTiming.mark(ScanTimingStage.COMPOSE_FRAME_DELIVERY) }
        }
    }
    return Modifier.drawWithContent {
        drawContent()
        if (drawn.compareAndSet(false, true)) {
            DebugScanTiming.withOwner(token) { DebugScanTiming.mark(ScanTimingStage.COMPOSE_FIRST_DRAW) }
            DebugScanTiming.persistAsync(context, token)
        }
    }
}
