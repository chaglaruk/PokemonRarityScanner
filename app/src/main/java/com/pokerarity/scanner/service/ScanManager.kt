package com.pokerarity.scanner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.OcrConfidenceReasons
import com.pokerarity.scanner.data.model.OcrConfidenceReasonsBuilder
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator
import com.pokerarity.scanner.ui.result.ResultActivity
import com.pokerarity.scanner.util.ScanError
import com.pokerarity.scanner.util.ScanResult
import com.pokerarity.scanner.util.ocr.OcrDiagnosticsExporter
import com.pokerarity.scanner.util.ocr.OCRProcessor
import com.pokerarity.scanner.util.ocr.ConfidenceReasonDiagnostic
import com.pokerarity.scanner.util.ocr.FrameDiagnostic
import com.pokerarity.scanner.util.ocr.OcrFrameResult
import com.pokerarity.scanner.util.ocr.PokemonSummary
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import com.pokerarity.scanner.util.ocr.ScanConfidenceGate
import com.pokerarity.scanner.util.ocr.ScanConfidenceInput
import com.pokerarity.scanner.util.ocr.ScanDiagnosticReport
import com.pokerarity.scanner.util.ocr.ScanDecision
import com.pokerarity.scanner.util.ocr.ScanDecisionType
import com.pokerarity.scanner.util.ocr.SpeciesRefiner
import com.pokerarity.scanner.util.ocr.StageTimingDiagnostic
import com.pokerarity.scanner.util.ocr.TextParser
import com.pokerarity.scanner.util.ocr.VariantVisualSummary
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import com.pokerarity.scanner.util.vision.Phase2VariantFeatureMerger
import com.pokerarity.scanner.util.vision.VariantDecisionEngine
import com.pokerarity.scanner.util.vision.VisualFeatureDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Date
import com.pokerarity.scanner.util.DateParseUtils
import com.pokerarity.scanner.util.DateParseUtils.formatDate


/**
 * Orchestrates the full scan pipeline:
 *   Screenshot → OCR → Visual Detection → Rarity Calculation → Save → Show Result
 *
 * Register with [start] from an Activity / Application and unregister with [stop].
 */
class ScanManager(private val context: Context) {

    companion object {
        private const val TAG = "ScanManager"
        private const val IV_DIAGNOSTIC_BROAD_THRESHOLD = 20
        private const val MAX_SCREENSHOT_FRAMES = 3

        internal fun shouldRunDetailedPassForAuthoritative(
            pokemon: PokemonData,
            cpQuality: Double,
            topTextConfidence: Double
        ): Boolean {
            return ScanFrameFusion.shouldRunDetailedPass(pokemon, cpQuality, topTextConfidence)
        }

        internal fun sanitizeScreenshotPaths(paths: List<String>, cacheDir: File): List<String> {
            val cacheRoot = runCatching { cacheDir.canonicalFile }.getOrElse { return emptyList() }
            return paths.asSequence()
                .mapNotNull { rawPath ->
                    val file = runCatching { File(rawPath).canonicalFile }.getOrNull() ?: return@mapNotNull null
                    val isInCache = file.parentFile == cacheRoot
                    val isScanImage = file.name.startsWith("scan_") && file.name.endsWith(".png")
                    file.takeIf { isInCache && isScanImage && it.isFile }
                }
                .take(MAX_SCREENSHOT_FRAMES)
                .map { it.absolutePath }
                .toList()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var retryCount = 0
    private val scanMutex = Mutex()
    private val decodeBitmapPool = BitmapPool(maxSize = 2)

    private val ocrProcessor by lazy { OCRProcessor(context) }
    private val textParser by lazy { TextParser(context) }
    private val visualDetector by lazy { VisualFeatureDetector(context) }
    private val variantDecisionEngine by lazy { VariantDecisionEngine(context) }
    private val phase2VariantClassifier by lazy { Phase2VariantClassifier(context) }
    private val repository by lazy { PokemonRepository(AppDatabase.getInstance(context)) }
    private val rarityCalculator by lazy { RarityCalculator(context) }
    private val speciesRefiner by lazy { SpeciesRefiner(context, rarityCalculator) }
    private val consistencyGate by lazy { ScanConsistencyGate(context, rarityCalculator) }
    private val scanConfidenceGate by lazy { ScanConfidenceGate() }
    private val telemetryCoordinator by lazy { ScanTelemetryCoordinator.getInstance(context) }

    // ── BroadcastReceiver for screenshot-ready events ────────────────────

    private val screenshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            Log.d(TAG, "onReceive: action=${intent.action}, extras=${intent.extras?.keySet()?.joinToString()}")
            val paths = intent.getStringArrayListExtra(ScreenCaptureService.EXTRA_SCREENSHOT_PATHS)
            if (paths.isNullOrEmpty()) {
                Log.e(TAG, "onReceive: paths is null or empty")
                handleError(ScanResult.Failure(ScanError.CAPTURE_FAILED))
                return
            }
            val safePaths = sanitizeScreenshotPaths(paths, context.cacheDir)
            if (safePaths.isEmpty()) {
                Log.w(TAG, "onReceive: no valid app-cache screenshot paths")
                handleError(ScanResult.Failure(ScanError.CAPTURE_FAILED))
                return
            }
            if (safePaths.size != paths.size) {
                Log.w(TAG, "onReceive: filtered screenshot paths from ${paths.size} to ${safePaths.size}")
            }
            Log.d(TAG, "onReceive: paths size=${safePaths.size}")
            processScanSequence(safePaths)
        }
    }

