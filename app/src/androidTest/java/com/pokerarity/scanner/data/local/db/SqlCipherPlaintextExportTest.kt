package com.pokerarity.scanner.data.local.db

import android.content.Context
import android.content.ContextWrapper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqlCipherPlaintextExportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sqlCipherExportReadsCommittedWalRowsAndEncryptsTheStagedDatabase() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val sourceFile = target.getDatabasePath("plaintext_history")
        val encryptedFile = target.getDatabasePath("encrypted_history")
        // Mirror DatabasePassphraseStore's arbitrary 32-byte key representation.
        val passphrase = ByteArray(32) { index -> (index * 37 + 129).toByte() }
        val helper = plaintextHelper(target)
        helper.setWriteAheadLoggingEnabled(true)

        try {
            val plaintext = helper.writableDatabase
            assertEquals("wal", pragmaString(plaintext, "journal_mode").lowercase())
            plaintext.execSQL("CREATE TABLE scan_history (id INTEGER PRIMARY KEY, note TEXT, caughtAt INTEGER)")
            plaintext.execSQL(
                "INSERT INTO scan_history (id, note, caughtAt) VALUES (?, ?, ?)",
                arrayOf<Any>(431L, "synthetic row", 123456789L)
            )
            assertTrue("fixture must keep a committed row in its WAL", File("${sourceFile.path}-wal").length() > 32)
            assertTrue("staging directory must be writable", encryptedFile.createNewFile())

            val exporter = CipherSQLiteDatabase.create(null)
            try {
                exporter.execSQL(
                    "ATTACH DATABASE ? AS plaintext KEY ''",
                    arrayOf<Any>(sourceFile.absolutePath)
                )
                exporter.execSQL(
                    "ATTACH DATABASE ? AS encrypted KEY ?",
                    arrayOf<Any>(encryptedFile.absolutePath, passphrase)
                )
                exporter.rawQuery("SELECT sqlcipher_export('encrypted', 'plaintext')", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                }
                exporter.execSQL("DETACH DATABASE encrypted")
                exporter.execSQL("DETACH DATABASE plaintext")
            } finally {
                exporter.close()
            }
            assertTrue("plaintext source must remain available", sourceFile.isFile)
            assertEquals(3, pragmaString(plaintext, "user_version").toInt())
            plaintext.query("SELECT id, note, caughtAt FROM scan_history").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(431L, cursor.getLong(0))
                assertEquals("synthetic row", cursor.getString(1))
                assertEquals(123456789L, cursor.getLong(2))
            }

            val header = encryptedFile.inputStream().use { input -> ByteArray(16).also { input.read(it) } }
            assertFalse(String(header, Charsets.US_ASCII).startsWith("SQLite format 3"))

            val encrypted = CipherSQLiteDatabase.openDatabase(
                encryptedFile.absolutePath,
                passphrase,
                null,
                CipherSQLiteDatabase.OPEN_READONLY,
                null,
                null
            )
            try {
                assertEquals("ok", pragmaString(encrypted, "integrity_check").lowercase())
                encrypted.rawQuery("SELECT id, note, caughtAt FROM scan_history", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(431L, cursor.getLong(0))
                    assertEquals("synthetic row", cursor.getString(1))
                    assertEquals(123456789L, cursor.getLong(2))
                    assertFalse(cursor.moveToNext())
                }
            } finally {
                encrypted.close()
            }
        } finally {
            helper.close()
            CipherSQLiteDatabase.deleteDatabase(sourceFile)
            CipherSQLiteDatabase.deleteDatabase(encryptedFile)
            Files.deleteIfExists(sourceFile.parentFile!!.toPath())
        }
    }

    private fun plaintextHelper(target: Context): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(target)
                .name("plaintext_history")
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                        error("Synthetic export fixture does not migrate")
                })
                .build()
        )

    private fun pragmaString(database: SupportSQLiteDatabase, pragma: String): String =
        database.query("PRAGMA $pragma").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun isolatedContext(): Context {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "sqlcipher-export-").toFile()
        return object : ContextWrapper(context) {
            override fun getDatabasePath(name: String): File = File(directory, name)
            override fun getApplicationContext(): Context = this
        }
    }
}
