package com.pokerarity.scanner.ui.main

import java.util.concurrent.CancellationException

/** Marks a failure thrown specifically while opening the History database. */
internal class HistoryDatabaseStartupFailure(cause: Throwable) :
    RuntimeException("History database open failed", cause)

/** Keeps recovery classification scoped to the database-open operation. */
@Suppress("TooGenericExceptionCaught") // Room and SQLCipher expose provider-specific open failures.
internal inline fun <T> openHistoryDatabase(open: () -> T): T = try {
    open()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    throw HistoryDatabaseStartupFailure(failure)
} catch (failure: UnsatisfiedLinkError) {
    throw HistoryDatabaseStartupFailure(failure)
}

internal fun Throwable.asHistoryDatabaseRecoveryIssue(): HistoryDatabaseRecoveryIssue? =
    (this as? HistoryDatabaseStartupFailure)?.cause?.let(HistoryDatabaseRecoveryIssue::from)
