package com.pokerarity.scanner

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.local.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanHistoryIvMigrationTest {

    @Test
    fun versionElevenMigrationPreservesExistingHistoryFields() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("""CREATE TABLE scan_history (
                        id INTEGER PRIMARY KEY, pokemonName TEXT, cp INTEGER, hp INTEGER,
                        ivMin INTEGER, ivMax INTEGER, ivCandidateCount INTEGER, ivSolveMode TEXT,
                        ivLevelMin REAL, ivLevelMax REAL, recognitionIdentityJson TEXT,
                        recognitionSummary TEXT)""")
                    db.execSQL("""INSERT INTO scan_history VALUES (
                        7, 'Pikachu', 640, 0, 82, 91, 3, 'RANGE', 30.0, 31.0,
                        '{"speciesStatus":"KNOWN"}', 'saved-summary')""")
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = error("The migration fixture already uses schema version 11")
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val database = helper.writableDatabase
            AppDatabase.MIGRATION_11_12.migrate(database)
            database.query(
                "SELECT pokemonName, cp, hp, ivMin, ivMax, ivCandidateCount, ivSolveMode, " +
                    "ivLevelMin, ivLevelMax, recognitionIdentityJson, recognitionSummary, " +
                    "ivLevelAlternatives FROM scan_history WHERE id = 7"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Pikachu", cursor.getString(0))
                assertEquals(640, cursor.getInt(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals(82, cursor.getInt(3))
                assertEquals(91, cursor.getInt(4))
                assertEquals(3, cursor.getInt(5))
                assertEquals("RANGE", cursor.getString(6))
                assertEquals(30f, cursor.getFloat(7))
                assertEquals(31f, cursor.getFloat(8))
                assertEquals("{\"speciesStatus\":\"KNOWN\"}", cursor.getString(9))
                assertEquals("saved-summary", cursor.getString(10))
                assertTrue("old rows remain unknown", cursor.isNull(11))
            }
        }
    }

    @Test
    fun versionSevenRowsSurviveAndNewIvColumnsStartUnknown() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE scan_history (id INTEGER PRIMARY KEY, pokemonName TEXT NOT NULL)")
                    db.execSQL("INSERT INTO scan_history (id, pokemonName) VALUES (1, 'Pikachu')")
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = error("The migration fixture already uses schema version 7")
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            val database = helper.writableDatabase
            AppDatabase.MIGRATION_7_8.migrate(database)
            AppDatabase.MIGRATION_8_9.migrate(database)
            AppDatabase.MIGRATION_9_10.migrate(database)
            AppDatabase.MIGRATION_10_11.migrate(database)
            AppDatabase.MIGRATION_11_12.migrate(database)

            database.query(
                "SELECT pokemonName, ivMin, ivMax, ivCandidateCount, ivSolveMode, ivLevelMin, " +
                    "ivLevelMax, recognitionIdentityJson, eventConfidenceCode, eventConfidenceLabel, " +
                    "eventConfidenceDetail, mismatchGuardTitle, mismatchGuardDetail, recognitionSummary, " +
                    "ivLevelAlternatives " +
                    "FROM scan_history WHERE id = 1"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Pikachu", cursor.getString(0))
                for (column in 1..6) assertTrue("legacy IV field $column stays unknown", cursor.isNull(column))
                assertTrue("legacy identity remains unknown", cursor.isNull(7))
                for (column in 8..13) {
                    assertTrue("legacy decision-support field $column stays unknown", cursor.isNull(column))
                }
                assertTrue("legacy discrete levels remain unknown", cursor.isNull(14))
            }
        }
    }
}
