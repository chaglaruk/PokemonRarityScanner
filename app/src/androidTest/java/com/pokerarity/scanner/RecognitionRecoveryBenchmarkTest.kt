package com.pokerarity.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.service.reconcileSpeciesProfileEvidence
import com.pokerarity.scanner.util.ocr.FrameDiagnostic
import com.pokerarity.scanner.util.ocr.FrameOcrRequest
import com.pokerarity.scanner.util.ocr.OCRProcessor
import com.pokerarity.scanner.util.ocr.ScanConfidenceGate
import com.pokerarity.scanner.util.ocr.ScanConfidenceInput
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import com.pokerarity.scanner.util.ocr.SpeciesRefiner
import com.pokerarity.scanner.util.ocr.VariantVisualSummary
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import com.pokerarity.scanner.util.vision.Phase2VariantFeatureMerger
import com.pokerarity.scanner.util.vision.VariantDecisionEngine
import com.pokerarity.scanner.util.vision.VisualFeatureDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil

/**
 * Real on-device OCR, replaying one saved bitmap through the production decision components.
 * This is not MediaProjection, a temporal burst, or an independent final holdout evaluation.
 * Arguments: policies=baseline,native,adaptive; subset=pr06|legacy|limited_manual|all; report=<safe basename>.json.
 * Reports contain no screenshot, raw OCR, filename, location, or unrestricted diagnostic object.
 * Existing production debug logging is unchanged; unfiltered logcat is not sanitized evidence.
 */
@RunWith(AndroidJUnit4::class)
class RecognitionRecoveryBenchmarkTest {
    private val limitedManualLegacyAssets = setOf(
        "scan_fixtures/live_variant_batch_20260318/scan_1773797298959_0.png",
        "scan_fixtures/live_variant_batch_20260318/scan_1773797985802_0.png",
        "scan_fixtures/live_variant_batch_20260318/scan_1773798028504_0.png",
        "scan_fixtures/live_variant_batch_20260318/scan_1773798087894_0.png"
    )

    private data class Fixture(
        val id: String,
        val dataset: String,
        val asset: String,
        val expectedSpecies: String?,
        val expectedCp: Int?,
        val expectedHp: Int?,
        val correlationGroup: String,
        val expectedCpVisible: Boolean? = null,
        val expectedHpVisible: Boolean? = null,
        val hash: String? = null,
        val width: Int? = null,
        val height: Int? = null
    )

    @Test
    fun developmentFixtureManifestDeclaresVisibleCpAndHpIndependently() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val document = JSONObject(context.assets.open(
            "scan_fixtures/pr06_1080_development/fixture_manifest.json"
        ).bufferedReader().use { it.readText() })
        assertEquals("manual_visual_review_of_each_development_fixture", document.getString("fieldVisibilitySource"))
        val manifest = document.getJSONArray("fixtures")
        assertEquals(15, manifest.length())
        val cpVisible = mutableSetOf<String>()
        val hpVisible = mutableSetOf<String>()
        for (index in 0 until manifest.length()) {
            val fixture = manifest.getJSONObject(index)
            val visibility = fixture.getJSONObject("fieldVisibility")
            if (visibility.getBoolean("cp")) cpVisible += fixture.getString("id")
            if (visibility.getBoolean("hp")) hpVisible += fixture.getString("id")
        }

