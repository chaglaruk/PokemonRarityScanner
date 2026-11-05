package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.service.reconcileSpeciesProfileEvidence
import com.pokerarity.scanner.util.ocr.ActionResourceMatchView
import com.pokerarity.scanner.util.ocr.ActionResourceMatcher
import com.pokerarity.scanner.util.ocr.FrameDiagnostic
import com.pokerarity.scanner.util.ocr.MLKitOcrProvider
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesRefiner
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Replays a locally supplied real game frame through ScanManager's production route. */
@RunWith(AndroidJUnit4::class)
class LiveNativeResolutionRetryTest {

    @Test
    fun unresolvedLiveIdentityUsesNativeDetailedPassAndRecoversProfileEvidence() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(requireNotNull(context.getExternalFilesDir(null)), INPUT_FILE_NAME)
        assumeTrue("Private real-game frame was not supplied", input.isFile && input.length() in 1..MAX_FRAME_BYTES)

        val source = BitmapFactory.decodeFile(input.absolutePath, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: error("Private real-game frame could not be decoded")
        val fastBitmap = Bitmap.createScaledBitmap(
            source,
            PRODUCTION_WIDTH,
            (source.height * (PRODUCTION_WIDTH.toFloat() / source.width)).toInt(),
            true
        )
        val resourceBitmap = Bitmap.createBitmap(fastBitmap)
        val provider = MLKitOcrProvider(context)
        val manager = ScanManager(context)
        val calculator = RarityCalculator(context)
        val fastFrames = mutableListOf<ScanFrameCandidate>()
        val diagnostics = mutableListOf<FrameDiagnostic>()
        val routes = mutableListOf<com.pokerarity.scanner.util.ocr.FrameRouteDiagnostic>()
        val report = JSONObject()
            .put("schemaVersion", 1)
            .put("captureType", "real_game_frame_offline_replay")
            .put("buildSourceSha", com.pokerarity.scanner.BuildConfig.QA_SOURCE_SHA)
            .put("sourceFrameSha256", sha256(input.readBytes()))
            .put("sourceWidth", source.width)
            .put("sourceHeight", source.height)
            .put("productionFastWidth", fastBitmap.width)
            .put("productionFastHeight", fastBitmap.height)

        try {
            val fastQuality = estimateReplayCpQuality(fastBitmap)
            val fastLayout = provider.recognizeLayout(resourceBitmap)
            report.put("resourceMatcher900", resourceTraceJson(
                ActionResourceMatcher.diagnose(resourceBitmap, fastLayout)
            ))

            manager.processRoutedFrame(
                ScanManager.DecodedFrame(
                    index = 0,
                    path = OPAQUE_FRAME_REFERENCE,
                    bitmap = fastBitmap,
                    cpQuality = fastQuality,
                    pooled = false,
                    sourceWidth = source.width,
                    sourceHeight = source.height
                ),
                fastFrames,
                diagnostics,
                routes
            )
            report.put("route", routes.firstOrNull()?.let { route ->
                JSONObject()
                    .put("action", route.action.name)
                    .put("screenType", route.screenType)
                    .put("confidence", route.confidence.toDouble())
                    .put("reason", route.reason)
            } ?: JSONObject.NULL)

            val fast = fastFrames.singleOrNull()
            report.put("fastFrame", fast?.let { frame ->
                JSONObject()
                    .put("species", frame.speciesEvidence.selectedCanonicalSpecies ?: JSONObject.NULL)
                    .put("speciesAuthority", evidenceJson(frame.speciesEvidence))
                    .put("cp", frame.data.cp ?: JSONObject.NULL)
                    .put("hp", frame.data.hp ?: JSONObject.NULL)
                    .put("maxHp", frame.data.maxHp ?: JSONObject.NULL)
                    .put("candySpecies", frame.data.candyName ?: JSONObject.NULL)
                    .put("evolveCost", structuredFieldJson(diagnostics.firstOrNull(), "EvolveCost"))
                    .put("nameCandidates", candidateReasonsJson(diagnostics.firstOrNull()))
            } ?: JSONObject.NULL)

            val shouldRunDetailed = fast?.let {
                ScanManager.shouldRunDetailedPassForAuthoritative(it.data, fastQuality, it.speciesEvidence)
            } ?: false
            val preferNative = fast?.let {
                ScanFrameFusion.shouldAttemptNativeResolutionForUnresolvedIdentity(it.speciesEvidence) &&
                    ScanFrameFusion.canUseNativeResolution(source.width, source.height)
            } ?: false
            report.put("detailedPassRequested", shouldRunDetailed)
                .put("nativeResolutionRetryRequested", preferNative)

            val detailed = if (fast != null && shouldRunDetailed) {
                manager.runDetailedPassIfNeeded(
                    input.absolutePath,
                    fast.recognitionContext,
                    preferNativeResolution = preferNative
                )
            } else null
            report.put("detailedFrame", detailed?.let { frame ->
                JSONObject()
                    .put("width", frame.diagnostic.imageWidth)
                    .put("height", frame.diagnostic.imageHeight)
                    .put("species", frame.pokemon.realName ?: frame.pokemon.name ?: JSONObject.NULL)
                    .put("cp", frame.pokemon.cp ?: JSONObject.NULL)
                    .put("hp", frame.pokemon.hp ?: JSONObject.NULL)
                    .put("maxHp", frame.pokemon.maxHp ?: JSONObject.NULL)
                    .put("candySpecies", frame.pokemon.candyName ?: JSONObject.NULL)
                    .put("speciesAuthority", evidenceJson(ScanManager.deriveSpeciesEvidence(
                        frame.diagnostic.fieldCandidates, frame.pokemon, calculator
                    )))
                    .put("evolveCost", structuredFieldJson(frame.diagnostic, "EvolveCost"))
                    .put("nameCandidates", candidateReasonsJson(frame.diagnostic))
            } ?: JSONObject.NULL)

            val final = if (fast != null && detailed != null) {
                val detailedEvidence = ScanManager.deriveSpeciesEvidence(
                    detailed.diagnostic.fieldCandidates, detailed.pokemon, calculator
                )
                val detailedCandidate = ScanFrameCandidate(
                    path = fast.path,
                    data = detailed.pokemon,
                    cpQuality = fastQuality,
                    speciesEvidence = detailedEvidence,
                    recognitionContext = fast.recognitionContext,
                    fieldCandidates = detailed.diagnostic.fieldCandidates
                )
                val selection = ScanFrameFusion.resolveAnchoredFrames(
                    frames = listOf(fast),
                    authoritative = fast,
                    detailed = detailedCandidate
                ) { frame, excludeEvolutionCost, excludePowerUpCost ->
                    ScanManager.deriveSpeciesEvidenceWithoutActionCosts(
                        frame.fieldCandidates,
                        frame.data,
                        calculator,
                        excludeEvolutionCost,
                        excludePowerUpCost
                    )
                }
                val fused = selection?.frame?.data ?: fast.data
                var evidence = selection?.let {
                    ScanManager.finalAnchoredSpeciesEvidence(it, fused, calculator)
                } ?: fast.speciesEvidence
                val refined = SpeciesRefiner(context, calculator).refine(
                    fused,
                    fast.fieldCandidates + detailed.diagnostic.fieldCandidates
                )
                evidence = reconcileSpeciesProfileEvidence(
                    evidence,
                    ScanManager.profileStatus(refined, evidence.selectedCanonicalSpecies, calculator)
                )
                val decision = ScanConsistencyGate(context, calculator).evaluate(fused, refined, evidence)
                JSONObject()
                    .put("species", evidence.selectedCanonicalSpecies ?: JSONObject.NULL)
                    .put("authority", evidenceJson(evidence))
                    .put("consistencyReason", decision.reason)
                    .put("shouldRetry", decision.shouldRetry)
                    .put("publishedSpecies", decision.pokemon.realName ?: decision.pokemon.name ?: JSONObject.NULL)
            } else JSONObject.NULL
            report.put("finalDecision", final)

            val reportText = report.toString(2)
            assertTrue("Bounded diagnostic report exceeded 16 KiB",
                reportText.toByteArray(Charsets.UTF_8).size <= MAX_REPORT_BYTES)
            val output = File(requireNotNull(context.getExternalFilesDir(null)), OUTPUT_FILE_NAME)
            output.parentFile?.mkdirs()
            output.writeText(reportText)

            assertEquals("detail route should yield one frame", 1, fastFrames.size)
            assertTrue("unresolved fast evidence must request a detailed pass", shouldRunDetailed)
            assertTrue("selected but unresolved species must request native resolution", preferNative)
            assertNotNull("native detailed pass must return OCR output", detailed)
            assertEquals("native pass should use the full source width", source.width, detailed!!.diagnostic.imageWidth)
            assertEquals("READ", structuredFieldStatus(detailed.diagnostic, "EvolveCost"))
            assertEquals("25", structuredFieldValue(detailed.diagnostic, "EvolveCost"))
            val finalDecision = final as JSONObject
            assertEquals("Nacli", finalDecision.optString("species"))
            assertEquals("Nacli", finalDecision.optString("publishedSpecies"))
            assertFalse("resolved species should leave the consistency retry gate",
                finalDecision.getBoolean("shouldRetry"))
        } finally {
            provider.close()
            manager.stop()
            if (!source.isRecycled) source.recycle()
            if (!resourceBitmap.isRecycled) resourceBitmap.recycle()
            if (!fastBitmap.isRecycled) fastBitmap.recycle()
        }
    }

