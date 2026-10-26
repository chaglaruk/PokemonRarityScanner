package com.pokerarity.scanner.data.local.db

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in host-orchestrated process-death validation; normal instrumentation runs skip it. */
@RunWith(AndroidJUnit4::class)
class PlaintextHistoryProcessDeathHarnessTest {
    @Test
    fun processDeathHarnessOptIn() {
        val arguments = InstrumentationRegistry.getArguments()
        val phase = arguments.getString(ARG_PHASE)
        val interruptionPoint = arguments.getString(ARG_INTERRUPTION_POINT)
        val expectedCheckpoint = when (interruptionPoint) {
            POINT_AFTER_STAGE_READY -> READY_MARKER
            POINT_AFTER_PROMOTION -> PROMOTED_MARKER
            else -> null
        }
        assumeTrue("Run only through the process-death host harness", phase == PHASE_PREPARE || phase == PHASE_RECOVER)
        assumeTrue("Select a supported process-death checkpoint", expectedCheckpoint != null)

        val runId = arguments.getString(ARG_RUN_ID).orEmpty()
        assertTrue("A valid isolated run ID is required", RUN_ID_PATTERN.matches(runId))
        SqlCipherInitializer.ensureLoaded()

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val runDirectory = File(File(appContext.filesDir, HARNESS_DIRECTORY), runId)
        val databaseDirectory = File(runDirectory, DATABASE_DIRECTORY)
        val markerFile = File(runDirectory, CHECKPOINT_FILE)

        if (phase == PHASE_PREPARE) {
            assertFalse("The run ID must be unused", runDirectory.exists())
            assertTrue("The isolated fixture directory must be created", databaseDirectory.mkdirs())
            pauseAtSelectedCheckpoint(appContext, databaseDirectory, markerFile, checkNotNull(interruptionPoint))
        } else {
            assertTrue("The prepare phase must have created its fixture", databaseDirectory.isDirectory)
            assertTrue("The selected checkpoint must survive process death", markerFile.isFile)
            assertEquals(expectedCheckpoint, markerFile.readText(StandardCharsets.US_ASCII))
            recoverAndValidate(appContext, databaseDirectory)
            assertTrue("The successful harness removes only its synthetic fixture", runDirectory.deleteRecursively())
        }
    }

