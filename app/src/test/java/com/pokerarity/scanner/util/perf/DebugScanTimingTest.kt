package com.pokerarity.scanner.util.perf

import com.pokerarity.scanner.service.RequestOrigin
import com.pokerarity.scanner.service.ScanRequestToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DebugScanTimingTest {
    private fun token(id: Long) = ScanRequestToken(id, 1, 11, RequestOrigin.USER)
    @After fun restoreEnabled() { DebugScanTiming.enabled = true }

    @Test fun disabledInstrumentationDoesNotReadOrCreateTrace() {
        DebugScanTiming.enabled = false
        val token = token(100)
        DebugScanTiming.requestAccepted(token)
        assertEquals(42, DebugScanTiming.withOwner(token) {
            DebugScanTiming.measure(ScanTimingStage.TUPLE_FEASIBILITY) { 42 }
        })
        assertNull(DebugScanTiming.snapshot(token))
    }

    @Test fun dispatcherSwitchesCarryFrameAndOwnerWithoutLeaking() = runBlocking {
        val token = token(101)
        withContext(DebugScanTiming.context(token, sequence = 3)) {
            DebugScanTiming.withFrame(2) {
                withContext(Dispatchers.Default) { DebugScanTiming.mark(ScanTimingStage.OCR_LAYOUT) }
            }
        }
        DebugScanTiming.mark(ScanTimingStage.OCR_TEXT)
        val events = DebugScanTiming.snapshot(token)!!.events
        assertEquals(1, events.size)
        assertEquals(2, events.single().frameIndex)
        assertEquals(3L, events.single().captureSequenceId)
        assertEquals(101L, DebugScanTiming.snapshot(token)!!.owner.requestId)
    }

    @Test fun cancellationIsRethrownAndRecorded() = runBlocking {
        val token = token(102)
        var caught = false
        withContext(DebugScanTiming.context(token)) {
            try {
                DebugScanTiming.measureSuspend(ScanTimingStage.DETAILED_WORK) { throw CancellationException() }
            } catch (_: CancellationException) { caught = true }
        }
        assertTrue(caught)
        assertEquals("cancelled", DebugScanTiming.snapshot(token)!!.events.single().outcome)
    }

    @Test fun attemptsAndProjectionEpochsCannotShareTrace() {
        val first = token(103)
        val retry = first.copy(attemptId = 2)
        val otherEpoch = first.copy(projectionEpoch = 12)
        listOf(first, retry, otherEpoch).forEach(DebugScanTiming::requestAccepted)
        assertTrue(DebugScanTiming.snapshot(first)!!.owner != DebugScanTiming.snapshot(retry)!!.owner)
        assertTrue(DebugScanTiming.snapshot(first)!!.owner != DebugScanTiming.snapshot(otherEpoch)!!.owner)
    }

    @Test fun completedCacheSnapshotsReplaceAtomicallyAndRemainBounded() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val first = token(110)
        DebugScanTiming.requestAccepted(first)
        DebugScanTiming.persist(context, first)
        DebugScanTiming.withOwner(first) { DebugScanTiming.mark(ScanTimingStage.PROCESSING_FINISHED) }
        DebugScanTiming.persist(context, first)
        val directory = java.io.File(context.cacheDir, "debug_scan_timing")
        val json = java.io.File(directory, "11-110-1.json").readText()
        val saved = com.google.gson.Gson().fromJson(json, ScanTimingSnapshot::class.java)
        assertEquals(first.requestId, saved.owner.requestId)
        assertTrue(saved.events.any { it.stage == ScanTimingStage.PROCESSING_FINISHED })
        for (id in 111L..120L) {
            val next = token(id)
            DebugScanTiming.requestAccepted(next)
            DebugScanTiming.persist(context, next)
        }
        val files = directory.listFiles().orEmpty()
        assertEquals(8, files.size)
        assertTrue(files.all { it.extension == "json" })
        files.forEach { com.google.gson.JsonParser.parseString(it.readText()) }
    }

}
