package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
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

/** Local exact-frame replay. The private source frame is supplied on-device and is never committed. */
@RunWith(AndroidJUnit4::class)
class LiveActionResourceReplayTest {

    @Test
    fun liveNacliFrameHasReadableEvolutionCandyEvidence() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val input = File(requireNotNull(context.getExternalFilesDir(null)), INPUT_FILE_NAME)
        assumeTrue("Private live frame was not supplied", input.isFile && input.length() in 1..MAX_FRAME_BYTES)

        val bitmap = BitmapFactory.decodeFile(input.absolutePath, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: error("Private live frame could not be decoded")
        val processor = OCRProcessor(context)
        val provider = MLKitOcrProvider(context)
        try {
            val pipeline = processor.processImageWithDiagnostics(
                FrameOcrRequest(bitmap = bitmap, includeSecondaryFields = true, frameRole = "live_frame_replay")
            )
            val layout = provider.recognizeLayout(bitmap)
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
            val pipelineEvolve = pipeline.diagnostic.structuredFields.firstOrNull { it.field == "EvolveCost" }
            val frameBytes = input.readBytes()
            val report = JSONObject()
                .put("schemaVersion", 1)
                .put("captureType", "real_game_frame_offline_replay")
                .put("buildSourceSha", BuildConfig.QA_SOURCE_SHA)
                .put("frameSha256", sha256(frameBytes))
                .put("frameWidth", bitmap.width)
                .put("frameHeight", bitmap.height)
                .put("pipeline", JSONObject()
                    .put("species", pipeline.pokemon.realName ?: JSONObject.NULL)
                    .put("cp", pipeline.pokemon.cp ?: JSONObject.NULL)
                    .put("hp", pipeline.pokemon.hp ?: JSONObject.NULL)
                    .put("evolveCost", pipelineEvolve?.let(::fieldJson) ?: JSONObject.NULL))
                .put("directExtraction", JSONObject()
                    .put("species", fields.name?.acceptedSpeciesOrNull() ?: JSONObject.NULL)
                    .put("cp", fields.cp ?: JSONObject.NULL)
                    .put("hp", fields.hp?.first ?: JSONObject.NULL)
                    .put("evolveCost", fieldJson(fields.evolveRead.status.name, fields.evolveRead.value,
                        fields.evolveRead.candidateCount, fields.evolveRead.reasonCode)))
                .put("resourceMatcher", JSONObject()
                    .put("status", trace.status.name)
                    .put("numericCandidateCount", trace.numericCandidateCount)
                    .put("referenceCount", trace.referenceCount)
                    .put("candidateSummaries", JSONArray().apply {
                        trace.candidates.forEach { candidate ->
                            put(JSONObject()
                                .put("bounds", JSONArray(listOf(candidate.bounds.left, candidate.bounds.top,
                                    candidate.bounds.right, candidate.bounds.bottom)))
                                .put("inventoryReferenceKind", candidate.inventoryReferenceKind?.name ?: JSONObject.NULL)
                                .put("direct", viewJson(candidate.direct))
                                .put("intrinsic", viewJson(candidate.intrinsic))
                                .put("selectedKind", candidate.selectedKind?.name ?: ActionResourceKind.UNKNOWN.name)
                                .put("selectedRole", candidate.selectedRole.name)
                                .put("amountClear", candidate.amountClear)
                                .put("reasonCode", candidate.reasonCode))
                        }
                    }))
            val reportText = report.toString(2)
            assertTrue("Bounded report exceeded 12 KiB", reportText.toByteArray(Charsets.UTF_8).size <= MAX_REPORT_BYTES)
            val output = File(context.filesDir, OUTPUT_FILE_NAME)
            output.parentFile?.mkdirs()
            output.writeText(reportText)
            instrumentation.sendStatus(0, Bundle().apply { putString("diagnostic", reportText) })

            // This expectation intentionally exercises the currently failing real frame.
            assertEquals("READ", fields.evolveRead.status.name)
            assertEquals(25, fields.evolveRead.value)
            assertEquals("READ", pipelineEvolve?.status)
            assertEquals("Nacli", fields.name?.acceptedSpeciesOrNull())
            assertTrue("Resource trace exceeded its candidate cap", trace.candidates.size <= MAX_DIAGNOSTIC_CANDIDATES)
        } finally {
            provider.close()
            processor.release()
            bitmap.recycle()
        }
    }

    private fun fieldJson(status: String, value: Any?, count: Int, reason: String) = JSONObject()
        .put("status", status)
        .put("value", value ?: JSONObject.NULL)
        .put("candidateCount", count)
        .put("reasonCode", reason)

    private fun fieldJson(field: FieldReadDiagnostic) = fieldJson(field.status, field.value, field.candidateCount,
        field.reasonCode)

    private fun viewJson(view: ActionResourceMatchView) = JSONObject()
        .put("bestKind", view.bestKind?.name ?: JSONObject.NULL)
        .put("bestScore", view.bestScore ?: JSONObject.NULL)
        .put("runnerUpKind", view.runnerUpKind?.name ?: JSONObject.NULL)
        .put("runnerUpScore", view.runnerUpScore ?: JSONObject.NULL)
        .put("margin", view.margin ?: JSONObject.NULL)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02X".format(it) }

    companion object {
        private const val INPUT_FILE_NAME = "live-nacli-cp545-frame.png"
        private const val OUTPUT_FILE_NAME = "qa-diagnostics/live-nacli-action-resource.json"
        private const val MAX_FRAME_BYTES = 12L * 1024 * 1024
        private const val MAX_REPORT_BYTES = 12 * 1024
        private const val MAX_DIAGNOSTIC_CANDIDATES = 12
    }
}
