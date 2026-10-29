package com.pokerarity.scanner

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.util.DateParseUtils
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScanHistoryIvMigrationTest {

    @Test
    fun versionTwelveMigrationFreezesDisplayedDateAndPreservesLegacyEpochAndNulls() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
            val legacyEpoch = DateParseUtils.parseIsoDate("2019-07-12")!!.time
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE scan_history (" +
                                "id INTEGER PRIMARY KEY, caughtDate INTEGER, rarityAnalysisJson TEXT)"
                        )
                        db.execSQL("INSERT INTO scan_history (id, caughtDate) VALUES (1, ?)", arrayOf(legacyEpoch))
                        db.execSQL("INSERT INTO scan_history (id, caughtDate) VALUES (2, NULL)")
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = error("The migration fixture already uses schema version 12")
                })
                .build()
            FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
                val database = helper.writableDatabase
                AppDatabase.MIGRATION_12_13.migrate(database)

                TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
                database.query(
                    "SELECT id, caughtDate, caughtDateLocalDate FROM scan_history ORDER BY id"
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1L, cursor.getLong(0))
                    assertEquals(legacyEpoch, cursor.getLong(1))
                    assertEquals("2019-07-12", cursor.getString(2))
                    assertTrue(cursor.moveToNext())
                    assertEquals(2L, cursor.getLong(0))
                    assertNull(cursor.getString(1))
                    assertNull(cursor.getString(2))
                    assertTrue(!cursor.moveToNext())
                }
            }
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun versionTwelveBackfillProcessesMultipleBatchesWithSparseIdsAndNullDates() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
            val legacyEpoch = DateParseUtils.parseIsoDate("2019-07-12")!!.time
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE scan_history (" +
                                "id INTEGER PRIMARY KEY, caughtDate INTEGER, rarityAnalysisJson TEXT)"
                        )
                        repeat(513) { index ->
                            val id = index * 2L + 1L
                            if (index % 5 == 0) {
                                db.execSQL(
                                    "INSERT INTO scan_history (id, caughtDate) VALUES (?, NULL)",
                                    arrayOf(id)
                                )
                            } else {
                                db.execSQL(
                                    "INSERT INTO scan_history (id, caughtDate) VALUES (?, ?)",
                                    arrayOf(id, legacyEpoch)
                                )
                            }
                        }
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = error("The migration fixture already uses schema version 12")
                })
                .build()

            FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
                val database = helper.writableDatabase
                AppDatabase.MIGRATION_12_13.migrate(database)

                TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
                database.query(
                    "SELECT id, caughtDate, caughtDateLocalDate FROM scan_history ORDER BY id"
                ).use { cursor ->
                    var rowIndex = 0
                    while (cursor.moveToNext()) {
                        assertEquals(rowIndex * 2L + 1L, cursor.getLong(0))
                        if (rowIndex % 5 == 0) {
                            assertTrue(cursor.isNull(1))
                            assertTrue(cursor.isNull(2))
                        } else {
                            assertEquals(legacyEpoch, cursor.getLong(1))
                            assertEquals("2019-07-12", cursor.getString(2))
                        }
                        rowIndex++
                    }
                    assertEquals(513, rowIndex)
                }
            }
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun versionSevenRowsSurviveAndNewIvColumnsStartUnknown() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE scan_history (" +
                            "id INTEGER PRIMARY KEY, pokemonName TEXT NOT NULL, caughtDate INTEGER)"
                    )
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
            AppDatabase.MIGRATION_12_13.migrate(database)

            database.query(
                "SELECT pokemonName, ivMin, ivMax, ivCandidateCount, ivSolveMode, ivLevelMin, " +
                    "ivLevelMax, recognitionIdentityJson, eventConfidenceCode, eventConfidenceLabel, " +
                    "eventConfidenceDetail, mismatchGuardTitle, mismatchGuardDetail, recognitionSummary, " +
                    "rarityAnalysisJson, caughtDateLocalDate " +
                    "FROM scan_history WHERE id = 1"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Pikachu", cursor.getString(0))
                for (column in 1..6) assertTrue("legacy IV field $column stays unknown", cursor.isNull(column))
                assertTrue("legacy identity remains unknown", cursor.isNull(7))
                for (column in 8..15) {
                    assertTrue("legacy decision-support field $column stays unknown", cursor.isNull(column))
                }
            }
        }
    }
}
