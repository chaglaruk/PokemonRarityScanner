package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.BuildConfig
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Local exact-frame OCR comparison. The private source frame is supplied on-device and never committed. */
@RunWith(AndroidJUnit4::class)
class LiveActionResourceReplayTest {

    @Test
    fun liveNacliFrameComparesNativeAndProduction900WidthRecognition() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(requireNotNull(context.getExternalFilesDir(null)), INPUT_FILE_NAME)
        assumeTrue("Private live frame was not supplied", input.isFile && input.length() in 1..MAX_FRAME_BYTES)

        val native = BitmapFactory.decodeFile(input.absolutePath, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: error("Private live frame could not be decoded")
        val scaled = Bitmap.createScaledBitmap(native, PRODUCTION_RECOGNITION_WIDTH,
            (native.height * (PRODUCTION_RECOGNITION_WIDTH.toFloat() / native.width)).toInt(), true)
        val processor = OCRProcessor(context)
        val provider = MLKitOcrProvider(context)
        try {
            val nativeAnalysis = analyze(context, processor, provider, native, "native_1080")
            val scaledAnalysis = analyze(context, processor, provider, scaled, "baseline_900")
            val sourceBytes = input.readBytes()
            val report = JSONObject()
                .put("schemaVersion", 2)
                .put("captureType", "real_game_frame_offline_replay")
                .put("buildSourceSha", BuildConfig.QA_SOURCE_SHA)
                .put("frameSha256", sha256(sourceBytes))
                .put("frameWidth", native.width)
                .put("frameHeight", native.height)
                .put("groundTruth", JSONObject().put("species", "Nacli").put("cp", 545).put("hp", 95))
                .put("native1080", nativeAnalysis.report)
                .put("baseline900", scaledAnalysis.report)
            val reportText = report.toString(2)
            assertTrue("Bounded report exceeded 24 KiB",
                reportText.toByteArray(Charsets.UTF_8).size <= MAX_REPORT_BYTES)
            val output = File(context.filesDir, OUTPUT_FILE_NAME)
            output.parentFile?.mkdirs()
            output.writeText(reportText)

            assertEquals("Nacli", nativeAnalysis.fields.name?.acceptedSpeciesOrNull())
            assertEquals("Nacli", scaledAnalysis.fields.name?.acceptedSpeciesOrNull())
            assertEquals("native1080 should read the visible EVOLVE cost", 25,
                nativeAnalysis.fields.evolveRead.value)
            assertEquals("baseline_900 should recover the visible CP 545", 545,
                scaledAnalysis.pipeline.pokemon.cp ?: -1)
        } finally {
            provider.close()
            processor.release()
            if (scaled !== native && !scaled.isRecycled) scaled.recycle()
            if (!native.isRecycled) native.recycle()
        }
    }

    private suspend fun analyze(
        context: android.content.Context,
        processor: OCRProcessor,
        provider: MLKitOcrProvider,
        bitmap: Bitmap,
        mode: String
    ): Analysis {
        val pipelineStart = SystemClock.elapsedRealtime()
        val pipeline = processor.processImageWithDiagnostics(
            FrameOcrRequest(bitmap = bitmap, includeSecondaryFields = true, frameRole = "live_frame_$mode")
        )
        val pipelineMs = SystemClock.elapsedRealtime() - pipelineStart

        val layoutStart = SystemClock.elapsedRealtime()
        val layout = provider.recognizeLayout(bitmap)
        val layoutMs = SystemClock.elapsedRealtime() - layoutStart
        val bar = HealthBarLocator.locate(bitmap)
        val witnesses = ActionResourceMatcher.observe(bitmap, layout)
        val nameBand = bar?.let { ScreenGeometryBuilder.deriveNameBand(it, bitmap.width, bitmap.height) }
        val fields = AnchoredScreenText.extract(
            layout,
            TextParser(context),
            bitmap.width,
            bitmap.height,
            ExtractionContext(bar = bar, nameBand = nameBand, actionResources = witnesses)
        )
        val trace = ActionResourceMatcher.diagnose(bitmap, layout)
        val report = JSONObject()
            .put("recognitionWidth", bitmap.width)
            .put("recognitionHeight", bitmap.height)
            .put("pipelineMs", pipelineMs)
            .put("layoutMs", layoutMs)
            .put("pipeline", JSONObject()
                .put("species", pipeline.pokemon.realName ?: JSONObject.NULL)
                .put("cp", pipeline.pokemon.cp ?: JSONObject.NULL)
                .put("hp", pipeline.pokemon.hp ?: JSONObject.NULL)
                .put("evolveCost", pipeline.diagnostic.structuredFields
                    .firstOrNull { it.field == "EvolveCost" }?.let(::structuredFieldJson) ?: JSONObject.NULL))
            .put("directExtraction", JSONObject()
                .put("species", fields.name?.acceptedSpeciesOrNull() ?: JSONObject.NULL)
                .put("cp", fields.cp ?: JSONObject.NULL)
                .put("hp", fields.hp?.first ?: JSONObject.NULL)
                .put("evolveCost", fieldJson(fields.evolveRead.status.name, fields.evolveRead.value,
                    fields.evolveRead.candidateCount, fields.evolveRead.reasonCode)))
            .put("cpAndNameCandidates", JSONArray().apply {
                pipeline.diagnostic.fieldCandidates
                    .filter { it.field in DIAGNOSTIC_FIELDS }
                    .take(MAX_FIELD_CANDIDATES)
                    .forEach { put(candidateJson(it)) }
            })
            .put("cropGeometry", JSONArray().apply {
                pipeline.diagnostic.crops
                    .filter { it.field.contains("cp", ignoreCase = true) ||
                        it.field.contains("name", ignoreCase = true) }
                    .take(MAX_CROPS)
                    .forEach { put(cropJson(it)) }
            })
            .put("resourceMatcher", JSONObject()
                .put("status", trace.status.name)
                .put("numericCandidateCount", trace.numericCandidateCount)
                .put("referenceCount", trace.referenceCount)
                .put("candidateSummaries", JSONArray().apply {
                    trace.candidates
                        .filter { it.inventoryReferenceKind != null || it.selectedRole == ActionResourceRole.COST ||
                            it.direct.bestScore != null || it.intrinsic.bestScore != null }
                        .take(MAX_RESOURCE_CANDIDATES)
                        .forEach { candidate ->
                            put(JSONObject()
                                .put("bounds", rectJson(candidate.bounds.left, candidate.bounds.top,
                                    candidate.bounds.right, candidate.bounds.bottom))
                                .put("inventoryReferenceKind", candidate.inventoryReferenceKind?.name ?: JSONObject.NULL)
                                .put("direct", viewJson(candidate.direct))
                                .put("intrinsic", viewJson(candidate.intrinsic))
                                .put("selectedKind", candidate.selectedKind?.name ?: ActionResourceKind.UNKNOWN.name)
                                .put("selectedRole", candidate.selectedRole.name)
                                .put("amountClear", candidate.amountClear)
                                .put("reasonCode", candidate.reasonCode))
                        }
                }))
        return Analysis(pipeline, fields, report)
    }