    // ── Public API ───────────────────────────────────────────────────────

    fun start() {
        val filter = IntentFilter(ScreenCaptureService.ACTION_SCREENSHOT_READY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(screenshotReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                context,
                screenshotReceiver,
                filter,
                ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION,
                null,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        Log.d(TAG, "ScanManager started, receiver registered for ${ScreenCaptureService.ACTION_SCREENSHOT_READY}")
    }

    fun stop() {
        try { context.unregisterReceiver(screenshotReceiver) } catch (_: Exception) { Log.w(TAG, "screenshotReceiver not registered during stop") }
        ocrProcessor.release()
        scope.cancel()
        Log.d(TAG, "ScanManager stopped")
    }

    // ── Pipeline ─────────────────────────────────────────────────────────

    private fun processScanSequence(paths: List<String>) {
        Log.d(TAG, "processScanSequence: starting with ${paths.size} frames")
        scope.launch {
            scanMutex.withLock {
                val pipelineStart = System.currentTimeMillis()
                val pipelineTimings = mutableListOf<StageTimingDiagnostic>()
                try {
                    // 1. Parallel bitmap decode and preprocessing (these are CPU-bound)
                    // Tesseract OCR will happen sequentially after because it's not thread-safe
                    val decodeStart = System.currentTimeMillis()
                    data class DecodedFrame(
                        val index: Int,
                        val path: String,
                        val bitmap: Bitmap,
                        val cpQuality: Double,
                        val pooled: Boolean
                    )
                    val frameJobs = paths.mapIndexed { index, path ->
                        async(Dispatchers.Default) {
                            val bitmap = decodeBitmapPool.decodeFile(path) ?: return@async null
                            try {
                                val scaled = if (bitmap.width > 900) {
                                    Bitmap.createScaledBitmap(bitmap, 900, (bitmap.height * (900f / bitmap.width)).toInt(), true)
                                } else bitmap
                                val cpQuality = estimateCpQuality(scaled)
                                if (scaled !== bitmap) {
                                    decodeBitmapPool.release(bitmap)
                                }
                                DecodedFrame(index, path, scaled, cpQuality, scaled === bitmap)
                            } catch (e: Exception) {
                                decodeBitmapPool.release(bitmap)
                                null
                            }
                        }
                    }
                    
                    val decodedFrames = frameJobs.awaitAll().filterNotNull()
                    val decodeTime = System.currentTimeMillis() - decodeStart
                    pipelineTimings += StageTimingDiagnostic("decode", decodeTime)
                    Log.d(TAG, "Parallel decode + preprocess: ${decodedFrames.size} frames in ${decodeTime}ms (avg ${if (decodedFrames.isNotEmpty()) decodeTime / decodedFrames.size else 0}ms/frame)")

                    // 2. Run OCR sequentially (Tesseract is not thread-safe)
                    val ocrStart = System.currentTimeMillis()
                    val results = mutableListOf<ScanFrameCandidate>()
                    val frameDiagnostics = mutableListOf<FrameDiagnostic>()
                    var processedFrameCount = 0
                    try {
                        for ((index, path, scaled, cpQuality, pooled) in decodedFrames) {
                            var shouldStop = false
                            try {
                                val frameResult = ocrProcessor.processImageWithDiagnostics(
                                    bitmap = scaled,
                                    includeSecondaryFields = false,
                                    frameIndex = index,
                                    frameRole = "fast",
                                    estimatedCpCropQuality = cpQuality
                                )
                                val data = frameResult.pokemon
                                frameDiagnostics += frameResult.diagnostic
                                results.add(ScanFrameCandidate(path, data, cpQuality))
                                if (ScanFrameFusion.isHighConfidence(results)) {
                                    Log.d(TAG, "Early exit: high-confidence OCR frame found after ${results.size} frames")
                                    shouldStop = true
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Frame OCR failed: framePath=${SafeDebugLogValue.localFileReference(path)}", e)
                            } finally {
                                releaseBitmap(scaled, pooled)
                                processedFrameCount++
                            }

                            if (shouldStop) {
                                break
                            }
                        }
                    } finally {
                        decodedFrames.drop(processedFrameCount).forEach { frame ->
                            releaseBitmap(frame.bitmap, frame.pooled)
                        }
                    }
                    
                    val ocrTime = System.currentTimeMillis() - ocrStart
                    pipelineTimings += StageTimingDiagnostic("ocr_fast_total", ocrTime)
                    Log.d(TAG, "Sequential OCR: ${results.size} frames in ${ocrTime}ms (avg ${if (results.isNotEmpty()) ocrTime / results.size else 0}ms/frame)")

                    if (results.isEmpty()) {
                        handleError(ScanResult.Failure(ScanError.OCR_FAILED))
                        return@withLock
                    }

                    // 2. Aggregate all seen CP candidates across frames for better fallback
                    val allOcrCPs = ScanFrameFusion.validCpCandidates(results)

                    // 3. Score and pick the best result
                    // Quality Score: CP (+100), Name (+30), HP (+20), Arc (+20), Date (+10)
                    val bestEntry = ScanFrameFusion.selectBestFrame(results) ?: run {
                        Log.w(TAG, "No valid scan results after filtering")
                        return@withLock
                    }

                    val bestResult = bestEntry.data
                    val bestCpQuality = bestEntry.cpQuality

                    Log.d(TAG, "Best frame selected: CP=${bestResult.cp}, Name=${bestResult.name}, HP=${bestResult.hp}, Arc=${bestResult.arcLevel}")

                    val shouldRunDetailedPass = shouldRunDetailedPass(bestResult, bestCpQuality)
                    if (!shouldRunDetailedPass) {
                        Log.d(
                            TAG,
                            "Detailed OCR skipped: cp/name/date already reliable (cpQuality=$bestCpQuality)"
                        )
                    }
                    val detailedDeferred = if (shouldRunDetailedPass) {
                        async(Dispatchers.Default) {
                            runDetailedPassIfNeeded(bestEntry.path)
                        }
                    } else {
                        null
                    }

                    // 3.1 Multi-frame fusion for stability.
                    // The fast pass remains authoritative for primary fields. The detailed
                    // pass only backfills secondary fields and richer raw OCR traces.
                    val detailedAwaitStart = System.currentTimeMillis()
                    val detailedFrameResult = detailedDeferred?.await()
                    if (detailedDeferred != null) {
                        pipelineTimings += StageTimingDiagnostic("ocr_detailed_total", System.currentTimeMillis() - detailedAwaitStart)
                    }
                    val detailedBestResult = detailedFrameResult?.pokemon ?: bestResult
                    val reportFrames = if (detailedFrameResult != null) {
                        frameDiagnostics + detailedFrameResult.diagnostic
                    } else {
                        frameDiagnostics.toList()
                    }
                    val fused = ScanFrameFusion.fuse(results, bestResult, detailedBestResult, allOcrCPs, bestCpQuality)
                    val resolverStart = System.currentTimeMillis()
                    val refined = speciesRefiner.refine(fused, reportFrames.flatMap { it.fieldCandidates })
                    pipelineTimings += StageTimingDiagnostic("species_resolver", System.currentTimeMillis() - resolverStart)
                    val consistencyStart = System.currentTimeMillis()
                    val consistencyDecision = consistencyGate.evaluate(fused, refined)
                    pipelineTimings += StageTimingDiagnostic("consistency_gate", System.currentTimeMillis() - consistencyStart)
                    if (consistencyDecision.shouldRetry) {
                        Log.w(TAG, "Consistency gate requested retry: ${consistencyDecision.reason}")
                        exportRetryDiagnostics(
                            screenshotPath = bestEntry.path,
                            pokemon = refined,
                            reason = consistencyDecision.reason,
                            frames = reportFrames,
                            stageTimings = pipelineTimings + StageTimingDiagnostic("total", System.currentTimeMillis() - pipelineStart)
                        )
                        handleError(ScanResult.Failure(ScanError.LOW_CONFIDENCE_RESULT))
                        return@withLock
                    }
                    if (consistencyDecision.reason != "accepted") {
                        Log.i(TAG, "Consistency gate applied: ${consistencyDecision.reason}")
                    }
                    val fallbackReason = consistencyDecision.reason.takeUnless { it == "accepted" }
                    val finalBase = consistencyDecision.pokemon

                    // 4. Visual Detection on the best frame
                    val bestPath = bestEntry.path
                    val bestBitmap = decodeBitmapPool.decodeFile(bestPath)
                    try {
                        if (bestBitmap == null) {
                            Log.e(TAG, "Best frame decode failed: framePath=${SafeDebugLogValue.localFileReference(bestPath)}")
                        }

                        // OCR'dan gelen boyut etiketini çek (XL, XS, XXL, XXS)
                        val provisionalSizeTag = finalBase.rawOcrText.split("|").find { it.startsWith("SizeTag:") }?.substringAfter(":")
                        val classifierStart = System.currentTimeMillis()
                        val classificationDeferred = async(Dispatchers.Default) {
                            try {
                                if (bestBitmap != null) {
                                    variantDecisionEngine.classify(bestBitmap, finalBase)
                                } else {
                                    VariantDecisionEngine.ClassificationResult(finalBase, null, null, null, null)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Variant classifier failed", e)
                                VariantDecisionEngine.ClassificationResult(finalBase, null, null, null, null)
                            }
                        }
                        val visualStart = System.currentTimeMillis()
                        val visualDeferred = async(Dispatchers.Default) {
                            try {
                                if (bestBitmap != null) {
                                    visualDetector.detect(bestBitmap, finalBase.name, provisionalSizeTag)
                                } else {
                                    com.pokerarity.scanner.data.model.VisualFeatures()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Visual detection failed", e)
                                com.pokerarity.scanner.data.model.VisualFeatures()
                            }
                        }
                        val classification = classificationDeferred.await()
                        val classifierElapsed = System.currentTimeMillis() - classifierStart
                        pipelineTimings += StageTimingDiagnostic("variant_classifier", classifierElapsed)
                        classification.globalMatch?.let {
                            Log.d(
                                TAG,
                                "Variant classifier(${it.scope}): species=${it.species}, sprite=${it.spriteKey}, type=${it.variantType}, shiny=${it.isShiny}, costume=${it.isCostumeLike}, score=${it.score}, confidence=${it.confidence}, top=${it.topSpecies}"
                            )
                        }
                        classification.speciesMatch?.let {
                            Log.d(
                                TAG,
                                "Variant classifier(${it.scope}): species=${it.species}, sprite=${it.spriteKey}, type=${it.variantType}, shiny=${it.isShiny}, costume=${it.isCostumeLike}, score=${it.score}, confidence=${it.confidence}, top=${it.topSpecies}"
                            )
                        }
                        val resolvedVariantMatch = classification.resolvedMatch
                        resolvedVariantMatch?.let {
                            if (it !== classification.speciesMatch) {
                                Log.d(
                                    TAG,
                                    "Variant classifier rescue(${it.scope}): species=${it.species}, sprite=${it.spriteKey}, type=${it.variantType}, shiny=${it.isShiny}, costume=${it.isCostumeLike}, score=${it.score}, confidence=${it.confidence}"
                                )
                            }
                        }
                        val tracedBase = classification.pokemon
                        val visualFeatures = visualDeferred.await()
                        val visualElapsed = System.currentTimeMillis() - visualStart
                        pipelineTimings += StageTimingDiagnostic("visual_detector", visualElapsed)
                        val ocrLucky = tracedBase.rawOcrText.split("|")
                            .find { it.startsWith("LuckyDetected:") }
                            ?.substringAfter(":")
                            ?.equals("true", ignoreCase = true) == true
                        val luckyMergedVisualFeatures = if (ocrLucky && !visualFeatures.isLucky) {
                            Log.d(TAG, "Lucky override applied from OCR label")
                            visualFeatures.copy(
                                isLucky = true,
                                hasLocationCard = false,
                                confidence = maxOf(visualFeatures.confidence, 0.75f)
                            )
                        } else {
                            visualFeatures
                        }
                        val mergedVisualFeatures = variantDecisionEngine.mergeVisualFeatures(
                            luckyMergedVisualFeatures,
                            classification.fullMatch,
                            resolvedVariantMatch ?: classification.globalMatch
                        )

                    // 5. Calculate rarity
                    val baseRarity = repository.getPokemonBaseRarity(tracedBase.realName ?: tracedBase.name ?: "Unknown")

                    // Matematiksel CP Dogrulama / Fallback
                    var finalResult = tracedBase
                    val fixedCP = rarityCalculator.validateAndFixCP(tracedBase, allOcrCPs, mergedVisualFeatures)

                    if (fixedCP != null && fixedCP > 0) {
                        if (tracedBase.cp == null || tracedBase.cp == 0) {
                            Log.i(TAG, "CP was missing, using mathematical estimate: $fixedCP")
                            finalResult = tracedBase.copy(cp = fixedCP)
                        } else if (fixedCP != tracedBase.cp) {
                            Log.i(TAG, "CP OCR was likely wrong (${tracedBase.cp}), fixing to: $fixedCP")
                            finalResult = tracedBase.copy(cp = fixedCP)
                        }
                    }

                    val phase2Result = try {
                        val phase2Start = System.currentTimeMillis()
                        val phase2Species = finalResult.realName ?: finalResult.name
                        val result = if (bestBitmap != null && !phase2Species.isNullOrBlank()) {
                            phase2VariantClassifier.classify(bestBitmap, phase2Species)
                        } else {
                            null
                        }
                        pipelineTimings += StageTimingDiagnostic("phase2_variant_classifier", System.currentTimeMillis() - phase2Start)
                        result
                    } catch (e: Exception) {
                        Log.w(TAG, "Phase 2 variant classifier failed", e)
                        null
                    }
                    phase2Result?.let { result ->
                        Log.d(
                            TAG,
                            "Phase2 variant: species=${result.species} supported=${result.supportedTargets.joinToString(",")} applied=${result.appliedTargets.joinToString(",")}"
                        )
                        result.predictions.forEach { prediction ->
                            Log.d(
                                TAG,
                                "Phase2 target=${prediction.target} predicted=${prediction.predictedValue} confidence=${prediction.confidence} margin=${prediction.margin} passed=${prediction.passedThreshold}"
                            )
                        }
                    }
                    val scoringVisualFeatures = Phase2VariantFeatureMerger.merge(mergedVisualFeatures, phase2Result)
                    val variantSummary = VariantVisualSummary.from(scoringVisualFeatures, finalResult.variantDecisionTrace)
                    val scanDecision = scanConfidenceGate.evaluate(
                        ScanConfidenceInput(
                            pokemon = finalResult,
                            frames = reportFrames,
                            consistencyReason = consistencyDecision.reason,
                            consistencyRequestedRetry = false,
                            cpCropQuality = bestCpQuality,
                            visualSummary = variantSummary
                        )
                    )
                    finalResult = finalResult.copy(scanDecision = scanDecision)
                    if (!scanDecision.mayShowOverlay || !scanDecision.maySaveScan) {
                        Log.w(
                            TAG,
                            "Scan confidence gate blocked result: decision=${scanDecision.decision} score=${scanDecision.confidence} reasons=${scanDecision.developerReasons.joinToString(",")}"
                        )
                        exportRetryDiagnostics(
                            screenshotPath = bestPath,
                            pokemon = finalResult,
                            reason = "${scanDecision.decision}: ${scanDecision.userSafeReason}",
                            frames = reportFrames,
                            scanDecision = scanDecision,
                            variantSummary = variantSummary,
                            stageTimings = pipelineTimings + StageTimingDiagnostic("total", System.currentTimeMillis() - pipelineStart)
                        )
                        val error = if (scanDecision.decision == ScanDecisionType.REJECT_NOT_POKEMON_SCREEN) {
                            ScanError.NOT_POKEMON_SCREEN
                        } else {
                            ScanError.LOW_CONFIDENCE_RESULT
                        }
                        handleError(ScanResult.Failure(error))
                        return@withLock
                    }

                    val eventWeight = repository.resolveEventBonus(finalResult, scoringVisualFeatures)
                    val liveEventContext = repository.resolveLiveEventContext(finalResult, scoringVisualFeatures)
                    val solverStart = System.currentTimeMillis()
                    val rarityScore = rarityCalculator.calculate(
                        finalResult,
                        scoringVisualFeatures,
                        baseRarity,
                        eventWeight,
                        liveEventContext
                    )
                    val solverElapsed = System.currentTimeMillis() - solverStart
                    val pipelineElapsed = System.currentTimeMillis() - pipelineStart
                    pipelineTimings += StageTimingDiagnostic("rarity_scoring", solverElapsed)
                    pipelineTimings += StageTimingDiagnostic("total", pipelineElapsed)
                    val decisionSummary = PipelineDecisionSummary.build(
                        pokemon = finalResult,
                        features = scoringVisualFeatures,
                        rarityScore = rarityScore,
                        phase2Result = phase2Result,
                        screenshotPath = bestPath,
                        pipelineMs = pipelineElapsed
                    )
                    Log.d(TAG, decisionSummary.toLogLine())
                    Log.d(
                        TAG,
                        "Stage timing: classifier=${classifierElapsed}ms visual=${visualElapsed}ms rarity=${solverElapsed}ms"
                    )
                    retryCount = 0

                    val displayDate = finalResult.caughtDate?.let { formatDate(it, DateParseUtils.MMM_DD_YYYY_FORMATTER) } ?: "Unknown"
                    val telemetryUploadId = telemetryCoordinator.newUploadIdOrNull()
                    val diagnosticId = telemetryUploadId ?: "local-${System.currentTimeMillis()}"
                    val overlayIntent = Intent(context, OverlayService::class.java).apply {
                        action = OverlayService.ACTION_SHOW_RESULT
                        putExtra(ResultActivity.EXTRA_POKEMON_NAME, finalResult.name ?: "Unknown")
                        putExtra(ResultActivity.EXTRA_CP, finalResult.cp ?: 0)
                        putExtra(ResultActivity.EXTRA_HP, finalResult.hp ?: 0)
                        putExtra(ResultActivity.EXTRA_SCORE, rarityScore.totalScore)
                        putExtra(ResultActivity.EXTRA_TIER, rarityScore.tier.name)
                        putExtra(ResultActivity.EXTRA_IS_SHINY, scoringVisualFeatures.isShiny)
                        putExtra(ResultActivity.EXTRA_IS_SHADOW, scoringVisualFeatures.isShadow)
                        putExtra(ResultActivity.EXTRA_IS_LUCKY, scoringVisualFeatures.isLucky)
                        putExtra(ResultActivity.EXTRA_HAS_COSTUME, scoringVisualFeatures.hasCostume)
                        putExtra(ResultActivity.EXTRA_HAS_SPECIAL_FORM, scoringVisualFeatures.hasSpecialForm)
                        putStringArrayListExtra(ResultActivity.EXTRA_EXPLANATIONS, ArrayList(rarityScore.explanation))
                        putStringArrayListExtra(ResultActivity.EXTRA_BREAKDOWN_KEYS, ArrayList(rarityScore.breakdown.keys.toList()))
                        putIntegerArrayListExtra(ResultActivity.EXTRA_BREAKDOWN_VALUES, ArrayList(rarityScore.breakdown.values.toList()))
                        putExtra(ResultActivity.EXTRA_DATE, displayDate)
                        putExtra(ResultActivity.EXTRA_TELEMETRY_UPLOAD_ID, telemetryUploadId)
                        rarityScore.decisionSupport?.let { support ->
                            putExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_CODE, support.eventConfidenceCode)
                            putExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_LABEL, support.eventConfidenceLabel)
                            putExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_DETAIL, support.eventConfidenceDetail)
                            putExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_SCORE, support.scanConfidenceScore)
                            putExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_LABEL, support.scanConfidenceLabel)
                            putExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_DETAIL, support.scanConfidenceDetail)
                            putExtra(ResultActivity.EXTRA_MISMATCH_GUARD_TITLE, support.mismatchGuardTitle)
                            putExtra(ResultActivity.EXTRA_MISMATCH_GUARD_DETAIL, support.mismatchGuardDetail)
                            putExtra(ResultActivity.EXTRA_RECOGNITION_SUMMARY, support.recognitionSummary ?: support.whyNotExact)
                        }
                    }

                    // 5. Show result first so UI is not blocked by disk writes
                    launch(Dispatchers.Main) {
                        context.startService(overlayIntent)
                    }

                    finalResult = attachRecognitionDiagnostics(
                        pokemon = finalResult,
                        rarityScore = rarityScore,
                        screenshotPath = bestPath,
                        diagnosticId = diagnosticId,
                        frames = reportFrames,
                        fallbackReason = fallbackReason,
                        variantSummary = variantSummary,
                        stageTimings = pipelineTimings
                    )

                    // 6. Save in background after result is already visible
                    launch {
                        repository.saveScan(finalResult, scoringVisualFeatures, rarityScore)
                    }
                    telemetryCoordinator.enqueueAndFlush(
                        uploadId = telemetryUploadId,
                        pokemonData = finalResult,
                        features = scoringVisualFeatures,
                        rarityScore = rarityScore,
                        screenshotPath = null,
                        pipelineMs = pipelineElapsed,
                        phase2Result = phase2Result
                    )
                    Log.d(TAG, "processScanSequence: overlay dispatched in ${pipelineElapsed}ms")

                    cleanOldScreenshots()
                    } finally {
                        bestBitmap?.let { decodeBitmapPool.release(it) }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Pipeline error", e)
                    handleError(ScanResult.Failure(ScanError.UNKNOWN, e))
                }
            }
        }
    }

