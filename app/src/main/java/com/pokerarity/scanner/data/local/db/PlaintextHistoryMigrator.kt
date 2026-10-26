package com.pokerarity.scanner.data.local.db

import android.content.Context
import android.database.Cursor
import android.util.AtomicFile
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pokerarity.scanner.BuildConfig
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherSQLiteDatabase

/** Converts a legacy SQLite History file without replacing its source until the cipher copy opens. */
@Suppress("TooManyFunctions") // Private helpers keep the staged migration transaction and recovery paths together.
internal object PlaintextHistoryMigrator {
    private const val ACTIVATION_MARKER_PREFIX = "PRS_SQLCIPHER_HISTORY_V2"
    private const val HASH_NULL: Byte = 0
    private const val HASH_PRESENT: Byte = 1
    private const val ACTIVATION_MARKER_MIN_LINE_COUNT = 3
    private const val ACTIVATION_MARKER_MAX_LINE_COUNT = 4
    private const val SOURCE_FINGERPRINT_LINE_INDEX = 1
    private const val ENCRYPTED_FINGERPRINT_LINE_INDEX = 2
    private const val CLEANUP_STATE_LINE_INDEX = 3
    private const val BYTE_UNSIGNED_MASK = 0xff
    private const val HEX_BYTE_WIDTH = 2
    private const val HEX_RADIX = 16
    private const val SOURCE_CLEANUP_STARTED_MARKER = "SOURCE_CLEANUP_STARTED"
    private const val ATTACHED_PLAINTEXT_NAME = "legacy_plaintext"
    private const val ATTACHED_ENCRYPTED_NAME = "encrypted_copy"
    private const val SQLITE_MASTER_SQL_COLUMN = 3
    private const val SQLITE_FIRST_SUPPORTED_SCHEMA_VERSION = 3L
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
        val marker = readActivationMarker(request.markerFile)
            ?: migrationRequired()
        if (!isValidCipherDatabase(
                request.encryptedFile,
                request.passphrase,
                expectedVersion = AppDatabase.CURRENT_SCHEMA_VERSION,
                expectedFingerprint = marker.encryptedFingerprint
            )
        ) migrationRequired()

