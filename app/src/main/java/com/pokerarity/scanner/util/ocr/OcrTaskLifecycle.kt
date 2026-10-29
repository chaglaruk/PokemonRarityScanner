package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicBoolean

/** Defers recognizer shutdown until every submitted native OCR task releases its input. */
internal class OcrTaskLifecycle(
    private val closeRecognizer: () -> Unit,
    private val maxActiveTasks: Int = MAX_ACTIVE_TASKS,
    private val maxTaskBytes: Long = MAX_TASK_BYTES
) {
    private companion object {
        const val MAX_ACTIVE_TASKS = 4
        const val MAX_TASK_BYTES = 32L * 1024 * 1024
        const val BYTES_PER_ARGB_8888_PIXEL = 4
    }

    private val lock = Any()
    private var activeTasks = 0
    private var activeTaskBytes = 0L
    private var closeRequested = false
    private var recognizerClosed = false

    internal class Lease internal constructor(private val releaseTask: () -> Unit) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) releaseTask()
        }
    }

    internal class BitmapInput internal constructor(
        val bitmap: Bitmap,
        private val taskLease: Lease
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                if (!bitmap.isRecycled) bitmap.recycle()
                taskLease.close()
            }
        }
    }

    fun acquireTask(reservedBytes: Long = 0): Lease = synchronized(lock) {
        check(!closeRequested) { "ocr_recognizer_closing" }
        require(reservedBytes >= 0) { "ocr_task_byte_reservation_invalid" }
        check(activeTasks < maxActiveTasks && activeTaskBytes + reservedBytes <= maxTaskBytes) {
            "ocr_task_capacity_exceeded"
        }
        activeTasks++
        activeTaskBytes += reservedBytes
        Lease { releaseTask(reservedBytes) }
    }

    fun copyInput(source: Bitmap): BitmapInput {
        val reservedBytes = source.width.toLong() * source.height * BYTES_PER_ARGB_8888_PIXEL
        val taskLease = acquireTask(reservedBytes)
        var ownershipTransferred = false
        try {
            val bitmap = requireNotNull(source.copy(Bitmap.Config.ARGB_8888, false)) {
                "ocr_input_copy_failed"
            }
            return BitmapInput(bitmap, taskLease).also { ownershipTransferred = true }
        } finally {
            if (!ownershipTransferred) taskLease.close()
        }
    }

    fun close() {
        val closeNow = synchronized(lock) {
            closeRequested = true
            markRecognizerClosedIfIdle()
        }
        if (closeNow) closeRecognizer()
    }

    private fun releaseTask(reservedBytes: Long) {
        val closeNow = synchronized(lock) {
            check(activeTasks > 0) { "ocr_task_lease_underflow" }
            activeTasks--
            activeTaskBytes -= reservedBytes
            check(activeTaskBytes >= 0) { "ocr_task_byte_lease_underflow" }
            markRecognizerClosedIfIdle()
        }
        if (closeNow) closeRecognizer()
    }

    /** Must be called while [lock] is held. */
    private fun markRecognizerClosedIfIdle(): Boolean {
        if (!closeRequested || activeTasks != 0 || recognizerClosed) return false
        recognizerClosed = true
        return true
    }
}
