package com.pokerarity.scanner.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistorySchemaThreeToFourMigrationTest {
    @Test
    fun addsOfflineTelemetryWithoutChangingExistingHistoryRows() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE scan_history (id INTEGER PRIMARY KEY, pokemonName TEXT NOT NULL)")
                    db.execSQL("INSERT INTO scan_history (id, pokemonName) VALUES (42, 'Pikachu')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    error("Fixture already uses schema version 3")
            })
            .build()

        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val database = helper.writableDatabase
            AppDatabase.MIGRATION_3_4.migrate(database)

            database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='offline_telemetry'")
                .use { assertTrue("the v4 table exists", it.moveToFirst()) }
            database.query("PRAGMA table_info(offline_telemetry)").use { cursor ->
                val columns = buildList {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                assertEquals(
                    listOf("id", "uploadId", "endpointUrl", "statusCode", "payloadJson", "createdAt", "flushedAt"),
                    columns
                )
            }
            database.query("SELECT id, pokemonName FROM scan_history WHERE id=42").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(42, cursor.getInt(0))
                assertEquals("Pikachu", cursor.getString(1))
            }
        }
    }
}
