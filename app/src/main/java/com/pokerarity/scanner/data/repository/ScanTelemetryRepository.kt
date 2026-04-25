package com.pokerarity.scanner.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.local.db.OfflineTelemetryEntity
import com.pokerarity.scanner.data.local.db.TelemetryUploadEntity
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.ScanFeedbackPayload
import com.pokerarity.scanner.data.model.ScanTelemetryPayload
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.remote.ScanTelemetryUploader
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import java.io.File
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ScanTelemetryRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val gson: Gson = Gson(),
    private val uploader: ScanTelemetryUploader = ScanTelemetryUploader(context)
) {
    private val dao = database.telemetryUploadDao()
    private val offlineDao = database.offlineTelemetryDao()
    private val flushMutex = Mutex()

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

        val payload = buildPayload(
            uploadId = uploadId,
            pokemonData = pokemonData,
            features = features,
            rarityScore = rarityScore,
            screenshotPath = screenshotPath,
            screenshotBounds = null,
            pipelineMs = pipelineMs,
            phase2Result = phase2Result
        )

        dao.deleteByUploadId(uploadId)
        return dao.insert(
            TelemetryUploadEntity(
                uploadId = uploadId,
                payloadJson = gson.toJson(payload),
                screenshotPath = screenshotPath
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

    suspend fun flushPending(limit: Int = 5) = flushMutex.withLock {
        if (!uploader.isEnabled()) return@withLock
        if (uploader.hasConfiguredApiKey()) {
            Log.d("ScanTelemetryRepository", "Telemetry probe skipped: configured API key present; using authenticated upload path")
            dao.unblockBlocked()
        } else {
            val primaryProbe = uploader.probePrimaryTelemetryEndpoint()
            Log.d(
                "ScanTelemetryRepository",
                "Telemetry probe: primary=${primaryProbe.statusCode ?: primaryProbe.error}"
            )
            if (ScanTelemetryUploader.shouldStageOfflineTelemetryForStatus(primaryProbe.statusCode)) {
                dao.getPending(limit).forEach { entity ->
                    stageOfflineTelemetry(entity, primaryProbe.statusCode)
                    dao.markFailed(entity.id, entity.attempts + 1, "Primary telemetry unavailable; staged offline")
                }
                return@withLock
            }
            if ((primaryProbe.statusCode ?: 0) in 200..499) {
                offlineDao.markAllFlushed(Date())
                dao.unblockBlocked()
            }
        }
        val processedUploadIds = mutableSetOf<String>()
        dao.getPending(limit).forEach { entity ->
            if (!processedUploadIds.add(entity.uploadId)) {
                dao.deleteById(entity.id)
                return@forEach
            }
            val preparedUpload = prepareUploadEntity(entity)
            if (!entity.screenshotPath.isNullOrBlank() && preparedUpload.screenshotFile == null) {
                Log.w(
                    "ScanTelemetryRepository",
                    "Telemetry screenshot unavailable; uploading metadata only: uploadId=${entity.uploadId} path=${entity.screenshotPath} attempts=${entity.attempts}"
                )
            }
            val result = uploader.upload(preparedUpload.entity)
            if (result.success) {
                Log.d(
                    "ScanTelemetryRepository",
                    "Telemetry upload success: uploadId=${entity.uploadId} attempts=${entity.attempts} screenshotUrl=${result.screenshotUrl}"
                )
                dao.deleteByUploadId(entity.uploadId)
                preparedUpload.screenshotFile?.delete()
            } else {
                val retryable = ScanTelemetryUploader.isRetryableFailure(result.error)
                Log.w(
                    "ScanTelemetryRepository",
                    "Telemetry ${if (retryable) "retry queued" else "blocked"}: uploadId=${entity.uploadId} nextAttempt=${entity.attempts + 1} error=${result.error}"
                )
                if (retryable) {
                    dao.markFailed(entity.id, entity.attempts + 1, result.error)
                } else {
                    dao.markBlocked(entity.id, result.error)
                }
            }
        }
    }

    private suspend fun stageOfflineTelemetry(entity: TelemetryUploadEntity, statusCode: Int?) {
        if (offlineDao.countPending(entity.uploadId) > 0) return
        offlineDao.insert(
            OfflineTelemetryEntity(
                uploadId = entity.uploadId,
                endpointUrl = uploader.primaryUploadEndpoint(),
                statusCode = statusCode,
                payloadJson = buildOfflineSummaryJson(entity.payloadJson)
            )
        )
    }

    private fun buildOfflineSummaryJson(payloadJson: String): String {
        return runCatching {
            val root = JsonParser.parseString(payloadJson).asJsonObject
            val prediction = root.getAsJsonObject("prediction")
            val debug = root.getAsJsonObject("debug")
            gson.toJson(
                mapOf(
                    "species" to prediction?.get("species")?.takeIf { !it.isJsonNull }?.asString,
                    "speciesId" to prediction?.get("speciesId")?.takeIf { !it.isJsonNull }?.asString,
                    "formDetected" to prediction?.get("formDetected")?.takeIf { !it.isJsonNull }?.asString,
                    "rarityScore" to prediction?.get("rarityScore")?.takeIf { !it.isJsonNull }?.asInt,
                    "isEventBoosted" to prediction?.get("isEventBoosted")?.takeIf { !it.isJsonNull }?.asBoolean,
                    "latencyMs" to debug?.get("pipelineMs")?.takeIf { !it.isJsonNull }?.asLong,
                    "uploadId" to root.get("uploadId")?.takeIf { !it.isJsonNull }?.asString
                )
            )
        }.getOrDefault(payloadJson)
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
                species = pokemonData.fullVariantMatch?.finalSpecies ?: pokemonData.realName ?: pokemonData.name,
                speciesId = pokemonData.fullVariantMatch?.finalSpriteKey?.substringBefore('_')?.takeIf { it.isNotBlank() },
                formDetected = resolveFormDetected(pokemonData, features),
                cp = pokemonData.cp,
                hp = pokemonData.hp,
                maxHp = pokemonData.maxHp,
                caughtDateEpochMs = pokemonData.caughtDate?.time,
                isShiny = features.isShiny,
                isShadow = features.isShadow,
                isLucky = features.isLucky,
                hasCostume = features.hasCostume,
                hasSpecialForm = features.hasSpecialForm,
                hasLocationCard = features.hasLocationCard,
                isEventBoosted = rarityScore.decisionSupport?.eventConfidenceCode == "LIVE_EVENT",
                rarityScore = rarityScore.totalScore,
                rarityTier = rarityScore.tier.name
            ),
            debug = ScanTelemetryPayload.DebugInfo(
                rawOcrText = "",
                pipelineMs = pipelineMs,
                explanations = rarityScore.explanation,
                breakdown = rarityScore.breakdown,
                explanationMode = pokemonData.fullVariantMatch?.explanationMode,
                eventConfidenceCode = rarityScore.decisionSupport?.eventConfidenceCode,
                eventConfidenceLabel = rarityScore.decisionSupport?.eventConfidenceLabel,
                mismatchGuard = rarityScore.decisionSupport?.mismatchGuardTitle != null,
                recognitionSummary = rarityScore.recognitionSummary ?: rarityScore.decisionSupport?.recognitionSummary,
                scanConfidenceScore = rarityScore.decisionSupport?.scanConfidenceScore,
                scanConfidenceLabel = rarityScore.decisionSupport?.scanConfidenceLabel,
                ocrConfidenceScore = computeOcrConfidenceScore(pokemonData),
                contradictionField = detectContradictionField(pokemonData, rarityScore),
                cpOcrStatus = if (pokemonData.cp != null) "parsed" else "missing",
                hpOcrStatus = when {
                    pokemonData.maxHp != null -> "max_hp_parsed"
                    pokemonData.hp != null -> "current_hp_only"
                    else -> "missing"
                },
                dynamicNameSource = resolveDynamicNameSource(pokemonData.rawOcrText),
                livingDbVersion = RemoteMetadataSyncManager.currentVersion(context),
                diagnosticDirectory = null,
                diagnosticFiles = null,
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

    private fun computeOcrConfidenceScore(pokemonData: PokemonData): Int {
        var score = 0
        if (!pokemonData.name.isNullOrBlank() || !pokemonData.realName.isNullOrBlank()) score += 30
        if ((pokemonData.cp ?: 0) > 0) score += 35
        if (pokemonData.maxHp != null || pokemonData.hp != null) score += 35
        return score.coerceIn(0, 100)
    }

    private fun detectContradictionField(
        pokemonData: PokemonData,
        rarityScore: RarityScore
    ): String? {
        val cp = pokemonData.cp ?: 0
        val maxHp = pokemonData.maxHp ?: pokemonData.hp
        return when {
            pokemonData.name.isNullOrBlank() && pokemonData.realName.isNullOrBlank() -> "species"
            pokemonData.fullVariantMatch != null &&
                !pokemonData.name.isNullOrBlank() &&
                pokemonData.fullVariantMatch.finalSpecies.isNotBlank() &&
                !pokemonData.fullVariantMatch.finalSpecies.equals(pokemonData.name, ignoreCase = true) &&
                pokemonData.fullVariantMatch.speciesConfidence < 0.7f -> "species"
            cp > 0 && maxHp != null && cp >= 1000 && maxHp <= 20 -> "hp"
            rarityScore.decisionSupport?.mismatchGuardTitle != null -> "variant"
            else -> null
        }
    }

    private fun resolveFormDetected(
        pokemonData: PokemonData,
        features: VisualFeatures
    ): String {
        val resolved = pokemonData.fullVariantMatch?.resolvedVariantClass.orEmpty()
        return when {
            resolved.isNotBlank() && resolved != "base" -> resolved
            pokemonData.fullVariantMatch?.resolvedCostume == true || features.hasCostume -> "costume"
            pokemonData.fullVariantMatch?.resolvedForm == true || features.hasSpecialForm -> "form"
            else -> "base"
        }
    }

    private fun resolveDynamicNameSource(rawOcrText: String): String? {
        val marker = rawOcrText.split('|', '\n')
            .asSequence()
            .firstOrNull {
                it.startsWith("NameDynamic:", ignoreCase = true) ||
                    it.startsWith("DynamicName:", ignoreCase = true)
            }
            ?.substringAfter(':', "")
            ?.trim()
        return when {
            marker.isNullOrBlank() -> null
            marker.equals("ocr", ignoreCase = true) -> "static_name_crop"
            else -> "mlkit_dynamic"
        }
    }

    companion object {
        internal data class PreparedUpload(
            val entity: TelemetryUploadEntity,
            val screenshotFile: File?
        )

        internal fun prepareUploadEntity(entity: TelemetryUploadEntity): PreparedUpload {
            val screenshotPath = entity.screenshotPath?.trim()
            val screenshotFile = normalizedScreenshotFile(screenshotPath)
            return if (screenshotFile != null) {
                PreparedUpload(entity, screenshotFile)
            } else {
                PreparedUpload(entity.copy(screenshotPath = null), null)
            }
        }

        internal fun normalizedScreenshotFile(path: String?): File? {
            val normalized = path
                ?.trim()
                ?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
                ?: return null
            val file = File(normalized)
            return file.takeIf { it.exists() && it.isFile }
        }
    }
}
