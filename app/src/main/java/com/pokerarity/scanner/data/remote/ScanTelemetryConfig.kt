package com.pokerarity.scanner.data.remote

import com.pokerarity.scanner.BuildConfig

data class ScanTelemetryConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val apiKey: String
) {
    companion object {
        fun fromBuildConfig(): ScanTelemetryConfig {
            val base = BuildConfig.SCAN_TELEMETRY_BASE_URL.trim().trimEnd('/')
            return ScanTelemetryConfig(
                enabled = BuildConfig.SCAN_TELEMETRY_ENABLED && base.isNotBlank(),
                baseUrl = base,
                apiKey = BuildConfig.SCAN_TELEMETRY_API_KEY.trim()
            )
        }
    }
}
