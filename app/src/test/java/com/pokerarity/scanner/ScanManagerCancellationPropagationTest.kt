// Purpose: cancellation must not be converted into a recoverable detailed-OCR miss.
package com.pokerarity.scanner

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.service.FrameOcr
import com.pokerarity.scanner.service.ScanManager
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ScanManagerCancellationPropagationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun detailedPassPropagatesOcrTaskCancellation() = runBlocking {
        val screenshot = createScreenshot()
        val manager = ScanManager(context)
        manager.frameOcr = FrameOcr { throw CancellationException("ocr_task_cancelled") }

        val thrown = try {
            manager.runDetailedPassIfNeeded(screenshot.absolutePath, context = null)
            null
        } catch (cancelled: CancellationException) {
            cancelled
        } finally {
            screenshot.delete()
        }

        assertNotNull("detailed OCR cancellation must reach the caller", thrown)
        assertEquals("ocr_task_cancelled", thrown?.message)
    }

    @Test
    fun detailedPassStillReturnsNullForOrdinaryOcrFailure() = runBlocking {
        val screenshot = createScreenshot()
        val manager = ScanManager(context)
        manager.frameOcr = FrameOcr { throw IllegalStateException("ocr_unavailable") }

        val result = try {
            manager.runDetailedPassIfNeeded(screenshot.absolutePath, context = null)
        } finally {
            screenshot.delete()
        }

        assertEquals(null, result)
    }

    private fun createScreenshot(): File {
        val bitmap = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF888888.toInt())
        return File(context.cacheDir, "scan_cancel_detail_test.png").also { file ->
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            bitmap.recycle()
        }
    }
}