        val sourceArtifactsExist = DatabasePassphraseStore.databaseArtifactsExist(request.sourceFile)
        if (request.sourceFile.exists()) {
            if (!DatabasePassphraseStore.isPlaintextSqliteDatabase(request.sourceFile) ||
                plaintextFingerprint(request.sourceFile) != marker.sourceFingerprint
            ) migrationRequired()
        } else if (sourceArtifactsExist && !marker.sourceCleanupStarted) {
            migrationRequired()
        }
        return DatabasePassphraseStore.CIPHER_DB_NAME
    }

    private fun convertPlaintextSource(request: PlaintextHistoryMigrationRequest): String {
        // Only a valid activation marker authorizes cleanup of a previous encrypted stage.
        // An unmarked destination may be an older encrypted History with newer rows.
        if (request.encryptedArtifactsExist) throw ExistingPlaintextHistoryMigrationRequiredException()
        if (!request.allowNewConversion) {
            throw ExistingPlaintextHistoryMigrationRequiredException()
        }
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
    fun complete(
        context: Context,
        databaseName: String,
        openedDatabase: SupportSQLiteDatabase,
        markerCommitter: ((File, ByteArray) -> Unit)? = null,
        deleteSourceArtifacts: (File) -> Boolean = DatabasePassphraseStore::deleteDatabaseArtifacts
    ) {
        if (databaseName != DatabasePassphraseStore.CIPHER_DB_NAME) return

        val sourceFile = context.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val marker = readActivationMarker(markerFile(context)) ?: return
        validateOpenedCipherDatabase(openedDatabase, marker)
        cleanupValidatedSource(context, sourceFile, marker, markerCommitter, deleteSourceArtifacts)
        val markerPath = markerFile(context)
        AtomicFile(markerPath).delete()
        if (hasMarkerArtifacts(markerPath)) conversionFailed()
    }

    private fun validateOpenedCipherDatabase(openedDatabase: SupportSQLiteDatabase, marker: ActivationMarker) {
        if (pragmaLong(openedDatabase, "main", "user_version") != AppDatabase.CURRENT_SCHEMA_VERSION.toLong() ||
            !integrityIsOk(openedDatabase, "main") ||
            logicalFingerprint(openedDatabase, "main") != marker.encryptedFingerprint
        ) conversionFailed()
    }

    private fun cleanupValidatedSource(
        context: Context,
        sourceFile: File,
        marker: ActivationMarker,
        markerCommitter: ((File, ByteArray) -> Unit)?,
        deleteSourceArtifacts: (File) -> Boolean
    ) {
        if (DatabasePassphraseStore.databaseArtifactsExist(sourceFile)) {
            validateSourceBeforeCleanup(sourceFile, marker)
            if (!marker.sourceCleanupStarted) {
                val markerPath = markerFile(context)
                val cleanupStartedMarker = marker.copy(sourceCleanupStarted = true)
                if (markerCommitter == null) {
                    writeActivationMarker(markerPath, cleanupStartedMarker)
                } else {
                    markerCommitter(markerPath, activationMarkerContents(cleanupStartedMarker))
                }
                if (readActivationMarker(markerPath) != cleanupStartedMarker) conversionFailed()
            }
            val sourceArtifactsRemoved = deleteSourceArtifacts(sourceFile)
            if (!sourceArtifactsRemoved || DatabasePassphraseStore.databaseArtifactsExist(sourceFile)) {
                conversionFailed()
            }
        }
        if (DatabasePassphraseStore.databaseArtifactsExist(sourceFile)) conversionFailed()
    }

    private fun validateSourceBeforeCleanup(sourceFile: File, marker: ActivationMarker) {
        if (sourceFile.exists()) {
            if (!DatabasePassphraseStore.isPlaintextSqliteDatabase(sourceFile) ||
                plaintextFingerprint(sourceFile) != marker.sourceFingerprint
            ) conversionFailed()
        } else if (!marker.sourceCleanupStarted) {
            conversionFailed()
        }
    }

    private fun convertAndActivate(request: PlaintextHistoryMigrationRequest) {
        var ownsStage = false
        var activated = false
        try {
            request.encryptedFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) error("History staging directory unavailable")
            }
            if (!request.encryptedFile.createNewFile()) error("History staging file already exists")
            ownsStage = true
            val sourceFingerprint = exportAndValidate(
                request.sourceFile,
                request.encryptedFile,
                request.passphrase
            )
            AppDatabase.migrateStagedDatabase(
                request.context,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                request.passphrase
            )
            val encryptedFingerprint = cipherFingerprint(request.encryptedFile, request.passphrase)
            check(encryptedFingerprint != null)

            request.markerFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) error("History migration directory unavailable")
            }
            writeActivationMarker(request.markerFile, ActivationMarker(sourceFingerprint, encryptedFingerprint))
            activated = true
        } catch (_: Exception) {
            throw PlaintextHistoryConversionFailedException()
        } finally {
            if (!activated && ownsStage) removeIncompleteCipherStage(request.encryptedFile)
        }
    }

    private fun exportAndValidate(sourceFile: File, encryptedFile: File, passphrase: ByteArray): String {
        require(DatabasePassphraseStore.isPlaintextSqliteDatabase(sourceFile))
        require(sourceFile.isFile)

        val exporter = CipherSQLiteDatabase.create(null)
        var sourceFingerprint: String? = null
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
            sourceFingerprint = logicalFingerprint(exporter, ATTACHED_PLAINTEXT_NAME)
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
            check(logicalFingerprint(exporter, ATTACHED_PLAINTEXT_NAME) == sourceFingerprint)
            exporter.execSQL("DETACH DATABASE " + ATTACHED_ENCRYPTED_NAME)
            exporter.execSQL("DETACH DATABASE " + ATTACHED_PLAINTEXT_NAME)
        } finally {
            exporter.close()
        }

        check(!DatabasePassphraseStore.isPlaintextSqliteDatabase(encryptedFile))
        check(isValidCipherDatabase(encryptedFile, passphrase, expectedVersion = null))
        return checkNotNull(sourceFingerprint)
    }

    private fun isValidCipherDatabase(
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
                integrityIsOk(database, "main") &&
                    (expectedVersion == null ||
                        pragmaLong(database, "main", "user_version") == expectedVersion.toLong()) &&
                    (expectedFingerprint == null ||
                        logicalFingerprint(database, "main") == expectedFingerprint)
            } finally {
                database.close()
            }
        }.getOrDefault(false)
    }

    private fun cipherFingerprint(file: File, passphrase: ByteArray): String? {
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
                check(integrityIsOk(database, "main"))
                logicalFingerprint(database, "main")
            } finally {
                database.close()
            }
        }.getOrNull()
    }

    private data class ActivationMarker(
        val sourceFingerprint: String,
        val encryptedFingerprint: String,
        val sourceCleanupStarted: Boolean = false
    )

    private fun readActivationMarker(markerFile: File): ActivationMarker? = runCatching {
        val lines = AtomicFile(markerFile).readFully().toString(Charsets.US_ASCII).lines()
        if (lines.size !in ACTIVATION_MARKER_MIN_LINE_COUNT..ACTIVATION_MARKER_MAX_LINE_COUNT ||
            lines[0] != ACTIVATION_MARKER_PREFIX
        ) return null
        val source = lines[SOURCE_FINGERPRINT_LINE_INDEX]
        val encrypted = lines[ENCRYPTED_FINGERPRINT_LINE_INDEX]
        if (!source.matches(Regex("[0-9a-f]{64}")) || !encrypted.matches(Regex("[0-9a-f]{64}"))) {
            return null
        }
        val sourceCleanupStarted = when (lines.getOrNull(CLEANUP_STATE_LINE_INDEX)) {
            null -> false
            SOURCE_CLEANUP_STARTED_MARKER -> true
            else -> return null
        }
        ActivationMarker(source, encrypted, sourceCleanupStarted)
    }.getOrNull()

    private fun plaintextFingerprint(sourceFile: File): String {
        val exporter = CipherSQLiteDatabase.create(null)
        try {
            exporter.execSQL(
                "ATTACH DATABASE ? AS " + ATTACHED_PLAINTEXT_NAME + " KEY ''",
                arrayOf<Any>(sourceFile.absolutePath)
            )
            check(integrityIsOk(exporter, ATTACHED_PLAINTEXT_NAME))
            return logicalFingerprint(exporter, ATTACHED_PLAINTEXT_NAME)
        } finally {
            exporter.close()
        }
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

    /** Stable logical identity for a database snapshot, independent of page layout or encryption salt. */
    private fun logicalFingerprint(database: SupportSQLiteDatabase, schema: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digestLong(digest, pragmaLong(database, schema, "user_version"))
        schemaObjects(database, schema).forEach { item ->
            digestSchemaObject(digest, item)
        }
        tableNames(database, schema).forEach { table ->
            digestTableRows(digest, database, schema, table)
        }
        return digest.digest().joinToString(separator = "") { value ->
            (value.toInt() and BYTE_UNSIGNED_MASK).toString(HEX_RADIX).padStart(HEX_BYTE_WIDTH, '0')
        }
    }

    private fun digestNullableString(digest: MessageDigest, value: String?) {
        if (value == null) {
            digest.update(HASH_NULL)
        } else {
            digest.update(HASH_PRESENT)
            digestString(digest, value)
        }
    }

    private fun digestSchemaObject(digest: MessageDigest, item: SchemaObject) {
        digestString(digest, item.type)
        digestString(digest, item.name)
        digestString(digest, item.tableName)
        digestNullableString(digest, item.sql)
    }

    private fun digestTableRows(
        digest: MessageDigest,
        database: SupportSQLiteDatabase,
        schema: String,
        table: String
    ) {
        digestString(digest, table)
        val definition = checkNotNull(tableDefinition(database, schema, table))
        val columns = definition.first
        val withoutRowId = definition.second.contains("WITHOUT ROWID", ignoreCase = true)
        val rowIdAlias = listOf("_rowid_", "rowid", "oid").firstOrNull { alias ->
            columns.none { it.equals(alias, ignoreCase = true) }
        }
        val orderBy = when {
            withoutRowId || rowIdAlias == null -> columns.joinToString(", ", transform = ::quoteIdentifier)
            else -> quoteIdentifier(rowIdAlias)
        }
        val cursor = database.query(
            "SELECT * FROM " + quoteIdentifier(schema) + "." + quoteIdentifier(table) + " ORDER BY " + orderBy
        )
        cursor.use { rows ->
            digestLong(digest, rows.columnCount.toLong())
            while (rows.moveToNext()) digestRow(digest, rows)
        }
    }

    private fun digestRow(digest: MessageDigest, rows: Cursor) {
        for (column in 0 until rows.columnCount) digestValue(digest, rows, column)
    }

    private fun digestValue(digest: MessageDigest, rows: Cursor, column: Int) {
        when (rows.getType(column)) {
            Cursor.FIELD_TYPE_NULL -> digest.update(Cursor.FIELD_TYPE_NULL.toByte())
            Cursor.FIELD_TYPE_INTEGER -> {
                digest.update(Cursor.FIELD_TYPE_INTEGER.toByte())
                digestLong(digest, rows.getLong(column))
            }
            Cursor.FIELD_TYPE_FLOAT -> {
                digest.update(Cursor.FIELD_TYPE_FLOAT.toByte())
                digestLong(digest, rows.getDouble(column).toBits())
            }
            Cursor.FIELD_TYPE_STRING -> {
                digest.update(Cursor.FIELD_TYPE_STRING.toByte())
                digestString(digest, rows.getString(column))
            }
            Cursor.FIELD_TYPE_BLOB -> {
                digest.update(Cursor.FIELD_TYPE_BLOB.toByte())
                digestBytes(digest, rows.getBlob(column))
            }
            else -> error("Unknown SQLite value type")
        }
    }

    private fun migrationRequired(): Nothing = throw ExistingPlaintextHistoryMigrationRequiredException()

    private fun conversionFailed(): Nothing = throw PlaintextHistoryConversionFailedException()

    private fun digestString(digest: MessageDigest, value: String) =
        digestBytes(digest, value.toByteArray(Charsets.UTF_8))

    private fun digestLong(digest: MessageDigest, value: Long) {
        digest.update(ByteBuffer.allocate(java.lang.Long.BYTES).putLong(value).array())
    }

    private fun digestBytes(digest: MessageDigest, value: ByteArray) {
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value.size).array())
        digest.update(value)
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

    private fun writeActivationMarker(markerFile: File, marker: ActivationMarker) {
        val atomicFile = AtomicFile(markerFile)
        val stream = atomicFile.startWrite()
        try {
            stream.write(activationMarkerContents(marker))
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (failure: IOException) {
            atomicFile.failWrite(stream)
            throw failure
        }
        if (readActivationMarker(markerFile) != marker) conversionFailed()
        syncMarkerParentDirectory(markerFile)
        if (readActivationMarker(markerFile) != marker) conversionFailed()
    }

    private fun syncMarkerParentDirectory(markerFile: File) {
        val parent = markerFile.parentFile ?: conversionFailed()
        val parentSynced = runCatching {
            FileChannel.open(parent.toPath(), StandardOpenOption.READ).use { channel -> channel.force(true) }
        }.isSuccess
        if (!parentSynced) conversionFailed()
    }

    private fun activationMarkerContents(marker: ActivationMarker): ByteArray =
        listOf(ACTIVATION_MARKER_PREFIX, marker.sourceFingerprint, marker.encryptedFingerprint)
            .plus(if (marker.sourceCleanupStarted) listOf(SOURCE_CLEANUP_STARTED_MARKER) else emptyList())
            .joinToString("\n")
            .toByteArray(Charsets.US_ASCII)

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
