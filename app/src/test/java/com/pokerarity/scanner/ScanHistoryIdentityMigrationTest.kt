package com.pokerarity.scanner

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.local.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanHistoryIdentityMigrationTest {

    @Test
    fun versionEightHistoryRowsAndIvEvidenceSurviveWithUnknownIdentity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE scan_history (" +
                            "id INTEGER PRIMARY KEY, pokemonName TEXT NOT NULL, " +
                            "ivMin INTEGER, ivMax INTEGER, ivCandidateCount INTEGER, " +
                            "ivSolveMode TEXT, ivLevelMin REAL, ivLevelMax REAL)"
                    )
                    db.execSQL(
                        "INSERT INTO scan_history " +
                            "(id, pokemonName, ivMin, ivMax, ivCandidateCount, ivSolveMode, ivLevelMin, ivLevelMax) " +
                            "VALUES (42, 'Pikachu', 70, 88, 17, 'RANGE', 30.0, 31.0)"
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = error("The migration fixture already uses schema version 8")
            })
            .build()

        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val database = helper.writableDatabase
            AppDatabase.MIGRATION_8_9.migrate(database)

            database.query(
                "SELECT pokemonName, ivMin, ivMax, ivCandidateCount, ivSolveMode, " +
                    "ivLevelMin, ivLevelMax, recognitionIdentityJson FROM scan_history WHERE id = 42"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Pikachu", cursor.getString(0))
                assertEquals(70, cursor.getInt(1))
                assertEquals(88, cursor.getInt(2))
                assertEquals(17, cursor.getInt(3))
                assertEquals("RANGE", cursor.getString(4))
                assertEquals(30.0, cursor.getDouble(5), 0.001)
                assertEquals(31.0, cursor.getDouble(6), 0.001)
                assertNull(cursor.getString(7))
            }
        }
    }
}
