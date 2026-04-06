package com.pokerarity.scanner.data.local.db

import android.content.Context
import android.util.Base64
import android.util.Log
import com.pokerarity.scanner.data.local.SecurePreferencesFactory
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.security.MessageDigest
import java.security.SecureRandom

internal object DatabasePassphraseStore {
    private const val PREFS_NAME = "db_security"
    private const val KEY_DB_PASSPHRASE = "db_passphrase_v1"
    private const val DB_NAME = "pokerarity_db"
    private const val LEGACY_KEY_MATERIAL = "PokeRarityScanner_DB_v3"

    fun getOrCreate(context: Context): ByteArray {
        val prefs = SecurePreferencesFactory.create(context, PREFS_NAME)
        prefs.getString(KEY_DB_PASSPHRASE, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return Base64.decode(it, Base64.DEFAULT) }

        val databaseFile = context.getDatabasePath(DB_NAME)
        val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }

        val finalPassphrase = if (databaseFile.exists()) {
            migrateLegacyDatabaseIfNeeded(context, databaseFile.absolutePath, generated)
        } else {
            generated
        }

        prefs.edit()
            .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(finalPassphrase, Base64.NO_WRAP))
            .apply()

        return finalPassphrase
    }

    private fun migrateLegacyDatabaseIfNeeded(
        context: Context,
        databasePath: String,
        newPassphrase: ByteArray
    ): ByteArray {
        return runCatching {
            val legacyPassphrase = legacyPassphrase()
            val database = SQLiteDatabase.openDatabase(
                databasePath,
                legacyPassphrase,
                null,
                SQLiteDatabase.OPEN_READWRITE,
                null,
                null
            )
            try {
                database.changePassword(newPassphrase)
            } finally {
                database.close()
            }
            Log.i("AppDatabase", "Migrated legacy SQLCipher passphrase to keystore-backed secret")
            newPassphrase
        }.getOrElse { error ->
            Log.w("AppDatabase", "Legacy database rekey failed, keeping compatibility passphrase: ${error.message}")
            legacyPassphrase()
        }
    }

    private fun legacyPassphrase(): ByteArray {
        return MessageDigest.getInstance("SHA-256")
            .digest(LEGACY_KEY_MATERIAL.toByteArray(Charsets.UTF_8))
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
