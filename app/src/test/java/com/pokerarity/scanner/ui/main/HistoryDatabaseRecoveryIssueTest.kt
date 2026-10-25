package com.pokerarity.scanner.ui.main

import com.pokerarity.scanner.data.local.db.ExistingDatabasePassphraseUnavailableException
import com.pokerarity.scanner.data.local.db.ExistingPlaintextHistoryMigrationRequiredException
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryDatabaseRecoveryIssueTest {
    @Test
    fun legacyPlaintextDatabaseUsesSpecificPreservationGuidance() {
        assertEquals(
            HistoryDatabaseRecoveryIssue.LEGACY_PLAINTEXT,
            HistoryDatabaseRecoveryIssue.from(ExistingPlaintextHistoryMigrationRequiredException())
        )
    }

    @Test
    fun missingEncryptionPassphraseUsesSpecificPreservationGuidance() {
        assertEquals(
            HistoryDatabaseRecoveryIssue.ENCRYPTION_KEY_UNAVAILABLE,
            HistoryDatabaseRecoveryIssue.from(ExistingDatabasePassphraseUnavailableException())
        )
    }

    @Test
    fun unexpectedOpenFailureUsesGenericSafeRecoveryGuidance() {
        assertEquals(
            HistoryDatabaseRecoveryIssue.OPEN_FAILED,
            HistoryDatabaseRecoveryIssue.from(IllegalStateException("internal details"))
        )
    }
}
