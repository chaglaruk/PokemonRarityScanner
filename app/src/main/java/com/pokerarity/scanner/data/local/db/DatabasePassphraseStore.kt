// Purpose: Manage SQLCipher database passphrase and legacy plaintext cleanup.
package com.pokerarity.scanner.data.local.db

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import android.util.Log
import com.pokerarity.scanner.data.local.SecurePreferencesFactory
import java.io.File
import java.security.SecureRandom

internal object DatabasePassphraseStore {
    private const val TAG = "AppDatabase"
    private const val PREFS_NAME = "db_security"
    private const val KEY_DB_PASSPHRASE = "db_passphrase_v1"
    internal const val DB_NAME = "pokerarity_db"
    internal const val CIPHER_DB_NAME = "pokerarity_db_cipher"
    internal const val CIPHER_MIGRATION_MARKER_NAME = "pokerarity_db_cipher.active"
    private val SQLITE_HEADER = "SQLite format 3".toByteArray(Charsets.US_ASCII)

    fun getOrCreate(context: Context): ByteArray {
        val databaseFile = context.getDatabasePath(DB_NAME)
        SqlCipherInitializer.ensureLoaded()
        val prefs = SecurePreferencesFactory.create(context, PREFS_NAME)
        val storedPassphrase = prefs.getString(KEY_DB_PASSPHRASE, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { Base64.decode(it, Base64.DEFAULT) }

        val cipherFile = context.getDatabasePath(CIPHER_DB_NAME)
        val plaintextSource = isPlaintextSqliteDatabase(databaseFile)
        if (storedPassphrase == null &&
            (databaseArtifactsExist(cipherFile) || (databaseArtifactsExist(databaseFile) && !plaintextSource))
        ) {
            throw ExistingDatabasePassphraseUnavailableException()
        }
        val finalPassphrase = storedPassphrase ?: ByteArray(32).also { SecureRandom().nextBytes(it) }

        if (storedPassphrase == null) {
            val committed = prefs.edit()
                .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(finalPassphrase, Base64.NO_WRAP))
                .commit()
            if (!committed) throw DatabasePassphrasePersistenceException()
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

    internal fun databaseArtifactsExist(databaseFile: File): Boolean {
        if (listOf(
                databaseFile,
                File("${databaseFile.absolutePath}-wal"),
                File("${databaseFile.absolutePath}-shm"),
                File("${databaseFile.absolutePath}-journal")
            ).any(File::exists)
        ) return true
        val parent = databaseFile.parentFile ?: return false
        val masterJournalPrefix = databaseFile.name + "-mj"
        return parent.listFiles()?.any { it.name.startsWith(masterJournalPrefix) } == true
    }

    internal fun deleteDatabaseArtifacts(databaseFile: File): Boolean {
        val parent = databaseFile.parentFile
        val masterJournalPrefix = databaseFile.name + "-mj"
        val artifacts = buildList {
            add(databaseFile)
            add(File("${databaseFile.absolutePath}-wal"))
            add(File("${databaseFile.absolutePath}-shm"))
            add(File("${databaseFile.absolutePath}-journal"))
            parent?.listFiles()
                ?.filter { it.name.startsWith(masterJournalPrefix) }
                ?.let(::addAll)
        }
        artifacts.forEach { file -> if (file.exists()) file.delete() }
        return !databaseArtifactsExist(databaseFile)
    }

    internal fun isPlaintextSqliteDatabase(databaseFile: File): Boolean {
        if (!databaseFile.exists()) return false
        val header = ByteArray(SQLITE_HEADER.size)
        val readCount = runCatching {
            databaseFile.inputStream().use { input -> input.read(header) }
        }.getOrDefault(-1)
        return readCount == SQLITE_HEADER.size && header.contentEquals(SQLITE_HEADER)
    }

    fun deleteDatabaseFiles(context: Context, databaseName: String = DB_NAME) {
        val names = if (databaseName == DB_NAME) listOf(DB_NAME, CIPHER_DB_NAME) else listOf(databaseName)
        names.forEach { name ->
            context.deleteDatabase(name)
            val databaseFile = context.getDatabasePath(name)
            if (!deleteDatabaseArtifacts(databaseFile)) {
                Log.w(TAG, "Failed to delete database artifact: ${safeDatabaseArtifactLabel(databaseFile)}")
            }
        }
        if (databaseName == DB_NAME) {
            val marker = File(context.getDatabasePath(DB_NAME).parentFile, CIPHER_MIGRATION_MARKER_NAME)
            AtomicFile(marker).delete()
        }
    }

    internal fun safeDatabaseArtifactLabel(file: File): String =
        file.name.takeIf { it.isNotBlank() } ?: "database-artifact"
}

internal class ExistingDatabasePassphraseUnavailableException : IllegalStateException(
    "Existing History database files cannot be opened because their passphrase is unavailable. " +
        "The files were preserved."
)

internal class ExistingPlaintextHistoryMigrationRequiredException : IllegalStateException(
    "A legacy plaintext History database was found. Its database and sidecars were preserved; " +
        "safe migration is required before SQLCipher can open it."
)

internal class DatabasePassphrasePersistenceException : IllegalStateException(
    "The History database encryption key could not be saved. Existing database files were preserved."
)
