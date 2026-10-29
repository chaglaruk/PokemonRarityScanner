package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pokerarity.scanner.util.ocr.AnchoredScreenText
import com.pokerarity.scanner.util.ocr.HealthBarLocator
import com.pokerarity.scanner.util.ocr.MLKitOcrProvider
import com.pokerarity.scanner.util.ocr.ScreenClassifier
import com.pokerarity.scanner.util.ocr.ScreenGeometryBuilder
import com.pokerarity.scanner.util.ocr.TextParser
import com.pokerarity.scanner.util.diagnostics.ScanReplayReportRedactor
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEBUG-ONLY Phase 2C diagnostic: runs the current [AnchoredScreenText] extraction over
 * the preserved corpus. It writes only aggregate completion counts and timing; typed
 * fields and OCR lines remain in process memory. Not a product test.
 *
 * Input: cacheDir/replay_in (staged PNGs). Output: cacheDir/phase2c_out/baseline.json.
 * No per-frame OCR text or field values are written to a report.
 */
@RunWith(AndroidJUnit4::class)
class Phase2cExtractionBaselineTest {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @Test
    fun baselineExtractionReport() = runBlocking<Unit> {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val inDir = File(appContext.cacheDir, "replay_in")
        val outDir = File(appContext.cacheDir, "phase2c_out")
        outDir.deleteRecursively()
        outDir.mkdirs()
        appContext.getExternalFilesDir(null)?.let { File(it, "phase2c_out").deleteRecursively() }
        val frames = inDir.listFiles { f -> f.extension.equals("png", true) }
            ?.sortedBy { it.name }
            .orEmpty()
        org.junit.Assume.assumeTrue("No replay input frames available", frames.isNotEmpty())

        val provider = MLKitOcrProvider(appContext)
        val parser = TextParser(appContext)
        val classifier = ScreenClassifier()
        val geometryBuilder = ScreenGeometryBuilder()
        provider.warmUp()

        val reports = mutableListOf<Map<String, Any?>>()
        try {
            for (file in frames) {
                reports += try {
                    val startedAt = android.os.SystemClock.elapsedRealtime()
                    val frameResult = baselineOne(file, provider, parser, classifier, geometryBuilder)
                    mapOf(
                        "outcome" to if (frameResult["error"] == null) "COMPLETE" else "ERROR",
                        "totalLatencyMs" to (android.os.SystemClock.elapsedRealtime() - startedAt)
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    mapOf("outcome" to "ERROR")
                }
            }
        } finally {
            provider.close()
        }

        val report = ScanReplayReportRedactor.aggregate(reports)
        File(outDir, "baseline.json").writeText(gson.toJson(report))
    }

    private suspend fun baselineOne(
        file: File,
        provider: MLKitOcrProvider,
        parser: TextParser,
        classifier: ScreenClassifier,
        geometryBuilder: ScreenGeometryBuilder
    ): Map<String, Any?> {
        val decoded = BitmapFactory.decodeFile(file.absolutePath)
            ?: return mapOf("frame" to file.nameWithoutExtension, "error" to "decode_failed")
        val bitmap = if (decoded.width > 900) {
            val scaled = Bitmap.createScaledBitmap(decoded, 900, decoded.height * 900 / decoded.width, true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val classification = classifier.classify(bitmap)
            val geometry = geometryBuilder.build(bitmap, classification)
            val bar = HealthBarLocator.locate(bitmap)
            val started = android.os.SystemClock.elapsedRealtime()
            val layout = provider.recognizeLayout(bitmap)
            val ocrMs = android.os.SystemClock.elapsedRealtime() - started
            val extractionContext = com.pokerarity.scanner.util.ocr.ExtractionContext(
                bar = bar,
                nameBand = bar?.let { ScreenGeometryBuilder.deriveNameBand(it, w, h) },
                detailCardTop = geometry.detailCardRect?.top,
                actionResources = com.pokerarity.scanner.util.ocr.ActionResourceMatcher.observe(bitmap, layout)
            )
            val fields = AnchoredScreenText.extract(layout, parser, w, h, extractionContext)

            val lines = layout.lines.mapNotNull { line ->
                val bounds = line.bounds ?: return@mapNotNull null
                lineText(line.text, bounds, w, h)
            }
            val elements = layout.elements.mapNotNull { element ->
                val bounds = element.bounds ?: return@mapNotNull null
                lineText(element.text, bounds, w, h)
            }

            val result = mapOf(
                "frame" to file.nameWithoutExtension,
                "screenType" to classification.screenType.name,
                "confidence" to classification.confidence,
                "detailCardTop" to geometry.detailCardRect?.let { it.top / h.toFloat() },
                "hpBar" to bar?.let { listOf(it.left, it.top, it.right, it.bottom) },
                "ocrMs" to ocrMs,
                "fields" to linkedMapOf(
                    "cp" to fields.cp,
                    "hp" to fields.hp?.let { "${it.first}/${it.second}" },
                    "candy" to fields.candy,
                    "powerUpCost" to fields.powerUpCost,
                    "evolutionCandyCost" to fields.evolutionCandyCost,
                    "types" to fields.types,
                    "detailScreen" to fields.detailScreen,
                    "numericConflict" to fields.numericConflict,
                    "nameRaw" to fields.nameRaw
                ),
                "lowerHalfLines" to lines.filter { (it["topF"] as Float) > 0.30f },
                "lowerHalfElements" to elements.filter { (it["topF"] as Float) > 0.55f },
                "actionRowLines" to lines.filter {
                    val top = it["topF"] as Float
                    top in 0.55f..0.95f
                }
            )
            result
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun lineText(text: String, bounds: android.graphics.Rect, w: Int, h: Int): Map<String, Any?> = mapOf(
        "text" to text,
        "leftF" to bounds.left / w.toFloat(),
        "topF" to bounds.top / h.toFloat(),
        "rightF" to bounds.right / w.toFloat(),
        "bottomF" to bounds.bottom / h.toFloat()
    )
}
