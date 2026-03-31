package com.pokerarity.scanner

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.util.ocr.OCRProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrDateRegressionTest {

    @Test
    fun slowpokeRegularFixtureParsesCaughtDate() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixtureContext = instrumentation.context
        val appContext = instrumentation.targetContext
        val bitmap = fixtureContext.assets.open(
            "scan_fixtures/live_variant_batch_20260318/scan_1773797298959_0.png"
        ).use(BitmapFactory::decodeStream)

        requireNotNull(bitmap) { "Fixture bitmap decode failed" }

        val processor = OCRProcessor(appContext)
        try {
            processor.ensureInitialized()
            val result = processor.processImage(bitmap, includeSecondaryFields = true)
            assertNotNull("Expected caughtDate for slowpoke regular fixture. raw=${result.rawOcrText}", result.caughtDate)
        } finally {
            processor.release()
            bitmap.recycle()
        }
    }
}
