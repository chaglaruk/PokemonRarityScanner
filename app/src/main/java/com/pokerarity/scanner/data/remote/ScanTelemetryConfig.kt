package com.pokerarity.scanner.data.remote

import android.content.Context
import com.pokerarity.scanner.BuildConfig
import com.pokerarity.scanner.data.local.TelemetryConfigPreferences
import java.net.URI
import java.util.Locale

data class ScanTelemetryConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val apiKey: String
) {
    companion object {
        fun fromContext(context: Context): ScanTelemetryConfig {
            val prefs = TelemetryConfigPreferences(context.applicationContext)
            val bundledBase = normalizeBaseUrl(BuildConfig.SCAN_TELEMETRY_BASE_URL)
            val base = enforceBundledHost(
                candidate = normalizeBaseUrl(prefs.baseUrl),
                bundled = bundledBase
            )
            return ScanTelemetryConfig(
                enabled = BuildConfig.SCAN_TELEMETRY_ENABLED && base.isNotBlank(),
                baseUrl = base,
                apiKey = prefs.apiKey
            )
        }

        private fun normalizeBaseUrl(raw: String): String {
            val candidate = raw.trim().trimEnd('/')
            if (candidate.isBlank()) return ""
            val uri = runCatching { URI(candidate) }.getOrNull() ?: return ""
            val scheme = uri.scheme?.lowercase(Locale.US)
            if (scheme != "https") return ""
            if (uri.host.isNullOrBlank()) return ""
            return uri.toString().trimEnd('/')
        }

        private fun enforceBundledHost(candidate: String, bundled: String): String {
            if (candidate.isBlank() || bundled.isBlank()) return candidate
            val candidateHost = runCatching { URI(candidate).host?.lowercase(Locale.US) }.getOrNull()
            val bundledHost = runCatching { URI(bundled).host?.lowercase(Locale.US) }.getOrNull()
            return if (candidateHost != null && candidateHost == bundledHost) candidate else ""
        }
    }
}