    // ── Error handling ───────────────────────────────────────────────────

    private fun handleError(failure: ScanResult.Failure) {
        if (failure.canRetry() && retryCount < ScanError.MAX_RETRIES) {
            retryCount++
            Log.w(TAG, "Retryable error (${failure.error}), attempt $retryCount")
            OverlayStateStore.dispatch(OverlayIntent.ShowError(failure.error.userMessage))
            scope.launch(Dispatchers.Main) {
                Toast.makeText(context, "Retrying scan…", Toast.LENGTH_SHORT).show()
            }
            // Re-trigger capture
            context.sendBroadcast(Intent(OverlayService.ACTION_CAPTURE_REQUESTED).apply {
                setPackage(context.packageName)
            }, ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION)
        } else {
            retryCount = 0
            OverlayStateStore.dispatch(OverlayIntent.ShowError(failure.error.userMessage))
            scope.launch(Dispatchers.Main) {
                Toast.makeText(context, failure.error.userMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Utilities ────────────────────────────────────────────────────────

    private fun attachRecognitionDiagnostics(
        pokemon: PokemonData,
        rarityScore: com.pokerarity.scanner.data.model.RarityScore,
        screenshotPath: String?,
        diagnosticId: String,
        frames: List<FrameDiagnostic>,
        fallbackReason: String?,
        variantSummary: VariantVisualSummary?,
        stageTimings: List<StageTimingDiagnostic>
    ): PokemonData {
        val confidenceReasons = ScanOcrConfidenceReasonFactory.create(pokemon, rarityScore)
        val bestScreenFrame = frames.maxByOrNull { it.screenConfidence ?: 0f }
        val scanReport = ScanDiagnosticReport(
            diagnosticId = diagnosticId,
            screenState = bestScreenFrame?.screenState ?: "Unknown",
            screenConfidence = bestScreenFrame?.screenConfidence,
            stageTimings = stageTimings,
            frames = frames,
            finalPokemon = PokemonSummary.from(pokemon),
            rarityBreakdown = rarityScore.breakdown,
            confidenceReasons = ConfidenceReasonDiagnostic.from(confidenceReasons),
            fallbackReason = fallbackReason,
            resolverTrace = pokemon.speciesResolverTrace,
            variantSummary = variantSummary,
            scanDecision = pokemon.scanDecision
        )
        val shouldDump = pokemon.cp == null || pokemon.caughtDate == null ||
            (pokemon.maxHp == null && pokemon.hp == null) ||
            (rarityScore.decisionSupport?.mismatchGuardTitle != null)
        val diagnosticBundle = if (shouldDump) {
            OcrDiagnosticsExporter.export(
                context = context,
                screenshotPath = screenshotPath,
                diagnosticId = diagnosticId,
                pokemon = pokemon,
                solve = null,
                whyNotExact = rarityScore.recognitionSummary ?: rarityScore.decisionSupport?.recognitionSummary,
                scanReport = scanReport,
                confidenceReasons = confidenceReasons
            )
        } else {
            null
        }
        return ScanRawOcrDiagnostics.attach(pokemon, rarityScore, diagnosticBundle)
    }

    private fun exportRetryDiagnostics(
        screenshotPath: String?,
        pokemon: PokemonData,
        reason: String,
        frames: List<FrameDiagnostic>,
        scanDecision: ScanDecision? = pokemon.scanDecision,
        variantSummary: VariantVisualSummary? = null,
        stageTimings: List<StageTimingDiagnostic> = emptyList()
    ) {
        val diagnosticId = "local-retry-${System.currentTimeMillis()}"
        val bestScreenFrame = frames.maxByOrNull { it.screenConfidence ?: 0f }
        val scanReport = ScanDiagnosticReport(
            diagnosticId = diagnosticId,
            screenState = bestScreenFrame?.screenState ?: "Unknown",
            screenConfidence = bestScreenFrame?.screenConfidence,
            stageTimings = stageTimings,
            frames = frames,
            finalPokemon = PokemonSummary.from(pokemon),
            retryReason = reason,
            resolverTrace = pokemon.speciesResolverTrace,
            variantSummary = variantSummary,
            scanDecision = scanDecision
        )
        OcrDiagnosticsExporter.export(
            context = context,
            screenshotPath = screenshotPath,
            diagnosticId = diagnosticId,
            pokemon = pokemon,
            solve = null,
            whyNotExact = reason,
            scanReport = scanReport
        )
    }

    private fun cleanOldScreenshots() {
        try {
            val cacheDir = context.cacheDir
            val screenshots = cacheDir.listFiles { f -> f.name.startsWith("scan_") && f.name.endsWith(".png") }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            if (screenshots.size > 20) {
                screenshots.drop(20).forEach { it.delete() }
            }
        } catch (_: Exception) {
            Log.e(TAG, "cleanOldScreenshots failed")
        }
    }

    private fun releaseBitmap(bitmap: Bitmap, pooled: Boolean) {
        if (pooled) {
            decodeBitmapPool.release(bitmap)
        } else if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private suspend fun runDetailedPassIfNeeded(path: String): OcrFrameResult? {
        return runCatching {
            val bitmap = BitmapFactory.decodeFile(path) ?: return@runCatching null
            val scaled = if (bitmap.width > 900) {
                Bitmap.createScaledBitmap(bitmap, 900, (bitmap.height * (900f / bitmap.width)).toInt(), true)
            } else {
                bitmap
            }
            try {
                ocrProcessor.processImageWithDiagnostics(
                    bitmap = scaled,
                    includeSecondaryFields = true,
                    frameIndex = -1,
                    frameRole = "detailed_best",
                    estimatedCpCropQuality = null
                )
            } finally {
                if (scaled != bitmap) scaled.recycle()
                bitmap.recycle()
            }
        }.getOrElse {
            Log.e(TAG, "Detailed OCR pass failed", it)
            null
        }
    }

    private fun shouldRunDetailedPass(
        authoritative: com.pokerarity.scanner.data.model.PokemonData,
        cpQuality: Double
    ): Boolean {
        val fields = parseRawOcrFields(authoritative.rawOcrText)
        val topTextConfidence = maxOf(
            textParser.rankNameCandidates(fields["Name"].orEmpty(), limit = 1).firstOrNull()?.score ?: 0.0,
            textParser.rankNameCandidates(fields["NameHC"].orEmpty(), limit = 1).firstOrNull()?.score ?: 0.0
        )
        return ScanFrameFusion.shouldRunDetailedPass(authoritative, cpQuality, topTextConfidence)
    }



    private fun parseRawOcrFields(raw: String): LinkedHashMap<String, String> {
        val result = linkedMapOf<String, String>()
        raw.split("|").forEach { part ->
            val separator = part.indexOf(':')
            if (separator <= 0) return@forEach
            val key = part.substring(0, separator)
            val value = part.substring(separator + 1)
            result[key] = value
        }
        return result
    }

    private fun estimateCpQuality(bitmap: Bitmap): Double {
        val mask = com.pokerarity.scanner.util.ocr.ImagePreprocessor.processWhiteMask(bitmap)
        val rect = com.pokerarity.scanner.util.ocr.ScreenRegions.getRectForRegion(mask, com.pokerarity.scanner.util.ocr.ScreenRegions.REGION_CP)
        val safeLeft = rect.left.coerceIn(0, mask.width - 1)
        val safeTop = rect.top.coerceIn(0, mask.height - 1)
        val safeWidth = rect.width().coerceAtMost(mask.width - safeLeft)
        val safeHeight = rect.height().coerceAtMost(mask.height - safeTop)
        if (safeWidth <= 0 || safeHeight <= 0) {
            if (!mask.isRecycled) mask.recycle()
            return 0.0
        }
        val cropped = Bitmap.createBitmap(mask, safeLeft, safeTop, safeWidth, safeHeight)
        if (cropped != mask && !mask.isRecycled) mask.recycle()

        val w = cropped.width
        val h = cropped.height
        val pixels = IntArray(w * h)
        cropped.getPixels(pixels, 0, w, 0, 0, w, h)
        if (!cropped.isRecycled) cropped.recycle()

        var blackCount = 0
        var rowsWithBlack = 0
        for (y in 0 until h) {
            var rowHasBlack = false
            val rowStart = y * w
            for (x in 0 until w) {
                val p = pixels[rowStart + x]
                if ((p and 0x00FFFFFF) == 0x000000) {
                    blackCount++
                    rowHasBlack = true
                }
            }
            if (rowHasBlack) rowsWithBlack++
        }

        val total = w * h
        if (total <= 0) return 0.0
        val blackRatio = blackCount.toDouble() / total.toDouble()
        val rowCoverage = rowsWithBlack.toDouble() / h.toDouble()

        val ratioScore = when {
            blackRatio < 0.005 -> 0.0
            blackRatio < 0.015 -> 0.5
            blackRatio <= 0.20 -> 1.0
            blackRatio <= 0.30 -> 0.5
            else -> 0.0
        }
        val rowScore = when {
            rowCoverage < 0.15 -> 0.0
            rowCoverage < 0.35 -> 0.5
            rowCoverage <= 0.85 -> 1.0
            else -> 0.5
        }

        return (ratioScore * 0.6) + (rowScore * 0.4)
    }

}