        assertEquals(
            setOf(
                "pr06_1080_set01_reference", "pr06_1080_set02_reference",
                "pr06_1080_set03_reference", "pr06_1080_set04_reference",
                "pr06_1080_set05_reference"
            ),
            cpVisible
        )
        val allFixtureIds = (0 until manifest.length())
            .map { manifest.getJSONObject(it).getString("id") }.toSet()
        assertEquals(
            allFixtureIds - setOf("pr06_1080_set02_scrolled", "pr06_1080_set03_scrolled"),
            hpVisible
        )
    }

    @Test
    fun summaryScoresOnlyFieldsMarkedVisibleForThatFixture() {
        val visibleButMissed = JSONObject()
            .put("dataset", "pr06").put("policy", "baseline")
            .put("expectedSpecies", "Pikipek").put("predictedSpecies", "Pikipek")
            .put("accepted", true).put("failureCategory", "accepted_correct")
            .put("expectedCpVisible", true).put("cpTruthAvailable", true).put("cpCorrect", false)
            .put("expectedHpVisible", true).put("hpTruthAvailable", true).put("hpCorrect", true)
        val correctlyAbsent = JSONObject()
            .put("dataset", "pr06").put("policy", "baseline")
            .put("expectedSpecies", "Pikipek").put("predictedSpecies", "Pikipek")
            .put("accepted", true).put("failureCategory", "accepted_correct")
            .put("expectedCpVisible", false).put("cpTruthAvailable", true).put("cpCorrect", JSONObject.NULL)
            .put("expectedHpVisible", false).put("hpTruthAvailable", true).put("hpCorrect", JSONObject.NULL)

        val summary = summarize(listOf(visibleButMissed, correctlyAbsent)).getJSONObject(0)

        assertEquals(1, summary.getInt("cpVisibleLabeled"))
        assertEquals(0, summary.getInt("cpVisibleCorrect"))
        assertEquals(0.0, summary.getDouble("cpVisibleAccuracy"), 0.0)
        assertEquals(1, summary.getInt("hpVisibleLabeled"))
        assertEquals(1, summary.getInt("hpVisibleCorrect"))
        assertEquals(1.0, summary.getDouble("hpVisibleAccuracy"), 0.0)
    }

    @Test
    fun summarySeparatesFastOcrCpFromCorrectedResultCp() {
        val correctedAfterRead = JSONObject()
            .put("dataset", "pr06").put("policy", "adaptive")
            .put("expectedSpecies", "Pikipek").put("predictedSpecies", "Pikipek")
            .put("accepted", true)
            .put("expectedCpVisible", true).put("cpTruthAvailable", true)
            .put("cpFastOcrCorrect", false)
            .put("cpPreCorrectionCorrect", false)
            .put("cpFinalResultCorrect", true)
            .put("cpCorrect", true)

        val summary = summarize(listOf(correctedAfterRead)).getJSONObject(0)

        assertEquals(1, summary.getInt("cpVisibleLabeled"))
        assertEquals(0, summary.getInt("cpVisibleFastOcrCorrect"))
        assertEquals(0.0, summary.getDouble("cpVisibleFastOcrAccuracy"), 0.0)
        assertEquals(0, summary.getInt("cpVisiblePreCorrectionCorrect"))
        assertEquals(0.0, summary.getDouble("cpVisiblePreCorrectionAccuracy"), 0.0)
        assertEquals(1, summary.getInt("cpVisibleFinalResultCorrect"))
        assertEquals(1.0, summary.getDouble("cpVisibleFinalResultAccuracy"), 0.0)
    }

    @Test
    fun summaryCountsVisibleFieldsFromProcessingFailuresAsIncorrect() {
        val failedVisible = identity(
            Fixture("failed_visible", "pr06", "", "Pikipek", 236, 51, "set01", true, true),
            "baseline",
            "fixture-hash"
        ).put("error", "IllegalStateException")
            .put("failureCategory", "processing_error")

        val summary = summarize(listOf(failedVisible)).getJSONObject(0)

        assertEquals(1, summary.getInt("cpVisibleLabeled"))
        assertEquals(0, summary.getInt("cpVisibleCorrect"))
        assertEquals(1, summary.getInt("hpVisibleLabeled"))
        assertEquals(0, summary.getInt("hpVisibleCorrect"))
    }

    @Test
    fun visibleCurrentHpMissIsClassifiedEvenWhenMaxHpIsPresent() {
        val row = JSONObject()
            .put("expectedCpVisible", false)
            .put("expectedHpVisible", true)
            .put("cpPresent", true)
            .put("hpPresent", true)
            .put("currentHpPresent", false)
            .put("authority", "EXACT")
            .put("profileStatus", "SUPPORTED")
            .put("decision", "REVIEW")

        assertEquals("primary_numeric_fields_missing", failureCategory(row))
    }

    @Test
    fun limitedManualSubsetSelectsOnlyItsFourLegacyAssetsAndLabelsThemSeparately() {
        val assets = limitedManualLegacyAssets + "scan_fixtures/unlabeled.png" + "scan_fixtures/pr06.png"
        val fixtures = assets.mapIndexed { index, asset ->
            Fixture("legacy_%03d".format(index + 1), "legacy", asset, "Slowpoke", null, null,
                "legacy_grouping_unverified")
        }
        val withPr06 = fixtures.last().copy(id = "pr06_001", dataset = "pr06")
        val input = fixtures.dropLast(1) + withPr06

        val legacy = selectFixtures(input, "legacy")
        val selected = selectFixtures(input, "limited_manual")
        val pr06 = selectFixtures(input, "pr06")
        val all = selectFixtures(input, "all")

        assertEquals(5, legacy.size)
        assertEquals(1, legacy.count { it.dataset == "legacy" })
        assertEquals(4, legacy.count { it.dataset == "legacy_limited_manual" })
        assertEquals(4, selected.size)
        assertEquals(limitedManualLegacyAssets, selected.map { it.asset }.toSet())
        assertTrue(selected.all { it.dataset == "legacy_limited_manual" })
        assertEquals(listOf(withPr06), pr06)
        assertEquals(input.map { it.id }.toSet(), all.map { it.id }.toSet())
        assertEquals(input.size, all.size)
        assertEquals(1, all.count { it.dataset == "pr06" })
        assertEquals(1, all.count { it.dataset == "legacy" })
        assertEquals(4, all.count { it.dataset == "legacy_limited_manual" })
    }

    @Test
    fun measureBitmapReplay(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val app = instrumentation.targetContext
        val fixtureContext = instrumentation.context
        val policies = arguments.getString("policies", "baseline,native").split(',')
        require(policies.isNotEmpty() && policies.distinct().size == policies.size &&
            policies.all { it in setOf("baseline", "native", "adaptive") })
        val subset = arguments.getString("subset", "pr06")
        require(subset in setOf("pr06", "legacy", "limited_manual", "all"))
        val reportName = arguments.getString("report", "recognition_recovery.json")
        require(reportName.matches(Regex("[A-Za-z0-9_-]{1,80}\\.json")))
        val sourceRevision = arguments.getString("sourceRevision", "unspecified")
        require(sourceRevision.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        val runId = arguments.getString("runId")?.takeIf { it.matches(Regex("[A-Fa-f0-9-]{36}")) }
            ?: UUID.randomUUID().toString()
        val fixtures = selectFixtures(loadFixtures(fixtureContext), subset)
        require(fixtures.isNotEmpty())
        val namesJson = JSONArray(app.assets.open("data/pokemon_names.json").bufferedReader().use { it.readText() })
        val canonicalNames = (0 until namesJson.length()).associate {
            val name = namesJson.getString(it)
            name.lowercase(Locale.ROOT) to name
        }
        fun canonical(name: String?): String? = name?.lowercase(Locale.ROOT)?.let(canonicalNames::get)
        require(fixtures.all { it.expectedSpecies == null || canonical(it.expectedSpecies) != null })
        val runtime = RuntimeDecisions(app)
        val ocr = OCRProcessor(app)
        val calculator = RarityCalculator(app)
        val refiner = SpeciesRefiner(app, calculator)
        val consistency = ScanConsistencyGate(app, calculator)
        val visual = VisualFeatureDetector(app)
        val variants = VariantDecisionEngine(app)
        val phase2 = Phase2VariantClassifier(app)
        val confidence = ScanConfidenceGate()
        val outcomes = mutableListOf<JSONObject>()
        val initializationStart = SystemClock.elapsedRealtime()
        ocr.ensureInitialized()
        val initializationMs = SystemClock.elapsedRealtime() - initializationStart
        val report = JSONObject()
            .put("schemaVersion", 3)
            .put("runId", runId)
            .put("sourceRevision", sourceRevision)
            .put("evaluationMode", "single_bitmap_fast_detailed_component_replay")
            .put("fixtureSubset", subset)
            .put("comparisonScope", "same_build_baseline_vs_unresolved_identity_native_detail_retry")
            .put("mediaProjectionVerified", false)
            .put("temporalBurstVerified", false)
            .put("independentHoldout", false)
            .put("endToEndScanLatencyMeasured", false)
            .put("latencyScope", "bitmap_scaling_and_decision_components_with_instrumentation_overhead_excludes_decode_capture_save_overlay")
            .put("memoryScope", "process_snapshots_not_peak_attribution")
            .put("warmupScope", "shared_ocr_initialization_then_alternating_policy_order_no_warmup_discard")
            .put("expectedOutcomes", fixtures.size * policies.size)
            .put("completed", false)
            .put("initializationMs", initializationMs)
            .put("policies", JSONArray(policies))
            .put("truthSources", JSONObject()
                .put("pr06", "manifest_user_confirmed_live_source_screen")
                .put("legacy", "historical_declared_labels_unknowns_excluded")
                .put("legacy_limited_manual", "fixture_notes_declare_manual_exported_live_labels; source_and_reviewer_unverified"))
            .put("fieldVisibilityTruthSource", "pr06_fixture_manifest_visual_review")
        val reportFile = File(app.filesDir, reportName)
        fun saveReport() {
            report.put("outcomes", JSONArray(outcomes))
                .put("summaries", summarize(outcomes))
            reportFile.writeText(report.toString(2))
        }
        try {
            for (fixture in fixtures) {
                val bytes = fixtureContext.assets.open(fixture.asset).use { it.readBytes() }
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                require(fixture.hash == null || hash.equals(fixture.hash, ignoreCase = true)) {
                    "Fixture integrity mismatch: ${fixture.id}"
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap == null) {
                    policies.forEach { policy ->
                        outcomes += identity(fixture, policy, hash).put("error", "decode_failed")
                            .put("failureCategory", "processing_error")
                    }
                    saveReport()
                    continue
                }
                require((fixture.width == null || bitmap.width == fixture.width) &&
                    (fixture.height == null || bitmap.height == fixture.height))
                try {
                    // Alternate order to reduce systematic warm-up/thermal bias between policies.
                    val order = if (outcomes.size / policies.size % 2 == 0) policies else policies.reversed()
                    for (policy in order) {
                        val started = SystemClock.elapsedRealtime()
                        val heapBefore = usedHeap()
                        val nativeBefore = Debug.getNativeHeapAllocatedSize()
                        val input = if (policy in setOf("baseline", "adaptive") && bitmap.width > 900) {
                            Bitmap.createScaledBitmap(bitmap, 900, (bitmap.height * (900f / bitmap.width)).toInt(), true)
                        } else bitmap
                        val row = identity(fixture, policy, hash)
                            .put("sourceWidth", bitmap.width).put("sourceHeight", bitmap.height)
                            .put("ocrFrameWidth", input.width).put("ocrFrameHeight", input.height)
                            .put("pssBeforeKb", pssKb())
                        try {
                            val quality = runtime.cpQuality(input)
                            val fast = ocr.processImageWithDiagnostics(
                                FrameOcrRequest(bitmap = input, includeSecondaryFields = false, frameIndex = 0, frameRole = "fast", estimatedCpCropQuality = quality)
                            )
                            val fastEvidence = ScanManager.deriveSpeciesEvidence(
                                fast.diagnostic.fieldCandidates, fast.pokemon, calculator
                            )
                            val candidates = listOf(ScanFrameCandidate("fixture", fast.pokemon, quality, fastEvidence))
                            val runDetailed = ScanFrameFusion.shouldRunDetailedPass(fast.pokemon, quality, fastEvidence)
                            var detailedFailed = false
                            val nativeResolutionRetry = policy == "adaptive" && runDetailed &&
                                ScanFrameFusion.shouldAttemptNativeResolutionForUnresolvedIdentity(fastEvidence) &&
                                ScanFrameFusion.canUseNativeResolution(bitmap.width, bitmap.height)
                            val detailedInput = if (policy == "native" || nativeResolutionRetry) bitmap else input
                            val detailed = if (runDetailed) {
                                try {
                                    ocr.processImageWithDiagnostics(
                                    FrameOcrRequest(bitmap = detailedInput, includeSecondaryFields = true, frameIndex = -1, frameRole = "detailed_best")
                                )
                                } catch (_: Exception) {
                                    // ScanManager falls back to the fast result when its detailed pass fails.
                                    detailedFailed = true
                                    null
                                }
                            } else null
                            val frames = listOfNotNull(fast.diagnostic, detailed?.diagnostic)
                            val validCp = ScanFrameFusion.validCpCandidates(candidates)
                            val detailedEvidence = detailed?.let {
                                ScanManager.deriveSpeciesEvidence(it.diagnostic.fieldCandidates, it.pokemon, calculator)
                            } ?: SpeciesEvidence.failClosed()
                            val detailedCandidate = detailed?.let {
                                ScanFrameCandidate("fixture", it.pokemon, quality, detailedEvidence)
                            }
                            val anchoredSelection = ScanFrameFusion.resolveAnchoredFrames(
                                frames = candidates,
                                authoritative = candidates.single(),
                                detailed = detailedCandidate,
                                evidenceWithoutActionCosts = { frame, excludeEvolutionCost, excludePowerUpCost ->
                                    ScanManager.deriveSpeciesEvidenceWithoutActionCosts(
                                        frame.fieldCandidates,
                                        frame.data,
                                        calculator,
                                        excludeEvolutionCost = excludeEvolutionCost,
                                        excludePowerUpCost = excludePowerUpCost
                                    )
                                }
                            )
                            val fused = anchoredSelection?.frame?.data ?: ScanFrameFusion.fuse(
                                candidates, fast.pokemon, detailed?.pokemon ?: fast.pokemon, validCp, quality)
                            val ocrFinished = SystemClock.elapsedRealtime()
                            var evidence = anchoredSelection?.speciesEvidence ?: runtime.aggregate(listOf(fastEvidence))
                            val detailEvidence = detailed?.takeIf { anchoredSelection == null }
                                ?.let { SpeciesEvidence.fromFieldCandidates(it.diagnostic.fieldCandidates) }
                            if (detailEvidence?.hasHardAuthority == true &&
                                evidence.selectedCanonicalSpecies != null &&
                                detailEvidence.selectedCanonicalSpecies != null &&
                                !evidence.selectedCanonicalSpecies.equals(detailEvidence.selectedCanonicalSpecies, true)) {
                                evidence = runtime.conflictingEvidence()
                            }
                            val refined = refiner.refine(fused, frames.flatMap { it.fieldCandidates })
                            evidence = reconcileSpeciesProfileEvidence(
                                evidence,
                                ScanManager.profileStatus(refined, evidence.selectedCanonicalSpecies, calculator))
                            val consistencyResult = consistency.evaluate(fused, refined, evidence)
                            val resolverFinished = SystemClock.elapsedRealtime()
                            var finalPokemon = consistencyResult.pokemon
                            var cpPreCorrection = finalPokemon.cp
                            var variantSummary: VariantVisualSummary? = null
                            var classifierFailed = false
                            var visualFailed = false
                            var phase2Failed = false
                            if (!consistencyResult.shouldRetry) {
                                val sizeTag = rawField(finalPokemon, "SizeTag").ifBlank { null }
                                val finalBase = finalPokemon
                                val (classification, features) = coroutineScope {
                                    val classificationTask = async(Dispatchers.Default) {
                                        try { variants.classify(bitmap, finalBase) }
                                        catch (_: Exception) {
                                            classifierFailed = true
                                            VariantDecisionEngine.ClassificationResult(finalBase, null, null, null, null)
                                        }
                                    }
                                    val visualTask = async(Dispatchers.Default) {
                                        try { visual.detect(bitmap, finalBase.name, sizeTag) }
                                        catch (_: Exception) {
                                            visualFailed = true
                                            VisualFeatures()
                                        }
                                    }
                                    classificationTask.await() to visualTask.await()
                                }
                                var visualResult = features
                                finalPokemon = classification.pokemon
                                if (rawField(finalPokemon, "LuckyDetected").equals("true", true) && !visualResult.isLucky) {
                                    visualResult = visualResult.copy(isLucky = true, hasLocationCard = false,
                                        confidence = maxOf(visualResult.confidence, 0.75f))
                                }
                                visualResult = variants.mergeVisualFeatures(visualResult, classification.fullMatch,
                                    classification.resolvedMatch ?: classification.globalMatch)
                                cpPreCorrection = finalPokemon.cp
                                val fixedCp = calculator.validateAndFixCP(finalPokemon, validCp, visualResult)
                                if (fixedCp != null && fixedCp > 0) finalPokemon = finalPokemon.copy(cp = fixedCp)
                                val phase2Gate = ScanManager.resolvePhase2AuthorityGate(evidence,
                                    consistencyResult.pokemon.realName ?: consistencyResult.pokemon.name, false)
                                val acceptedSpecies = phase2Gate.acceptedSpecies
                                if (phase2Gate.mayRunSpeciesScopedPhase2 && !acceptedSpecies.isNullOrBlank()) {
                                    val classified = try { phase2.classify(bitmap, acceptedSpecies) }
                                    catch (_: Exception) { phase2Failed = true; null }
                                    if (phase2Gate.mayApplyPhase2 && classified != null) {
                                        visualResult = Phase2VariantFeatureMerger.merge(visualResult, classified)
                                    }
                                }
                                variantSummary = VariantVisualSummary.from(visualResult, finalPokemon.variantDecisionTrace)
                            }
                            val visualFinished = SystemClock.elapsedRealtime()
                            val decision = confidence.evaluate(ScanConfidenceInput(
                                pokemon = finalPokemon, frames = frames,
                                consistencyReason = consistencyResult.reason,
                                consistencyRequestedRetry = consistencyResult.shouldRetry,
                                cpCropQuality = quality, visualSummary = variantSummary, speciesEvidence = evidence
                            ))
                            val predicted = canonical(finalPokemon.realName ?: finalPokemon.name)
                            val accepted = !consistencyResult.shouldRetry && decision.mayShowOverlay && decision.maySaveScan
                            row.put("predictedSpecies", predicted ?: JSONObject.NULL)
                                .put("fastSpecies", canonical(fast.pokemon.realName ?: fast.pokemon.name) ?: JSONObject.NULL)
                                .put("refinedSpecies", canonical(refined.realName ?: refined.name) ?: JSONObject.NULL)
                                .put("accepted", accepted)
                                .put("collectionSafe", !consistencyResult.shouldRetry && decision.collectionSafe)
                                .put("finalGateReached", !consistencyResult.shouldRetry)
                                .put("lowConfidenceAccepted", accepted && decision.decision.name == "ACCEPT_LOW_CONFIDENCE")
                                .put("decision", if (consistencyResult.shouldRetry) "CONSISTENCY_RETRY" else decision.decision.name)
                                .put("confidence", decision.confidence.toDouble())
                                .put("consistencyReason", consistencyResult.reason)
                                .put("decisionReasons", JSONArray(decision.developerReasons))
                                .put("authority", evidence.authority.name)
                                .put("profileStatus", evidence.profileStatus.name)
                                .put("cpPresent", finalPokemon.cp != null)
                                .put("cpFastOcrCorrect", fieldCorrectness(
                                    fixture.expectedCpVisible, fixture.expectedCp, fast.pokemon.cp))
                                .put("cpPreCorrectionCorrect", fieldCorrectness(
                                    fixture.expectedCpVisible, fixture.expectedCp, cpPreCorrection))
                                .put("cpFinalResultCorrect", fieldCorrectness(
                                    fixture.expectedCpVisible, fixture.expectedCp, finalPokemon.cp))
                                .put("hpPresent", finalPokemon.hp != null || finalPokemon.maxHp != null)
                                .put("currentHpPresent", finalPokemon.hp != null)
                                .put("candyPresent", !finalPokemon.candyName.isNullOrBlank())
                                .put("arcPresent", finalPokemon.arcLevel != null)
                                .put("cpCorrect", fieldCorrectness(fixture.expectedCpVisible, fixture.expectedCp, finalPokemon.cp))
                                .put("hpCorrect", fieldCorrectness(fixture.expectedHpVisible, fixture.expectedHp, finalPokemon.hp))
                                .put("cpQuality", quality)
                                .put("detailedPass", runDetailed)
                                .put("nativeResolutionRetryRequested", nativeResolutionRetry)
                                .put("nativeResolutionFastFrame", policy == "native")
                                .put("nativeResolutionDetailedPass",
                                    runDetailed && (policy == "native" || nativeResolutionRetry))
                                .put("detailedFailed", detailedFailed)
                                .put("classifierFailed", classifierFailed)
                                .put("visualFailed", visualFailed)
                                .put("phase2Failed", phase2Failed)
                                .put("frames", JSONArray(frames.map(::safeFrame)))
                                .put("ocrMs", ocrFinished - started)
                                .put("resolverMs", resolverFinished - ocrFinished)
                                .put("visualMs", visualFinished - resolverFinished)
                                .put("totalMs", SystemClock.elapsedRealtime() - started)
                                .put("heapBeforeBytes", heapBefore).put("heapAfterBytes", usedHeap())
                                .put("nativeHeapBeforeBytes", nativeBefore)
                                .put("nativeHeapAfterBytes", Debug.getNativeHeapAllocatedSize())
                            row.put("failureCategory", failureCategory(row))
                        } catch (error: Exception) {
                            // Do not serialize exception messages: they may include scan content or paths.
                            row.put("error", error.javaClass.simpleName)
                                .put("failureCategory", "processing_error")
                                .put("cpCorrect", fieldCorrectness(fixture.expectedCpVisible, fixture.expectedCp, null))
                                .put("cpFastOcrCorrect", fieldCorrectness(fixture.expectedCpVisible, fixture.expectedCp, null))
                                .put("cpPreCorrectionCorrect", fieldCorrectness(fixture.expectedCpVisible, fixture.expectedCp, null))
                                .put("cpFinalResultCorrect", fieldCorrectness(fixture.expectedCpVisible, fixture.expectedCp, null))
                                .put("hpCorrect", fieldCorrectness(fixture.expectedHpVisible, fixture.expectedHp, null))
                                .put("totalMs", SystemClock.elapsedRealtime() - started)
                        } finally {
                            if (input !== bitmap) input.recycle()
                            row.put("pssAfterKb", pssKb())
                        }
                        outcomes += row
                        saveReport()
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            report.put("completed", true)
        } finally {
            try {
                ocr.release()
            } finally {
                try {
                    saveReport()
                } finally {
                    // Keep sanitized outcomes available to a host collector in bounded
                    // status messages, even if the target app is later removed.
                    sendReportToInstrumentation(instrumentation, runId, report.toString())
                }
            }
        }
    }

    private fun sendReportToInstrumentation(
        instrumentation: android.app.Instrumentation,
        runId: String,
        reportJson: String,
    ) {
        val reportBytes = reportJson.toByteArray(Charsets.UTF_8)
        val maxReportBytes = 1024 * 1024
        val bytesPerChunk = 1024
        if (reportBytes.size > maxReportBytes) {
            instrumentation.sendStatus(0, Bundle().apply {
                putString("recognitionRecoveryRunId", runId)
                putString("recognitionRecoveryReportError", "report_too_large")
            })
            return
        }
        val chunkCount = (reportBytes.size + bytesPerChunk - 1) / bytesPerChunk
        val reportSha256 = MessageDigest.getInstance("SHA-256").digest(reportBytes)
            .joinToString("") { "%02x".format(it) }
        repeat(chunkCount) { index ->
            val start = index * bytesPerChunk
            val end = minOf(start + bytesPerChunk, reportBytes.size)
            val chunk = reportBytes.copyOfRange(start, end)
            instrumentation.sendStatus(0, Bundle().apply {
                putString("recognitionRecoveryRunId", runId)
                putInt("recognitionRecoveryChunkIndex", index)
                putInt("recognitionRecoveryChunkCount", chunkCount)
                putString("recognitionRecoveryReportSha256", reportSha256)
                putString("recognitionRecoveryChunkBase64", Base64.encodeToString(chunk, Base64.NO_WRAP))
            })
        }
    }

    private fun identity(fixture: Fixture, policy: String, hash: String) = JSONObject()
        .put("id", fixture.id).put("dataset", fixture.dataset).put("policy", policy)
        .put("correlationGroup", fixture.correlationGroup)
        .put("sourceSha256", hash)
        .put("expectedSpecies", fixture.expectedSpecies ?: JSONObject.NULL)
        .put("expectedCpVisible", fixture.expectedCpVisible ?: JSONObject.NULL)
        .put("expectedHpVisible", fixture.expectedHpVisible ?: JSONObject.NULL)
        .put("cpTruthAvailable", fixture.expectedCp != null)
        .put("hpTruthAvailable", fixture.expectedHp != null)

    private fun fieldCorrectness(visible: Boolean?, expected: Int?, actual: Int?): Any =
        if (visible == true && expected != null) expected == actual else JSONObject.NULL

    private fun safeFrame(frame: FrameDiagnostic) = JSONObject()
        .put("role", frame.role).put("screenState", frame.screenState)
        .put("screenConfidence", frame.screenConfidence?.toDouble() ?: JSONObject.NULL)
        .put("geometryFallbackReasons", JSONArray(frame.geometryFallbackReasons))
        .put("crops", JSONArray(frame.crops.map { crop -> JSONObject()
            .put("field", crop.field).put("status", crop.status)
            .put("left", crop.left ?: JSONObject.NULL).put("top", crop.top ?: JSONObject.NULL)
            .put("right", crop.right ?: JSONObject.NULL).put("bottom", crop.bottom ?: JSONObject.NULL)
            .put("provenance", crop.provenance ?: JSONObject.NULL) }))
        .put("fields", JSONArray(frame.fieldCandidates.map { field -> JSONObject()
            .put("field", field.field).put("status", field.status).put("winner", field.winner)
            .put("rawPresent", !field.rawText.isNullOrBlank())
            .put("parsedPresent", !field.parsedValue.isNullOrBlank())
            .put("selectedPresent", !field.selectedValue.isNullOrBlank()) }))
        .put("stageTimings", JSONArray(frame.stageTimings.map { JSONObject()
            .put("stage", it.stage).put("durationMs", it.durationMs) }))

    private fun loadFixtures(context: Context): List<Fixture> = buildList {
        val manifestDocument = JSONObject(context.assets.open("scan_fixtures/pr06_1080_development/fixture_manifest.json")
            .bufferedReader().use { it.readText() })
        require(manifestDocument.getString("corpusClass") == "development")
        val manifest = manifestDocument.getJSONArray("fixtures")
        for (index in 0 until manifest.length()) {
            val entry = manifest.getJSONObject(index)
            val truth = entry.getJSONObject("truth")
            val fieldVisibility = entry.getJSONObject("fieldVisibility")
            require(truth.getString("status") == "confirmed")
            require(truth.getString("source") == "user_confirmed_live_source_screen")
            require(entry.getBoolean("excludedFromHoldout") && entry.getBoolean("eligibleForDevelopmentMeasurement"))
            add(Fixture(entry.getString("id"), "pr06", entry.getString("relativePath"),
                truth.getString("canonicalSpecies"), truth.getInt("cp"), truth.getInt("hp"),
                entry.getString("setId"), fieldVisibility.getBoolean("cp"), fieldVisibility.getBoolean("hp"),
                entry.getString("sha256"), entry.getInt("width"), entry.getInt("height")))
        }
        val legacy = JSONArray(context.assets.open("scan_regression_cases.json").bufferedReader().use { it.readText() })
        for (index in 0 until legacy.length()) {
            val entry = legacy.getJSONObject(index)
            val expected = entry.optJSONObject("expected") ?: JSONObject()
            add(Fixture("legacy_%03d".format(index + 1), "legacy", entry.getString("assetPath"),
                if (expected.isNull("species")) null else expected.getString("species"),
                if (expected.isNull("cp")) null else expected.getInt("cp"),
                if (expected.isNull("hp")) null else expected.getInt("hp"),
                "legacy_grouping_unverified"))
        }
    }

    private fun selectFixtures(fixtures: List<Fixture>, subset: String): List<Fixture> {
        val classified = fixtures.map { fixture ->
            if (fixture.dataset == "legacy" && fixture.asset in limitedManualLegacyAssets) {
                // Keep these rows in broad replays while separating their unverified label provenance.
                fixture.copy(dataset = "legacy_limited_manual")
            } else {
                fixture
            }
        }
        return when (subset) {
            "all" -> classified
            "pr06" -> classified.filter { it.dataset == "pr06" }
            "legacy" -> classified.filter { it.dataset in setOf("legacy", "legacy_limited_manual") }
            "limited_manual" -> {
                val selected = classified.filter { it.dataset == "legacy_limited_manual" }
                require(selected.size == limitedManualLegacyAssets.size &&
                    selected.map { it.asset }.toSet() == limitedManualLegacyAssets) {
                    "Limited-manual fixture set changed; refusing to run a partial subset"
                }
                selected
            }
            else -> error("Unsupported recognition fixture subset")
        }
    }

    private fun summarize(outcomes: List<JSONObject>): JSONArray = JSONArray(
        outcomes.groupBy { it.getString("dataset") to it.getString("policy") }.map { (group, rows) ->
            val labeled = rows.filterNot { it.isNull("expectedSpecies") }
            val accepted = labeled.filter { it.optBoolean("accepted") }
            fun correct(row: JSONObject) = !row.isNull("predictedSpecies") &&
                row.getString("expectedSpecies").equals(row.getString("predictedSpecies"), true)
            val acceptedCorrect = accepted.count(::correct)
            val latency = rows.filter { it.has("totalMs") }.map { it.getLong("totalMs") }.sorted()
            val categories = rows.groupingBy { it.optString("failureCategory", "processing_error") }.eachCount()
            val cpVisibleRows = rows.filter { it.optBoolean("expectedCpVisible") && it.optBoolean("cpTruthAvailable") }
            val hpVisibleRows = rows.filter { it.optBoolean("expectedHpVisible") && it.optBoolean("hpTruthAvailable") }
            fun rate(count: Int): Any = if (labeled.isEmpty()) JSONObject.NULL else count.toDouble() / labeled.size
            fun fieldRate(correct: Int, total: Int): Any =
                if (total == 0) JSONObject.NULL else correct.toDouble() / total
            fun cpCorrectCount(key: String): Int = cpVisibleRows.count { it.optBoolean(key) }
            val fastCpCorrect = cpCorrectCount("cpFastOcrCorrect")
            val preCorrectionCpCorrect = cpCorrectCount("cpPreCorrectionCorrect")
            val finalResultCpCorrect = cpCorrectCount("cpFinalResultCorrect")
            JSONObject().put("dataset", group.first).put("policy", group.second)
                .put("total", rows.size).put("labeled", labeled.size)
                .put("unknown", rows.size - labeled.size).put("errors", rows.count { it.has("error") })
                .put("speciesCorrect", labeled.count(::correct))
                .put("speciesAccuracy", rate(labeled.count(::correct)))
                .put("acceptedCorrect", acceptedCorrect).put("confidentlyWrong", accepted.size - acceptedCorrect)
                .put("acceptedAccuracy", if (accepted.isEmpty()) JSONObject.NULL else acceptedCorrect.toDouble() / accepted.size)
                .put("strictlyConfidentWrong", accepted.count { !correct(it) && it.optString("decision") == "ACCEPT" })
                .put("lowConfidenceAccepted", labeled.count { it.optBoolean("lowConfidenceAccepted") })
                .put("unknownAcceptedUnscored", rows.count { it.isNull("expectedSpecies") && it.optBoolean("accepted") })
                .put("usableCoverage", rate(acceptedCorrect)).put("acceptanceRate", rate(accepted.size))
                .put("uncertainOrRejected", labeled.size - accepted.size)
                .put("uncertainOrRejectedRate", rate(labeled.size - accepted.size))
                .put("cpVisibleLabeled", cpVisibleRows.size)
                .put("cpVisibleFastOcrCorrect", fastCpCorrect)
                .put("cpVisibleFastOcrAccuracy", fieldRate(fastCpCorrect, cpVisibleRows.size))
                .put("cpVisiblePreCorrectionCorrect", preCorrectionCpCorrect)
                .put("cpVisiblePreCorrectionAccuracy", fieldRate(preCorrectionCpCorrect, cpVisibleRows.size))
                .put("cpVisibleFinalResultCorrect", finalResultCpCorrect)
                .put("cpVisibleFinalResultAccuracy", fieldRate(finalResultCpCorrect, cpVisibleRows.size))
                .put("cpVisibleCorrect", cpVisibleRows.count { it.optBoolean("cpCorrect") })
                .put("cpVisibleAccuracy", fieldRate(cpVisibleRows.count { it.optBoolean("cpCorrect") }, cpVisibleRows.size))
                .put("hpVisibleLabeled", hpVisibleRows.size)
                .put("hpVisibleCorrect", hpVisibleRows.count { it.optBoolean("hpCorrect") })
                .put("hpVisibleAccuracy", fieldRate(hpVisibleRows.count { it.optBoolean("hpCorrect") }, hpVisibleRows.size))
                .put("medianTotalMs", latency.getOrNull(latency.size / 2) ?: JSONObject.NULL)
                .put("p95TotalMs", latency.getOrNull((ceil(latency.size * 0.95).toInt() - 1).coerceAtLeast(0)) ?: JSONObject.NULL)
                .put("maxSampledPssKb", rows.mapNotNull { if (it.has("pssAfterKb")) it.getInt("pssAfterKb") else null }.maxOrNull() ?: JSONObject.NULL)
                .put("failureCategories", JSONObject(categories))
        }
    )

    // Categories describe the observed output; they are not asserted root-cause diagnoses.
    private fun failureCategory(row: JSONObject): String = when {
        row.has("error") -> "processing_error"
        row.optBoolean("accepted") && row.isNull("expectedSpecies") -> "accepted_truth_unknown"
        row.optBoolean("accepted") && row.optString("expectedSpecies").equals(row.optString("predictedSpecies"), true) -> "accepted_correct"
        row.optBoolean("accepted") -> "accepted_wrong"
        row.optString("decision") == "REJECT_NOT_POKEMON_SCREEN" -> "screen_rejected"
        row.optString("authority") == "CONFLICT" -> "authority_conflict"
        row.optString("authority") in setOf("UNCERTAIN", "NO_MATCH") -> "species_authority_missing_or_uncertain"
        row.optString("profileStatus") in setOf("CONTRADICTORY", "IMPOSSIBLE") -> "profile_conflict"
        (row.optBoolean("expectedCpVisible") && !row.optBoolean("cpPresent")) ||
            (row.optBoolean("expectedHpVisible") && !row.optBoolean("currentHpPresent")) ->
            "primary_numeric_fields_missing"
        row.optString("decision") == "CONSISTENCY_RETRY" -> "consistency_retry"
        else -> "final_gate_uncertain_or_rejected"
    }

    private fun rawField(pokemon: PokemonData, key: String): String = pokemon.rawOcrText.split('|')
        .firstOrNull { it.startsWith("$key:") }?.substringAfter(':').orEmpty()

    private fun usedHeap(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    private fun pssKb(): Int = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss

    /**
     * Calls the actual private helpers without opening a receiver, database, or capture session.
     * Keeping this test-only bridge avoids changing the baseline or maintaining parallel gate logic.
     * A production helper rename fails setup, instead of silently evaluating different behavior.
     * ScanManager.profileStatus is called directly (internal visibility, same module): the
     * profile status is applied through the production reconcileSpeciesProfileEvidence
     * contract, never a direct overwrite.
     */
    private class RuntimeDecisions(context: Context) {
        private val manager = ScanManager(context)
        private val aggregate = method("aggregateFastEvidence", List::class.java)
        private val conflict = method("conflictingEvidence")
        private val quality = method("estimateCpQuality", Bitmap::class.java)
        fun aggregate(evidence: List<SpeciesEvidence>): SpeciesEvidence = aggregate.invoke(manager, evidence) as SpeciesEvidence
        fun conflictingEvidence(): SpeciesEvidence = conflict.invoke(manager) as SpeciesEvidence
        fun cpQuality(bitmap: Bitmap): Double = quality.invoke(manager, bitmap) as Double

        private fun method(name: String, vararg parameters: Class<*>) =
            ScanManager::class.java.getDeclaredMethod(name, *parameters).apply { isAccessible = true }
    }
}
