package com.pokerarity.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.util.diagnostics.ScanReplayReportRedactor
import com.pokerarity.scanner.util.ocr.FrameDiagnostic
import com.pokerarity.scanner.util.ocr.FrameRouteDiagnostic
import com.pokerarity.scanner.util.ocr.OCRProcessor
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEBUG-ONLY Phase 2B diagnostic: cold/warm persistent-calibration replay over the
 * preserved corpus, driven through the PRODUCTION frame step ([ScanManager.processRoutedFrame])
 * exactly as the pipeline runs it (source geometry preserved, 900-wide recognition policy).
 *
 * Pass "cold" (arg -e pass cold): clears ONLY the dedicated calibration preferences file,
 * then runs every frame — every detail frame must go through full geometry autoconfig.
 * Pass "warm" (arg -e pass warm): runs again WITHOUT clearing, in a fresh instrumentation
 * process, proving the calibration survived on disk (not an in-memory cache).
 *
 * Input: cacheDir/replay_in (staged PNGs). Output: cacheDir/phase2b_out/report_<pass>.json
 * (pulled via run-as). Not a product test.
 */
@RunWith(AndroidJUnit4::class)
class Phase2bCalibrationReplayTest {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    @Test
    fun calibrationReplay() = runBlocking<Unit> {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val requestedPass = InstrumentationRegistry.getArguments().getString("pass")
        org.junit.Assume.assumeTrue(
            "Replay pass must be cold or warm",
            requestedPass == null || requestedPass == "cold" || requestedPass == "warm"
        )
        val pass = requestedPass ?: "cold"

        val outDir = File(appContext.cacheDir, "phase2b_out")
        if (pass == "cold") outDir.deleteRecursively()
        outDir.mkdirs()
        cleanupLegacyReports(outDir)
        appContext.getExternalFilesDir(null)?.let { File(it, "phase2b_out").deleteRecursively() }

        if (pass == "cold") {
            clearOnlyCalibrationPreferences(appContext)
        }

        val inDir = File(appContext.cacheDir, "replay_in")
        val frames = inDir.listFiles { f -> f.extension.equals("png", true) }
            ?.sortedBy { it.name }
            .orEmpty()
        org.junit.Assume.assumeTrue("No replay input frames available", frames.isNotEmpty())

        val manager = ScanManager(appContext)
        val ocrProcessor = OCRProcessor(appContext)
        ocrProcessor.ensureInitialized()

        val reports = mutableListOf<Map<String, Any?>>()
        try {
            for ((index, file) in frames.withIndex()) {
                reports += try {
                    replayOne(index, file, manager)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    mapOf("outcome" to "ERROR")
                }
            }
        } finally {
            ocrProcessor.release()
        }

        val report = linkedMapOf<String, Any?>(
            "reportVersion" to 2,
            "generatedAtMs" to System.currentTimeMillis(),
            "pass" to pass
        ).apply { putAll(ScanReplayReportRedactor.aggregate(reports)) }
        val outFile = File(outDir, "report_$pass.json")
        outFile.writeText(gson.toJson(report))
        // Keep only aggregate metrics in app-private cache; do not export per-frame scan details.
    }

    private suspend fun replayOne(index: Int, file: File, manager: ScanManager): Map<String, Any?> {
        val decoded = BitmapFactory.decodeFile(file.absolutePath)
            ?: return mapOf("outcome" to "ERROR")
        val sourceWidth = decoded.width
        val sourceHeight = decoded.height

        // Production caller-side policy: full frame downscaled to width 900 before OCR.
        val bitmap = if (decoded.width > ScanManager900Policy.MAX_WIDTH) {
            val scaled = Bitmap.createScaledBitmap(
                decoded, 900, decoded.height * 900 / decoded.width, true
            )
            if (scaled !== decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }
        val cpQuality = estimateReplayCpQuality(bitmap)

        val results = mutableListOf<ScanFrameCandidate>()
        val frameDiagnostics = mutableListOf<FrameDiagnostic>()
        val routes = mutableListOf<FrameRouteDiagnostic>()

        val started = android.os.SystemClock.elapsedRealtime()
        manager.processRoutedFrame(
            ScanManager.DecodedFrame(
                index = index,
                path = file.absolutePath,
                bitmap = bitmap,
                cpQuality = cpQuality,
                pooled = false,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight
            ),
            results, frameDiagnostics, routes
        )
        val frameMs = android.os.SystemClock.elapsedRealtime() - started

        val diagnostic = frameDiagnostics.singleOrNull()
        val calibration = diagnostic?.calibration
        return mapOf(
            "outcome" to if (calibration == null) "CALIBRATION_UNAVAILABLE" else "CALIBRATION_AVAILABLE",
            "totalLatencyMs" to frameMs
        )
    }

    /** Clears ONLY the dedicated calibration preferences file — no unrelated app data. */
    private fun clearOnlyCalibrationPreferences(context: Context) {
        val prefs = context.getSharedPreferences("screen_calibration_geometry", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        Log.i(TAG, "Cleared calibration preferences")
    }

    private fun cleanupLegacyReports(outDir: File) {
        listOf("report_cold.json", "report_warm.json").forEach { name ->
            val report = File(outDir, name)
            val isAggregateReport = runCatching {
                JsonParser.parseString(report.readText())
                    .asJsonObject
                    .get("reportVersion")
                    ?.asInt == 2
            }.getOrDefault(false)
            if (report.exists() && !isAggregateReport) report.delete()
        }
    }

    private object ScanManager900Policy {
        const val MAX_WIDTH = 900
    }

    private companion object {
        const val TAG = "Phase2bCalibrationReplay"
    }
}
