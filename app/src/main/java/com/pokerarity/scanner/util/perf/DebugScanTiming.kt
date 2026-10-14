package com.pokerarity.scanner.util.perf

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import com.google.gson.Gson
import com.pokerarity.scanner.BuildConfig
import com.pokerarity.scanner.service.ScanRequestToken
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Debug-only, local, bounded spans. No logcat/telemetry payload, screenshots or OCR values. */
@Suppress("TooManyFunctions")
object DebugScanTiming {
    private const val MAX_ATTEMPTS = 8
    private const val NANOS_PER_MS = 1_000_000L
    @Volatile var enabled: Boolean = true
    private data class Binding(val trace: ScanTimingTrace, val frame: Int?, val sequence: Long?)
    private val binding = ThreadLocal<Binding?>()
    private val traces = linkedMapOf<ScanTimingOwner, ScanTimingTrace>()
    private val writer by lazy {
        ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(MAX_ATTEMPTS),
            { task -> Thread(task, "scan-timing-writer").apply { isDaemon = true } },
            ThreadPoolExecutor.DiscardOldestPolicy())
    }

    fun nowNanos(): Long = SystemClock.elapsedRealtimeNanos()
    fun nowMillis(): Long = nowNanos() / NANOS_PER_MS
    fun nanosToMillis(nanos: Long): Long = nanos / NANOS_PER_MS
    private fun active(): Boolean = BuildConfig.DEBUG && enabled
    fun isEnabled(): Boolean = active()
    private fun owner(token: ScanRequestToken) =
        ScanTimingOwner(token.requestId, token.attemptId, token.projectionEpoch)

    @Synchronized private fun trace(token: ScanRequestToken): ScanTimingTrace {
        val key = owner(token)
        return traces.getOrPut(key) {
            if (traces.size == MAX_ATTEMPTS) traces.remove(traces.keys.first())
            ScanTimingTrace(key)
        }
    }

    fun context(token: ScanRequestToken, frame: Int? = null, sequence: Long? = null): CoroutineContext =
        binding.asContextElement(if (active()) Binding(trace(token), frame, sequence) else null)

    fun requestAccepted(token: ScanRequestToken) = withOwner(token) { mark(ScanTimingStage.REQUEST_ACCEPTED) }

    fun <T> withOptionalOwner(token: ScanRequestToken?, block: () -> T): T =
        if (token == null) block() else withOwner(token, block = block)

    fun markForOwner(token: ScanRequestToken?, stage: ScanTimingStage) =
        withOptionalOwner(token) { mark(stage) }

    fun <T> withOwner(token: ScanRequestToken, sequence: Long? = null, frame: Int? = null, block: () -> T): T {
        val previous = binding.get()
        binding.set(if (active()) Binding(trace(token), frame, sequence) else null)
        return try { block() } finally { binding.set(previous) }
    }

    suspend fun <T> withFrame(frame: Int?, block: suspend () -> T): T {
        val current = binding.get()
        return withContext(binding.asContextElement(current?.copy(frame = frame))) { block() }
    }

    fun mark(stage: ScanTimingStage) {
        val current = binding.get()?.takeIf { active() } ?: return
        val now = nowNanos()
        current.trace.record(ScanTimingEvent(stage, now, now, current.frame, current.sequence))
    }

    fun interval(stage: ScanTimingStage, start: Long, end: Long) {
        val current = binding.get()?.takeIf { active() } ?: return
        current.trace.record(ScanTimingEvent(stage, start, end, current.frame, current.sequence))
    }

    fun begin(stage: ScanTimingStage, inputWidth: Int? = null, inputHeight: Int? = null): Span? =
        binding.get()?.takeIf { active() }?.let {
        val size = if (inputWidth != null && inputHeight != null) inputWidth to inputHeight else null
        Span(it.trace, stage, nowNanos(), it.frame, it.sequence, size)
    }

    class Span internal constructor(
        private val trace: ScanTimingTrace, private val stage: ScanTimingStage,
        private val start: Long, private val frame: Int?, private val sequence: Long?,
        private val inputSize: Pair<Int, Int>?
    ) : AutoCloseable {
        private var closed = false
        @Synchronized fun finish(outcome: String) {
            if (!closed) {
                closed = true
                trace.record(ScanTimingEvent(stage, start, nowNanos(), frame, sequence, outcome,
                    inputSize?.first, inputSize?.second))
            }
        }
        override fun close() = finish("completed")
    }

    @Suppress("TooGenericExceptionCaught") // Observation only: every failure is rethrown unchanged.
    fun <T> measure(stage: ScanTimingStage, block: () -> T): T {
        val span = begin(stage)
        return try { block() } catch (failure: CancellationException) {
            span?.finish("cancelled")
            throw failure
        } catch (failure: Throwable) {
            span?.finish("failed")
            throw failure
        } finally { span?.close() }
    }

    @Suppress("TooGenericExceptionCaught") // Observation only: every failure is rethrown unchanged.
    suspend fun <T> measureSuspend(stage: ScanTimingStage, block: suspend () -> T): T {
        val span = begin(stage)
        return try { block() } catch (failure: CancellationException) {
            span?.finish("cancelled")
            throw failure
        } catch (failure: Throwable) {
            span?.finish("failed")
            throw failure
        } finally { span?.close() }
    }

    @Synchronized fun snapshot(token: ScanRequestToken): ScanTimingSnapshot? =
        if (active()) traces[owner(token)]?.snapshot() else null

    /** Local cache only; caller runs this outside the UI critical path. Files mirror the bounded ring. */
    fun persist(context: Context, token: ScanRequestToken) {
        val snapshot = snapshot(token) ?: return
        runCatching {
            val directory = File(context.cacheDir, "debug_scan_timing").apply { mkdirs() }
            val name = "${token.projectionEpoch}-${token.requestId}-${token.attemptId}.json"
            File(directory, name).writeText(Gson().toJson(snapshot))
            directory.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_ATTEMPTS)?.forEach { it.delete() }
        }
    }

    fun persistAsync(context: Context, token: ScanRequestToken) {
        if (active()) writer.execute { persist(context.applicationContext, token) }
    }

    /** A first Android draw is a timestamp, never a claim of visible/nontransparent result pixels. */
    fun observeFirstDraw(view: View, token: ScanRequestToken) {
        if (!active()) return
        val observer = object : ViewTreeObserver.OnDrawListener {
            private var recorded = false
            override fun onDraw() {
                if (recorded) return
                recorded = true
                withOwner(token) { mark(ScanTimingStage.ANDROID_VIEW_FIRST_DRAW) }
                persistAsync(view.context, token)
                view.post { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(this) }
            }
        }
        view.viewTreeObserver.addOnDrawListener(observer)
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) {
                if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(observer)
                view.removeOnAttachStateChangeListener(this)
            }
        })
    }
}
