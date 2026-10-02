package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pokerarity.scanner.util.ocr.ScreenClassifier
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEBUG-ONLY Phase 2A diagnostic: runs the CURRENT [ScreenClassifier] as a preflight
 * over the preserved replay corpus staged under cacheDir/replay_in (PNG files, pushed
 * via adb + run-as) WITHOUT touching the recognition pipeline, and records per-frame
 * routing evidence. Not a product test.
 *
 * Args: -e scale baseline900|native (default baseline900 = production input policy)
 * Output: cacheDir/replay_out/report.json (pulled via run-as).
 */
@RunWith(AndroidJUnit4::class)
class ScreenRouterBaselineTest {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @Test
    fun classifyReplayFrames() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()

        val scalePolicy = args.getString("scale") ?: "baseline900"
        val inDir = File(appContext.cacheDir, "replay_in")
        val outDir = File(appContext.cacheDir, "router_out")
        outDir.deleteRecursively()
        outDir.mkdirs()

        val frames = inDir.listFiles { f -> f.extension.equals("png", true) }
            ?.sortedBy { it.name }
            .orEmpty()
        org.junit.Assume.assumeTrue(
            "No replay input PNGs in ${inDir.absolutePath}",
            frames.isNotEmpty()
        )

        val classifier = ScreenClassifier()
        val reports = frames.map { file ->
            val decoded = BitmapFactory.decodeFile(file.absolutePath)
                ?: return@map mapOf<String, Any?>(
                    "frame" to file.nameWithoutExtension,
                    "error" to "decode_failed"
                )
            val bitmap = if (scalePolicy == "baseline900" && decoded.width > 900) {
                val scaled = Bitmap.createScaledBitmap(
                    decoded, 900, decoded.height * 900 / decoded.width, true)
                if (scaled !== decoded) decoded.recycle()
                scaled
            } else {
                decoded
            }
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val result = classifier.classify(bitmap)
            val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAt
            bitmap.recycle()
            mapOf(
                "frame" to file.nameWithoutExtension,
                "screenType" to result.screenType.name,
                "confidence" to result.confidence,
                "safeFallback" to result.safeFallback,
                "reasons" to result.reasons,
                "anchors" to result.anchors.map { anchor ->
                    mapOf(
                        "name" to anchor.name.name,
                        "confidence" to anchor.confidence,
                        "reason" to anchor.reason,
                        "rect" to listOf(anchor.rect.left, anchor.rect.top, anchor.rect.right, anchor.rect.bottom)
                    )
                },
                "classifyMs" to elapsedMs,
                "inputDims" to listOf(bitmap.width, bitmap.height)
            )
        }

        val report = mapOf(
            "generatedAtMs" to System.currentTimeMillis(),
            "scalePolicy" to scalePolicy,
            "frameCount" to frames.size,
            "frames" to reports
        )
        val reportFile = File(outDir, "report.json")
        reportFile.writeText(gson.toJson(report))
        appContext.getExternalFilesDir(null)?.let { ext ->
            val extDir = File(ext, "router_out")
            extDir.mkdirs()
            reportFile.copyTo(File(extDir, "report.json"), overwrite = true)
        }
        Log.i("ScreenRouterBaseline", "Classified ${frames.size} frames -> ${reportFile.absolutePath}")
    }
}
