package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MLKitOcrProviderLifecycleTest {
    @Test
    fun stopDuringNativeOcrRetainsTaskInputAndDefersRecognizerClose() = runBlocking {
        val taskSource = TaskCompletionSource<Text>()
        val recognizer = GatedRecognizer(taskSource.task)
        val provider = MLKitOcrProvider(RuntimeEnvironment.getApplication(), recognizer)
        val source = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val scan = async(start = CoroutineStart.UNDISPATCHED) {
            provider.recognizeText(source)
        }

        try {
            val taskInput = requireNotNull(recognizer.input)
            assertNotSame("ML Kit must receive a task-owned bitmap", source, taskInput)
            assertFalse(taskInput.isRecycled)

            // ScreenCaptureService.stop releases OCR before it cancels ScanManager's scope.
            provider.close()
            scan.cancel()
            assertEquals("recognizer remains open while the native Task owns its input", 0, recognizer.closeCount)
            assertFalse("cancelling the coroutine must not recycle the Task input", taskInput.isRecycled)

            taskSource.setException(IllegalStateException("controlled_task_failure"))
            scan.join()

            assertTrue("Task completion releases its private bitmap", taskInput.isRecycled)
            assertEquals("recognizer closes after the final native Task", 1, recognizer.closeCount)
        } finally {
            if (!taskSource.task.isComplete) {
                taskSource.setException(IllegalStateException("test_cleanup"))
            }
            provider.close()
            if (!source.isRecycled) source.recycle()
        }
    }

    @Test
    fun taskCopyCountAndByteCapsRejectOverflowAndRecoverAfterRelease() {
        val source = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        var closeCount = 0
        val byteLimited = OcrTaskLifecycle({ closeCount++ }, maxActiveTasks = 4, maxTaskBytes = 256)
        val first = byteLimited.copyInput(source)
        try {
            assertEquals(
                "byte reservation matches the copied ARGB_8888 allocation",
                256,
                first.bitmap.allocationByteCount
            )
            val byteOverflow = runCatching { byteLimited.copyInput(source) }.exceptionOrNull()
            assertEquals("ocr_task_capacity_exceeded", byteOverflow?.message)
            first.close()

            val afterByteRelease = byteLimited.copyInput(source)
            afterByteRelease.close()
            byteLimited.close()
            assertEquals(1, closeCount)
        } finally {
            first.close()
            byteLimited.close()
            if (!source.isRecycled) source.recycle()
        }

        var countCloseCount = 0
        val countSource = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val countLimited = OcrTaskLifecycle({ countCloseCount++ }, maxActiveTasks = 1, maxTaskBytes = 512)
        val countLimitedInput = countLimited.copyInput(countSource)
        try {
            val countOverflow = runCatching { countLimited.copyInput(countSource) }.exceptionOrNull()
            assertEquals("ocr_task_capacity_exceeded", countOverflow?.message)
            countLimitedInput.close()
            countLimited.close()
            assertEquals(1, countCloseCount)
        } finally {
            countLimitedInput.close()
            countLimited.close()
            if (!countSource.isRecycled) countSource.recycle()
        }
    }

    private class GatedRecognizer(private val task: Task<Text>) : OcrTextRecognitionClient {
        var input: Bitmap? = null
            private set
        var closeCount = 0
            private set

        override fun process(bitmap: Bitmap): Task<Text> {
            input = bitmap
            return task
        }

        override fun close() {
            closeCount++
        }
    }
}
