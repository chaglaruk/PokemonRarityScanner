package com.pokerarity.scanner.data.local

import android.content.Context
import com.pokerarity.scanner.util.SecurityAuditLogger

/**
 * Manages telemetry user preferences and consent state.
 * Stores whether user has opted in to telemetry data collection.
 * All consent changes are logged to SecurityAuditLogger for compliance.
 */
class TelemetryPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val legacyPrefs = appContext.getSharedPreferences(
        "telemetry_prefs",
        Context.MODE_PRIVATE
    )
    private val prefs = SecurePreferencesFactory.create(appContext, "telemetry_prefs_secure")
    private val auditLogger = SecurityAuditLogger.getInstance(appContext)

    companion object {
        internal const val KEY_USER_CONSENT = "user_consent"
        internal const val KEY_CONSENT_TIMESTAMP = "consent_timestamp"
        internal const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        internal const val KEY_LEGACY_MIGRATED = "legacy_migrated"
    }

    init {
        migrateLegacyPrefsIfNeeded()
    }

    /**
     * Whether user has explicitly opted in to telemetry
     * Default: false (opt-in required)
     */
    var userConsent: Boolean
        get() = prefs.getBoolean(KEY_USER_CONSENT, false)
        set(value) {
            val previousValue = prefs.getBoolean(KEY_USER_CONSENT, false)
            if (previousValue != value) {
                prefs.edit().putBoolean(KEY_USER_CONSENT, value).apply()
                consentTimestamp = System.currentTimeMillis()
                auditLogger.logConsentChange(value, "user_setter")
            }
        }

    /**
     * Timestamp when user gave/revoked consent
     */
    var consentTimestamp: Long
        get() = prefs.getLong(KEY_CONSENT_TIMESTAMP, 0L)
        private set(value) = prefs.edit().putLong(KEY_CONSENT_TIMESTAMP, value).apply()

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
        val wasEnabled = userConsent
        prefs.edit()
            .remove(KEY_USER_CONSENT)
            .remove(KEY_CONSENT_TIMESTAMP)
            .remove(KEY_HAS_SEEN_ONBOARDING)
            .remove(KEY_LEGACY_MIGRATED)
            .apply()
        auditLogger.logConsentChange(false, "reset_consent")
    }

    private fun migrateLegacyPrefsIfNeeded() {
        TelemetryPreferencesLegacyMigration.migrate(legacyPrefs, prefs) { hadConsent ->
            auditLogger.log(
                SecurityAuditLogger.EventType.CONSENT_CHANGED,
                "Legacy preferences migrated and cleared",
                "Consent preserved: $hadConsent",
                success = true
            )
        }
    }
}

/** Serializes the check-copy-clear sequence across all constructors in this process. */
internal object TelemetryPreferencesLegacyMigration {
    private val lock = Any()

    fun migrate(
        legacyPrefs: android.content.SharedPreferences,
        securePrefs: android.content.SharedPreferences,
        onMigrated: (hadConsent: Boolean) -> Unit
    ) {
        synchronized(lock) {
            if (securePrefs.getBoolean(TelemetryPreferences.KEY_LEGACY_MIGRATED, false)) return

            val hadConsent = legacyPrefs.getBoolean(TelemetryPreferences.KEY_USER_CONSENT, false)
            val secureEditor = securePrefs.edit()
            if (legacyPrefs.contains(TelemetryPreferences.KEY_USER_CONSENT)) {
                secureEditor.putBoolean(
                    TelemetryPreferences.KEY_USER_CONSENT,
                    legacyPrefs.getBoolean(TelemetryPreferences.KEY_USER_CONSENT, false)
                )
            }
            if (legacyPrefs.contains(TelemetryPreferences.KEY_CONSENT_TIMESTAMP)) {
                secureEditor.putLong(
                    TelemetryPreferences.KEY_CONSENT_TIMESTAMP,
                    legacyPrefs.getLong(TelemetryPreferences.KEY_CONSENT_TIMESTAMP, 0L)
                )
            }
            if (legacyPrefs.contains(TelemetryPreferences.KEY_HAS_SEEN_ONBOARDING)) {
                secureEditor.putBoolean(
                    TelemetryPreferences.KEY_HAS_SEEN_ONBOARDING,
                    legacyPrefs.getBoolean(TelemetryPreferences.KEY_HAS_SEEN_ONBOARDING, false)
                )
            }
            secureEditor.putBoolean(TelemetryPreferences.KEY_LEGACY_MIGRATED, true).apply()

            legacyPrefs.edit().clear().apply()
            onMigrated(hadConsent)
        }
    }
}