    private fun structuredFieldJson(field: FieldReadDiagnostic) = JSONObject()
        .put("status", field.status)
        .put("value", field.value ?: JSONObject.NULL)
        .put("candidateCount", field.candidateCount)
        .put("reasonCode", field.reasonCode)

    private fun fieldJson(status: String, value: Any?, count: Int, reason: String) = JSONObject()
        .put("status", status)
        .put("value", value ?: JSONObject.NULL)
        .put("candidateCount", count)
        .put("reasonCode", reason)

    private fun candidateJson(candidate: FieldCandidateDiagnostic) = JSONObject()
        .put("field", candidate.field)
        .put("source", candidate.source)
        .put("parsedValue", candidate.parsedValue ?: JSONObject.NULL)
        .put("status", candidate.status)
        .put("cropName", candidate.cropName ?: JSONObject.NULL)
        .put("cropBounds", if (candidate.cropLeft != null && candidate.cropTop != null &&
            candidate.cropRight != null && candidate.cropBottom != null) {
            rectJson(candidate.cropLeft, candidate.cropTop, candidate.cropRight, candidate.cropBottom)
        } else JSONObject.NULL)
        .put("cropProvenance", candidate.cropProvenance ?: JSONObject.NULL)
        .put("cropConfidence", candidate.cropConfidence ?: JSONObject.NULL)
        .put("preprocessing", candidate.preprocessing ?: JSONObject.NULL)
        .put("parserResult", candidate.parserResult ?: JSONObject.NULL)
        .put("candidateScore", candidate.candidateScore ?: JSONObject.NULL)
        .put("winner", candidate.winner)
        .put("reason", candidate.reason ?: JSONObject.NULL)
        .put("selectedValue", candidate.selectedValue ?: JSONObject.NULL)

    private fun cropJson(crop: CropDiagnostic) = JSONObject()
        .put("field", crop.field)
        .put("source", crop.source)
        .put("bounds", if (crop.left != null && crop.top != null && crop.right != null && crop.bottom != null) {
            rectJson(crop.left, crop.top, crop.right, crop.bottom)
        } else JSONObject.NULL)
        .put("status", crop.status)
        .put("provenance", crop.provenance ?: JSONObject.NULL)
        .put("confidence", crop.confidence ?: JSONObject.NULL)
        .put("reasons", JSONArray(crop.reasons.take(MAX_CROP_REASONS)))

    private fun rectJson(left: Int, top: Int, right: Int, bottom: Int) = JSONArray(listOf(left, top, right, bottom))

    private fun viewJson(view: ActionResourceMatchView) = JSONObject()
        .put("bestKind", view.bestKind?.name ?: JSONObject.NULL)
        .put("bestScore", view.bestScore ?: JSONObject.NULL)
        .put("runnerUpKind", view.runnerUpKind?.name ?: JSONObject.NULL)
        .put("runnerUpScore", view.runnerUpScore ?: JSONObject.NULL)
        .put("margin", view.margin ?: JSONObject.NULL)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02X".format(it) }

    private data class Analysis(
        val pipeline: OcrFrameResult,
        val fields: AnchoredScreenText.Fields,
        val report: JSONObject
    )

    companion object {
        private const val INPUT_FILE_NAME = "live-nacli-cp545-frame.png"
        private const val OUTPUT_FILE_NAME = "qa-diagnostics/live-nacli-resolution-comparison.json"
        private const val MAX_FRAME_BYTES = 12L * 1024 * 1024
        private const val MAX_REPORT_BYTES = 24 * 1024
        private const val PRODUCTION_RECOGNITION_WIDTH = 900
        private const val MAX_FIELD_CANDIDATES = 12
        private const val MAX_CROPS = 16
        private const val MAX_CROP_REASONS = 8
        private const val MAX_RESOURCE_CANDIDATES = 8
        private val DIAGNOSTIC_FIELDS = setOf("Name", "NameTextual", "CP")
    }
}
