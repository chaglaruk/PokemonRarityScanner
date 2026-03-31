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
        val cp: Int?,
        val hp: Int?,
        val caughtDateEpochMs: Long?,
        val isShiny: Boolean,
        val isShadow: Boolean,
        val isLucky: Boolean,
        val hasCostume: Boolean,
        val hasSpecialForm: Boolean,
        val hasLocationCard: Boolean,
        val rarityScore: Int,
        val rarityTier: String,
        val ivEstimate: String?
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
        val whyNotExact: String? = null,
        val scanConfidenceScore: Int? = null,
        val scanConfidenceLabel: String? = null
    )

    data class ScreenshotInfo(
        val sourceFileName: String?,
        val width: Int?,
        val height: Int?
    )
}
