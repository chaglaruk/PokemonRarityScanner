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
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.ui.result.ResultActivity
import com.pokerarity.scanner.util.ScanError
import com.pokerarity.scanner.util.ScanResult
import com.pokerarity.scanner.util.ocr.OCRProcessor
import com.pokerarity.scanner.util.vision.VisualFeatureDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var retryCount = 0

    private val ocrProcessor by lazy { OCRProcessor(context) }
    private val visualDetector by lazy { VisualFeatureDetector(context) }
    private val repository by lazy { PokemonRepository(AppDatabase.getInstance(context)) }
    private val rarityCalculator by lazy { RarityCalculator(context) }

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
            try {
                // 1. Run OCR on all frames in parallel
                val results = paths.map { path ->
                    async(Dispatchers.Default) {
                        val bitmap = BitmapFactory.decodeFile(path) ?: return@async null
                        try {
                            // Hızlandırma: Bitmap'i küçült (OCR ve analiz için yeterli)
                            val scaled = if (bitmap.width > 1080) {
                                Bitmap.createScaledBitmap(bitmap, 1080, (bitmap.height * (1080f / bitmap.width)).toInt(), true)
                            } else bitmap
                            
                            val data = ocrProcessor.processImage(scaled)
                            
                            // Bellek temizliği
                            if (scaled != bitmap) scaled.recycle()
                            bitmap.recycle()
                            
                            data
                        } catch (e: Exception) {
                            Log.e(TAG, "Frame processing failed: $path", e)
                            null
                        }
                    }
                }.mapNotNull { it.await() }

                if (results.isEmpty()) {
                    handleError(ScanResult.Failure(ScanError.OCR_FAILED))
                    return@launch
                }

                // 2. Aggregate all seen CP candidates across frames for better fallback
                val allOcrCPs = results.mapNotNull { it.cp }.distinct()

                // 3. Score and pick the best result
                // Quality Score: CP (+100), Name (+30), HP (+20), Arc (+20), Date (+10)
                val bestResult = results.maxByOrNull { data ->
                    var score = 0
                    val cpVal = data.cp ?: 0
                    if (cpVal >= 100) score += 100 
                    else if (cpVal > 0) score += 50
                    
                    if (data.name != "Unknown") score += 30
                    if (data.hp != null) score += 20
                    if (data.arcLevel != null) score += 20
                    if (data.caughtDate != null) score += 10
                    score
                }!!

                Log.d(TAG, "Best frame selected: CP=${bestResult.cp}, Name=${bestResult.name}, HP=${bestResult.hp}, Arc=${bestResult.arcLevel}")

                // 4. Visual Detection on the best frame
                val bestPath = paths[results.indexOf(bestResult)]
                val bestBitmap = BitmapFactory.decodeFile(bestPath)
                
                // OCR'dan gelen boyut etiketini çek (XL, XS, XXL, XXS)
                val sizeTag = bestResult.rawOcrText?.split("|")?.find { it.startsWith("SizeTag:") }?.substringAfter(":")
                
                val visualFeatures = try {
                    visualDetector.detect(bestBitmap, bestResult.name, sizeTag)
                } catch (e: Exception) {
                    Log.e(TAG, "Visual detection failed", e)
                    com.pokerarity.scanner.data.model.VisualFeatures()
                }

                // 5. Calculate rarity
                val baseRarity = repository.getPokemonBaseRarity(bestResult.realName ?: bestResult.name ?: "Unknown")
                val eventWeight = repository.getEventWeight(bestResult.realName ?: bestResult.name ?: "Unknown", bestResult.caughtDate)
                
                // Matematiksel CP Dogrulama / Fallback
                var finalResult = bestResult
                val fixedCP = rarityCalculator.validateAndFixCP(bestResult, allOcrCPs, visualFeatures)
                
                if (fixedCP != null && fixedCP > 0) {
                    if (bestResult.cp == null || bestResult.cp == 0) {
                        Log.i(TAG, "CP was missing, using mathematical estimate: $fixedCP")
                        finalResult = bestResult.copy(cp = fixedCP)
                    } else if (fixedCP != bestResult.cp) {
                        Log.i(TAG, "CP OCR was likely wrong (${bestResult.cp}), fixing to: $fixedCP")
                        finalResult = bestResult.copy(cp = fixedCP)
                    }
                }

                val rarityScore = rarityCalculator.calculate(finalResult, visualFeatures, baseRarity, eventWeight)

                // 5. Save
                repository.saveScan(finalResult, visualFeatures, rarityScore)

                bestBitmap?.recycle()
                retryCount = 0

                // 6. Launch ResultActivity
                launch(Dispatchers.Main) {
                    val intent = Intent(context, ResultActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(ResultActivity.EXTRA_POKEMON_NAME, finalResult.name ?: "Unknown")
                        putExtra(ResultActivity.EXTRA_CP, finalResult.cp ?: 0)
                        putExtra(ResultActivity.EXTRA_HP, finalResult.hp ?: 0)
                        putExtra(ResultActivity.EXTRA_SCORE, rarityScore.totalScore)
                        putExtra(ResultActivity.EXTRA_TIER, rarityScore.tier.name)
                        putExtra(ResultActivity.EXTRA_IV_ESTIMATE, rarityScore.ivEstimate ?: "???")
                        
                        val rawDebugText = """
                            SPECIES: ${finalResult.realName ?: finalResult.name}
                            CP_RAW: '${finalResult.cp}'
                            HP_RAW: '${finalResult.hp}'
                            STARDUST_RAW: '${finalResult.stardust}'
                            ARC_LEVEL: ${String.format("%.3f", finalResult.arcLevel ?: 0f)}
                            COSTUME: ${visualFeatures.hasCostume}
                            GENDER: ${finalResult.gender}
                            OCR_TEXT: ${finalResult.rawOcrText}
                        """.trimIndent()
                        putExtra(ResultActivity.EXTRA_RAW_DEBUG, rawDebugText)
                        
                        putExtra(ResultActivity.EXTRA_IS_SHINY, visualFeatures.isShiny)
                        putExtra(ResultActivity.EXTRA_IS_SHADOW, visualFeatures.isShadow)
                        putExtra(ResultActivity.EXTRA_IS_LUCKY, visualFeatures.isLucky)
                        putExtra(ResultActivity.EXTRA_HAS_COSTUME, visualFeatures.hasCostume)
                        putStringArrayListExtra(ResultActivity.EXTRA_EXPLANATIONS, ArrayList(rarityScore.explanation))
                        putStringArrayListExtra(ResultActivity.EXTRA_BREAKDOWN_KEYS, ArrayList(rarityScore.breakdown.keys.toList()))
                        putIntegerArrayListExtra(ResultActivity.EXTRA_BREAKDOWN_VALUES, ArrayList(rarityScore.breakdown.values.toList()))
                        bestResult.caughtDate?.let { date ->
                            putExtra(ResultActivity.EXTRA_DATE, SimpleDateFormat("MMM dd, yyyy", Locale.US).format(date))
                        }
                    }
                    context.startActivity(intent)
                }

                cleanOldScreenshots()

            } catch (e: Exception) {
                Log.e(TAG, "Pipeline error", e)
                handleError(ScanResult.Failure(ScanError.UNKNOWN, e))
            }
        }
    }

    // ── Error handling ───────────────────────────────────────────────────

    private fun handleError(failure: ScanResult.Failure) {
        if (failure.canRetry() && retryCount < 3) { // Use fixed retry count for now
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
}
