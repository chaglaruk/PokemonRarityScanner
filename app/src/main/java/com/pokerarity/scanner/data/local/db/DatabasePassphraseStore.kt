// Purpose: Manage SQLCipher database passphrase and legacy plaintext cleanup.
package com.pokerarity.scanner.data.local.db

import android.content.Context
import android.util.Base64
import android.util.Log
import com.pokerarity.scanner.data.local.SecurePreferencesFactory
import java.io.File
import java.security.SecureRandom

internal object DatabasePassphraseStore {
    private const val TAG = "AppDatabase"
    private const val PREFS_NAME = "db_security"
    private const val KEY_DB_PASSPHRASE = "db_passphrase_v1"
    private const val DB_NAME = "pokerarity_db"
    private val SQLITE_HEADER = "SQLite format 3".toByteArray(Charsets.US_ASCII)

    fun getOrCreate(context: Context): ByteArray {
        SqlCipherInitializer.ensureLoaded()
        dropPlaintextDatabaseIfPresent(context)
        val prefs = SecurePreferencesFactory.create(context, PREFS_NAME)
        val storedPassphrase = prefs.getString(KEY_DB_PASSPHRASE, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { Base64.decode(it, Base64.DEFAULT) }

        val databaseFile = context.getDatabasePath(DB_NAME)
        val finalPassphrase = resolvePassphrase(databaseFile, storedPassphrase) {
            ByteArray(32).also { SecureRandom().nextBytes(it) }
        }

        if (storedPassphrase == null) {
            prefs.edit()
                .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(finalPassphrase, Base64.NO_WRAP))
                .apply()
        }

        return finalPassphrase
    }

    /** Never replace existing database files with a newly keyed empty database. */
    internal fun resolvePassphrase(
        databaseFile: File,
        storedPassphrase: ByteArray?,
        createPassphrase: () -> ByteArray
    ): ByteArray {
        if (storedPassphrase != null) return storedPassphrase
        if (databaseArtifactsExist(databaseFile)) {
            throw ExistingDatabasePassphraseUnavailableException()
        }
        return createPassphrase()
    }

    private fun databaseArtifactsExist(databaseFile: File): Boolean = listOf(
        databaseFile,
        File("${databaseFile.absolutePath}-wal"),
        File("${databaseFile.absolutePath}-shm"),
        File("${databaseFile.absolutePath}-journal")
    ).any(File::exists)

    private fun dropPlaintextDatabaseIfPresent(context: Context) {
        val databaseFile = context.getDatabasePath(DB_NAME)
        if (!databaseFile.exists()) return
        val header = ByteArray(SQLITE_HEADER.size)
        val readCount = runCatching {
            databaseFile.inputStream().use { input -> input.read(header) }
        }.getOrDefault(-1)
        if (readCount != SQLITE_HEADER.size || !header.contentEquals(SQLITE_HEADER)) return
        Log.w(TAG, "Detected plaintext legacy database, deleting before SQLCipher open")
        deleteDatabaseFiles(context, DB_NAME)
    }

    fun deleteDatabaseFiles(context: Context, databaseName: String = DB_NAME) {
        context.deleteDatabase(databaseName)
        val databaseFile = context.getDatabasePath(databaseName)
        listOf(
            databaseFile,
            File("${databaseFile.absolutePath}-wal"),
            File("${databaseFile.absolutePath}-shm"),
            File("${databaseFile.absolutePath}-journal")
        ).forEach { file ->
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Failed to delete database artifact: ${safeDatabaseArtifactLabel(file)}")
            }
        }
    }

    internal fun safeDatabaseArtifactLabel(file: File): String =
        file.name.takeIf { it.isNotBlank() } ?: "database-artifact"
}

internal class ExistingDatabasePassphraseUnavailableException : IllegalStateException(
    "Existing History database files cannot be opened because their passphrase is unavailable. " +
        "The files were preserved."
)
