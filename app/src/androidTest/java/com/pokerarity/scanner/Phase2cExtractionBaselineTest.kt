package com.pokerarity.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.AnchoredScreenText
import com.pokerarity.scanner.util.ocr.HealthBarLocator
import com.pokerarity.scanner.util.ocr.MLKitOcrProvider
import com.pokerarity.scanner.util.ocr.ScreenClassifier
import com.pokerarity.scanner.util.ocr.ScreenGeometryBuilder
import com.pokerarity.scanner.util.ocr.TextParser
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEBUG-ONLY Phase 2C diagnostic: per-field baseline extraction report over the preserved
 * corpus. For each staged frame it records the current [AnchoredScreenText] extraction
 * result (typed fields), the geometry provenance, and the raw OCR line/element layout in
 * the detail-card region so the earliest failing stage of each known field defect can be
 * identified from real evidence. Not a product test.
 *
 * Input: cacheDir/replay_in (staged PNGs). Output: cacheDir/phase2c_out/baseline.json.
 * Raw OCR text stays in local diagnostics only, consistent with existing replay harnesses.
 */
@RunWith(AndroidJUnit4::class)
class Phase2cExtractionBaselineTest {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @Test
    fun baselineExtractionReport() = runBlocking<Unit> {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val inDir = File(appContext.cacheDir, "replay_in")
        val outDir = File(appContext.cacheDir, "phase2c_out")
        outDir.mkdirs()
        val frames = inDir.listFiles { f -> f.extension.equals("png", true) }
            ?.sortedBy { it.name }
            .orEmpty()
        org.junit.Assume.assumeTrue("No replay input PNGs in ${inDir.absolutePath}", frames.isNotEmpty())

        val provider = MLKitOcrProvider(appContext)
        val parser = TextParser(appContext)
        val classifier = ScreenClassifier()
        val geometryBuilder = ScreenGeometryBuilder()
        provider.warmUp()

        val reports = mutableListOf<Map<String, Any?>>()
        try {
            for (file in frames) {
                reports += try {
                    baselineOne(appContext, file, provider, parser, classifier, geometryBuilder)
                } catch (t: Throwable) {
                    Log.e(TAG, "Frame ${file.name} failed: ${t.message}", t)
                    mapOf("frame" to file.nameWithoutExtension, "error" to (t.message ?: t.toString()))
                }
            }
        } finally {
            provider.close()
        }

        val report = mapOf("frameCount" to frames.size, "frames" to reports)
        File(outDir, "baseline.json").writeText(gson.toJson(report))
        appContext.getExternalFilesDir(null)?.let { ext ->
            val extDir = File(ext, "phase2c_out")
            extDir.mkdirs()
            File(extDir, "baseline.json").writeText(gson.toJson(report))
        }
        Log.i(TAG, "Phase 2C baseline complete: ${frames.size} frames")
    }

    private suspend fun baselineOne(
        appContext: Context,
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
            detailCardTop = geometry.detailCardRect?.top
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
        bitmap.recycle()
        return result
    }

    private fun lineText(text: String, bounds: android.graphics.Rect, w: Int, h: Int): Map<String, Any?> = mapOf(
        "text" to text,
        "leftF" to bounds.left / w.toFloat(),
        "topF" to bounds.top / h.toFloat(),
        "rightF" to bounds.right / w.toFloat(),
        "bottomF" to bounds.bottom / h.toFloat()
    )

    private companion object {
        const val TAG = "Phase2cBaseline"
    }
}