    private fun evidenceJson(evidence: SpeciesEvidence) = JSONObject()
        .put("selectedSpecies", evidence.selectedCanonicalSpecies ?: JSONObject.NULL)
        .put("authority", evidence.authority.name)
        .put("profileStatus", evidence.profileStatus.name)
        .put("observationsAgree", evidence.observationsAgree)
        .put("authorityConflict", evidence.authorityConflict)
        .put("candidatesClose", evidence.candidatesClose)
        .put("reasonCodes", JSONArray(evidence.reasonCodes.take(MAX_REASON_CODES)))

    private fun candidateReasonsJson(diagnostic: FrameDiagnostic?) = JSONArray().apply {
        diagnostic?.fieldCandidates
            ?.filter { it.field in NAME_FIELDS }
            ?.take(MAX_CANDIDATES)
            ?.forEach { candidate ->
                put(JSONObject()
                    .put("field", candidate.field)
                    .put("status", candidate.status)
                    .put("reason", candidate.reason ?: JSONObject.NULL)
                    .put("winner", candidate.winner)
                    .put("canonicalValuePresent", !candidate.selectedValue.isNullOrBlank()))
            }
    }

    private fun structuredFieldJson(diagnostic: FrameDiagnostic?, name: String): JSONObject =
        JSONObject()
            .put("status", structuredFieldStatus(diagnostic, name) ?: JSONObject.NULL)
            .put("value", structuredFieldValue(diagnostic, name) ?: JSONObject.NULL)
            .put("reasonCode", diagnostic?.structuredFields?.firstOrNull { it.field == name }
                ?.reasonCode ?: JSONObject.NULL)

