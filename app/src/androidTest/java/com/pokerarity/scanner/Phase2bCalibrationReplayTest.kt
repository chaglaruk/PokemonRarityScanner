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
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.util.ocr.FrameDiagnostic
import com.pokerarity.scanner.util.ocr.FrameRouteDiagnostic
import com.pokerarity.scanner.util.ocr.OCRProcessor
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
    private val rarityCalculator = RarityCalculator(
        InstrumentationRegistry.getInstrumentation().targetContext
    )

    @Test
    fun calibrationReplay() = runBlocking<Unit> {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val pass = InstrumentationRegistry.getArguments().getString("pass") ?: "cold"

        if (pass == "cold") {
            clearOnlyCalibrationPreferences(appContext)
        }

        val inDir = File(appContext.cacheDir, "replay_in")
        val outDir = File(appContext.cacheDir, "phase2b_out")
        outDir.mkdirs()
        val frames = inDir.listFiles { f -> f.extension.equals("png", true) }
            ?.sortedBy { it.name }
            .orEmpty()
        org.junit.Assume.assumeTrue("No replay input PNGs in ${inDir.absolutePath}", frames.isNotEmpty())

        val manager = ScanManager(appContext)
        val ocrProcessor = OCRProcessor(appContext)
        ocrProcessor.ensureInitialized()

        val reports = mutableListOf<Map<String, Any?>>()
        try {
            for ((index, file) in frames.withIndex()) {
                reports += try {
                    replayOne(index, file, manager)
                } catch (t: Throwable) {
                    Log.e(TAG, "Frame ${file.name} failed: ${t.message}", t)
                    mapOf(
                        "frame" to file.nameWithoutExtension,
                        "error" to (t.message ?: t.toString()),
                        "errorType" to t.javaClass.simpleName
                    )
                }
            }
        } finally {
            ocrProcessor.release()
        }

        val counts = reports.groupingBy { it["calibrationResolution"] ?: "none" }.eachCount()
        val report = mapOf(
            "generatedAtMs" to System.currentTimeMillis(),
            "pass" to pass,
            "frameCount" to frames.size,
            "calibrationResolutionCounts" to counts,
            "frames" to reports
        )
        val outFile = File(outDir, "report_$pass.json")
        outFile.writeText(gson.toJson(report))
        appContext.getExternalFilesDir(null)?.let { ext ->
            val extDir = File(ext, "phase2b_out")
            extDir.mkdirs()
            outFile.copyTo(File(extDir, "report_$pass.json"), overwrite = true)
        }
        Log.i(TAG, "Phase 2B replay pass=$pass complete: ${frames.size} frames -> ${outFile.absolutePath}")
    }

    private suspend fun replayOne(index: Int, file: File, manager: ScanManager): Map<String, Any?> {
        val decoded = BitmapFactory.decodeFile(file.absolutePath)
            ?: return mapOf("frame" to file.nameWithoutExtension, "error" to "decode_failed")
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

        val route = routes.singleOrNull()
        val diagnostic = frameDiagnostics.singleOrNull()
        val calibration = diagnostic?.calibration
        val speciesEvidence = diagnostic?.let {
            ScanManager.deriveSpeciesEvidence(it.fieldCandidates, results.lastOrNull()?.data ?: it.selected.let { summary ->
                com.pokerarity.scanner.data.model.PokemonData(
                    cp = summary.cp, hp = summary.hp, maxHp = summary.maxHp,
                    name = summary.name, realName = summary.realName, candyName = summary.candyName,
                    megaEnergy = null, weight = null, height = null, stardust = null, caughtDate = null
                )
            }, rarityCalculator)
        }
        return mapOf(
            "frame" to file.nameWithoutExtension,
            "source" to "${sourceWidth}x$sourceHeight",
            "recognition" to "${bitmap.width}x${bitmap.height}",
            "route" to route?.action?.name,
            "screenType" to route?.screenType,
            "ocrInvoked" to (diagnostic != null),
            "frameMs" to frameMs,
            "calibrationResolution" to calibration?.resolution,
            "calibrationProvenance" to calibration?.provenance,
            "calibrationBarSource" to calibration?.barSource,
            "calibrationSignatureKey" to calibration?.signatureKey,
            "calibrationSchemaRevision" to calibration?.schemaRevision,
            "calibrationReasonCodes" to calibration?.reasonCodes,
            "calibrationLookupMs" to calibration?.lookupMs,
            "calibrationValidationMs" to calibration?.validationMs,
            "speciesAuthority" to speciesEvidence?.authority?.name,
            "speciesProfileStatus" to speciesEvidence?.profileStatus?.name,
            "speciesSelected" to speciesEvidence?.selectedCanonicalSpecies,
            "recognizedSpecies" to recognizedSpecies(results, diagnostic)
        )
    }

    private fun recognizedSpecies(
        results: List<ScanFrameCandidate>,
        diagnostic: FrameDiagnostic?
    ): String? = results.lastOrNull()?.data?.realName
        ?: results.lastOrNull()?.data?.name
        ?: diagnostic?.selected?.realName
        ?: diagnostic?.selected?.name

    /** Clears ONLY the dedicated calibration preferences file — no unrelated app data. */
    private fun clearOnlyCalibrationPreferences(context: Context) {
        val prefs = context.getSharedPreferences("screen_calibration_geometry", Context.MODE_PRIVATE)
        val before = prefs.all.keys.toString()
        prefs.edit().clear().commit()
        Log.i(TAG, "Cleared calibration preferences (keys were: $before)")
    }

    private object ScanManager900Policy {
        const val MAX_WIDTH = 900
    }

    private companion object {
        const val TAG = "Phase2bCalibrationReplay"
    }
}