    private fun pauseAtSelectedCheckpoint(
        appContext: Context,
        databaseDirectory: File,
        markerFile: File,
        interruptionPoint: String
    ) {
        val target = isolatedContext(appContext, databaseDirectory)
        val sourceHelper = createPlaintextV3(target)
        try {
            val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
            seedSyntheticHistoryRow(sourceHelper.writableDatabase)
            assertTrue("The committed synthetic row must be WAL-backed", File("${source.path}-wal").length() > 32L)

            PlaintextHistoryMigrator.prepareForIsolatedValidation(
                target,
                testPassphrase(),
                when (interruptionPoint) {
                    POINT_AFTER_STAGE_READY -> PlaintextHistoryMigrator.ConversionHooks(
                        afterStageReady = { stageFile ->
                            assertTrue("The validated stage must exist before promotion", DatabasePassphraseStore.databaseArtifactsExist(stageFile))
                            assertFalse(
                                "The destination must remain absent before promotion",
                                DatabasePassphraseStore.databaseArtifactsExist(target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME))
                            )
                            pauseAtCheckpoint(markerFile, READY_MARKER)
                        }
                    )
                    POINT_AFTER_PROMOTION -> PlaintextHistoryMigrator.ConversionHooks(
                        afterStagePromotion = { stageFile ->
                            assertFalse("The stage must have moved to the active database", DatabasePassphraseStore.databaseArtifactsExist(stageFile))
                            assertTrue(
                                "The promoted encrypted database must exist before activation is marked",
                                DatabasePassphraseStore.databaseArtifactsExist(target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME))
                            )
                            assertFalse(
                                "The process must stop before the activation marker is written",
                                PlaintextHistoryMigrationMarkers.hasMarkerArtifacts(
                                    target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME)
                                )
                            )
                            pauseAtCheckpoint(markerFile, PROMOTED_MARKER)
                        }
                    )
                    else -> error("Unsupported process-death checkpoint")
                }
            )
            throw AssertionError("The host must stop the process at the selected checkpoint")
        } finally {
            sourceHelper.close()
        }
    }

    private fun pauseAtCheckpoint(markerFile: File, checkpoint: String) {
        check(markerFile.createNewFile())
        FileOutputStream(markerFile).use { stream ->
            stream.write(checkpoint.toByteArray(StandardCharsets.US_ASCII))
            stream.fd.sync()
        }
        if (!CountDownLatch(1).await(CHECKPOINT_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            throw IOException("The process-death checkpoint timed out")
        }
    }

    private fun recoverAndValidate(appContext: Context, databaseDirectory: File) {
        val target = isolatedContext(appContext, databaseDirectory)
        val sourceFile = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        assertTrue("The plaintext source must survive the forced stop", sourceFile.isFile)
        assertTrue("The plaintext source header must remain readable", DatabasePassphraseStore.isPlaintextSqliteDatabase(sourceFile))

        assertEquals(
            DatabasePassphraseStore.CIPHER_DB_NAME,
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, testPassphrase())
        )

        val preparationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_PREPARATION_NAME)
        val stageName = PlaintextHistoryMigrationMarkers.readPreparationMarker(preparationMarker)?.stageDatabaseName
        assertNotNull("The validated stage marker must remain available until Room opens", stageName)
        val stageFile = target.getDatabasePath(checkNotNull(stageName))
        assertFalse("Recovery must atomically promote the validated stage", DatabasePassphraseStore.databaseArtifactsExist(stageFile))
        val activeMarker = PlaintextHistoryMigrationMarkers.readActivationMarker(
            target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME)
        )
        assertNotNull("The recovered database must have an activation marker", activeMarker)
        val expectedCipherFingerprint = checkNotNull(activeMarker).encryptedFingerprint

        val migrated = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            val opened = migrated.openHelper.writableDatabase
            assertEquals(
                AppDatabase.CURRENT_SCHEMA_VERSION.toLong(),
                HistoryDatabaseIntegrity.pragmaLong(opened, "main", "user_version")
            )
            assertTrue("The recovered database must pass SQLite integrity checks", HistoryDatabaseIntegrity.integrityIsOk(opened, "main"))
            assertTrue("The recovered database must pass SQLCipher integrity checks", HistoryDatabaseIntegrity.cipherIntegrityIsOk(opened, "main"))
            assertTrue(
                "The recovered encrypted database must match its activation marker",
                expectedCipherFingerprint == HistoryDatabaseIntegrity.logicalFingerprint(opened, "main")
            )
            assertEquals(1, historyCount(opened))
            assertTrue("The synthetic History row must retain its fields", syntheticHistoryRowMatches(opened))

            PlaintextHistoryMigrator.complete(
                target,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                opened
            )
        } finally {
            migrated.close()
        }

        assertFalse("Validated cleanup must remove the plaintext database and sidecars", DatabasePassphraseStore.databaseArtifactsExist(sourceFile))
        assertFalse("The promoted stage name must no longer exist", DatabasePassphraseStore.databaseArtifactsExist(stageFile))
        assertTrue(
            "The active marker must record completed cleanup",
            PlaintextHistoryMigrationMarkers.readActivationMarker(
                target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME)
            )?.conversionComplete == true
        )

        val coldStart = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            val opened = coldStart.openHelper.writableDatabase
            assertEquals(1, historyCount(opened))
            assertTrue(
                "The encrypted database must remain readable after a cold Room open",
                expectedCipherFingerprint == HistoryDatabaseIntegrity.logicalFingerprint(opened, "main")
            )
            assertTrue("The synthetic History row must survive a cold Room open", syntheticHistoryRowMatches(opened))
        } finally {
            coldStart.close()
        }
    }

    private fun createPlaintextV3(target: Context): SupportSQLiteOpenHelper {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(target)
                .name(DatabasePassphraseStore.DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        InstrumentationRegistry.getInstrumentation().context.assets
                            .open(V3_SCHEMA_ASSET)
                            .bufferedReader()
                            .use { reader ->
                                reader.readLines()
                                    .filter { it.isNotBlank() && !it.trimStart().startsWith("--") }
                                    .forEach { db.execSQL(it.trim().removeSuffix(";")) }
                            }
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = error("The synthetic fixture must remain at schema version 3")
                })
                .build()
        )
        helper.setWriteAheadLoggingEnabled(true)
        helper.writableDatabase
        return helper
    }

    private fun seedSyntheticHistoryRow(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO scan_history " +
                "(id,timestamp,pokemonName,cp,hp,caughtDate,rawOcrText,isShiny,isShadow,isLucky,hasCostume,rarityScore,rarityTier) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(
                SYNTHETIC_HISTORY_ID,
                SYNTHETIC_TIMESTAMP,
                SYNTHETIC_POKEMON_NAME,
                0,
                0,
                null,
                SYNTHETIC_OCR_MARKER,
                0,
                0,
                0,
                0,
                42,
                "RARE"
            )
        )
    }

    private fun openCipherRoom(target: Context, databaseName: String): AppDatabase =
        Room.databaseBuilder(target, AppDatabase::class.java, databaseName)
            .openHelperFactory(SupportOpenHelperFactory(testPassphrase().clone()))
            .addMigrations(
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11
            )
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    private fun historyCount(db: androidx.sqlite.db.SupportSQLiteDatabase): Int =
        db.query("SELECT COUNT(*) FROM scan_history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun syntheticHistoryRowMatches(db: androidx.sqlite.db.SupportSQLiteDatabase): Boolean =
        db.query(
            "SELECT id,timestamp,pokemonName,cp,hp,caughtDate,rawOcrText,isShiny,isShadow,isLucky,hasCostume,rarityScore,rarityTier " +
                "FROM scan_history WHERE id = 431"
        ).use { cursor ->
            cursor.moveToFirst() &&
                cursor.getLong(0) == SYNTHETIC_HISTORY_ID &&
                cursor.getLong(1) == SYNTHETIC_TIMESTAMP &&
                cursor.getString(2) == SYNTHETIC_POKEMON_NAME &&
                cursor.getInt(3) == 0 &&
                cursor.getInt(4) == 0 &&
                cursor.isNull(5) &&
                cursor.getString(6) == SYNTHETIC_OCR_MARKER &&
                cursor.getInt(7) == 0 &&
                cursor.getInt(8) == 0 &&
                cursor.getInt(9) == 0 &&
                cursor.getInt(10) == 0 &&
                cursor.getInt(11) == 42 &&
                cursor.getString(12) == "RARE" &&
                !cursor.moveToNext()
        }

    private fun isolatedContext(appContext: Context, databaseDirectory: File): Context =
        object : ContextWrapper(appContext) {
            override fun getDatabasePath(name: String): File = File(databaseDirectory, name)
            override fun getApplicationContext(): Context = this
        }

    private fun testPassphrase(): ByteArray = ByteArray(TEST_KEY_BYTES) { index -> (index * 37 + 129).toByte() }

    private companion object {
        const val ARG_PHASE = "prs_history_harness_phase"
        const val ARG_RUN_ID = "prs_history_harness_run"
        const val ARG_INTERRUPTION_POINT = "prs_history_harness_interruption"
        const val PHASE_PREPARE = "prepare"
        const val PHASE_RECOVER = "recover"
        const val POINT_AFTER_STAGE_READY = "stage-ready"
        const val POINT_AFTER_PROMOTION = "after-promotion"
        const val HARNESS_DIRECTORY = "sprint6-sqlcipher-process-death"
        const val DATABASE_DIRECTORY = "database"
        const val CHECKPOINT_FILE = "checkpoint"
        const val READY_MARKER = "READY"
        const val PROMOTED_MARKER = "PROMOTED"
        const val V3_SCHEMA_ASSET = "history-schema-v3.sql"
        const val TEST_KEY_BYTES = 32
        const val CHECKPOINT_TIMEOUT_MINUTES = 5L
        const val SYNTHETIC_HISTORY_ID = 431L
        const val SYNTHETIC_TIMESTAMP = 123456789L
        const val SYNTHETIC_POKEMON_NAME = "synthetic-fixture"
        const val SYNTHETIC_OCR_MARKER = "synthetic"
        val RUN_ID_PATTERN = Regex("[a-f0-9]{32}")
    }
}
