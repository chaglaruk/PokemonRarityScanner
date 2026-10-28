package com.pokerarity.scanner.data.local.db

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.io.File

/** Routes debug-only History recovery probes into a run-scoped database and key-preference file. */
internal class IsolatedHistoryDatabaseContext(
    base: Context,
    private val databaseDirectory: File,
    private val runId: String
) : ContextWrapper(base) {
    private val isolatedFilesDirectory = File(databaseDirectory.parentFile, ISOLATED_FILES_DIRECTORY)

    init {
        require(RUN_ID_PATTERN.matches(runId)) { "A valid isolated database run ID is required" }
        check(isolatedFilesDirectory.isDirectory || isolatedFilesDirectory.mkdirs()) {
            "The isolated History recovery files directory is unavailable"
        }
    }

    override fun getDatabasePath(name: String): File = File(databaseDirectory, name)

    override fun getCacheDir(): File = checkNotNull(databaseDirectory.parentFile)

    override fun getFilesDir(): File = isolatedFilesDirectory

    override fun getApplicationContext(): Context = this

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        super.getSharedPreferences(scopedPreferencesName(name, runId), mode)

    internal fun deletePassphrasePreferences(): Boolean =
        deleteSharedPreferences(scopedPreferencesName(DATABASE_SECURITY_PREFERENCES, runId))

    companion object {
        private const val DATABASE_SECURITY_PREFERENCES = "db_security"
        private const val ISOLATED_FILES_DIRECTORY = "isolated-files"
        private val RUN_ID_PATTERN = Regex("[a-f0-9]{32}")

        private fun scopedPreferencesName(name: String, runId: String): String = "$name-sprint6-$runId"
    }
}
