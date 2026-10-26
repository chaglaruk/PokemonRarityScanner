package com.pokerarity.scanner.data.local.db

import android.content.Context
import android.database.Cursor
import android.util.AtomicFile
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pokerarity.scanner.BuildConfig
import java.io.File
import java.io.IOException
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherSQLiteDatabase

/** Converts a legacy SQLite History file without replacing its source until the cipher copy opens. */
@Suppress("TooManyFunctions") // Private helpers keep the staged migration transaction and recovery paths together.
internal object PlaintextHistoryMigrator {
    private val ACTIVATION_MARKER_CONTENT = "PRS_SQLCIPHER_HISTORY_V1".toByteArray(Charsets.US_ASCII)
    private const val ATTACHED_PLAINTEXT_NAME = "legacy_plaintext"
    private const val ATTACHED_ENCRYPTED_NAME = "encrypted_copy"
    private const val SQLITE_MASTER_SQL_COLUMN = 3
    private const val SQLITE_FIRST_SUPPORTED_SCHEMA_VERSION = 1L
    private const val SQLITE_PRAGMA_PREFIX = "PRAGMA "

    fun prepare(context: Context, passphrase: ByteArray): String =
        prepareInternal(context, passphrase, allowNewConversion = false)

    /** Exercise the migration algorithm only from isolated instrumentation fixtures. */
    internal fun prepareForIsolatedValidation(context: Context, passphrase: ByteArray): String {
        check(BuildConfig.DEBUG) { "Plaintext History conversion validation is debug-only" }
        return prepareInternal(context, passphrase, allowNewConversion = true)
    }

    private fun prepareInternal(context: Context, passphrase: ByteArray, allowNewConversion: Boolean): String {
        val sourceFile = context.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encryptedFile = context.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val request = PlaintextHistoryMigrationRequest(
            context = context,
            sourceFile = sourceFile,
            encryptedFile = encryptedFile,
            markerFile = markerFile(context),
            passphrase = passphrase,
            sourceIsPlaintext = DatabasePassphraseStore.isPlaintextSqliteDatabase(sourceFile),
            encryptedArtifactsExist = DatabasePassphraseStore.databaseArtifactsExist(encryptedFile),
            allowNewConversion = allowNewConversion
        )
        return when {
            hasMarkerArtifacts(request.markerFile) -> resumeMarkedConversion(request)
            request.sourceIsPlaintext -> convertPlaintextSource(request)
            else -> openExistingDatabase(request)
        }
    }

    private fun resumeMarkedConversion(request: PlaintextHistoryMigrationRequest): String {
        if (hasActiveMarker(request.markerFile) && request.encryptedFile.isFile &&
            isValidCipherDatabase(request.encryptedFile, request.passphrase, expectedVersion = null)
        ) return DatabasePassphraseStore.CIPHER_DB_NAME

        if (!request.sourceIsPlaintext || !request.allowNewConversion) {
            throw ExistingPlaintextHistoryMigrationRequiredException()
        }
        AtomicFile(request.markerFile).delete()
        removeIncompleteCipherStage(request.encryptedFile)
        convertAndActivate(request)
        return DatabasePassphraseStore.CIPHER_DB_NAME
    }

    private fun convertPlaintextSource(request: PlaintextHistoryMigrationRequest): String {
        if (!request.allowNewConversion) {
            if (request.encryptedArtifactsExist) removeIncompleteCipherStage(request.encryptedFile)
            throw ExistingPlaintextHistoryMigrationRequiredException()
        }
        if (request.encryptedArtifactsExist) removeIncompleteCipherStage(request.encryptedFile)
        convertAndActivate(request)
        return DatabasePassphraseStore.CIPHER_DB_NAME
    }

    private fun openExistingDatabase(request: PlaintextHistoryMigrationRequest): String {
        if (request.encryptedArtifactsExist) {
            // A destination without the marker and without its plaintext source is ambiguous.
            throw ExistingPlaintextHistoryMigrationRequiredException()
        }
        if (!request.sourceFile.exists() && DatabasePassphraseStore.databaseArtifactsExist(request.sourceFile)) {
            throw ExistingPlaintextHistoryMigrationRequiredException()
        }
        return DatabasePassphraseStore.DB_NAME
    }

