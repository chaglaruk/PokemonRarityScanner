package com.pokerarity.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.util.ocr.FrameDiagnostic
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
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.ceil

/**
 * Real on-device OCR, replaying one saved bitmap through the production decision components.
 * This is not MediaProjection, a temporal burst, or an independent final holdout evaluation.
 * Arguments: policies=baseline,native; subset=pr06|legacy|all; report=<safe basename>.json.
 * Reports contain no screenshot, raw OCR, filename, location, or unrestricted diagnostic object.
 * Existing production debug logging is unchanged; unfiltered logcat is not sanitized evidence.
 */
@RunWith(AndroidJUnit4::class)
class RecognitionRecoveryBenchmarkTest {
    private data class Fixture(
        val id: String,
        val dataset: String,
        val asset: String,
        val expectedSpecies: String?,
        val expectedCp: Int?,
        val expectedHp: Int?,
        val correlationGroup: String,
        val hash: String? = null,
        val width: Int? = null,
        val height: Int? = null
    )

    @Test
    fun measureBitmapReplay(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val app = instrumentation.targetContext
        val fixtureContext = instrumentation.context
        val policies = arguments.getString("policies", "baseline,native").split(',')
        require(policies.isNotEmpty() && policies.distinct().size == policies.size &&
            policies.all { it in setOf("baseline", "native") })
        val subset = arguments.getString("subset", "pr06")
        require(subset in setOf("pr06", "legacy", "all"))
        val reportName = arguments.getString("report", "recognition_recovery.json")
        require(reportName.matches(Regex("[A-Za-z0-9_-]{1,80}\\.json")))
        val sourceRevision = arguments.getString("sourceRevision", "unspecified")
        require(sourceRevision.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        val fixtures = loadFixtures(fixtureContext).filter { subset == "all" || it.dataset == subset }
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
            .put("schemaVersion", 2)
            .put("sourceRevision", sourceRevision)
            .put("evaluationMode", "single_bitmap_fast_detailed_component_replay")
            .put("comparisonScope", "same_build_900px_cap_vs_native_ocr_input")
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
                .put("legacy", "historical_declared_labels_unknowns_excluded"))
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
                        val input = if (policy == "baseline" && bitmap.width > 900) {
                            Bitmap.createScaledBitmap(bitmap, 900, (bitmap.height * (900f / bitmap.width)).toInt(), true)
                        } else bitmap
                        val row = identity(fixture, policy, hash)
                            .put("sourceWidth", bitmap.width).put("sourceHeight", bitmap.height)
                            .put("ocrFrameWidth", input.width).put("ocrFrameHeight", input.height)
                            .put("pssBeforeKb", pssKb())
                        try {
                            val quality = runtime.cpQuality(input)
                            val fast = ocr.processImageWithDiagnostics(input, false, 0, "fast", quality)
                            val fastEvidence = ScanManager.deriveSpeciesEvidence(
                                fast.diagnostic.fieldCandidates, fast.pokemon, calculator
                            )
                            val candidates = listOf(ScanFrameCandidate("fixture", fast.pokemon, quality, fastEvidence))
                            val runDetailed = ScanFrameFusion.shouldRunDetailedPass(fast.pokemon, quality, fastEvidence)
                            var detailedFailed = false
                            val detailed = if (runDetailed) {
                                try {
                                    ocr.processImageWithDiagnostics(input, true, -1, "detailed_best")
                                } catch (_: Exception) {
                                    // ScanManager falls back to the fast result when its detailed pass fails.
                                    detailedFailed = true
                                    null
                                }
                            } else null
                            val frames = listOfNotNull(fast.diagnostic, detailed?.diagnostic)
                            val validCp = ScanFrameFusion.validCpCandidates(candidates)
                            val anchoredSelection = ScanFrameFusion.resolveAnchoredFrames(
                                frames = candidates,
                                authoritative = candidates.single(),
                                detailed = detailed?.let { ScanFrameCandidate("fixture", it.pokemon, quality) },
                                deriveEvidence = { ScanManager.deriveSpeciesEvidence(emptyList(), it, calculator) }
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
                            evidence = evidence.withProfileStatus(runtime.profile(refined,
                                evidence.selectedCanonicalSpecies, calculator))
                            val consistencyResult = consistency.evaluate(fused, refined, evidence)
                            val resolverFinished = SystemClock.elapsedRealtime()
                            var finalPokemon = consistencyResult.pokemon
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
                                .put("hpPresent", finalPokemon.hp != null || finalPokemon.maxHp != null)
                                .put("candyPresent", !finalPokemon.candyName.isNullOrBlank())
                                .put("arcPresent", finalPokemon.arcLevel != null)
                                .put("cpCorrect", fixture.expectedCp?.let { it == finalPokemon.cp } ?: JSONObject.NULL)
                                .put("hpCorrect", fixture.expectedHp?.let { it == finalPokemon.hp } ?: JSONObject.NULL)
                                .put("cpQuality", quality)
                                .put("detailedPass", runDetailed)
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
            ocr.release()
            saveReport()
        }
    }

    private fun identity(fixture: Fixture, policy: String, hash: String) = JSONObject()
        .put("id", fixture.id).put("dataset", fixture.dataset).put("policy", policy)
        .put("correlationGroup", fixture.correlationGroup)
        .put("sourceSha256", hash)
        .put("expectedSpecies", fixture.expectedSpecies ?: JSONObject.NULL)

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
            require(truth.getString("status") == "confirmed")
            require(truth.getString("source") == "user_confirmed_live_source_screen")
            require(entry.getBoolean("excludedFromHoldout") && entry.getBoolean("eligibleForDevelopmentMeasurement"))
            add(Fixture(entry.getString("id"), "pr06", entry.getString("relativePath"),
                truth.getString("canonicalSpecies"), truth.getInt("cp"), truth.getInt("hp"),
                entry.getString("setId"),
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

    private fun summarize(outcomes: List<JSONObject>): JSONArray = JSONArray(
        outcomes.groupBy { it.getString("dataset") to it.getString("policy") }.map { (group, rows) ->
            val labeled = rows.filterNot { it.isNull("expectedSpecies") }
            val accepted = labeled.filter { it.optBoolean("accepted") }
            fun correct(row: JSONObject) = !row.isNull("predictedSpecies") &&
                row.getString("expectedSpecies").equals(row.getString("predictedSpecies"), true)
            val acceptedCorrect = accepted.count(::correct)
            val latency = rows.filter { it.has("totalMs") }.map { it.getLong("totalMs") }.sorted()
            val categories = rows.groupingBy { it.optString("failureCategory", "processing_error") }.eachCount()
            fun rate(count: Int): Any = if (labeled.isEmpty()) JSONObject.NULL else count.toDouble() / labeled.size
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
        !row.optBoolean("cpPresent") || !row.optBoolean("hpPresent") -> "primary_numeric_fields_missing"
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
     */
    private class RuntimeDecisions(context: Context) {
        private val manager = ScanManager(context)
        private val aggregate = method("aggregateFastEvidence", List::class.java)
        private val conflict = method("conflictingEvidence")
        private val quality = method("estimateCpQuality", Bitmap::class.java)
        private val profile = ScanManager.Companion::class.java.getDeclaredMethod(
            "profileStatus", PokemonData::class.java, String::class.java, RarityCalculator::class.java
        ).apply { isAccessible = true }

        fun aggregate(evidence: List<SpeciesEvidence>): SpeciesEvidence = aggregate.invoke(manager, evidence) as SpeciesEvidence
        fun conflictingEvidence(): SpeciesEvidence = conflict.invoke(manager) as SpeciesEvidence
        fun cpQuality(bitmap: Bitmap): Double = quality.invoke(manager, bitmap) as Double
        fun profile(pokemon: PokemonData, species: String?, calculator: RarityCalculator): SpeciesProfileStatus =
            profile.invoke(ScanManager.Companion, pokemon, species, calculator) as SpeciesProfileStatus

        private fun method(name: String, vararg parameters: Class<*>) =
            ScanManager::class.java.getDeclaredMethod(name, *parameters).apply { isAccessible = true }
    }
}
