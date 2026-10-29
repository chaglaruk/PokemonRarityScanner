package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.Rarity
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OverlayStateStoreTest {

    @Test
    fun resetToIdle_clearsPreviousResultState() {
        val pokemon = Pokemon(
            id = 1,
            sourceId = 1,
            name = "Pikachu",
            cp = 500,
            hp = 50,
            iv = null,
            rarityScore = 10,
            rarity = Rarity.COMMON,
            type = "Electric",
            displayDate = "Apr 13, 2026",
            caughtDate = "Apr 13, 2026",
            tags = emptyList(),
            analysis = emptyList()
        )

        OverlayStateStore.dispatch(OverlayIntent.ShowResult(pokemon))
        OverlayStateStore.resetToIdle()

        assertTrue(OverlayStateStore.state.value is OverlayState.Idle)
    }

    @Test
    fun historySaveOutcomeUpdatesOnlyTheResultOwnedByThatRequest() {
        val first = token(1)
        val other = token(2)

        OverlayStateStore.dispatch(OverlayIntent.ShowResult(pokemon(), first))
        OverlayStateStore.dispatch(
            OverlayIntent.HistorySaveFinished(first, OverlayHistorySaveStatus.SAVED)
        )
        assertEquals(
            OverlayHistorySaveStatus.SAVED,
            (OverlayStateStore.state.value as OverlayState.Result).historySaveStatus
        )

        OverlayStateStore.dispatch(
            OverlayIntent.HistorySaveFinished(other, OverlayHistorySaveStatus.FAILED)
        )

        assertEquals(
            OverlayHistorySaveStatus.SAVED,
            (OverlayStateStore.state.value as OverlayState.Result).historySaveStatus
        )
    }

    @Test
    fun saveOutcomeThatArrivesBeforeOverlayIsRetainedForItsResult() {
        val owner = token(3)

        OverlayStateStore.dispatch(
            OverlayIntent.HistorySaveFinished(owner, OverlayHistorySaveStatus.FAILED)
        )
        OverlayStateStore.dispatch(OverlayIntent.ShowResult(pokemon(), owner))

        assertEquals(
            OverlayHistorySaveStatus.FAILED,
            (OverlayStateStore.state.value as OverlayState.Result).historySaveStatus
        )
    }

    @Test
    fun unknownSaveOutcomeDoesNotClaimSuccessOrFailure() {
        val owner = token(4)

        OverlayStateStore.dispatch(OverlayIntent.ShowResult(pokemon(), owner))
        OverlayStateStore.dispatch(
            OverlayIntent.HistorySaveFinished(owner, OverlayHistorySaveStatus.UNKNOWN)
        )

        assertEquals(
            OverlayHistorySaveStatus.UNKNOWN,
            (OverlayStateStore.state.value as OverlayState.Result).historySaveStatus
        )
    }

    @Test
    fun failedVisibleSaveCanRetryOnceAndDismissalReleasesRetryAction() {
        val owner = token(5)
        var retryCount = 0
        OverlayStateStore.dispatch(OverlayIntent.ShowResult(pokemon(), owner))
        OverlayStateStore.registerHistorySaveRetry(owner) {
            retryCount += 1
            true
        }
        OverlayStateStore.dispatch(
            OverlayIntent.HistorySaveFinished(owner, OverlayHistorySaveStatus.FAILED)
        )

        assertTrue(OverlayStateStore.requestHistorySaveRetry(owner))
        assertFalse(OverlayStateStore.requestHistorySaveRetry(owner))
        assertEquals(1, retryCount)
        assertEquals(
            OverlayHistorySaveStatus.PENDING,
            (OverlayStateStore.state.value as OverlayState.Result).historySaveStatus
        )

        OverlayStateStore.dispatch(
            OverlayIntent.HistorySaveFinished(owner, OverlayHistorySaveStatus.FAILED)
        )
        OverlayStateStore.clearVisibleHistorySaveRetry()

        assertFalse(OverlayStateStore.requestHistorySaveRetry(owner))
        assertEquals(
            OverlayHistorySaveStatus.FAILED,
            (OverlayStateStore.state.value as OverlayState.Result).historySaveStatus
        )
    }

    @Test
    fun scannerStopRetainsVisibleCompletedSaveAndFailedRetry() {
        listOf(OverlayHistorySaveStatus.SAVED, OverlayHistorySaveStatus.FAILED).forEach { status ->
            OverlayStateStore.resetToIdle()
            val owner = token(6)
            OverlayStateStore.dispatch(OverlayIntent.ShowResult(pokemon(), owner))
            OverlayStateStore.registerHistorySaveRetry(owner) { true }
            OverlayStateStore.dispatch(OverlayIntent.HistorySaveFinished(owner, status))

            OverlayStateStore.scannerStopped()

            assertTrue(OverlayStateStore.state.value is OverlayState.Idle)
            assertEquals(status, OverlayStateStore.historySaveOutcomes.value[owner])
            assertEquals(status == OverlayHistorySaveStatus.FAILED,
                OverlayStateStore.requestHistorySaveRetry(owner))
            OverlayStateStore.clearVisibleHistorySaveRetry()
            assertFalse(OverlayStateStore.requestHistorySaveRetry(owner))
        }
        OverlayStateStore.resetToIdle()
    }

    @Test
    fun pendingVisibleSaveCanFinishAfterScannerStops() {
        OverlayStateStore.resetToIdle()
        val owner = token(7)
        OverlayStateStore.dispatch(OverlayIntent.ShowResult(pokemon(), owner))
        OverlayStateStore.scannerStopped()

        OverlayStateStore.dispatch(OverlayIntent.HistorySaveFinished(owner, OverlayHistorySaveStatus.SAVED))

        assertEquals(OverlayHistorySaveStatus.SAVED, OverlayStateStore.historySaveOutcomes.value[owner])
        OverlayStateStore.resetToIdle()
    }

    private fun token(id: Long) = ScanRequestToken(
        requestId = id,
        attemptId = 1,
        projectionEpoch = 1,
        origin = RequestOrigin.USER
    )

    private fun pokemon() = Pokemon(
        id = 1,
        sourceId = 1,
        name = "Pikachu",
        cp = 500,
        hp = 50,
        iv = null,
        rarityScore = 10,
        rarity = Rarity.COMMON,
        type = "Electric",
        displayDate = "Apr 13, 2026",
        caughtDate = "Apr 13, 2026",
        tags = emptyList(),
        analysis = emptyList()
    )
}
