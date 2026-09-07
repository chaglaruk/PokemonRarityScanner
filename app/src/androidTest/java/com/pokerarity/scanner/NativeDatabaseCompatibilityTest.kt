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
    @Test fun preservesEncryptedDatabaseAcrossNativeLibraryUpgrade() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mode = InstrumentationRegistry.getArguments().getString("mode")
        assumeTrue("Run prepare on 4.5.4, then verify after the in-place update", mode != null)
        require(mode in setOf("prepare", "verify"))
        System.loadLibrary("sqlcipher")
        val databaseName = "recognition_native_compat_test.db"
        if (mode == "verify") require(context.getDatabasePath(databaseName).isFile)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    check(mode == "prepare") { "Verification must reopen the existing database" }
                    db.execSQL("CREATE TABLE compatibility_marker (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
                    db.execSQL("INSERT INTO compatibility_marker VALUES (1, 'synthetic-data-from-4.5.4')")
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
            check(version.startsWith(if (mode == "prepare") "4.5.4" else "4.6.1"))
            db.query("SELECT value FROM compatibility_marker WHERE id = 1").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("synthetic-data-from-4.5.4", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
            if (mode == "verify") {
                db.execSQL("INSERT OR REPLACE INTO compatibility_marker VALUES (2, 'written-under-4.6.1')")
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
                reopened.writableDatabase.query("SELECT value FROM compatibility_marker ORDER BY id").use { cursor ->
                    check(cursor.moveToFirst())
                    assertEquals("synthetic-data-from-4.5.4", cursor.getString(0))
                    check(cursor.moveToNext())
                    assertEquals("written-under-4.6.1", cursor.getString(0))
                    assertFalse(cursor.moveToNext())
                }
            } finally { reopened.close() }
        }
    }
}
