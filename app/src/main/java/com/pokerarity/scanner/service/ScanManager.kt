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
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.normalizeIvText
import com.pokerarity.scanner.data.repository.PokemonFamilyRegistry
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator
import com.pokerarity.scanner.ui.result.ResultActivity
import com.pokerarity.scanner.util.ScanError
import com.pokerarity.scanner.util.ScanResult
import com.pokerarity.scanner.util.ocr.OCRProcessor
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import com.pokerarity.scanner.util.ocr.SpeciesRefiner
import com.pokerarity.scanner.util.ocr.TextParser
import com.pokerarity.scanner.util.vision.VariantDecisionEngine
import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Orchestrates the full scan pipeline:
 *   Screenshot → OCR → Visual Detection → Rarity Calculation → Save → Show Result
 *
 * Register with [start] from an Activity / Application and unregister with [stop].
 */
class ScanManager(private val context: Context) {

    companion object {
        private const val TAG = "ScanManager"
        private const val CP_QUALITY_MIN = 0.55
        private const val CLASSIFIER_SPECIES_CONFIDENCE = 0.72f
        private const val CLASSIFIER_SPECIES_CONFIDENCE_FAMILY = 0.62f
        private const val CLASSIFIER_VARIANT_CONFIDENCE = 0.66f
        private const val CLASSIFIER_VARIANT_CONFIDENCE_SPECIES = 0.52f
        private const val CLASSIFIER_FORM_CONFIDENCE_SPECIES = 0.34f
        private const val CLASSIFIER_VARIANT_CONSENSUS_MARGIN = 0.03f

        internal fun shouldRunDetailedPassForAuthoritative(
            pokemon: PokemonData,
            cpQuality: Double,
            topTextConfidence: Double
        ): Boolean {
            if (pokemon.cp == null || pokemon.cp <= 0) return true
            if (isUnknownSpeciesStatic(pokemon.name)) return true
            if (pokemon.caughtDate == null) return true
            if (cpQuality < CP_QUALITY_MIN) return true
            if (topTextConfidence < 0.78) return true
            return false
        }

        private fun isUnknownSpeciesStatic(value: String?): Boolean {
            return value.isNullOrBlank() || value.equals("Unknown", ignoreCase = true)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var retryCount = 0
    private val scanMutex = Mutex()

    private val ocrProcessor by lazy { OCRProcessor(context) }
    private val textParser by lazy { TextParser(context) }
    private val visualDetector by lazy { VisualFeatureDetector(context) }
    private val variantDecisionEngine by lazy { VariantDecisionEngine(context) }
    private val repository by lazy { PokemonRepository(AppDatabase.getInstance(context)) }
    private val rarityCalculator by lazy { RarityCalculator(context) }
    private val speciesRefiner by lazy { SpeciesRefiner(context, rarityCalculator) }
    private val consistencyGate by lazy { ScanConsistencyGate(context, rarityCalculator) }
    private val telemetryCoordinator by lazy { ScanTelemetryCoordinator.getInstance(context) }
    private val mainDateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.US)

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
            Log.d(TAG, "onReceive: paths size=${paths.size}")
            processScanSequence(paths)
        }
    }

    // ── Public API ───────────────────────────────────────────────────────

    fun start() {
        val filter = IntentFilter(ScreenCaptureService.ACTION_SCREENSHOT_READY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(screenshotReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(screenshotReceiver, filter)
        }
        Log.d(TAG, "ScanManager started, receiver registered for ${ScreenCaptureService.ACTION_SCREENSHOT_READY}")
    }

    fun stop() {
        try { context.unregisterReceiver(screenshotReceiver) } catch (_: Exception) { }
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
                try {
                    // 1. Parallel bitmap decode and preprocessing (these are CPU-bound)
                    // Tesseract OCR will happen sequentially after because it's not thread-safe
                    val decodeStart = System.currentTimeMillis()
                    val frameJobs = paths.map { path ->
                        async(Dispatchers.Default) {
                            val bitmap = BitmapFactory.decodeFile(path) ?: return@async null
                            try {
                                val scaled = if (bitmap.width > 900) {
                                    Bitmap.createScaledBitmap(bitmap, 900, (bitmap.height * (900f / bitmap.width)).toInt(), true)
                                } else bitmap
                                val cpQuality = estimateCpQuality(scaled)
                                if (scaled != bitmap) bitmap.recycle()
                                Triple(path, scaled, cpQuality)
                            } catch (e: Exception) {
                                bitmap.recycle()
                                null
                            }
                        }
                    }
                    
                    val decodedFrames = frameJobs.awaitAll().filterNotNull()
                    val decodeTime = System.currentTimeMillis() - decodeStart
                    Log.d(TAG, "Parallel decode + preprocess: ${decodedFrames.size} frames in ${decodeTime}ms (avg ${if (decodedFrames.isNotEmpty()) decodeTime / decodedFrames.size else 0}ms/frame)")

                    // 2. Run OCR sequentially (Tesseract is not thread-safe)
                    val ocrStart = System.currentTimeMillis()
                    val results = mutableListOf<FrameResult>()
                    
                    for ((path, scaled, cpQuality) in decodedFrames) {
                        try {
                            val data = ocrProcessor.processImage(scaled, includeSecondaryFields = false)
                            scaled.recycle()
                            
                            results.add(FrameResult(path, data, cpQuality))
                            if (isHighConfidence(data, cpQuality)) {
                                Log.d(TAG, "Early exit: high-confidence OCR frame found after ${results.size} frames")
                                break
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Frame OCR failed: $path", e)
                            scaled.recycle()
                        }
                    }
                    
                    val ocrTime = System.currentTimeMillis() - ocrStart
                    Log.d(TAG, "Sequential OCR: ${results.size} frames in ${ocrTime}ms (avg ${if (results.isNotEmpty()) ocrTime / results.size else 0}ms/frame)")

                    if (results.isEmpty()) {
                        handleError(ScanResult.Failure(ScanError.OCR_FAILED))
                        return@withLock
                    }

                    // 2. Aggregate all seen CP candidates across frames for better fallback
                    val allOcrCPs = results
                        .filter { it.cpQuality >= CP_QUALITY_MIN }
                        .mapNotNull { it.data.cp }

                    // 3. Score and pick the best result
                    // Quality Score: CP (+100), Name (+30), HP (+20), Arc (+20), Date (+10)
                    val bestEntry = results.maxByOrNull { frame ->
                        scoreFor(frame.data) + (frame.cpQuality * 20.0).toInt()
                    }!!

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
                    val detailedBestResult = if (shouldRunDetailedPass) {
                        runDetailedPassIfNeeded(bestEntry.path, bestResult)
                    } else {
                        bestResult
                    }

                    // 3.1 Multi-frame fusion for stability.
                    // The fast pass remains authoritative for primary fields. The detailed
                    // pass only backfills secondary fields and richer raw OCR traces.
                    val fused = fuseResults(results, bestResult, detailedBestResult, allOcrCPs, bestCpQuality)
                    val refined = speciesRefiner.refine(fused)
                    val consistencyDecision = consistencyGate.evaluate(fused, refined)
                    if (consistencyDecision.shouldRetry) {
                        Log.w(TAG, "Consistency gate requested retry: ${consistencyDecision.reason}")
                        handleError(ScanResult.Failure(ScanError.LOW_CONFIDENCE_RESULT))
                        return@withLock
                    }
                    if (consistencyDecision.reason != "accepted") {
                        Log.i(TAG, "Consistency gate applied: ${consistencyDecision.reason}")
                    }
                    val finalBase = consistencyDecision.pokemon

                    // 4. Visual Detection on the best frame
                    val bestPath = bestEntry.path
                    val bestBitmap = BitmapFactory.decodeFile(bestPath)
                    if (bestBitmap == null) {
                        Log.e(TAG, "Best frame decode failed: $bestPath")
                    }

                    // OCR'dan gelen boyut etiketini çek (XL, XS, XXL, XXS)
                    val classification = try {
                        if (bestBitmap != null) {
                            variantDecisionEngine.classify(bestBitmap, finalBase)
                        } else {
                            VariantDecisionEngine.ClassificationResult(finalBase, null, null, null, null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Variant classifier failed", e)
                        VariantDecisionEngine.ClassificationResult(finalBase, null, null, null, null)
                    }
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
                    val sizeTag = tracedBase.rawOcrText.split("|").find { it.startsWith("SizeTag:") }?.substringAfter(":")

                    val visualFeatures = try {
                        if (bestBitmap != null) {
                            visualDetector.detect(bestBitmap, tracedBase.name, sizeTag)
                        } else {
                            com.pokerarity.scanner.data.model.VisualFeatures()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Visual detection failed", e)
                        com.pokerarity.scanner.data.model.VisualFeatures()
                    }
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

                    val eventWeight = repository.resolveEventBonus(finalResult, mergedVisualFeatures)
                    val rarityScore = rarityCalculator.calculate(finalResult, mergedVisualFeatures, baseRarity, eventWeight)

                    bestBitmap?.recycle()
                    retryCount = 0

                    val displayDate = finalResult.caughtDate?.let { mainDateFormatter.format(it) } ?: "Unknown"
                    val telemetryUploadId = telemetryCoordinator.newUploadIdOrNull()
                    val overlayIntent = Intent(context, OverlayService::class.java).apply {
                        action = OverlayService.ACTION_SHOW_RESULT
                        putExtra(ResultActivity.EXTRA_POKEMON_NAME, finalResult.name ?: "Unknown")
                        putExtra(ResultActivity.EXTRA_CP, finalResult.cp ?: 0)
                        putExtra(ResultActivity.EXTRA_HP, finalResult.hp ?: 0)
                        putExtra(ResultActivity.EXTRA_SCORE, rarityScore.totalScore)
                        putExtra(ResultActivity.EXTRA_TIER, rarityScore.tier.name)
                        putExtra(
                            ResultActivity.EXTRA_IV_ESTIMATE,
                            normalizeIvText(rarityScore.ivEstimate) ?: "Hesaplanamadı"
                        )
                        putExtra(ResultActivity.EXTRA_HAS_ARC, finalResult.arcLevel != null)
                        putExtra(ResultActivity.EXTRA_IS_SHINY, mergedVisualFeatures.isShiny)
                        putExtra(ResultActivity.EXTRA_IS_SHADOW, mergedVisualFeatures.isShadow)
                        putExtra(ResultActivity.EXTRA_IS_LUCKY, mergedVisualFeatures.isLucky)
                        putExtra(ResultActivity.EXTRA_HAS_COSTUME, mergedVisualFeatures.hasCostume)
                        putExtra(ResultActivity.EXTRA_HAS_SPECIAL_FORM, mergedVisualFeatures.hasSpecialForm)
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
                            putExtra(ResultActivity.EXTRA_WHY_NOT_EXACT, support.whyNotExact)
                        }
                    }

                    // 5. Show result first so UI is not blocked by disk writes
                    launch(Dispatchers.Main) {
                        context.startService(overlayIntent)
                    }

                    // 6. Save in background after result is already visible
                    launch {
                        repository.saveScan(finalResult, mergedVisualFeatures, rarityScore)
                    }
                    val pipelineElapsed = System.currentTimeMillis() - pipelineStart
                    telemetryCoordinator.enqueueAndFlush(
                        uploadId = telemetryUploadId,
                        pokemonData = finalResult,
                        features = mergedVisualFeatures,
                        rarityScore = rarityScore,
                        screenshotPath = bestPath,
                        pipelineMs = pipelineElapsed
                    )
                    Log.d(TAG, "processScanSequence: overlay dispatched in ${pipelineElapsed}ms")

                    cleanOldScreenshots()

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
            scope.launch(Dispatchers.Main) {
                Toast.makeText(context, "Retrying scan…", Toast.LENGTH_SHORT).show()
            }
            // Re-trigger capture
            val overlayAction = "com.pokerarity.scanner.ACTION_CAPTURE_REQUESTED"
            context.sendBroadcast(Intent(overlayAction).apply {
                setPackage(context.packageName)
            })
        } else {
            retryCount = 0
            scope.launch(Dispatchers.Main) {
                Toast.makeText(context, failure.error.userMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Utilities ────────────────────────────────────────────────────────

    private fun cleanOldScreenshots() {
        try {
            val cacheDir = context.cacheDir
            val screenshots = cacheDir.listFiles { f -> f.name.startsWith("scan_") && f.name.endsWith(".png") }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            if (screenshots.size > 20) {
                screenshots.drop(20).forEach { it.delete() }
            }
        } catch (_: Exception) { }
    }

    private fun scoreFor(data: com.pokerarity.scanner.data.model.PokemonData): Int {
        var score = 0
        val cpVal = data.cp ?: 0
        if (cpVal >= 100) score += 100
        else if (cpVal > 0) score += 50

        if (data.name != "Unknown") score += 30
        if (data.hp != null) score += 20
        if (data.arcLevel != null) score += 20
        if (data.caughtDate != null) score += 10
        return score
    }

    private fun isHighConfidence(data: com.pokerarity.scanner.data.model.PokemonData, cpQuality: Double): Boolean {
        val cpVal = data.cp ?: 0
        val hasSupportSignal = data.hp != null || data.arcLevel != null || data.caughtDate != null
        return cpVal >= 100 && data.name != "Unknown" && cpQuality >= CP_QUALITY_MIN && hasSupportSignal
    }

    private data class FrameResult(
        val path: String,
        val data: com.pokerarity.scanner.data.model.PokemonData,
        val cpQuality: Double
    )

    private fun fuseResults(
        frames: List<FrameResult>,
        authoritative: com.pokerarity.scanner.data.model.PokemonData,
        detailed: com.pokerarity.scanner.data.model.PokemonData,
        validCpList: List<Int>,
        bestCpQuality: Double
    ): com.pokerarity.scanner.data.model.PokemonData {
        fun <T> mostFrequent(values: List<T?>): T? {
            val counts = values.filterNotNull().groupingBy { it }.eachCount()
            return counts.entries.maxByOrNull { it.value }?.key
        }

        val hpPair = mostFrequent(frames.map {
            val hp = it.data.hp
            val maxHp = it.data.maxHp
            if (hp == null && maxHp == null) null else (hp to maxHp)
        })
        val stardust = mostFrequent(frames.map { it.data.stardust })
        val caughtDate = mostFrequent(frames.map { it.data.caughtDate })
        val arcValues = frames.mapNotNull { it.data.arcLevel }.sorted()
        val arcLevel = if (arcValues.isNotEmpty()) {
            arcValues[arcValues.size / 2]
        } else null
        val consensusName = mostFrequent(frames.map { it.data.name }.map { it.takeUnless(::isUnknownSpecies) })
        val consensusRealName = mostFrequent(frames.map { it.data.realName }.map { it.takeUnless(::isUnknownSpecies) })

        val consensusCp = mostFrequent(
            frames
                .filter { it.cpQuality >= CP_QUALITY_MIN }
                .map { it.data.cp }
        )
        val keepAuthoritativeCp = authoritative.cp != null &&
            bestCpQuality >= CP_QUALITY_MIN &&
            validCpList.contains(authoritative.cp)
        val cp = when {
            keepAuthoritativeCp -> authoritative.cp
            consensusCp != null -> consensusCp
            detailed.cp != null && validCpList.contains(detailed.cp) -> detailed.cp
            else -> authoritative.cp ?: detailed.cp
        }

        return authoritative.copy(
            cp = cp,
            hp = hpPair?.first ?: authoritative.hp ?: detailed.hp,
            maxHp = hpPair?.second ?: authoritative.maxHp ?: detailed.maxHp,
            stardust = stardust ?: detailed.stardust ?: authoritative.stardust,
            arcLevel = arcLevel ?: authoritative.arcLevel ?: detailed.arcLevel,
            name = authoritative.name.takeUnless(::isUnknownSpecies)
                ?: consensusName
                ?: detailed.name.takeUnless(::isUnknownSpecies)
                ?: authoritative.name,
            realName = authoritative.realName.takeUnless(::isUnknownSpecies)
                ?: consensusRealName
                ?: detailed.realName.takeUnless(::isUnknownSpecies)
                ?: authoritative.realName,
            candyName = detailed.candyName ?: authoritative.candyName,
            megaEnergy = detailed.megaEnergy ?: authoritative.megaEnergy,
            weight = detailed.weight ?: authoritative.weight,
            height = detailed.height ?: authoritative.height,
            gender = authoritative.gender ?: detailed.gender,
            caughtDate = authoritative.caughtDate ?: caughtDate ?: detailed.caughtDate,
            rawOcrText = mergeRawOcrText(authoritative.rawOcrText, detailed.rawOcrText)
        )
    }

    private suspend fun runDetailedPassIfNeeded(
        path: String,
        authoritative: com.pokerarity.scanner.data.model.PokemonData
    ): com.pokerarity.scanner.data.model.PokemonData {
        return runCatching {
            val bitmap = BitmapFactory.decodeFile(path) ?: return@runCatching authoritative
            val scaled = if (bitmap.width > 900) {
                Bitmap.createScaledBitmap(bitmap, 900, (bitmap.height * (900f / bitmap.width)).toInt(), true)
            } else {
                bitmap
            }
            try {
                ocrProcessor.processImage(scaled, includeSecondaryFields = true)
            } finally {
                if (scaled != bitmap) scaled.recycle()
                bitmap.recycle()
            }
        }.getOrElse {
            Log.e(TAG, "Detailed OCR pass failed", it)
            authoritative
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
        return shouldRunDetailedPassForAuthoritative(authoritative, cpQuality, topTextConfidence)
    }

    private fun mergeRawOcrText(primaryRaw: String, detailedRaw: String): String {
        val primaryFields = parseRawOcrFields(primaryRaw)
        val detailedFields = parseRawOcrFields(detailedRaw)
        val primaryPreferredKeys = setOf("CP", "HP", "HPWM", "HPClean", "HPBlock", "Name", "NameHC")
        val orderedKeys = linkedSetOf<String>().apply {
            addAll(primaryFields.keys)
            addAll(detailedFields.keys)
        }

        return orderedKeys.joinToString("|") { key ->
            val primaryValue = primaryFields[key].orEmpty()
            val detailedValue = detailedFields[key].orEmpty()
            val mergedValue = when {
                key in primaryPreferredKeys -> primaryValue.ifBlank { detailedValue }
                detailedValue.isNotBlank() -> detailedValue
                else -> primaryValue
            }
            "$key:$mergedValue"
        }
    }

    private fun buildVariantClassifierHints(pokemon: PokemonData): Set<String> {
        val hints = linkedSetOf<String>()
        pokemon.name?.takeUnless(::isUnknownSpecies)?.let { hints += it }
        pokemon.realName?.takeUnless(::isUnknownSpecies)?.let { hints += it }
        pokemon.candyName?.takeUnless(::isUnknownSpecies)?.let { hints += it }
        pokemon.candyName?.let { hints += PokemonFamilyRegistry.getFamilyMembers(context, it) }
        pokemon.realName?.let { hints += PokemonFamilyRegistry.getFamilyMembers(context, it) }
        pokemon.name?.let { hints += PokemonFamilyRegistry.getFamilyMembers(context, it) }
        return hints.filterNot { it.isBlank() }.toSet()
    }

    private fun applyClassifierSpecies(
        pokemon: PokemonData,
        match: VariantPrototypeClassifier.MatchResult?
    ): PokemonData {
        if (match == null) return pokemon
        val currentSpecies = pokemon.realName ?: pokemon.name
        val sameSpecies = currentSpecies.equals(match.species, ignoreCase = true)
        val inCandyFamily = !pokemon.candyName.isNullOrBlank() &&
            PokemonFamilyRegistry.isSameFamily(context, match.species, pokemon.candyName)
        val shouldOverride = when {
            sameSpecies -> false
            isUnknownSpecies(currentSpecies) -> match.confidence >= CLASSIFIER_SPECIES_CONFIDENCE_FAMILY
            inCandyFamily -> match.confidence >= CLASSIFIER_SPECIES_CONFIDENCE_FAMILY
            else -> match.confidence >= CLASSIFIER_SPECIES_CONFIDENCE
        }
        val augmentedRaw = appendClassifierFields(pokemon.rawOcrText, match)
        if (!shouldOverride) {
            return if (augmentedRaw == pokemon.rawOcrText) pokemon else pokemon.copy(rawOcrText = augmentedRaw)
        }
        Log.d(TAG, "Classifier species override: current=$currentSpecies -> best=${match.species} confidence=${match.confidence}")
        return pokemon.copy(
            name = match.species,
            realName = match.species,
            rawOcrText = augmentedRaw
        )
    }

    private fun mergeClassifierVisuals(
        visualFeatures: com.pokerarity.scanner.data.model.VisualFeatures,
        match: VariantPrototypeClassifier.MatchResult?
    ): com.pokerarity.scanner.data.model.VisualFeatures {
        if (match == null) {
            return visualFeatures
        }
        val requiredConfidence = if (match.scope == "species") {
            CLASSIFIER_VARIANT_CONFIDENCE_SPECIES
        } else {
            CLASSIFIER_VARIANT_CONFIDENCE
        }
        val formConfidenceGate = if (match.scope == "species") {
            CLASSIFIER_FORM_CONFIDENCE_SPECIES
        } else {
            requiredConfidence
        }
        val promoteForm = match.variantType == "form" && match.confidence >= formConfidenceGate
        if (match.confidence < requiredConfidence && !promoteForm) {
            return visualFeatures
        }
        return visualFeatures.copy(
            isShiny = if (match.isShiny) true else visualFeatures.isShiny,
            hasCostume = if (match.isCostumeLike) true else visualFeatures.hasCostume,
            hasSpecialForm = if (promoteForm) true else visualFeatures.hasSpecialForm,
            confidence = maxOf(visualFeatures.confidence, match.confidence)
        )
    }

    private fun resolveVariantClassifierMatch(
        pokemon: PokemonData,
        globalMatch: VariantPrototypeClassifier.MatchResult?,
        speciesMatch: VariantPrototypeClassifier.MatchResult?
    ): VariantPrototypeClassifier.MatchResult? {
        if (speciesMatch == null) return globalMatch
        val sameFamilyGlobalNonBase = globalMatch != null &&
            globalMatch.variantType != "base" &&
            PokemonFamilyRegistry.isSameFamily(context, globalMatch.species, pokemon.realName ?: pokemon.name)
        val exactNonBaseConsensus = globalMatch != null &&
            speciesMatch.variantType != "base" &&
            globalMatch.variantType != "base" &&
            globalMatch.assetKey == speciesMatch.assetKey &&
            globalMatch.isShiny == speciesMatch.isShiny &&
            globalMatch.isCostumeLike == speciesMatch.isCostumeLike &&
            globalMatch.variantType == speciesMatch.variantType
        if (exactNonBaseConsensus) {
            return speciesMatch.copy(
                confidence = maxOf(speciesMatch.confidence, CLASSIFIER_VARIANT_CONFIDENCE_SPECIES)
            )
        }
        val bestBaseScore = speciesMatch.bestBaseScore
        val bestNonBaseScore = speciesMatch.bestNonBaseScore
        val bestNonBaseVariantType = speciesMatch.bestNonBaseVariantType
        val bestNonBaseSpriteKey = speciesMatch.bestNonBaseSpriteKey

        if (
            speciesMatch.variantType == "base" &&
            bestBaseScore != null &&
            bestNonBaseScore != null &&
            bestNonBaseVariantType != null &&
            bestNonBaseVariantType != "base" &&
            sameFamilyGlobalNonBase &&
            (bestNonBaseScore - bestBaseScore) <= CLASSIFIER_VARIANT_CONSENSUS_MARGIN &&
            speciesMatch.confidence <= 0.42f &&
            bestNonBaseSpriteKey != null
        ) {
            val boostedConfidence = if (
                globalMatch != null &&
                globalMatch.variantType == bestNonBaseVariantType &&
                globalMatch.isShiny == speciesMatch.bestNonBaseIsShiny
            ) {
                CLASSIFIER_VARIANT_CONFIDENCE_SPECIES
            } else {
                CLASSIFIER_FORM_CONFIDENCE_SPECIES
            }
            return speciesMatch.copy(
                assetKey = speciesMatch.bestNonBaseAssetKey ?: speciesMatch.assetKey,
                spriteKey = bestNonBaseSpriteKey,
                variantType = bestNonBaseVariantType,
                isShiny = speciesMatch.bestNonBaseIsShiny,
                isCostumeLike = speciesMatch.bestNonBaseIsCostumeLike,
                score = bestNonBaseScore,
                confidence = maxOf(speciesMatch.confidence, boostedConfidence)
            )
        }

        return speciesMatch
    }

    private fun appendClassifierTrace(
        pokemon: PokemonData,
        match: VariantPrototypeClassifier.MatchResult?,
        prefix: String
    ): PokemonData {
        if (match == null) return pokemon
        val augmentedRaw = appendClassifierFields(pokemon.rawOcrText, match, prefix)
        return if (augmentedRaw == pokemon.rawOcrText) pokemon else pokemon.copy(rawOcrText = augmentedRaw)
    }

    private fun appendClassifierFields(
        raw: String,
        match: VariantPrototypeClassifier.MatchResult,
        prefix: String = "Classifier"
    ): String {
        val fields = parseRawOcrFields(raw)
        fields["${prefix}Scope"] = match.scope
        fields["${prefix}Species"] = match.species
        fields["${prefix}SpriteKey"] = match.spriteKey
        fields["${prefix}VariantType"] = match.variantType
        fields["${prefix}Shiny"] = match.isShiny.toString()
        fields["${prefix}Costume"] = match.isCostumeLike.toString()
        fields["${prefix}Confidence"] = "%.3f".format(Locale.US, match.confidence)
        return fields.entries.joinToString("|") { "${it.key}:${it.value}" }
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

    private fun isUnknownSpecies(value: String?): Boolean {
        return value.isNullOrBlank() || value.equals("Unknown", ignoreCase = true)
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
