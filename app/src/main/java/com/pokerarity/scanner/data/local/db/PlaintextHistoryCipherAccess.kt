package com.pokerarity.scanner.data.local.db

import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherSQLiteDatabase

/** SQLCipher export and read-only validation for isolated History conversion stages. */
internal object PlaintextHistoryCipherAccess {
    private const val ATTACHED_PLAINTEXT_NAME = "legacy_plaintext"
    private const val ATTACHED_ENCRYPTED_NAME = "encrypted_copy"
    private const val SQLITE_FIRST_SUPPORTED_SCHEMA_VERSION = 3L
    private const val SQLITE_PRAGMA_PREFIX = "PRAGMA "

    fun exportAndValidate(
        sourceFile: File,
        encryptedFile: File,
        passphrase: ByteArray,
        beforeCipherExport: (CipherSQLiteDatabase) -> Unit,
        afterCipherExport: (File) -> Unit
    ): String {
        require(DatabasePassphraseStore.isPlaintextSqliteDatabase(sourceFile))
        require(sourceFile.isFile)

        val exporter = CipherSQLiteDatabase.create(null)
        val sourceFingerprint: String
        try {
            exporter.execSQL(
                "ATTACH DATABASE ? AS " + ATTACHED_PLAINTEXT_NAME + " KEY ''",
                arrayOf<Any>(sourceFile.absolutePath)
            )
            exporter.execSQL(
                "ATTACH DATABASE ? AS " + ATTACHED_ENCRYPTED_NAME + " KEY ?",
                arrayOf<Any>(encryptedFile.absolutePath, passphrase.clone())
            )

            val sourceVersion = HistoryDatabaseIntegrity.pragmaLong(exporter, ATTACHED_PLAINTEXT_NAME, "user_version")
            if (sourceVersion !in SQLITE_FIRST_SUPPORTED_SCHEMA_VERSION..AppDatabase.CURRENT_SCHEMA_VERSION.toLong()) {
                error("Unsupported plaintext History schema")
            }
            check(HistoryDatabaseIntegrity.integrityIsOk(exporter, ATTACHED_PLAINTEXT_NAME))
            sourceFingerprint = HistoryDatabaseIntegrity.logicalFingerprint(exporter, ATTACHED_PLAINTEXT_NAME)
            beforeCipherExport(exporter)
            exporter.rawQuery(
                "SELECT sqlcipher_export('" + ATTACHED_ENCRYPTED_NAME + "', '" + ATTACHED_PLAINTEXT_NAME + "')",
                null
            ).use { cursor -> check(cursor.moveToFirst()) }
            afterCipherExport(encryptedFile)

            // sqlcipher_export intentionally does not copy SQLite's user_version.
            exporter.execSQL(
                SQLITE_PRAGMA_PREFIX + quoteIdentifier(ATTACHED_ENCRYPTED_NAME) + ".user_version = " + sourceVersion
            )
            check(
                HistoryDatabaseIntegrity.pragmaLong(exporter, ATTACHED_ENCRYPTED_NAME, "user_version") == sourceVersion
            )
            check(HistoryDatabaseIntegrity.integrityIsOk(exporter, ATTACHED_ENCRYPTED_NAME))
            check(HistoryDatabaseIntegrity.cipherIntegrityIsOk(exporter, ATTACHED_ENCRYPTED_NAME))
            check(
                HistoryDatabaseIntegrity.databaseContentsMatch(
                    exporter,
                    ATTACHED_PLAINTEXT_NAME,
                    ATTACHED_ENCRYPTED_NAME
                )
            )
            check(HistoryDatabaseIntegrity.logicalFingerprint(exporter, ATTACHED_PLAINTEXT_NAME) == sourceFingerprint)
            exporter.execSQL("DETACH DATABASE " + ATTACHED_ENCRYPTED_NAME)
            exporter.execSQL("DETACH DATABASE " + ATTACHED_PLAINTEXT_NAME)
        } finally {
            exporter.close()
        }

        check(!DatabasePassphraseStore.isPlaintextSqliteDatabase(encryptedFile))
        check(isValidCipherDatabase(encryptedFile, passphrase, expectedVersion = null))
        return sourceFingerprint
    }

