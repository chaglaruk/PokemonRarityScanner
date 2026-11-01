package com.pokerarity.scanner

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
class QaSlowpokeCostumeStageTraceInstrumentedTest {

    @Test
    fun writesBoundedLocalStageTraceForSavedSlowpokeCostumeFixture() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixtureContext = instrumentation.context
        val appContext = instrumentation.targetContext
        val fixtureId = SLOWPOKE_COSTUME_CASE_ID
        val assetPath = SLOWPOKE_COSTUME_ASSET
        val fixtureBytes = fixtureContext.assets.open(assetPath).use { it.readBytes() }
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(
            fixtureBytes,
            0,
            fixtureBytes.size,
            android.graphics.BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
        ) ?: error("Selected offline fixture could not be decoded")
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
            recorder.recordSpeciesAuthority(refined.realName ?: refined.name)
            recorder.recordSelectedFrameReference(
                QaSelectedFrameReference(
                    ref = frameRef,
                    algorithm = "sha256-prefix-96bit",
                    status = "available"
                )
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
            val metadata = QaVariantDiagnosticWriter.metadataRevision(appContext)
            val stageReport = recorder.snapshot(build, metadata)
            assertTrue("Visual detector stage was not captured", stageReport.visualDetector.spriteCropWidth > 0)
            assertTrue("Costume signature stage was not captured", stageReport.costumeSignatures.isNotEmpty())

            val stageJson = QaVariantDiagnosticWriter.serialize(stageReport)
            val output = JSONObject()
                .put("schemaVersion", 1)
                .put("caseToken", opaqueToken(fixtureId))
                .put("captureType", "offline_fixture_replay")
                .put("fixtureSha256", fixtureSha256)
                .put("selectedFrameRef", frameRef)
                .put("buildSourceSha", BuildConfig.QA_SOURCE_SHA)
                .put("finalVisual", JSONObject()
                    .put("shiny", finalVisual.isShiny)
                    .put("costume", finalVisual.hasCostume))
                .put("stageReport", JSONObject(stageJson))
            val reportText = output.toString(2)
            assertTrue("Stage trace exceeded the local report size cap", reportText.toByteArray(Charsets.UTF_8).size <= MAX_REPORT_BYTES)
            assertFalse("Stage trace must not include OCR text", reportText.contains("rawOcr", ignoreCase = true))
            assertFalse("Stage trace must not include a local path", reportText.contains("C:\\"))

            val outputFile = File(requireNotNull(appContext.getExternalFilesDir(null)), OUTPUT_FILE)
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
        const val SLOWPOKE_COSTUME_CASE_ID = "live_variant_batch_20260318_slowpoke_costume"
        const val SLOWPOKE_COSTUME_ASSET = "scan_fixtures/live_variant_batch_20260318/scan_1773797985802_0.png"
        const val FRAME_REF_HEX_LENGTH = 24
        const val MAX_REPORT_BYTES = 32 * 1024
        const val OUTPUT_FILE = "slowpoke_costume_stage_trace.json"
    }
}
