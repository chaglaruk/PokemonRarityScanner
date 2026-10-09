package com.pokerarity.scanner.util.ocr

import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OcrTaskAwaitTest {
    @Test
    fun successAndEmptySuccessRemainSuccessful() = runBlocking {
        for (text in listOf("recognized", "")) {
            val source = TaskCompletionSource<String>()
            val result = async(start = CoroutineStart.UNDISPATCHED) { awaitOcrTask { source.task } }
            source.setResult(text)
            assertEquals(text, result.await().value)
            assertFalse(result.await().failed)
        }
    }

    @Test
    fun failedTaskDropsPrivateExceptionPayloadAndNextAttemptSucceeds() = runBlocking {
        val failed = TaskCompletionSource<String>()
        failed.setException(IllegalStateException("private OCR text and path"))
        assertEquals(OcrTaskResult<String>(null, true), awaitOcrTask { failed.task })
        val next = TaskCompletionSource<String>()
        next.setResult("next")
        assertEquals(OcrTaskResult("next", false), awaitOcrTask { next.task })
    }

    @Test
    fun synchronousProviderFailureIsBounded() = runBlocking {
        val result = awaitOcrTask<String> { throw IllegalArgumentException("private path") }
        assertTrue(result.failed)
        assertNull(result.value)
    }

    @Test
    fun providerTaskCancellationTerminatesCoroutine() = runBlocking {
        val token = CancellationTokenSource()
        val source = TaskCompletionSource<String>(token.token)
        val result = async(start = CoroutineStart.UNDISPATCHED) { awaitOcrTask { source.task } }
        token.cancel()
        result.join()
        assertTrue(result.isCancelled)
    }

    @Test
    fun callerCancellationCannotPublishLateTaskSuccess() = runBlocking {
        val source = TaskCompletionSource<String>()
        var published = false
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            awaitOcrTask { source.task }
            published = true
        }
        result.cancel()
        source.setResult("late")
        result.join()
        assertTrue(result.isCancelled)
        assertFalse(published)
    }

    @Test(expected = CancellationException::class)
    fun synchronousCancellationPropagates(): Unit = runBlocking {
        awaitOcrTask<String> { throw CancellationException("cancelled") }
        Unit
    }
}
