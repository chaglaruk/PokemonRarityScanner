package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.FrameOcrRequest
import com.pokerarity.scanner.util.ocr.OCRProcessor
import com.pokerarity.scanner.util.ocr.SpeciesRefiner
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import com.pokerarity.scanner.util.vision.Phase2VariantFeatureMerger
import com.pokerarity.scanner.util.vision.QaBuildIdentity
import com.pokerarity.scanner.util.vision.QaSelectedFrameReference
import com.pokerarity.scanner.util.vision.QaVariantDiagnosticWriter
import com.pokerarity.scanner.util.vision.VariantDecisionEngine
import com.pokerarity.scanner.util.vision.VariantMergeLogic
import com.pokerarity.scanner.util.vision.VariantStageDiagnosticRecorder
import com.pokerarity.scanner.util.vision.VisualFeatureDetector
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class QaRaikouShinyStageTraceInstrumentedTest {

    @Test
    fun writesBoundedLocalStageTraceForOrdinaryRaikouShinyFalsePositive() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixtureContext = instrumentation.context
        val appContext = instrumentation.targetContext
        val fixtureBytes = fixtureContext.assets.open(RAIKOU_ASSET).use { it.readBytes() }
        val bitmap = BitmapFactory.decodeByteArray(
            fixtureBytes,
            0,
            fixtureBytes.size,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        ) ?: error("Selected offline Raikou fixture could not be decoded")
        val fixtureSha256 = sha256(fixtureBytes)
        val frameRef = "sha256:${fixtureSha256.take(FRAME_REF_HEX_LENGTH)}"
        val recorder = VariantStageDiagnosticRecorder()
        val rarityCalculator = RarityCalculator(appContext)
        val speciesRefiner = SpeciesRefiner(appContext, rarityCalculator)
        val variantDecisionEngine = VariantDecisionEngine(appContext)
        val visualDetector = VisualFeatureDetector(appContext)
        val ocrProcessor = OCRProcessor(appContext)
        ocrProcessor.ensureInitialized()

        try {
            val ocrFrame = ocrProcessor.processImageWithDiagnostics(
                FrameOcrRequest(bitmap = bitmap, includeSecondaryFields = true)
            )
            val refined = speciesRefiner.refine(ocrFrame.pokemon, ocrFrame.diagnostic.fieldCandidates)
            val lockedSpecies = refined.realName ?: refined.name
            recorder.recordSpeciesAuthority(lockedSpecies)
            recorder.recordSelectedFrameReference(
                QaSelectedFrameReference(ref = frameRef, algorithm = FRAME_REF_ALGORITHM, status = "available")
            )

            val classified = variantDecisionEngine.classify(bitmap, refined, recorder)
            val classifiedPokemon = classified.pokemon
            val visual = visualDetector.detect(
                bitmap = bitmap,
                pokemonName = classifiedPokemon.name,
                sizeTag = null,
                diagnostics = recorder,
                lockedSpecies = classifiedPokemon.realName ?: classifiedPokemon.name
            )
            val merged = VariantMergeLogic.mergeVisualFeatures(
                visualFeatures = visual,
                fullMatch = classified.fullMatch,
                fallbackMatch = classified.resolvedMatch ?: classified.globalMatch,
                onDecision = recorder::recordVariantMerge
            )
            val phase2Result = runCatching {
                Phase2VariantClassifier(appContext).classify(
                    bitmap,
                    classifiedPokemon.realName ?: classifiedPokemon.name
                )
            }.getOrNull()
            recorder.recordPhase2Status(if (phase2Result == null) "classification_unavailable" else "classification_ready")
            val finalVisual = Phase2VariantFeatureMerger.merge(
                features = merged,
                result = phase2Result,
                onDecision = recorder::recordPhase2Merge
            )

            val build = QaBuildIdentity(
                applicationId = BuildConfig.APPLICATION_ID,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                sourceSha = BuildConfig.QA_SOURCE_SHA
            )
            val stageReport = recorder.snapshot(build, QaVariantDiagnosticWriter.metadataRevision(appContext))
            assertTrue("Shiny decision stage was not captured", stageReport.visualDetector.shiny != null)
            assertTrue("Shiny signature evidence was not captured", stageReport.shinySignature?.referenceStatus == "available")

            val output = JSONObject()
                .put("schemaVersion", 1)
                .put("caseToken", opaqueToken(RAIKOU_CASE_ID))
                .put("captureType", "offline_fixture_replay")
                .put("fixtureSha256", fixtureSha256)
                .put("selectedFrameRef", frameRef)
                .put("buildSourceSha", BuildConfig.QA_SOURCE_SHA)
                .put("groundTruth", JSONObject().put("species", "Raikou").put("shiny", false))
                .put("detectorShiny", visual.isShiny)
                .put("finalShiny", finalVisual.isShiny)
                .put("stageReport", JSONObject(QaVariantDiagnosticWriter.serialize(stageReport)))
            val reportText = output.toString(2)
            assertTrue("Raikou trace exceeded the local report size cap", reportText.toByteArray(Charsets.UTF_8).size <= MAX_REPORT_BYTES)
            assertFalse("Raikou trace must not include OCR text", reportText.contains("rawOcr", ignoreCase = true))
            assertFalse("Raikou trace must not include a local path", reportText.contains("C:\\"))

            val outputFile = File(requireNotNull(appContext.getExternalFilesDir(null)), OUTPUT_FILE)
            assertFalse("Refusing to replace prior QA evidence", outputFile.exists())
            outputFile.writeText(reportText)
            assertTrue(outputFile.exists() && outputFile.length() <= MAX_REPORT_BYTES)
        } finally {
            ocrProcessor.release()
            bitmap.recycle()
        }
    }

    private fun opaqueToken(value: String): String = sha256(value.toByteArray(Charsets.UTF_8)).take(16)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val RAIKOU_CASE_ID = "regression_20260317_1059/scan_1773745195014_0"
        const val RAIKOU_ASSET = "scan_fixtures/regression_20260317_1059/scan_1773745195014_0.png"
        const val FRAME_REF_HEX_LENGTH = 24
        const val FRAME_REF_ALGORITHM = "sha256-prefix-96bit"
        const val MAX_REPORT_BYTES = 32 * 1024
        const val OUTPUT_FILE = "raikou_shiny_stage_trace.json"
    }
}
