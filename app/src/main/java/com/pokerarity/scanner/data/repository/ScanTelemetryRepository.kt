package com.pokerarity.scanner.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import com.google.gson.Gson
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.local.db.TelemetryUploadEntity
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.ScanFeedbackPayload
import com.pokerarity.scanner.data.model.ScanTelemetryPayload
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.remote.ScanTelemetryUploader
import com.pokerarity.scanner.data.model.PokemonData
import java.io.File
import java.util.Date
import java.util.UUID

class ScanTelemetryRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val gson: Gson = Gson(),
    private val uploader: ScanTelemetryUploader = ScanTelemetryUploader()
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
        pipelineMs: Long?
    ): Long? {
        if (!uploader.isEnabled()) return null
        val copiedScreenshot = screenshotPath?.let { copyScreenshot(uploadId, it) }
        val screenshotBounds = copiedScreenshot?.let(::readBounds)
        val payload = buildPayload(
            uploadId = uploadId,
            pokemonData = pokemonData,
            features = features,
            rarityScore = rarityScore,
            screenshotPath = copiedScreenshot,
            screenshotBounds = screenshotBounds,
            pipelineMs = pipelineMs
        )

        return dao.insert(
            TelemetryUploadEntity(
                uploadId = uploadId,
                payloadJson = gson.toJson(payload),
                screenshotPath = copiedScreenshot
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
                dao.markUploaded(entity.id, Date())
                entity.screenshotPath?.let { File(it).takeIf(File::exists)?.delete() }
            } else {
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
        pipelineMs: Long?
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
                caughtDateEpochMs = pokemonData.caughtDate?.time,
                isShiny = features.isShiny,
                isShadow = features.isShadow,
                isLucky = features.isLucky,
                hasCostume = features.hasCostume,
                hasSpecialForm = features.hasSpecialForm,
                hasLocationCard = features.hasLocationCard,
                rarityScore = rarityScore.totalScore,
                rarityTier = rarityScore.tier.name,
                ivEstimate = rarityScore.ivEstimate
            ),
            debug = ScanTelemetryPayload.DebugInfo(
                rawOcrText = pokemonData.rawOcrText,
                pipelineMs = pipelineMs,
                explanations = rarityScore.explanation,
                breakdown = rarityScore.breakdown,
                explanationMode = pokemonData.fullVariantMatch?.explanationMode,
                eventConfidenceCode = rarityScore.decisionSupport?.eventConfidenceCode,
                eventConfidenceLabel = rarityScore.decisionSupport?.eventConfidenceLabel,
                mismatchGuard = rarityScore.decisionSupport?.mismatchGuardTitle != null,
                whyNotExact = rarityScore.decisionSupport?.whyNotExact,
                scanConfidenceScore = rarityScore.decisionSupport?.scanConfidenceScore,
                scanConfidenceLabel = rarityScore.decisionSupport?.scanConfidenceLabel
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
        if (!source.exists()) return null
        val dir = File(context.cacheDir, "telemetry").apply { mkdirs() }
        val target = File(dir, "$uploadId.png")
        source.copyTo(target, overwrite = true)
        return target.absolutePath
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
}
