package com.pokerarity.scanner.data.local.db

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaintextHistoryCipherValidationTest {
    @Test
    fun completedConversionStillChecksCipherPageIntegrityAfterOpen() {
        val queries = mutableListOf<String>()
        val database = supportDatabase(queries)

        PlaintextHistoryMigrator.validateOpenedCipherDatabase(
            database,
            ActivationMarker(
                sourceFingerprint = "source",
                encryptedFingerprint = "cipher",
                conversionComplete = true
            )
        )

        assertTrue(queries.any { it.contains("cipher_integrity_check", ignoreCase = true) })
    }

    @Test(expected = PlaintextHistoryConversionFailedException::class)
    fun completedConversionRejectsCipherPageIntegrityFailure() {
        val database = supportDatabase(mutableListOf(), cipherIntegrityFailed = true)

        PlaintextHistoryMigrator.validateOpenedCipherDatabase(
            database,
            ActivationMarker(
                sourceFingerprint = "source",
                encryptedFingerprint = "cipher",
                conversionComplete = true
            )
        )
    }

    private fun supportDatabase(
        queries: MutableList<String>,
        cipherIntegrityFailed: Boolean = false
    ): SupportSQLiteDatabase =
        Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, arguments ->
            when (method.name) {
                "query" -> {
                    val sql = arguments?.get(0) as String
                    queries += sql
                    cursorFor(sql, cipherIntegrityFailed)
                }
                else -> defaultValue(method.returnType)
            }
        } as SupportSQLiteDatabase

    private fun cursorFor(sql: String, cipherIntegrityFailed: Boolean): Cursor =
        Proxy.newProxyInstance(
            Cursor::class.java.classLoader,
            arrayOf(Cursor::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "moveToFirst" -> if (sql.contains("cipher_integrity_check", ignoreCase = true)) {
                    cipherIntegrityFailed
                } else {
                    true
                }
                "moveToNext" -> false
                "getString" -> "ok"
                "getLong" -> AppDatabase.CURRENT_SCHEMA_VERSION.toLong()
                else -> defaultValue(method.returnType)
            }
        } as Cursor

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> '\u0000'
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0.0f
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Short.TYPE -> 0.toShort()
        else -> null
    }
}