    fun isValidCipherDatabase(
        file: File,
        passphrase: ByteArray,
        expectedVersion: Int?,
        expectedFingerprint: String? = null
    ): Boolean {
        if (!file.isFile || DatabasePassphraseStore.isPlaintextSqliteDatabase(file)) return false
        return runCatching {
            val database = CipherSQLiteDatabase.openDatabase(
                file.absolutePath,
                passphrase.clone(),
                null,
                CipherSQLiteDatabase.OPEN_READONLY,
                null,
                null
            )
            try {
                HistoryDatabaseIntegrity.integrityIsOk(database, "main") &&
                    (expectedFingerprint == null || HistoryDatabaseIntegrity.cipherIntegrityIsOk(database, "main")) &&
                    (expectedVersion == null ||
                        HistoryDatabaseIntegrity.pragmaLong(database, "main", "user_version") ==
                            expectedVersion.toLong()) &&
                    (expectedFingerprint == null ||
                        HistoryDatabaseIntegrity.logicalFingerprint(database, "main") == expectedFingerprint)
            } finally {
                database.close()
            }
        }.getOrDefault(false)
    }

    fun cipherFingerprint(file: File, passphrase: ByteArray): String? {
        if (!file.isFile || DatabasePassphraseStore.isPlaintextSqliteDatabase(file)) return null
        return runCatching {
            val database = CipherSQLiteDatabase.openDatabase(
                file.absolutePath,
                passphrase.clone(),
                null,
                CipherSQLiteDatabase.OPEN_READONLY,
                null,
                null
            )
            try {
                check(HistoryDatabaseIntegrity.integrityIsOk(database, "main"))
                check(HistoryDatabaseIntegrity.cipherIntegrityIsOk(database, "main"))
                HistoryDatabaseIntegrity.logicalFingerprint(database, "main")
            } finally {
                database.close()
            }
        }.getOrNull()
    }

    fun validatedPlaintextFingerprint(sourceFile: File): String {
        val exporter = CipherSQLiteDatabase.create(null)
        try {
            exporter.execSQL(
                "ATTACH DATABASE ? AS " + ATTACHED_PLAINTEXT_NAME + " KEY ''",
                arrayOf<Any>(sourceFile.absolutePath)
            )
            val sourceVersion = HistoryDatabaseIntegrity.pragmaLong(exporter, ATTACHED_PLAINTEXT_NAME, "user_version")
            if (sourceVersion !in SQLITE_FIRST_SUPPORTED_SCHEMA_VERSION..AppDatabase.CURRENT_SCHEMA_VERSION.toLong()) {
                throw PlaintextHistoryConversionFailedException()
            }
            check(HistoryDatabaseIntegrity.integrityIsOk(exporter, ATTACHED_PLAINTEXT_NAME))
            return HistoryDatabaseIntegrity.logicalFingerprint(exporter, ATTACHED_PLAINTEXT_NAME)
        } finally {
            exporter.close()
        }
    }

    fun plaintextFingerprint(sourceFile: File): String {
        val exporter = CipherSQLiteDatabase.create(null)
        try {
            exporter.execSQL(
                "ATTACH DATABASE ? AS " + ATTACHED_PLAINTEXT_NAME + " KEY ''",
                arrayOf<Any>(sourceFile.absolutePath)
            )
            check(HistoryDatabaseIntegrity.integrityIsOk(exporter, ATTACHED_PLAINTEXT_NAME))
            return HistoryDatabaseIntegrity.logicalFingerprint(exporter, ATTACHED_PLAINTEXT_NAME)
        } finally {
            exporter.close()
        }
    }

    private fun quoteIdentifier(identifier: String): String =
        "\"" + identifier.replace("\"", "\"\"") + "\""
}
