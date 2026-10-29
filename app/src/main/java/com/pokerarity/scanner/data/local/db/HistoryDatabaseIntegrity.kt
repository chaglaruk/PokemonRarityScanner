package com.pokerarity.scanner.data.local.db

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Integrity checks and stable logical snapshots used to validate a History conversion. */
@Suppress("TooManyFunctions")
// Typed SQLite value hashing stays together to preserve null and storage-class distinctions.
internal object HistoryDatabaseIntegrity {
    private const val HASH_NULL: Byte = 0
    private const val HASH_PRESENT: Byte = 1
    private const val BYTE_UNSIGNED_MASK = 0xff
    private const val HEX_BYTE_WIDTH = 2
    private const val HEX_RADIX = 16
    private const val SQLITE_MASTER_SQL_COLUMN = 3
    private const val SQLITE_PRAGMA_PREFIX = "PRAGMA "

    internal fun integrityIsOk(database: SupportSQLiteDatabase, schema: String): Boolean =
        database.query(SQLITE_PRAGMA_PREFIX + quoteIdentifier(schema) + ".integrity_check").use { cursor ->
            cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true) && !cursor.moveToNext()
        }

    internal fun cipherIntegrityIsOk(database: SupportSQLiteDatabase, schema: String): Boolean =
        database.query(SQLITE_PRAGMA_PREFIX + quoteIdentifier(schema) + ".cipher_integrity_check").use { cursor ->
            !cursor.moveToFirst()
        }

    internal fun pragmaLong(database: SupportSQLiteDatabase, schema: String, pragma: String): Long =
        database.query(SQLITE_PRAGMA_PREFIX + quoteIdentifier(schema) + "." + pragma).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    internal fun databaseContentsMatch(
        database: SupportSQLiteDatabase,
        leftSchema: String,
        rightSchema: String
    ): Boolean = schemaObjects(database, leftSchema) == schemaObjects(database, rightSchema) &&
        tableRowsMatch(database, leftSchema, rightSchema)

    /** Stable logical identity independent of page layout, WAL checkpointing, or encryption salt. */
    internal fun logicalFingerprint(database: SupportSQLiteDatabase, schema: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digestLong(digest, pragmaLong(database, schema, "user_version"))
        schemaObjects(database, schema).forEach { item -> digestSchemaObject(digest, item) }
        tableNames(database, schema).forEach { table -> digestTableRows(digest, database, schema, table) }
        return digest.digest().joinToString(separator = "") { value ->
            (value.toInt() and BYTE_UNSIGNED_MASK).toString(HEX_RADIX).padStart(HEX_BYTE_WIDTH, '0')
        }
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
            "SELECT type, name, tbl_name, sql FROM " + quoteIdentifier(schema) + ".sqlite_master ORDER BY type, name"
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
        return left.use { leftCursor -> right.use { rightCursor -> compareCursors(leftCursor, rightCursor) } }
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

    private fun digestString(digest: MessageDigest, value: String) =
        digestBytes(digest, value.toByteArray(Charsets.UTF_8))

    private fun digestLong(digest: MessageDigest, value: Long) {
        digest.update(ByteBuffer.allocate(java.lang.Long.BYTES).putLong(value).array())
    }

    private fun digestBytes(digest: MessageDigest, value: ByteArray) {
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value.size).array())
        digest.update(value)
    }

    private fun quoteIdentifier(identifier: String): String =
        "\"" + identifier.replace("\"", "\"\"") + "\""

    private fun quoteLiteral(value: String): String = "'" + value.replace("'", "''") + "'"
}
