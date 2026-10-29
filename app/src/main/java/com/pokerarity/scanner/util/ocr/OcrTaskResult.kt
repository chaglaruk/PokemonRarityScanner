package com.pokerarity.scanner.util.ocr

import com.google.android.gms.tasks.Task
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** No exception payload crosses the OCR boundary. Task cancellation remains cancellation. */
internal data class OcrTaskResult<T>(val value: T?, val failed: Boolean)

private val ocrCompletionExecutor = Executor { runnable -> runnable.run() }

internal suspend fun <T> awaitOcrTask(
    onTaskComplete: () -> Unit = {},
    createTask: () -> Task<T>
): OcrTaskResult<T> {
    val completionDelivered = AtomicBoolean(false)
    fun releaseTaskResources() {
        if (completionDelivered.compareAndSet(false, true)) onTaskComplete()
    }

    var taskCreated = false
    val task = try {
        createTask().also { taskCreated = true }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: RuntimeException) {
        return OcrTaskResult(null, failed = true)
    } finally {
        if (!taskCreated) releaseTaskResources()
    }
    return suspendCancellableCoroutine { continuation ->
        task.addOnCompleteListener(ocrCompletionExecutor) { completed ->
            try {
                if (continuation.isActive) {
                    when {
                        completed.isCanceled -> continuation.cancel(CancellationException("ocr_task_cancelled"))
                        completed.isSuccessful -> continuation.resume(OcrTaskResult(completed.result, failed = false))
                        else -> continuation.resume(OcrTaskResult(null, failed = true))
                    }
                }
            } finally {
                releaseTaskResources()
            }
        }
    }
}
