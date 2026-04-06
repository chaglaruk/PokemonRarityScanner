package com.pokerarity.scanner.data.remote

import android.content.Context
import com.pokerarity.scanner.BuildConfig
import com.pokerarity.scanner.data.local.TelemetryConfigPreferences

data class ScanTelemetryConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val apiKey: String
) {
    companion object {
        fun fromContext(context: Context): ScanTelemetryConfig {
            val prefs = TelemetryConfigPreferences(context.applicationContext)
            val base = prefs.baseUrl.trim().trimEnd('/')
            return ScanTelemetryConfig(
                enabled = BuildConfig.SCAN_TELEMETRY_ENABLED && base.isNotBlank(),
                baseUrl = base,
                apiKey = prefs.apiKey
            )
        }
    }
}
