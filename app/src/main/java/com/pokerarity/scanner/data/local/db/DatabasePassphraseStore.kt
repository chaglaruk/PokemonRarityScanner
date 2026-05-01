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
        prefs.getString(KEY_DB_PASSPHRASE, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return Base64.decode(it, Base64.DEFAULT) }

        val databaseFile = context.getDatabasePath(DB_NAME)
        val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }

        val finalPassphrase = if (databaseFile.exists()) {
            Log.w(TAG, "Encrypted passphrase missing for existing database, deleting local database before recreation")
            deleteDatabaseFiles(context)
            generated
        } else {
            generated
        }

        prefs.edit()
            .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(finalPassphrase, Base64.NO_WRAP))
            .apply()

        return finalPassphrase
    }

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
                Log.w(TAG, "Failed to delete database artifact: ${file.absolutePath}")
            }
        }
    }
}
