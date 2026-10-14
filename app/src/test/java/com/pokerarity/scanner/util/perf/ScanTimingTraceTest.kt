package com.pokerarity.scanner.util.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTimingTraceTest {
    private val owner = ScanTimingOwner(1, 2, 3)
    private fun event(start: Long, end: Long, stage: ScanTimingStage = ScanTimingStage.OCR_LAYOUT) =
        ScanTimingEvent(stage, start, end, 1, 7)

    @Test fun boundedTraceRetainsBeginningAndReportsDrops() {
        val trace = ScanTimingTrace(owner, 2)
        trace.record(event(0, 10))
        trace.record(event(10, 20))
        trace.record(event(20, 30))
        assertEquals(2, trace.snapshot().events.size)
        assertEquals(1, trace.snapshot().droppedEvents)
        assertEquals(0L, trace.snapshot().events.first().startNanos)
    }

    @Test fun snapshotsDoNotMutateWithFutureRecords() {
        val trace = ScanTimingTrace(owner)
        trace.record(event(10, 20))
        val snapshot = trace.snapshot()
        trace.record(event(20, 40))
        assertEquals(1, snapshot.events.size)
        assertEquals(owner, snapshot.owner)
        assertEquals("android_elapsed_realtime_nanos", snapshot.clock)
    }

    @Test(expected = IllegalArgumentException::class)
    fun backwardsClockRecordsAreRejected() { ScanTimingTrace(owner).record(event(20, 10)) }

    @Test fun concurrentRecordsRemainBoundedAndOwned() {
        val trace = ScanTimingTrace(owner, 40)
        val threads = (0..3).map { Thread { repeat(20) { trace.record(event(10, 20)) } } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(40, trace.snapshot().events.size)
        assertEquals(40, trace.snapshot().droppedEvents)
    }

    @Test fun detailedWorkWaitAndLaunchIntervalsAreNotAdditiveDurations() {
        val trace = ScanTimingTrace(owner)
        trace.record(event(100, 600, ScanTimingStage.DETAILED_LAUNCH_TO_COMPLETION))
        trace.record(event(200, 600, ScanTimingStage.DETAILED_WORK))
        trace.record(event(300, 620, ScanTimingStage.DETAILED_WAIT))
        val durations = trace.snapshot().events.map { it.endNanos - it.startNanos }
        assertEquals(listOf(500L, 400L, 320L), durations)
        assertTrue(durations.sum() > 620L - 100L)
    }
}
