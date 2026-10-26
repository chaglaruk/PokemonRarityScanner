package com.pokerarity.scanner.service

import android.content.Context

/** Durable, content-free marker so a sticky service restart cannot lose consent recovery. */
internal object ProjectionConsentRecoveryState {
    private const val PREFERENCES_NAME = "projection_consent_recovery"
    private const val KEY_PENDING = "pending"

    fun markPending(context: Context): Boolean =
        preferences(context).edit().putBoolean(KEY_PENDING, true).commit()

    fun isPending(context: Context): Boolean =
        preferences(context).getBoolean(KEY_PENDING, false)

    fun clear(context: Context): Boolean =
        preferences(context).edit().remove(KEY_PENDING).commit()

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
