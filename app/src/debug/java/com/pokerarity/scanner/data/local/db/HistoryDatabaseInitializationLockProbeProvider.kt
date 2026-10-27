package com.pokerarity.scanner.data.local.db

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Process
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pokerarity.scanner.PokeRarityApp
import java.io.File
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
        METHOD_RECOVER_ISOLATED_DATABASE -> recoverIsolatedDatabase(checkNotNull(arg))
        else -> error("Unsupported lock probe method")
    }

    private fun recoverIsolatedDatabase(runId: String): Bundle {
        val appContext = checkNotNull(context).applicationContext
        val runDirectory = File(File(appContext.filesDir, HARNESS_DIRECTORY), runId)
        val databaseDirectory = File(runDirectory, DATABASE_DIRECTORY)
        check(RUN_ID_PATTERN.matches(runId) && databaseDirectory.isDirectory) {
            "The isolated History recovery fixture is unavailable"
        }

        val target = IsolatedHistoryDatabaseContext(appContext, databaseDirectory, runId)
        val database = AppDatabase.getInstance(target)
        try {
            val opened = database.openHelper.writableDatabase
            val marker = PlaintextHistoryMigrationMarkers.readActivationMarker(
                target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME)
            )
            val sourceArtifactsRemoved = !DatabasePassphraseStore.databaseArtifactsExist(
                target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
            )
            val schemaVersion = HistoryDatabaseIntegrity.pragmaLong(opened, "main", "user_version").toInt()
            val schemaIsCurrent = schemaVersion == AppDatabase.CURRENT_SCHEMA_VERSION
            val historyCount = historyCount(opened)
            val integrityOk = HistoryDatabaseIntegrity.integrityIsOk(opened, "main")
            val cipherIntegrityOk = HistoryDatabaseIntegrity.cipherIntegrityIsOk(opened, "main")
            val syntheticRowMatches = syntheticHistoryRowMatches(opened)
            val markerComplete = marker?.conversionComplete == true

            return Bundle().apply {
                putInt(RESULT_PROCESS_ID, Process.myPid())
                putInt(RESULT_SCHEMA_VERSION, schemaVersion)
                putInt(RESULT_HISTORY_COUNT, historyCount)
                putBoolean(RESULT_SCHEMA_IS_CURRENT, schemaIsCurrent)
                putBoolean(RESULT_INTEGRITY_OK, integrityOk)
                putBoolean(RESULT_CIPHER_INTEGRITY_OK, cipherIntegrityOk)
                putBoolean(RESULT_SYNTHETIC_ROW_MATCHES, syntheticRowMatches)
                putBoolean(RESULT_SOURCE_ARTIFACTS_REMOVED, sourceArtifactsRemoved)
                putBoolean(RESULT_ACTIVATION_MARKER_COMPLETE, markerComplete)
            }
        } finally {
            // The harness removes its synthetic directory after the call returns.
            database.close()
        }
    }

    private fun historyCount(database: SupportSQLiteDatabase): Int =
        database.query("SELECT COUNT(*) FROM scan_history").use { cursor ->
            check(cursor.moveToFirst()) { "The History count query returned no result" }
            cursor.getInt(0)
        }

    private fun syntheticHistoryRowMatches(database: SupportSQLiteDatabase): Boolean =
        database.query(
            "SELECT id,timestamp,pokemonName,cp,hp,caughtDate,rawOcrText,isShiny,isShadow,isLucky,hasCostume,rarityScore,rarityTier " +
                "FROM scan_history WHERE id = $SYNTHETIC_HISTORY_ID"
        ).use { cursor ->
            cursor.moveToFirst() &&
                cursor.getLong(0) == SYNTHETIC_HISTORY_ID &&
                cursor.getLong(1) == SYNTHETIC_TIMESTAMP &&
                cursor.getString(2) == SYNTHETIC_POKEMON_NAME &&
                cursor.getInt(3) == 0 &&
                cursor.getInt(4) == 0 &&
                cursor.isNull(5) &&
                cursor.getString(6) == SYNTHETIC_OCR_MARKER &&
                cursor.getInt(7) == 0 &&
                cursor.getInt(8) == 0 &&
                cursor.getInt(9) == 0 &&
                cursor.getInt(10) == 0 &&
                cursor.getInt(11) == 42 &&
                cursor.getString(12) == "RARE" &&
                !cursor.moveToNext()
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
        const val METHOD_RECOVER_ISOLATED_DATABASE = "recover_isolated_database"
        const val RESULT_ACQUIRED = "acquired"
        const val RESULT_ATTEMPT_STARTED = "attempt_started"
        const val RESULT_PROCESS_ID = "process_id"
        const val RESULT_SCANNER_RUNTIME_STARTED = "scanner_runtime_started"
        const val RESULT_SCHEMA_VERSION = "schema_version"
        const val RESULT_HISTORY_COUNT = "history_count"
        const val RESULT_SCHEMA_IS_CURRENT = "schema_is_current"
        const val RESULT_INTEGRITY_OK = "integrity_ok"
        const val RESULT_CIPHER_INTEGRITY_OK = "cipher_integrity_ok"
        const val RESULT_SYNTHETIC_ROW_MATCHES = "synthetic_row_matches"
        const val RESULT_SOURCE_ARTIFACTS_REMOVED = "source_artifacts_removed"
        const val RESULT_ACTIVATION_MARKER_COMPLETE = "activation_marker_complete"

        private const val HARNESS_DIRECTORY = "sprint6-sqlcipher-process-death"
        private const val DATABASE_DIRECTORY = "database"
        private const val SYNTHETIC_HISTORY_ID = 431L
        private const val SYNTHETIC_TIMESTAMP = 123456789L
        private const val SYNTHETIC_POKEMON_NAME = "synthetic-fixture"
        private const val SYNTHETIC_OCR_MARKER = "synthetic"
        private val RUN_ID_PATTERN = Regex("[a-f0-9]{32}")
    }
}
