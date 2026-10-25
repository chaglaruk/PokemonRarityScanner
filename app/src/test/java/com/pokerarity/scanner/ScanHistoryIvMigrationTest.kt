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

            database.query(
                "SELECT pokemonName, ivMin, ivMax, ivCandidateCount, ivSolveMode, ivLevelMin, ivLevelMax " +
                    "FROM scan_history WHERE id = 1"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Pikachu", cursor.getString(0))
                for (column in 1..6) assertTrue("legacy IV field $column stays unknown", cursor.isNull(column))
            }
        }
    }
}