    /** Remove plaintext only after the promoted encrypted database has opened through production Room. */
    fun complete(context: Context, databaseName: String) {
        if (databaseName != DatabasePassphraseStore.CIPHER_DB_NAME) return

        val sourceFile = context.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        if (!hasActiveMarker(markerFile(context))) throw PlaintextHistoryConversionFailedException()

        // The marker is written only after the encrypted copy has been validated and Room-migrated.
        // It also lets a later launch finish cleanup if the process stopped mid-delete.
        val sourceArtifactsRemoved = DatabasePassphraseStore.deleteDatabaseArtifacts(sourceFile)
        if (!sourceArtifactsRemoved || DatabasePassphraseStore.databaseArtifactsExist(sourceFile)) {
            throw PlaintextHistoryConversionFailedException()
        }
    }

    private fun convertAndActivate(request: PlaintextHistoryMigrationRequest) {
        var activated = false
        try {
            exportAndValidate(request.sourceFile, request.encryptedFile, request.passphrase)
            AppDatabase.migrateStagedDatabase(
                request.context,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                request.passphrase
            )
            check(isValidCipherDatabase(request.encryptedFile, request.passphrase, AppDatabase.CURRENT_SCHEMA_VERSION))

            request.markerFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) error("History migration directory unavailable")
            }
            writeActivationMarker(request.markerFile)
            activated = true
        } catch (_: Exception) {
            throw PlaintextHistoryConversionFailedException()
        } finally {
            if (!activated) removeIncompleteCipherStage(request.encryptedFile)
        }
    }

    private fun exportAndValidate(sourceFile: File, encryptedFile: File, passphrase: ByteArray) {
        require(DatabasePassphraseStore.isPlaintextSqliteDatabase(sourceFile))
        require(sourceFile.isFile)
        encryptedFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) error("History staging directory unavailable")
        }
        if (!encryptedFile.createNewFile()) error("History staging file already exists")

        val exporter = CipherSQLiteDatabase.create(null)
        try {
            exporter.execSQL(
                "ATTACH DATABASE ? AS " + ATTACHED_PLAINTEXT_NAME + " KEY ''",
                arrayOf<Any>(sourceFile.absolutePath)
            )
            exporter.execSQL(
                "ATTACH DATABASE ? AS " + ATTACHED_ENCRYPTED_NAME + " KEY ?",
                arrayOf<Any>(encryptedFile.absolutePath, passphrase.clone())
            )

            val sourceVersion = pragmaLong(exporter, ATTACHED_PLAINTEXT_NAME, "user_version")
            if (sourceVersion !in SQLITE_FIRST_SUPPORTED_SCHEMA_VERSION..AppDatabase.CURRENT_SCHEMA_VERSION.toLong()) {
                error("Unsupported plaintext History schema")
            }
            check(integrityIsOk(exporter, ATTACHED_PLAINTEXT_NAME))
            exporter.rawQuery(
                "SELECT sqlcipher_export('" + ATTACHED_ENCRYPTED_NAME + "', '" + ATTACHED_PLAINTEXT_NAME + "')",
                null
            ).use { cursor -> check(cursor.moveToFirst()) }

            // sqlcipher_export intentionally does not copy SQLite's user_version.
            exporter.execSQL(
                SQLITE_PRAGMA_PREFIX + quoteIdentifier(ATTACHED_ENCRYPTED_NAME) + ".user_version = " + sourceVersion
            )
            check(pragmaLong(exporter, ATTACHED_ENCRYPTED_NAME, "user_version") == sourceVersion)
            check(integrityIsOk(exporter, ATTACHED_ENCRYPTED_NAME))
            check(schemaObjects(exporter, ATTACHED_PLAINTEXT_NAME) == schemaObjects(exporter, ATTACHED_ENCRYPTED_NAME))
            check(tableRowsMatch(exporter, ATTACHED_PLAINTEXT_NAME, ATTACHED_ENCRYPTED_NAME))
            exporter.execSQL("DETACH DATABASE " + ATTACHED_ENCRYPTED_NAME)
            exporter.execSQL("DETACH DATABASE " + ATTACHED_PLAINTEXT_NAME)
        } finally {
            exporter.close()
        }

        check(!DatabasePassphraseStore.isPlaintextSqliteDatabase(encryptedFile))
        check(isValidCipherDatabase(encryptedFile, passphrase, expectedVersion = null))
    }

    private fun isValidCipherDatabase(file: File, passphrase: ByteArray, expectedVersion: Int?): Boolean {
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
                integrityIsOk(database, "main") &&
                    (expectedVersion == null ||
                        pragmaLong(database, "main", "user_version") == expectedVersion.toLong())
            } finally {
                database.close()
            }
        }.getOrDefault(false)
    }

    private fun integrityIsOk(database: SupportSQLiteDatabase, schema: String): Boolean =
        database.query(SQLITE_PRAGMA_PREFIX + quoteIdentifier(schema) + ".integrity_check").use { cursor ->
            cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true) && !cursor.moveToNext()
        }

    private fun pragmaLong(database: SupportSQLiteDatabase, schema: String, pragma: String): Long =
        database.query(SQLITE_PRAGMA_PREFIX + quoteIdentifier(schema) + "." + pragma).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private data class SchemaObject(
        val type: String,
        val name: String,
        val tableName: String,
        val sql: String?
    )

    private fun schemaObjects(database: SupportSQLiteDatabase, schema: String): List<SchemaObject> {
        val result = mutableListOf<SchemaObject>()
        database.query(
            "SELECT type, name, tbl_name, sql FROM " + quoteIdentifier(schema) +
                ".sqlite_master ORDER BY type, name"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += SchemaObject(
                    type = cursor.getString(0),
                    name = cursor.getString(1),
                    tableName = cursor.getString(2),
                    sql = if (cursor.isNull(SQLITE_MASTER_SQL_COLUMN)) {
                        null
                    } else {
                        cursor.getString(SQLITE_MASTER_SQL_COLUMN)
                    }
                )
            }
        }
        return result
    }

    private fun tableRowsMatch(database: SupportSQLiteDatabase, leftSchema: String, rightSchema: String): Boolean {
        val leftTables = tableNames(database, leftSchema)
        val rightTables = tableNames(database, rightSchema)
        if (leftTables != rightTables) return false
        return leftTables.all { table -> tableRowsMatch(database, leftSchema, rightSchema, table) }
    }

    private fun tableNames(database: SupportSQLiteDatabase, schema: String): List<String> =
        buildList {
            database.query(
                "SELECT name FROM " + quoteIdentifier(schema) + ".sqlite_master WHERE type = 'table' ORDER BY name"
            ).use { cursor -> while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun tableRowsMatch(
        database: SupportSQLiteDatabase,
        leftSchema: String,
        rightSchema: String,
        table: String
    ): Boolean {
        val leftDefinition = tableDefinition(database, leftSchema, table)
        val rightDefinition = tableDefinition(database, rightSchema, table)
        if (leftDefinition == null || rightDefinition == null || leftDefinition.first != rightDefinition.first) {
            return false
        }
        val columns = leftDefinition.first
        val withoutRowId = leftDefinition.second.contains("WITHOUT ROWID", ignoreCase = true)
        val rowIdAlias = listOf("_rowid_", "rowid", "oid").firstOrNull { alias ->
            columns.none { it.equals(alias, ignoreCase = true) }
        }
        val orderBy = when {
            withoutRowId || rowIdAlias == null -> columns.joinToString(", ", transform = ::quoteIdentifier)
            else -> quoteIdentifier(rowIdAlias)
        }
        val leftSql = "SELECT * FROM " + quoteIdentifier(leftSchema) + "." +
            quoteIdentifier(table) + " ORDER BY " + orderBy
        val rightSql = "SELECT * FROM " + quoteIdentifier(rightSchema) + "." +
            quoteIdentifier(table) + " ORDER BY " + orderBy
        val left = database.query(leftSql)
        val right = database.query(rightSql)
        return left.use { leftCursor ->
            right.use { rightCursor -> compareCursors(leftCursor, rightCursor) }
        }
    }

    private fun tableDefinition(
        database: SupportSQLiteDatabase,
        schema: String,
        table: String
    ): Pair<List<String>, String>? {
        val columns = mutableListOf<String>()
        database.query(
            SQLITE_PRAGMA_PREFIX + quoteIdentifier(schema) + ".table_info(" + quoteLiteral(table) + ")"
        ).use { cursor -> while (cursor.moveToNext()) columns += cursor.getString(1) }
        if (columns.isEmpty()) return null

        val createCursor = database.query(
            "SELECT sql FROM " + quoteIdentifier(schema) +
                ".sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        )
        val createSql = createCursor.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
        return createSql?.let { columns to it }
    }

    private fun compareCursors(left: Cursor, right: Cursor): Boolean {
        var rowsMatch = left.columnNames.contentEquals(right.columnNames)
        while (rowsMatch && left.moveToNext()) {
            rowsMatch = right.moveToNext() && (0 until left.columnCount).all { column ->
                valuesMatch(left, right, column)
            }
        }
        return rowsMatch && !right.moveToNext()
    }

    private fun valuesMatch(left: Cursor, right: Cursor, column: Int): Boolean {
        if (left.getType(column) != right.getType(column)) return false
        return when (left.getType(column)) {
            Cursor.FIELD_TYPE_NULL -> true
            Cursor.FIELD_TYPE_INTEGER -> left.getLong(column) == right.getLong(column)
            Cursor.FIELD_TYPE_FLOAT -> left.getDouble(column).toBits() == right.getDouble(column).toBits()
            Cursor.FIELD_TYPE_STRING -> left.getString(column) == right.getString(column)
            Cursor.FIELD_TYPE_BLOB -> left.getBlob(column).contentEquals(right.getBlob(column))
            else -> false
        }
    }

    private fun removeIncompleteCipherStage(file: File) {
        val deleted = DatabasePassphraseStore.deleteDatabaseArtifacts(file)
        if (!deleted || DatabasePassphraseStore.databaseArtifactsExist(file)) {
            throw PlaintextHistoryConversionFailedException()
        }
    }

    private fun writeActivationMarker(markerFile: File) {
        val atomicFile = AtomicFile(markerFile)
        val stream = atomicFile.startWrite()
        try {
            stream.write(ACTIVATION_MARKER_CONTENT)
            atomicFile.finishWrite(stream)
        } catch (failure: IOException) {
            atomicFile.failWrite(stream)
            throw failure
        }
    }

    private fun hasActiveMarker(markerFile: File): Boolean = runCatching {
        AtomicFile(markerFile).readFully().contentEquals(ACTIVATION_MARKER_CONTENT)
    }.getOrDefault(false)

    private fun hasMarkerArtifacts(markerFile: File): Boolean =
        markerFile.exists() || File(markerFile.path + ".bak").exists() || File(markerFile.path + ".new").exists()

    private fun markerFile(context: Context): File =
        File(
            context.getDatabasePath(DatabasePassphraseStore.DB_NAME).parentFile,
            DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME
        )

    private fun quoteIdentifier(identifier: String): String =
        "\"" + identifier.replace("\"", "\"\"") + "\""

    private fun quoteLiteral(value: String): String =
        "'" + value.replace("'", "''") + "'"
}

private data class PlaintextHistoryMigrationRequest(
    val context: Context,
    val sourceFile: File,
    val encryptedFile: File,
    val markerFile: File,
    val passphrase: ByteArray,
    val sourceIsPlaintext: Boolean,
    val encryptedArtifactsExist: Boolean,
    val allowNewConversion: Boolean
)

internal class PlaintextHistoryConversionFailedException : IllegalStateException(
    "The legacy History database could not be converted safely. The original files were preserved."
)
