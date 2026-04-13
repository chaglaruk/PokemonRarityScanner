package com.pokerarity.scanner.data.model

data class ScanTelemetryPayload(
    val uploadId: String,
    val uploadedAtEpochMs: Long,
    val app: AppInfo,
    val device: DeviceInfo,
    val prediction: PredictionInfo,
    val debug: DebugInfo,
    val screenshot: ScreenshotInfo
) {
    data class AppInfo(
        val packageName: String,
        val versionName: String,
        val versionCode: Long
    )

    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val sdkInt: Int
    )

    data class PredictionInfo(
        val species: String?,
        val speciesId: String? = null,
        val formDetected: String? = null,
        val cp: Int?,
        val hp: Int?,
        val maxHp: Int?,
        val caughtDateEpochMs: Long?,
        val isShiny: Boolean,
        val isShadow: Boolean,
        val isLucky: Boolean,
        val hasCostume: Boolean,
        val hasSpecialForm: Boolean,
        val hasLocationCard: Boolean,
        val isEventBoosted: Boolean = false,
        val rarityScore: Int,
        val rarityTier: String
    )

    data class DebugInfo(
        val rawOcrText: String,
        val pipelineMs: Long?,
        val explanations: List<String>,
        val breakdown: Map<String, Int>,
        val explanationMode: String? = null,
        val eventConfidenceCode: String? = null,
        val eventConfidenceLabel: String? = null,
        val mismatchGuard: Boolean = false,
        val recognitionSummary: String? = null,
        val scanConfidenceScore: Int? = null,
        val scanConfidenceLabel: String? = null,
        val ocrConfidenceScore: Int? = null,
        val contradictionField: String? = null,
        val cpOcrStatus: String? = null,
        val hpOcrStatus: String? = null,
        val dynamicNameSource: String? = null,
        val livingDbVersion: String? = null,
        val diagnosticDirectory: String? = null,
        val diagnosticFiles: Map<String, String>? = null,
        val phase2: Phase2DebugInfo? = null
    )

    data class Phase2DebugInfo(
        val species: String,
        val modelType: String,
        val supportedTargets: List<String>,
        val appliedTargets: List<String>,
        val minConfidence: Float,
        val minMargin: Float,
        val predictions: List<Phase2Prediction>
    )

    data class Phase2Prediction(
        val target: String,
        val predictedValue: Boolean,
        val confidence: Float,
        val margin: Float,
        val positiveScore: Float,
        val negativeScore: Float,
        val positiveCount: Int,
        val negativeCount: Int,
        val passedThreshold: Boolean
    )

    data class ScreenshotInfo(
        val sourceFileName: String?,
        val width: Int?,
        val height: Int?
    )
}
