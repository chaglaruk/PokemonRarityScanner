package com.pokerarity.scanner

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.local.db.SqlCipherInitializer
import java.io.File
import java.time.LocalDate
import java.util.Date
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises a Room-authored History file across an in-place SQLCipher library update. */
@RunWith(AndroidJUnit4::class)
class RoomNativeDatabaseUpgradeTest {
    @Test
    fun preservesRoomHistoryAcrossSqlCipherUpgrade() {
        val arguments = InstrumentationRegistry.getArguments()
        val mode = arguments.getString("mode")
        assumeTrue("Run prepare on the old driver and verify after the package update", mode != null)
        require(mode in setOf(PREPARE_MODE, VERIFY_MODE))

        val expectedCipherPrefix = requireNotNull(arguments.getString("expectedCipherPrefix"))
        val runId = requireNotNull(arguments.getString("runId"))
        require(RUN_ID_PATTERN.matches(runId))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "room_native_upgrade_$runId.db"
        val databaseFile = context.getDatabasePath(databaseName)
        if (mode == PREPARE_MODE) {
            assertDatabaseArtifactsAbsent(databaseFile)
        } else {
            assertTrue("The synthetic Room database must survive the package update", databaseFile.isFile)
        }

        SqlCipherInitializer.ensureLoaded()
        val database = openDatabase(context, databaseName)
        try {
            assertCipherAndPageIntegrity(database.openHelper.writableDatabase, expectedCipherPrefix)
            if (mode == PREPARE_MODE) {
                runBlocking {
                    assertEquals(ROOM_ROW_ID, database.scanHistoryDao().insert(historyRow()))
                    assertEquals(historyRow(), database.scanHistoryDao().getById(ROOM_ROW_ID))
                }
            } else {
                runBlocking {
                    assertEquals(historyRow(), database.scanHistoryDao().getById(ROOM_ROW_ID))
                    assertEquals(
                        1,
                        database.scanHistoryDao().update(
                            historyRow().copy(recognitionSummary = "written-after-native-upgrade")
                        )
                    )
                }
            }
        } finally {
            database.close()
        }

        if (mode == VERIFY_MODE) {
            val reopened = openDatabase(context, databaseName)
            try {
                runBlocking {
                    assertEquals(
                        historyRow().copy(recognitionSummary = "written-after-native-upgrade"),
                        reopened.scanHistoryDao().getById(ROOM_ROW_ID)
                    )
                }
                assertCipherAndPageIntegrity(reopened.openHelper.writableDatabase, expectedCipherPrefix)
            } finally {
                reopened.close()
            }
            assertTrue("Only the uniquely named synthetic database is removed", context.deleteDatabase(databaseName))
            assertDatabaseArtifactsAbsent(databaseFile)
        }
    }

    private fun openDatabase(context: android.content.Context, name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(TEST_PASSPHRASE.toByteArray()))
            .addMigrations(
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14
            )
            .build()

    private fun assertCipherAndPageIntegrity(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        expectedCipherPrefix: String
    ) {
        val cipherVersion = database.query("PRAGMA cipher_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }
        assertTrue("Unexpected SQLCipher version", cipherVersion.startsWith(expectedCipherPrefix))
        database.query("PRAGMA integrity_check").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
        }
        database.query("PRAGMA cipher_integrity_check").use { cursor ->
            assertFalse("SQLCipher page authentication must report no failures", cursor.moveToFirst())
        }
    }

    private fun assertDatabaseArtifactsAbsent(databaseFile: File) {
        listOf(
            databaseFile,
            File("${databaseFile.absolutePath}-wal"),
            File("${databaseFile.absolutePath}-shm"),
            File("${databaseFile.absolutePath}-journal")
        ).forEach { artifact -> assertFalse("Synthetic test artifact should be removed", artifact.exists()) }
    }

    private fun historyRow() = ScanHistoryEntity(
        id = ROOM_ROW_ID,
        timestamp = Date(1_761_700_000_123L),
        pokemonName = "Synthetic compatibility fixture",
        cp = 0,
        hp = 0,
        caughtDate = Date(1_761_600_000_000L),
        rawOcrText = "synthetic room upgrade fixture",
        isShiny = true,
        isShadow = true,
        isLucky = true,
        hasCostume = true,
        rarityScore = 73,
        rarityTier = "RARE",
        shinyTruth = "UNKNOWN",
        scanConfidenceScore = 81,
        scanConfidenceLabel = "REVIEW",
        scanConfidenceDetail = "synthetic confidence detail",
        eventConfidenceCode = "SYNTHETIC",
        eventConfidenceLabel = "Synthetic event confidence",
        eventConfidenceDetail = "synthetic event detail",
        mismatchGuardTitle = "Synthetic mismatch title",
        mismatchGuardDetail = "synthetic mismatch detail",
        recognitionSummary = "synthetic recognition summary",
        ivMin = 91,
        ivMax = 98,
        ivCandidateCount = 4,
        ivSolveMode = "RANGE",
        ivLevelMin = 20f,
        ivLevelMax = 21f,
        ivLevelAlternatives = "20.0,21.0",
        recognitionIdentityJson = "{\"synthetic\":true}",
        rarityAnalysisJson = "{\"synthetic\":true}",
        caughtDateLocalDate = LocalDate.of(2026, 10, 29)
    )

    private companion object {
        const val PREPARE_MODE = "prepare"
        const val VERIFY_MODE = "verify"
        const val ROOM_ROW_ID = 910_431L
        const val TEST_PASSPHRASE = "public-room-upgrade-test-only"
        val RUN_ID_PATTERN = Regex("[A-Za-z0-9_-]{8,48}")
    }
}
