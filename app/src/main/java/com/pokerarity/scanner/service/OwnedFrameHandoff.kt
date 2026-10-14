package com.pokerarity.scanner.service

import com.google.android.gms.tasks.Task
import android.graphics.Bitmap
import com.pokerarity.scanner.BuildConfig
import com.pokerarity.scanner.util.perf.DebugScanTiming
import com.pokerarity.scanner.util.perf.ScanTimingStage
import java.io.File
import java.util.IdentityHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job

/** External experiment. Default OFF, debug only. Source limit: two frames per attempt,
 * four live frames / 48 MiB for an active plus pending request.
 * Task copies: 4 / 32 MiB. A hung native task retains its bounded slot and refuses
 * later work; timeouts never recycle pixels still read by native code.
 */
@Suppress("TooManyFunctions", "MagicNumber")
object OwnedFrameHandoff {
    @Volatile var enabled = false
    fun active() = BuildConfig.DEBUG && enabled
    private const val SOURCE_BYTES = 48L * 1024 * 1024
    private const val TASK_BYTES = 32L * 1024 * 1024
    private val lock = Any()
    private val frames = linkedMapOf<String, Frame>()
    private val owned = IdentityHashMap<Bitmap, Frame>()
    private data class Reader(val token: ScanRequestToken, val kind: String, val lease: Lease)
    private val readers = IdentityHashMap<Bitmap, MutableList<Reader>>()
    private val jobs = mutableMapOf<ScanRequestToken, MutableList<Job>>()
    private var sourceBytes = 0L
    private var taskBytes = 0L
    private var tasks = 0
    private val nativeTasks = mutableListOf<Task<*>>()
    private var peakSourceBytes = 0L
    private var peakTaskBytes = 0L
    private val expiration = lazy {
        ScheduledThreadPoolExecutor(1) { task ->
            Thread(task, "owned-frame-expiry").apply { isDaemon = true }
        }.apply {
            scheduleAtFixedRate({ expireBefore(DebugScanTiming.nowNanos()) }, 1, 1, TimeUnit.SECONDS)
        }
    }
    private val writer = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(4), { task ->
            Thread(task, "owned-frame-evidence").apply { isDaemon = true }
        }, ThreadPoolExecutor.AbortPolicy())

    private class Frame(val token: ScanRequestToken, val sequence: Long,
        val index: Int, val path: String, val pixels: Bitmap) {
        var references = 1
        var accepted = true
        var claimed = false
        var rootOpen = true
        val deadlineNanos = DebugScanTiming.nowNanos() + TimeUnit.SECONDS.toNanos(60)
        val written = CompletableFuture<Boolean>()
    }
    class Lease internal constructor(val pixels: Bitmap, private val release: () -> Unit) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        override fun close() { if (closed.compareAndSet(false, true)) release() }
    }
    private fun retain(frame: Frame): Lease {
        frame.references++
        return Lease(frame.pixels) { synchronized(lock) { release(frame) } }
    }
    private fun release(frame: Frame) {
        frame.references--
        check(frame.references >= 0)
        if (frame.references == 0) {
            owned.remove(frame.pixels)
            sourceBytes -= frame.pixels.allocationByteCount
            frame.pixels.recycle()
        }
    }
    private fun closeRoot(frame: Frame) {
        if (frame.rootOpen) { frame.rootOpen = false; release(frame) }
    }
    private fun pruneStaleRoots() {
        val obsolete = frames.values.filter {
            !ScanRequests.coordinator.isLiveRequest(it.token) &&
                (ScanRequests.coordinator.snapshot(it.token)?.terminalOutcome !=
                    TerminalOutcome.SUCCESS_PUBLISHED || it.written.isDone)
        }.toList()
        obsolete.forEach { frame ->
            if (ScanRequests.coordinator.snapshot(frame.token)?.terminalOutcome != TerminalOutcome.SUCCESS_PUBLISHED) {
                jobs.remove(frame.token)?.forEach { it.cancel() }
            }
            if (frames.remove(frame.path) === frame) { frame.accepted = false; closeRoot(frame) }
        }
    }
    fun hasCapacityForAttempt(width: Int, height: Int): Boolean = synchronized(lock) {
        pruneStaleRoots()
        owned.size <= 2 && sourceBytes + width.toLong() * height * 8 <= SOURCE_BYTES
    }

    /** Reserve before allocating. No queue eviction and no evidence silently discarded. */
    @Suppress("TooGenericExceptionCaught") // Every writer failure is retained in its future; leases always release.
    fun capture(source: Bitmap, file: File, token: ScanRequestToken,
        sequence: Long, index: Int): String = synchronized(lock) {
        check(active() && ScanRequests.coordinator.isLiveRequest(token))
        check(index in 0..1)
        pruneStaleRoots()
        check(owned.size < 4)
        check(frames.values.count { it.token == token } < 2)
        val bytes = source.width.toLong() * source.height * 4
        check(sourceBytes + bytes <= SOURCE_BYTES)
        check(frames.values.filter { it.token == token }.all { it.sequence == sequence })
        check(frames.values.none { it.token == token && it.index == index })
        check(!frames.containsKey(file.absolutePath))
        val pixels = requireNotNull(source.copy(Bitmap.Config.ARGB_8888, false))
        if (sourceBytes + pixels.allocationByteCount > SOURCE_BYTES) {
            pixels.recycle(); error("owned_source_allocation_budget")
        }
        val frame = Frame(token, sequence, index, file.canonicalPath, pixels)
        sourceBytes += pixels.allocationByteCount
        peakSourceBytes = maxOf(peakSourceBytes, sourceBytes)
        owned[pixels] = frame
        frames[frame.path] = frame
        val evidence = retain(frame)
        try {
            writer.execute {
                try {
                    DebugScanTiming.withOwner(token, sequence, index) {
                        DebugScanTiming.measure(ScanTimingStage.PNG_STORAGE) {
                            file.outputStream().use {
                                check(evidence.pixels.compress(Bitmap.CompressFormat.PNG, 85, it))
                            }
                        }
                    }
                    evidence.close()
                    frame.written.complete(true)
                } catch (failure: Exception) {
                    frame.written.completeExceptionally(failure)
                } finally { evidence.close() }
            }
        } catch (failure: RuntimeException) {
            evidence.close(); frames.remove(frame.path); frame.accepted = false; closeRoot(frame)
            throw failure
        }
        expiration.value
        frame.path
    }

    /** Claim once before launching a coroutine so cancellation before entry still releases. */
    fun claim(paths: List<String>, token: ScanRequestToken, sequence: Long): List<Lease>? = synchronized(lock) {
        val selected = paths.map { frames[it] ?: return null }
        if (selected.isEmpty() || selected.size > 2) return null
        if (!ScanRequests.coordinator.isLiveRequest(token)) return null
        if (selected.any { !it.accepted || it.claimed }) return null
        if (selected.any { it.token != token || it.sequence != sequence }) return null
        selected.map { it.claimed = true; retain(it) }
    }

    fun pixels(path: String, token: ScanRequestToken?, reader: String = "fast"): Bitmap? = synchronized(lock) {
        val frame = frames[path]?.takeIf { it.accepted && it.claimed && it.token == token &&
            ScanRequests.coordinator.isLiveRequest(it.token) } ?: return null
        check(readers.values.sumOf { it.size } < 8)
        readers.getOrPut(frame.pixels) { mutableListOf() }.add(Reader(frame.token, reader, retain(frame)))
        frame.pixels
    }
    /** Each decode/detailed/variant read owns a reference separate from the pipeline job. */
    fun releaseRead(bitmap: Bitmap, reader: String = "fast"): Boolean = synchronized(lock) {
        val leases = readers[bitmap] ?: return false
        val index = leases.indexOfLast { it.kind == reader }
        if (index < 0) return false
        leases.removeAt(index).lease.close()
        if (leases.isEmpty()) readers.remove(bitmap)
        true
    }
    fun releaseReaders(token: ScanRequestToken, reader: String) = synchronized(lock) {
        readers.keys.toList().forEach { pixels ->
            val leases = readers[pixels] ?: return@forEach
            val matching = leases.filter { it.token == token && it.kind == reader }
            leases.removeAll(matching.toSet())
            if (leases.isEmpty()) readers.remove(pixels)
            matching.forEach { it.lease.close() }
        }
    }
    fun isOwned(bitmap: Bitmap) = synchronized(lock) { owned.containsKey(bitmap) }
    fun hasPaths(paths: List<String>) = synchronized(lock) { paths.any(frames::containsKey) }
    fun hasPath(path: String) = synchronized(lock) { frames.containsKey(path) }
    fun retireRecognitionRoots(token: ScanRequestToken) = synchronized(lock) {
        frames.values.filter { it.token == token }.forEach { it.accepted = false; closeRoot(it) }
    }
    fun finish(token: ScanRequestToken) = synchronized(lock) {
        jobs.remove(token)?.forEach { it.cancel() }
        frames.values.filter { it.token == token }.toList().forEach {
            frames.remove(it.path); it.accepted = false; closeRoot(it)
        }
    }
    fun invalidate() = synchronized(lock) {
        val cancelled = jobs.keys.filter {
            ScanRequests.coordinator.snapshot(it)?.terminalOutcome != TerminalOutcome.SUCCESS_PUBLISHED
        }
        val running = cancelled.flatMap { jobs.remove(it).orEmpty() }
        running.forEach { it.cancel() }
        frames.values.toList().forEach { it.accepted = false; closeRoot(it) }; frames.clear()
    }
    fun awaitEvidence(path: String) {
        val future = synchronized(lock) { frames[path]?.written } ?: return
        check(future.get(30, TimeUnit.SECONDS))
    }

    /** Native task owns a private immutable copy independently of coroutine cancellation.
     * It also protects derived/scaled/ROI inputs whose callers currently recycle eagerly.
     */
    fun taskCopy(bitmap: Bitmap): Lease = synchronized(lock) {
        val bytes = bitmap.width.toLong() * bitmap.height * 4
        check(tasks < 4 && taskBytes + bytes <= TASK_BYTES)
        val pixels = requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false))
        if (taskBytes + pixels.allocationByteCount > TASK_BYTES) {
            pixels.recycle(); error("owned_task_allocation_budget")
        }
        tasks++; taskBytes += pixels.allocationByteCount
        peakTaskBytes = maxOf(peakTaskBytes, taskBytes)
        Lease(pixels) { synchronized(lock) {
            tasks--; taskBytes -= pixels.allocationByteCount; pixels.recycle()
        } }
    }
    fun <T> trackNativeTask(task: Task<T>, lease: Lease) {
        synchronized(lock) { nativeTasks.add(task) }
        task.addOnCompleteListener(java.util.concurrent.Executor { it.run() }) {
            synchronized(lock) { nativeTasks.remove(task) }
            lease.close()
        }
    }
    fun hasPendingNativeTask(): Boolean = synchronized(lock) { nativeTasks.any { !it.isComplete } }
    fun trackOwnerJob(token: ScanRequestToken, job: Job) {
        synchronized(lock) {
            val group = jobs.getOrPut(token) { mutableListOf() }
            check(group.size < 2)
            check(jobs.values.sumOf { it.size } < 4)
            group.add(job)
        }
        job.invokeOnCompletion { synchronized(lock) {
            jobs[token]?.let { group -> group.remove(job); if (group.isEmpty()) jobs.remove(token) }
        } }
    }
    internal fun expireBefore(nowNanos: Long) = synchronized(lock) {
        val expired = frames.values.filter { it.deadlineNanos <= nowNanos &&
            ScanRequests.coordinator.snapshot(it.token)?.terminalOutcome != TerminalOutcome.SUCCESS_PUBLISHED
        }.map { it.token }.distinct()
        expired.forEach { token ->
            ScanRequests.coordinator.claimTerminal(token, TerminalOutcome.FINAL_FAILURE)
            jobs.remove(token)?.forEach { it.cancel() }
            frames.values.filter { it.token == token }.toList().forEach {
                frames.remove(it.path); it.accepted = false; closeRoot(it)
            }
        }
    }
    fun metrics(): Map<String, Long> = synchronized(lock) { mapOf(
        "liveSourceFrames" to owned.size.toLong(), "liveSourceBytes" to sourceBytes,
        "liveReaderLeases" to readers.values.sumOf { it.size }.toLong(),
        "managedJobs" to jobs.values.sumOf { it.size }.toLong(),
        "expiryQueueSize" to if (expiration.isInitialized()) expiration.value.queue.size.toLong() else 0L,
        "pendingNativeTasks" to nativeTasks.count { !it.isComplete }.toLong(),
        "liveTaskCopies" to tasks.toLong(), "liveTaskBytes" to taskBytes,
        "peakSourceBytes" to peakSourceBytes, "peakTaskBytes" to peakTaskBytes,
        "evidenceQueueSize" to writer.queue.size.toLong()) }
}
