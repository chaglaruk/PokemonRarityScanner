package com.pokerarity.scanner.data.local

import android.content.Context
import com.pokerarity.scanner.BuildConfig

class TelemetryConfigPreferences(context: Context) {
    private val prefs = SecurePreferencesFactory.create(context, "telemetry_config")

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MIGRATED = "migrated"
    }

    init {
        migrateLegacyBuildConfigIfNeeded()
    }

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, normalizeBaseUrl(BuildConfig.SCAN_TELEMETRY_BASE_URL)).orEmpty()
        set(value) = prefs.edit().putString(KEY_BASE_URL, normalizeBaseUrl(value)).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, defaultApiKey()).orEmpty().trim()
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank()

    fun clearSecrets() {
        prefs.edit()
            .remove(KEY_API_KEY)
            .apply()
    }

    private fun migrateLegacyBuildConfigIfNeeded() {
        prefs.edit().apply {
            if (!prefs.contains(KEY_BASE_URL) && BuildConfig.SCAN_TELEMETRY_BASE_URL.isNotBlank()) {
                putString(KEY_BASE_URL, normalizeBaseUrl(BuildConfig.SCAN_TELEMETRY_BASE_URL))
            }
            if (defaultApiKey().isNotBlank() && prefs.getString(KEY_API_KEY, "").orEmpty().trim() != defaultApiKey()) {
                putString(KEY_API_KEY, defaultApiKey())
            }
            putBoolean(KEY_MIGRATED, true)
        }.apply()
    }

    private fun defaultApiKey(): String = BuildConfig.SCAN_TELEMETRY_API_KEY.trim()

    private fun normalizeBaseUrl(value: String): String = value.trim().trimEnd('/')
}
