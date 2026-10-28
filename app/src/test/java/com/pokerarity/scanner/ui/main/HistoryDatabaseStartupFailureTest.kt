package com.pokerarity.scanner.ui.main

import com.pokerarity.scanner.data.local.db.ExistingPlaintextHistoryMigrationRequiredException
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryDatabaseStartupFailureTest {
    @Test
    fun unrelatedStartupFailureDoesNotBecomeHistoryRecoveryIssue() {
        val preferenceFailure = IllegalStateException("secure preference unavailable")

        assertNull(preferenceFailure.asHistoryDatabaseRecoveryIssue())
    }

    @Test
    fun databaseOpenFailureRetainsTypedRecoveryClassification() {
        val databaseFailure = ExistingPlaintextHistoryMigrationRequiredException()
        val failure = runCatching {
            openHistoryDatabase { throw databaseFailure }
        }.exceptionOrNull()

        assertTrue(failure is HistoryDatabaseStartupFailure)
        assertSame(databaseFailure, failure?.cause)
        assertEquals(
            HistoryDatabaseRecoveryIssue.LEGACY_PLAINTEXT,
            failure?.asHistoryDatabaseRecoveryIssue()
        )
    }

    @Test
    fun unexpectedDatabaseOpenFailureUsesRetryableRecoveryIssue() {
        val failure = runCatching {
            openHistoryDatabase { throw IllegalStateException("database unavailable") }
        }.exceptionOrNull()

        assertEquals(
            HistoryDatabaseRecoveryIssue.OPEN_FAILED,
            failure?.asHistoryDatabaseRecoveryIssue()
        )
    }

    @Test
    fun databaseNativeLoadFailureRetainsNativeRecoveryClassification() {
        val failure = runCatching {
            openHistoryDatabase { throw UnsatisfiedLinkError("native details") }
        }.exceptionOrNull()

        assertEquals(
            HistoryDatabaseRecoveryIssue.SQLCIPHER_NATIVE_UNAVAILABLE,
            failure?.asHistoryDatabaseRecoveryIssue()
        )
    }

    @Test
    fun cancellationIsNotConvertedToDatabaseRecoveryFailure() {
        val cancellation = CancellationException("startup cancelled")
        val failure = runCatching {
            openHistoryDatabase { throw cancellation }
        }.exceptionOrNull()

        assertSame(cancellation, failure)
    }
}
