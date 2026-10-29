package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pokerarity.scanner.util.ocr.ScreenClassifier
import com.pokerarity.scanner.util.diagnostics.ScanReplayReportRedactor
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEBUG-ONLY Phase 2A diagnostic: runs the CURRENT [ScreenClassifier] as a preflight
 * over the preserved replay corpus staged under cacheDir/replay_in (PNG files, pushed
 * via adb + run-as) WITHOUT touching the recognition pipeline. It writes only aggregate
 * screen-category counts and timing. Not a product test.
 *
 * Args: -e scale baseline900|native (default baseline900 = production input policy)
 * Output: aggregate-only cacheDir/router_out/report.json.
 */
@RunWith(AndroidJUnit4::class)
class ScreenRouterBaselineTest {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    @Test
    fun classifyReplayFrames() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()

        val scalePolicy = if (args.getString("scale") == "native") "native" else "baseline900"
        val inDir = File(appContext.cacheDir, "replay_in")
        val outDir = File(appContext.cacheDir, "router_out")
        outDir.deleteRecursively()
        outDir.mkdirs()
        appContext.getExternalFilesDir(null)?.let { File(it, "router_out").deleteRecursively() }

        val frames = inDir.listFiles { f -> f.extension.equals("png", true) }
            ?.sortedBy { it.name }
            .orEmpty()
        org.junit.Assume.assumeTrue("No replay input frames available", frames.isNotEmpty())

        val classifier = ScreenClassifier()
        val reports = frames.map { file ->
            val decoded = BitmapFactory.decodeFile(file.absolutePath)
                ?: return@map mapOf("outcome" to "ERROR")
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
                "outcome" to result.screenType.name,
                "totalLatencyMs" to elapsedMs
            )
        }

        val report = linkedMapOf<String, Any?>(
            "generatedAtMs" to System.currentTimeMillis(),
            "scalePolicy" to scalePolicy
        ).apply { putAll(ScanReplayReportRedactor.aggregate(reports)) }
        val reportFile = File(outDir, "report.json")
        reportFile.writeText(gson.toJson(report))
    }
}
