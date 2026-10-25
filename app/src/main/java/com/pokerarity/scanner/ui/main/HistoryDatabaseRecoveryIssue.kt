package com.pokerarity.scanner.ui.main

import androidx.annotation.StringRes
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.local.db.ExistingDatabasePassphraseUnavailableException
import com.pokerarity.scanner.data.local.db.ExistingPlaintextHistoryMigrationRequiredException

internal enum class HistoryDatabaseRecoveryIssue(@StringRes val messageRes: Int) {
    LEGACY_PLAINTEXT(R.string.history_database_plaintext_recovery_message),
    ENCRYPTION_KEY_UNAVAILABLE(R.string.history_database_key_recovery_message),
    OPEN_FAILED(R.string.history_database_open_recovery_message);

    companion object {
        fun from(failure: Throwable): HistoryDatabaseRecoveryIssue = when (failure) {
            is ExistingPlaintextHistoryMigrationRequiredException -> LEGACY_PLAINTEXT
            is ExistingDatabasePassphraseUnavailableException -> ENCRYPTION_KEY_UNAVAILABLE
            else -> OPEN_FAILED
        }
    }
}
