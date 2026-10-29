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
    internal const val PREFS_NAME = "db_security"
    private const val KEY_DB_PASSPHRASE = "db_passphrase_v1"
    internal const val DB_NAME = "pokerarity_db"
    internal const val CIPHER_DB_NAME = "pokerarity_db_cipher"
    internal const val CIPHER_MIGRATION_MARKER_NAME = "pokerarity_db_cipher.active"
    internal const val CIPHER_MIGRATION_PREPARATION_NAME = "pokerarity_db_cipher.preparing"
    private val SQLITE_HEADER = "SQLite format 3".toByteArray(Charsets.US_ASCII)

    fun getOrCreate(context: Context, isolatedPassphrasePreferencesName: String? = null): ByteArray {
        check(isolatedPassphrasePreferencesName == null ||
            HistoryValidationPassphrasePreferences.isValidName(isolatedPassphrasePreferencesName)
        ) {
            "History validation requires a unique fixture passphrase preference name"
        }
        val databaseFile = context.getDatabasePath(DB_NAME)
        SqlCipherInitializer.ensureLoaded()
        val preferencesName = isolatedPassphrasePreferencesName ?: PREFS_NAME
        val prefs = SecurePreferencesFactory.create(context, preferencesName)
        val storedPassphrase = prefs.getString(KEY_DB_PASSPHRASE, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { Base64.decode(it, Base64.DEFAULT) }

        val cipherFile = context.getDatabasePath(CIPHER_DB_NAME)
        val plaintextSource = isPlaintextSqliteDatabase(databaseFile)
        val migrationArtifactsExist = migrationArtifactsExist(databaseFile)
        if (hasArtifactsWithoutUsablePassphrase(
                storedPassphrase,
                databaseFile,
                cipherFile,
                plaintextSource,
                migrationArtifactsExist
            )
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

    private fun hasArtifactsWithoutUsablePassphrase(
        storedPassphrase: ByteArray?,
        databaseFile: File,
        cipherFile: File,
        plaintextSource: Boolean,
        migrationArtifactsExist: Boolean
    ): Boolean = storedPassphrase == null &&
        (migrationArtifactsExist || databaseArtifactsExist(cipherFile) ||
            (databaseArtifactsExist(databaseFile) && !plaintextSource))

    private fun migrationArtifactsExist(databaseFile: File): Boolean {
        val parent = databaseFile.parentFile
        val markerNames = listOf(CIPHER_MIGRATION_MARKER_NAME, CIPHER_MIGRATION_PREPARATION_NAME)
        val markerArtifactsExist = markerNames.any { name ->
            val marker = File(parent, name)
            listOf(marker, File(marker.path + ".bak"), File(marker.path + ".new")).any(File::exists)
        }
        val stageArtifactsExist = parent?.listFiles()?.any { file ->
            file.name.matches(Regex("${Regex.escape(CIPHER_DB_NAME)}\\.stage-[0-9a-f]{32}")) &&
                databaseArtifactsExist(file)
        } == true
        return markerArtifactsExist || stageArtifactsExist
    }

    internal fun databaseArtifactsExist(databaseFile: File): Boolean {
        val mainArtifactsExist = listOf(
                databaseFile,
                File("${databaseFile.absolutePath}-wal"),
                File("${databaseFile.absolutePath}-shm"),
                File("${databaseFile.absolutePath}-journal")
            ).any(File::exists)
        val masterJournalPrefix = databaseFile.name + "-mj"
        val masterJournalExists = databaseFile.parentFile
            ?.listFiles()
            ?.any { it.name.startsWith(masterJournalPrefix) } == true
        return mainArtifactsExist || masterJournalExists
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
        val everyArtifactDeleted = artifacts.all { file ->
            !file.exists() || file.delete() || !file.exists()
        }
        return everyArtifactDeleted && !databaseArtifactsExist(databaseFile)
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
            val preparationMarker = File(
                context.getDatabasePath(DB_NAME).parentFile,
                CIPHER_MIGRATION_PREPARATION_NAME
            )
            AtomicFile(preparationMarker).delete()
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
