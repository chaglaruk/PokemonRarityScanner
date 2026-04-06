package com.pokerarity.scanner.data.local

import android.content.Context

/**
 * Manages telemetry user preferences and consent state.
 * Stores whether user has opted in to telemetry data collection.
 */
class TelemetryPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(
        "telemetry_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_USER_CONSENT = "user_consent"
        private const val KEY_CONSENT_TIMESTAMP = "consent_timestamp"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
    }

    /**
     * Whether user has explicitly opted in to telemetry
     * Default: false (opt-in required)
     */
    var userConsent: Boolean
        get() = prefs.getBoolean(KEY_USER_CONSENT, false)
        set(value) = prefs.edit().putBoolean(KEY_USER_CONSENT, value).apply()

    /**
     * Timestamp when user gave/revoked consent
     */
    var consentTimestamp: Long
        get() = prefs.getLong(KEY_CONSENT_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_CONSENT_TIMESTAMP, value).apply()

    /**
     * Whether user has seen the telemetry consent onboarding dialog
     */
    var hasSeenOnboarding: Boolean
        get() = prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, value).apply()

    /**
     * Reset consent (e.g., for uninstall/reinstall)
     */
    fun resetConsent() {
        prefs.edit()
            .remove(KEY_USER_CONSENT)
            .remove(KEY_CONSENT_TIMESTAMP)
            .remove(KEY_HAS_SEEN_ONBOARDING)
            .apply()
    }
}
