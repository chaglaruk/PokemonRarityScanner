package com.pokerarity.scanner

import com.pokerarity.scanner.ui.splash.showSplashAfterMinimumPresentation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashNavigationTimingTest {

    @Test
    fun navigationRunsAfterPresentationMinimum() = runBlocking {
        var navigated = false

        val startedAt = System.nanoTime()
        showSplashAfterMinimumPresentation {
            navigated = true
        }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertTrue(navigated)
        assertTrue("Splash should remain visible for its minimum presentation interval", elapsedMillis >= 1_400)
    }
}
