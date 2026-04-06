package com.pokerarity.scanner.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.local.db.TelemetryUploadEntity
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.ScanFeedbackPayload
import com.pokerarity.scanner.data.model.ScanTelemetryPayload
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.remote.ScanTelemetryUploader
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import java.io.File
import java.util.Date
import java.util.UUID

class ScanTelemetryRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val gson: Gson = Gson(),
    private val uploader: ScanTelemetryUploader = ScanTelemetryUploader(context)
) {
    private val dao = database.telemetryUploadDao()

    fun isEnabled(): Boolean = uploader.isEnabled()

    fun newUploadId(): String = UUID.randomUUID().toString()

    suspend fun enqueueScan(
        uploadId: String,
        pokemonData: PokemonData,
        features: VisualFeatures,
        rarityScore: RarityScore,
        screenshotPath: String?,
        pipelineMs: Long?,
        phase2Result: Phase2VariantClassifier.Result? = null
    ): Long? {
        if (!uploader.isEnabled()) return null
        
        // 🔴 SECURITY FIX: Do NOT copy or upload screenshots
        // Reason: Full device screenshots contain PII from all apps
        // Instead, telemetry contains only derived stats (CP, HP, species, etc)
        val payload = buildPayload(
            uploadId = uploadId,
            pokemonData = pokemonData,
            features = features,
            rarityScore = rarityScore,
            screenshotPath = null,  // 🔴 No screenshot path in telemetry
            screenshotBounds = null,
            pipelineMs = pipelineMs,
            phase2Result = phase2Result
        )

        return dao.insert(
            TelemetryUploadEntity(
                uploadId = uploadId,
                payloadJson = gson.toJson(payload),
                screenshotPath = null  // 🔴 No screenshot upload
            )
        )
    }

    suspend fun submitFeedback(uploadId: String, category: String, notes: String? = null): Boolean {
        if (!uploader.isEnabled()) return false
        return uploader.uploadFeedback(
            ScanFeedbackPayload(
                uploadId = uploadId,
                category = category,
                notes = notes
            )
        ).success
    }

    suspend fun flushPending(limit: Int = 5) {
        if (!uploader.isEnabled()) return
        dao.getPending(limit).forEach { entity ->
            val result = uploader.upload(entity)
            if (result.success) {
                Log.d(
                    "ScanTelemetryRepository",
                    "Telemetry upload success: uploadId=${entity.uploadId} attempts=${entity.attempts} screenshotUrl=${result.screenshotUrl}"
                )
                dao.markUploaded(entity.id, Date())
                entity.screenshotPath?.let { File(it).takeIf(File::exists)?.delete() }
            } else {
                Log.w(
                    "ScanTelemetryRepository",
                    "Telemetry retry queued: uploadId=${entity.uploadId} nextAttempt=${entity.attempts + 1} error=${result.error}"
                )
                dao.markFailed(entity.id, entity.attempts + 1, result.error)
            }
        }
    }

    private fun buildPayload(
        uploadId: String,
        pokemonData: PokemonData,
        features: VisualFeatures,
        rarityScore: RarityScore,
        screenshotPath: String?,
        screenshotBounds: Pair<Int, Int>?,
        pipelineMs: Long?,
        phase2Result: Phase2VariantClassifier.Result?
    ): ScanTelemetryPayload {
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return ScanTelemetryPayload(
            uploadId = uploadId,
            uploadedAtEpochMs = System.currentTimeMillis(),
            app = ScanTelemetryPayload.AppInfo(
                packageName = context.packageName,
                versionName = packageInfo.versionName ?: "unknown",
                versionCode = packageInfo.longVersionCode
            ),
            device = ScanTelemetryPayload.DeviceInfo(
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
                sdkInt = Build.VERSION.SDK_INT
            ),
            prediction = ScanTelemetryPayload.PredictionInfo(
                species = pokemonData.realName ?: pokemonData.name,
                cp = pokemonData.cp,
                hp = pokemonData.hp,
                maxHp = pokemonData.maxHp,
                stardustCost = pokemonData.stardust,
                candyCost = pokemonData.powerUpCandyCost,
                caughtDateEpochMs = pokemonData.caughtDate?.time,
                isShiny = features.isShiny,
                isShadow = features.isShadow,
                isLucky = features.isLucky,
                hasCostume = features.hasCostume,
                hasSpecialForm = features.hasSpecialForm,
                hasLocationCard = features.hasLocationCard,
                rarityScore = rarityScore.totalScore,
                rarityTier = rarityScore.tier.name,
                ivEstimate = rarityScore.ivEstimate,
                ivSolveMode = rarityScore.ivSolve?.ivSolveMode?.name,
                ivExact = rarityScore.ivSolve?.ivExact,
                ivMin = rarityScore.ivSolve?.ivMin,
                ivMax = rarityScore.ivSolve?.ivMax,
                ivCandidateCount = rarityScore.ivSolve?.ivCandidateCount
            ),
            debug = ScanTelemetryPayload.DebugInfo(
                rawOcrText = "",  // 🔴 SECURITY FIX: Don't send raw OCR text (contains PII)
                pipelineMs = pipelineMs,
                explanations = rarityScore.explanation,
                breakdown = rarityScore.breakdown,
                explanationMode = pokemonData.fullVariantMatch?.explanationMode,
                eventConfidenceCode = rarityScore.decisionSupport?.eventConfidenceCode,
                eventConfidenceLabel = rarityScore.decisionSupport?.eventConfidenceLabel,
                mismatchGuard = rarityScore.decisionSupport?.mismatchGuardTitle != null,
                whyNotExact = rarityScore.decisionSupport?.whyNotExact,
                scanConfidenceScore = rarityScore.decisionSupport?.scanConfidenceScore,
                scanConfidenceLabel = rarityScore.decisionSupport?.scanConfidenceLabel,
                cpOcrStatus = if (pokemonData.cp != null) "parsed" else "missing",
                hpOcrStatus = when {
                    pokemonData.maxHp != null -> "max_hp_parsed"
                    pokemonData.hp != null -> "current_hp_only"
                    else -> "missing"
                },
                powerUpCandySource = pokemonData.powerUpCandySource,
                powerUpStardustSource = pokemonData.powerUpStardustSource,
                diagnosticDirectory = null,  // 🔴 SECURITY FIX: Don't send diagnostics
                diagnosticFiles = null,       // 🔴 SECURITY FIX: Don't send diagnostic files
                ivSolve = rarityScore.ivSolve?.let { solve ->
                    ScanTelemetryPayload.IvSolveInfo(
                        mode = solve.ivSolveMode.name,
                        ivExact = solve.ivExact,
                        ivMin = solve.ivMin,
                        ivMax = solve.ivMax,
                        candidateCount = solve.ivCandidateCount,
                        levelMin = solve.levelMin,
                        levelMax = solve.levelMax,
                        signalsUsed = solve.ivSolveSignalsUsed
                    )
                },
                phase2 = phase2Result?.let { result ->
                    ScanTelemetryPayload.Phase2DebugInfo(
                        species = result.species,
                        modelType = result.modelType,
                        supportedTargets = result.supportedTargets,
                        appliedTargets = result.appliedTargets,
                        minConfidence = result.minConfidence,
                        minMargin = result.minMargin,
                        predictions = result.predictions.map { prediction ->
                            ScanTelemetryPayload.Phase2Prediction(
                                target = prediction.target,
                                predictedValue = prediction.predictedValue,
                                confidence = prediction.confidence,
                                margin = prediction.margin,
                                positiveScore = prediction.positiveScore,
                                negativeScore = prediction.negativeScore,
                                positiveCount = prediction.positiveCount,
                                negativeCount = prediction.negativeCount,
                                passedThreshold = prediction.passedThreshold
                            )
                        }
                    )
                }
            ),
            screenshot = ScanTelemetryPayload.ScreenshotInfo(
                sourceFileName = screenshotPath?.let(::File)?.name,
                width = screenshotBounds?.first,
                height = screenshotBounds?.second
            )
        )
    }

    private fun copyScreenshot(uploadId: String, sourcePath: String): String? {
        val source = File(sourcePath)
        if (!source.exists() || !source.isFile || source.length() <= 0L) {
            Log.w(
                "ScanTelemetryRepository",
                "Screenshot copy blocked: uploadId=$uploadId source=$sourcePath exists=${source.exists()} size=${source.takeIf(File::exists)?.length() ?: 0L}"
            )
            return null
        }
        val dir = File(context.cacheDir, "telemetry").apply { mkdirs() }
        val target = File(dir, "$uploadId.png")
        return runCatching {
            source.copyTo(target, overwrite = true)
            if (!target.exists() || target.length() <= 0L) {
                Log.w(
                    "ScanTelemetryRepository",
                    "Screenshot copy invalid: uploadId=$uploadId target=${target.absolutePath} size=${target.takeIf(File::exists)?.length() ?: 0L}"
                )
                null
            } else {
                target.absolutePath
            }
        }.getOrElse { error ->
            Log.w(
                "ScanTelemetryRepository",
                "Screenshot copy failed: uploadId=$uploadId source=$sourcePath target=${target.absolutePath} error=${error.message}"
            )
            null
        }
    }

    private fun readBounds(path: String): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    }

    /**
     * Scrubs personally identifiable information (PII) from raw OCR text.
     * Used if diagnostics need to be sent (disabled by default for privacy).
     * 🟠 SECURITY: Redacts sensitive fields while preserving structure for debugging.
     */
    private fun scrubPII(rawOcrText: String?): String? {
        if (rawOcrText.isNullOrBlank()) return null
        
        var scrubbed = rawOcrText
        // Replace numeric patterns (CP, HP values, fractions)
        scrubbed = scrubbed.replace(Regex("\\b\\d{3,4}\\b"), "[STAT]")
        scrubbed = scrubbed.replace(Regex("\\d+/\\d+"), "[FRAC]")
        // Replace date patterns
        scrubbed = scrubbed.replace(Regex("\\b20\\d{2}-\\d{2}-\\d{2}\\b"), "[DATE]")
        // Replace text that might be player names
        scrubbed = scrubbed.replace(Regex("[A-Z][a-z]{2,}"), "[NAME]")
        
        return if (scrubbed.isBlank()) null else scrubbed
    }
}
