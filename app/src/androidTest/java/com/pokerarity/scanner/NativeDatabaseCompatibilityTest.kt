package com.pokerarity.scanner

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** An isolated synthetic database, never the app's database or its passphrase. */
@RunWith(AndroidJUnit4::class)
class NativeDatabaseCompatibilityTest {
    @Test fun preservesSyntheticDatabaseAcrossLibraryUpdate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val mode = arguments.getString("mode")
        assumeTrue("Run prepare with the old driver, then verify after the in-place update", mode != null)
        require(mode in setOf("prepare", "verify"))
        val expectedCipherPrefix = requireNotNull(arguments.getString("expectedCipherPrefix"))
        System.loadLibrary("sqlcipher")
        val databaseName = "recognition_native_compat_test.db"
        if (mode == "prepare") {
            context.deleteDatabase(databaseName)
            File(context.filesDir, "native_database_compat_prepare.json").delete()
            File(context.filesDir, "native_database_compat_verify.json").delete()
        }
        if (mode == "verify") require(context.getDatabasePath(databaseName).isFile)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    check(mode == "prepare") { "Verification must reopen the existing database" }
                    db.execSQL("CREATE TABLE compatibility_marker (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
                    db.execSQL("INSERT INTO compatibility_marker VALUES (1, 'written-before-library-update')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    error("This test has no schema migration")
            }).build()
        // Public test-only material. No actual application key is read or copied.
        val factory = SupportOpenHelperFactory("public-native-compat-test-only".toByteArray())
        val helper = factory.create(configuration)
        try {
            val db = helper.writableDatabase
            val version = db.query("PRAGMA cipher_version").use { cursor ->
                check(cursor.moveToFirst()); cursor.getString(0)
            }
            check(version.startsWith(expectedCipherPrefix)) {
                "Expected cipher version prefix $expectedCipherPrefix, found $version"
            }
            db.query("PRAGMA integrity_check").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
            db.query("PRAGMA cipher_integrity_check").use { cursor ->
                assertFalse("SQLCipher page authentication must report no failures", cursor.moveToFirst())
            }
            db.query("SELECT id, value FROM compatibility_marker WHERE id = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals("written-before-library-update", cursor.getString(1))
                assertFalse(cursor.moveToNext())
            }
            if (mode == "verify") {
                db.execSQL("INSERT OR REPLACE INTO compatibility_marker VALUES (2, 'written-after-library-update')")
            }
            File(context.filesDir, "native_database_compat_$mode.json").writeText(
                JSONObject().put("mode", mode).put("cipherVersion", version).put("markerPreserved", true).toString())
        } finally {
            helper.close()
        }
        val header = context.getDatabasePath(databaseName).inputStream().use { stream -> ByteArray(16).also { stream.read(it) } }
        assertFalse(String(header).startsWith("SQLite format 3"))
        if (mode == "verify") {
            val reopened = SupportOpenHelperFactory("public-native-compat-test-only".toByteArray()).create(configuration)
            try {
                reopened.writableDatabase.query("SELECT id, value FROM compatibility_marker ORDER BY id").use { cursor ->
                    check(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                    assertEquals("written-before-library-update", cursor.getString(1))
                    check(cursor.moveToNext())
                    assertEquals(2, cursor.getInt(0))
                    assertEquals("written-after-library-update", cursor.getString(1))
                    assertFalse(cursor.moveToNext())
                }
            } finally { reopened.close() }
        }
    }
}
