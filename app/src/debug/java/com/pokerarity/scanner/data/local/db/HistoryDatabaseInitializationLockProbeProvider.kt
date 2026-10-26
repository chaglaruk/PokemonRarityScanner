package com.pokerarity.scanner.data.local.db

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Process
import com.pokerarity.scanner.PokeRarityApp
import java.util.concurrent.atomic.AtomicReference

/** Debug-only second-process probe used by the isolated cross-process lock instrumentation test. */
class HistoryDatabaseInitializationLockProbeProvider : ContentProvider() {
    private val activeAcquisitionAttempt = AtomicReference<String?>(null)

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = when (method) {
        METHOD_TRY_LOCK -> Bundle().apply {
            putBoolean(
                RESULT_ACQUIRED,
                HistoryDatabaseInitializationLock.tryWithExclusiveLock(checkNotNull(context)) { }
            )
            putInt(RESULT_PROCESS_ID, Process.myPid())
            putBoolean(
                RESULT_SCANNER_RUNTIME_STARTED,
                (checkNotNull(context).applicationContext as PokeRarityApp).hasStartedScannerRuntime()
            )
        }
        METHOD_ACQUIRE_LOCK -> {
            val attemptId = checkNotNull(arg) { "An attempt ID is required" }
            val appContext = checkNotNull(context)
            check(!HistoryDatabaseInitializationLock.tryWithExclusiveLock(appContext) { }) {
                "The probe expected another process to hold the initialization lock"
            }
            activeAcquisitionAttempt.set(attemptId)
            val acquired = HistoryDatabaseInitializationLock.withExclusiveLock(appContext) { true }
            Bundle().apply {
                putBoolean(RESULT_ACQUIRED, acquired)
                putInt(RESULT_PROCESS_ID, Process.myPid())
            }
        }
        METHOD_STATE -> Bundle().apply {
            putBoolean(RESULT_ATTEMPT_STARTED, arg != null && activeAcquisitionAttempt.get() == arg)
            putInt(RESULT_PROCESS_ID, Process.myPid())
        }
        else -> error("Unsupported lock probe method")
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = error("The lock probe does not expose query data")

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("The lock probe does not accept inserts")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        error("The lock probe does not accept deletes")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = error("The lock probe does not accept updates")

    companion object {
        const val METHOD_TRY_LOCK = "try_lock"
        const val METHOD_ACQUIRE_LOCK = "acquire_lock"
        const val METHOD_STATE = "state"
        const val RESULT_ACQUIRED = "acquired"
        const val RESULT_ATTEMPT_STARTED = "attempt_started"
        const val RESULT_PROCESS_ID = "process_id"
        const val RESULT_SCANNER_RUNTIME_STARTED = "scanner_runtime_started"
    }
}