    private fun structuredFieldStatus(diagnostic: FrameDiagnostic?, name: String): String? =
        diagnostic?.structuredFields?.firstOrNull { it.field == name }?.status

    private fun structuredFieldValue(diagnostic: FrameDiagnostic?, name: String): String? =
        diagnostic?.structuredFields?.firstOrNull { it.field == name }?.value

    private fun resourceTraceJson(trace: com.pokerarity.scanner.util.ocr.ActionResourceTrace) =
        JSONObject()
            .put("documentStatus", trace.status.name)
            .put("numericCandidateCount", trace.numericCandidateCount)
            .put("referenceCount", trace.referenceCount)
            .put("candidates", JSONArray().apply {
                trace.candidates.take(MAX_RESOURCE_CANDIDATES).forEach { candidate ->
                    put(JSONObject()
                        .put("direct", scoreJson(candidate.direct))
                        .put("intrinsic", scoreJson(candidate.intrinsic))
                        .put("selectedKind", candidate.selectedKind.name)
                        .put("selectedRole", candidate.selectedRole.name)
                        .put("amountClear", candidate.amountClear)
                        .put("reasonCode", candidate.reasonCode))
                }
            })

    private fun scoreJson(score: ActionResourceMatchView) = JSONObject()
        .put("bestKind", score.bestKind?.name ?: JSONObject.NULL)
        .put("bestScore", score.bestScore ?: JSONObject.NULL)
        .put("runnerUpKind", score.runnerUpKind?.name ?: JSONObject.NULL)
        .put("runnerUpScore", score.runnerUpScore ?: JSONObject.NULL)
        .put("margin", score.margin ?: JSONObject.NULL)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02X".format(it) }

    companion object {
        private const val INPUT_FILE_NAME = "live-nacli-cp545-frame.png"
        private const val OUTPUT_FILE_NAME = "qa-diagnostics/live-native-resolution-retry.json"
        private const val OPAQUE_FRAME_REFERENCE = "private_real_game_frame"
        private const val PRODUCTION_WIDTH = 900
        private const val MAX_FRAME_BYTES = 12L * 1024 * 1024
        private const val MAX_REPORT_BYTES = 16 * 1024
        private const val MAX_CANDIDATES = 8
        private const val MAX_REASON_CODES = 8
        private const val MAX_RESOURCE_CANDIDATES = 8
        private val NAME_FIELDS = setOf("Name", "NameTextual", "NameDynamic", "NameHC")
    }
}
