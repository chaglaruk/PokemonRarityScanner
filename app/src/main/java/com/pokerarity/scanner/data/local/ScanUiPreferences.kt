package com.pokerarity.scanner.data.local

import android.content.Context

class ScanUiPreferences(context: Context) {

    private val prefs = SecurePreferencesFactory.create(context.applicationContext, "scan_ui_prefs")

    var autoCopyEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_COPY, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_COPY, value).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    companion object {
        private const val KEY_AUTO_COPY = "auto_copy_enabled"
        private const val KEY_HAPTICS = "haptics_enabled"
    }
}
