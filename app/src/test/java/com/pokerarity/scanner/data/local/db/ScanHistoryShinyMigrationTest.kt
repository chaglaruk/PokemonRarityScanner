package com.pokerarity.scanner.data.local.db

import android.app.Application
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ScanHistoryShinyMigrationTest {

    @Test
    fun v5MigrationPreservesRowsAndDoesNotPromoteLegacyFalseToVerifiedNegative() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE `scan_history` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `pokemonName` TEXT,
                            `cp` INTEGER,
                            `hp` INTEGER,
                            `caughtDate` INTEGER,
                            `rawOcrText` TEXT NOT NULL,
                            `isShiny` INTEGER NOT NULL,
                            `isShadow` INTEGER NOT NULL,
                            `isLucky` INTEGER NOT NULL,
                            `hasCostume` INTEGER NOT NULL,
                            `rarityScore` INTEGER NOT NULL,
                            `rarityTier` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """INSERT INTO `scan_history`
                            (`id`, `timestamp`, `pokemonName`, `rawOcrText`, `isShiny`, `isShadow`,
                             `isLucky`, `hasCostume`, `rarityScore`, `rarityTier`)
                            VALUES (1, 123, 'Pikachu', 'retained-positive', 1, 0, 0, 0, 25, 'COMMON')
                        """.trimIndent()
                    )
                    db.execSQL(
                        """INSERT INTO `scan_history`
                            (`id`, `timestamp`, `pokemonName`, `rawOcrText`, `isShiny`, `isShadow`,
                             `isLucky`, `hasCostume`, `rarityScore`, `rarityTier`)
                            VALUES (2, 456, 'Meowth', 'retained-legacy-false', 0, 0, 0, 0, 30, 'UNCOMMON')
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    error("The fixture must remain at schema v5 until the tested migration is invoked")
                }
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)

        try {
            val database = helper.writableDatabase
            AppDatabase.MIGRATION_5_6.migrate(database)
            database.execSQL(
                """INSERT INTO `scan_history`
                    (`id`, `timestamp`, `pokemonName`, `rawOcrText`, `isShiny`, `isShadow`,
                     `isLucky`, `hasCostume`, `rarityScore`, `rarityTier`)
                    VALUES (3, 789, 'Bulbasaur', 'new-default', 0, 0, 0, 0, 10, 'COMMON')
                """.trimIndent()
            )

            database.query(
                "SELECT `id`, `pokemonName`, `rawOcrText`, `isShiny`, `shinyTruth` FROM `scan_history` ORDER BY `id`"
            ).use { cursor ->
                assertEquals(3, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
                assertEquals("Pikachu", cursor.getString(1))
                assertEquals("retained-positive", cursor.getString(2))
                assertEquals(1, cursor.getInt(3))
                assertEquals("TRUE", cursor.getString(4))

                assertTrue(cursor.moveToNext())
                assertEquals(2L, cursor.getLong(0))
                assertEquals("Meowth", cursor.getString(1))
                assertEquals("retained-legacy-false", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals("UNKNOWN", cursor.getString(4))

                assertTrue(cursor.moveToNext())
                assertEquals(3L, cursor.getLong(0))
                assertEquals("UNKNOWN", cursor.getString(4))
                assertFalse(cursor.moveToNext())
            }
        } finally {
            helper.close()
        }
    }
}
