package com.pokerarity.scanner.data.local.db

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class PlaintextHistoryConversionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val passphrase = ByteArray(32) { index -> (index * 37 + 129).toByte() }

    @Test
    fun convertsPlaintextV3ThroughRoomAndPreservesLegacyHistoryRows() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val sourceHelper = createPlaintextDatabase(target, version = 3)
        sourceHelper.setWriteAheadLoggingEnabled(true)
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            assertTrue("committed v3 fixture row remains in the WAL", File("${source.path}-wal").length() > 32)
            assertEquals(
                DatabasePassphraseStore.CIPHER_DB_NAME,
                PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
            )
        } finally {
            sourceHelper.close()
        }

        val migrated = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            migrated.openHelper.writableDatabase.query(
                "SELECT id, timestamp, pokemonName, cp, hp, caughtDate, rawOcrText, ivMin, recognitionIdentityJson " +
                    "FROM scan_history WHERE id = 431"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(431L, cursor.getLong(0))
                assertEquals(123456789L, cursor.getLong(1))
                assertEquals("Pikachu", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(0, cursor.getInt(4))
                assertTrue(cursor.isNull(5))
                assertEquals("synthetic wal row", cursor.getString(6))
                assertTrue("new decision-support fields remain nullable for legacy rows", cursor.isNull(7))
                assertTrue(cursor.isNull(8))
                assertFalse(cursor.moveToNext())
            }
            PlaintextHistoryMigrator.complete(
                target,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                migrated.openHelper.writableDatabase
            )
        } finally {
            migrated.close()
        }
        assertFalse("plaintext v3 source is removed only after current Room opens", source.exists())
    }

    @Test
    fun convertsPlaintextV5WithWalRowsThroughRoomAndRemovesSourceAfterValidation() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encrypted = target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val sourceHelper = createPlaintextV5(target)
        sourceHelper.setWriteAheadLoggingEnabled(true)
        try {
            val sourceDb = sourceHelper.writableDatabase
            assertEquals("wal", sourceDb.query("PRAGMA journal_mode").use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getString(0).lowercase()
            })
            seedHistoryRow(sourceDb)
            assertTrue("committed fixture row should be present in the WAL", File("${source.path}-wal").length() > 32)

            val selectedName = PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
            assertEquals(DatabasePassphraseStore.CIPHER_DB_NAME, selectedName)
            assertTrue("plaintext source remains until production Room opens the promoted file", source.isFile)
            assertTrue("validated encrypted copy exists", encrypted.isFile)
            assertFalse(DatabasePassphraseStore.isPlaintextSqliteDatabase(encrypted))
            assertTrue(File(target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME).path).isFile)

            // Simulate a restart while the validated stage and source still match the activation marker.
            assertEquals(
                DatabasePassphraseStore.CIPHER_DB_NAME,
                PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
            )
        } finally {
            sourceHelper.close()
        }

        val migrated = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            migrated.openHelper.writableDatabase.query(
                "SELECT id, timestamp, pokemonName, cp, hp, caughtDate, rawOcrText FROM scan_history WHERE id = 431"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(431L, cursor.getLong(0))
                assertEquals(123456789L, cursor.getLong(1))
                assertEquals("Pikachu", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(0, cursor.getInt(4))
                assertTrue(cursor.isNull(5))
                assertEquals("synthetic wal row", cursor.getString(6))
                assertFalse(cursor.moveToNext())
            }
            PlaintextHistoryMigrator.complete(
                target,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                migrated.openHelper.writableDatabase
            )
        } finally {
            migrated.close()
        }
        assertFalse("plaintext database was removed after successful open", source.exists())
        assertFalse(File("${source.path}-wal").exists())
        assertFalse(File("${source.path}-shm").exists())
        val reopened = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(reopened))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun resumesValidatedCleanupAfterSourceMainWasDeletedButSidecarsRemain() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val sourceHelper = createPlaintextV5(target)
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            assertEquals(
                DatabasePassphraseStore.CIPHER_DB_NAME,
                PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
            )
        } finally {
            sourceHelper.close()
        }

        val firstOpen = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertThrows(PlaintextHistoryConversionFailedException::class.java) {
                PlaintextHistoryMigrator.complete(
                    target,
                    DatabasePassphraseStore.CIPHER_DB_NAME,
                    firstOpen.openHelper.writableDatabase
                ) { file ->
                    assertTrue("the interrupted cleanup removed the main plaintext file", file.delete())
                    File("${file.path}-wal").writeBytes(byteArrayOf(1, 2, 3))
                    File("${file.path}-shm").writeBytes(byteArrayOf(4, 5, 6))
                    false
                }
            }
        } finally {
            firstOpen.close()
        }
        assertFalse("the synthetic interruption removed the source main file", source.exists())
        assertTrue(File("${source.path}-wal").isFile)
        assertTrue(File("${source.path}-shm").isFile)

        assertEquals(
            DatabasePassphraseStore.CIPHER_DB_NAME,
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        )
        val recovered = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            PlaintextHistoryMigrator.complete(
                target,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                recovered.openHelper.writableDatabase
            )
            assertEquals(1, historyCount(recovered))
        } finally {
            recovered.close()
        }

        assertFalse("recovery removed residual plaintext artifacts", DatabasePassphraseStore.databaseArtifactsExist(source))
        assertFalse(
            "recovery removed the conversion marker",
            DatabasePassphraseStore.databaseArtifactsExist(
                target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME)
            )
        )
    }

    @Test
    fun preservesPlaintextWhenCleanupMarkerCommitIsNotObservable() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val sourceHelper = createPlaintextV5(target)
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            assertEquals(
                DatabasePassphraseStore.CIPHER_DB_NAME,
                PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
            )
        } finally {
            sourceHelper.close()
        }

        var deletionAttempted = false
        val firstOpen = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertThrows(PlaintextHistoryConversionFailedException::class.java) {
                PlaintextHistoryMigrator.complete(
                    context = target,
                    databaseName = DatabasePassphraseStore.CIPHER_DB_NAME,
                    openedDatabase = firstOpen.openHelper.writableDatabase,
                    markerCommitter = { _, _ -> },
                    deleteSourceArtifacts = {
                        deletionAttempted = true
                        DatabasePassphraseStore.deleteDatabaseArtifacts(it)
                    }
                )
            }
        } finally {
            firstOpen.close()
        }
        assertFalse("source deletion started before marker commit was observable", deletionAttempted)
        assertTrue("plaintext source remains recoverable", source.isFile)

        val retry = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            PlaintextHistoryMigrator.complete(
                target,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                retry.openHelper.writableDatabase
            )
            assertEquals(1, historyCount(retry))
        } finally {
            retry.close()
        }
        assertFalse("retry removes plaintext after a committed marker", source.exists())
    }

    @Test
    fun unsupportedPlaintextVersionPreservesSourceAndAllowsRetry() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encrypted = target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val sourceHelper = createPlaintextV5(target)
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            sourceHelper.writableDatabase.execSQL("PRAGMA user_version = 99")
            assertThrows(PlaintextHistoryConversionFailedException::class.java) {
                PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
            }
            assertTrue("failed attempt keeps source database", source.isFile)
            assertTrue(DatabasePassphraseStore.isPlaintextSqliteDatabase(source))
            assertFalse("failed stage is rolled back", encrypted.exists())
            assertFalse(File(target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME).path).exists())
            sourceHelper.writableDatabase.query("SELECT rawOcrText FROM scan_history WHERE id = 431").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("synthetic wal row", cursor.getString(0))
            }

            sourceHelper.writableDatabase.execSQL("PRAGMA user_version = 5")
        } finally {
            sourceHelper.close()
        }

        assertEquals(
            DatabasePassphraseStore.CIPHER_DB_NAME,
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        )
        val migrated = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(migrated))
            PlaintextHistoryMigrator.complete(
                target,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                migrated.openHelper.writableDatabase
            )
        } finally {
            migrated.close()
        }
        assertFalse(source.exists())
    }

    @Test
    fun invalidMarkedCipherStagePreservesSourceAndUnrecognizedDestination() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encrypted = target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val sourceHelper = createPlaintextV5(target)
        try {
            val sourceDb = sourceHelper.writableDatabase
            seedHistoryRow(sourceDb)
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        } finally {
            sourceHelper.close()
        }
        encrypted.writeBytes(byteArrayOf(1, 2, 3))
        val damagedStage = encrypted.readBytes()

        assertThrows(ExistingPlaintextHistoryMigrationRequiredException::class.java) {
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        }
        assertTrue("the plaintext source remains available", source.isFile)
        assertTrue(DatabasePassphraseStore.isPlaintextSqliteDatabase(source))
        assertArrayEquals("a stage that no longer matches its marker is preserved", damagedStage, encrypted.readBytes())
    }

    @Test
    fun changedPlaintextSourceIsPreservedWhenItNoLongerMatchesTheValidatedCopy() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val sourceHelper = createPlaintextV5(target)
        val sourceDb = sourceHelper.writableDatabase
        seedHistoryRow(sourceDb)
        PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        sourceDb.execSQL("UPDATE scan_history SET rawOcrText = 'newer source value' WHERE id = 431")

        val migrated = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertThrows(PlaintextHistoryConversionFailedException::class.java) {
                PlaintextHistoryMigrator.complete(
                    target,
                    DatabasePassphraseStore.CIPHER_DB_NAME,
                    migrated.openHelper.writableDatabase
                )
            }
            assertTrue("changed plaintext source is retained", source.isFile)
            sourceDb.query("SELECT rawOcrText FROM scan_history WHERE id = 431").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("newer source value", cursor.getString(0))
            }
        } finally {
            migrated.close()
            sourceHelper.close()
        }
    }

    @Test
    fun changedEncryptedDestinationIsPreservedAlongsideItsPlaintextSource() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val sourceHelper = createPlaintextV5(target)
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        } finally {
            sourceHelper.close()
        }

        val migrated = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            migrated.openHelper.writableDatabase.execSQL(
                "UPDATE scan_history SET rawOcrText = 'substituted destination value' WHERE id = 431"
            )
            assertThrows(PlaintextHistoryConversionFailedException::class.java) {
                PlaintextHistoryMigrator.complete(
                    target,
                    DatabasePassphraseStore.CIPHER_DB_NAME,
                    migrated.openHelper.writableDatabase
                )
            }
            assertTrue("plaintext source is retained when the promoted database changed", source.isFile)
            assertEquals(1, historyCount(migrated))
        } finally {
            migrated.close()
        }
    }

    @Test
    fun productionPrepareLeavesPlaintextHistoryUntouchedUntilAutomaticConversionIsEnabled() {
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val sourceHelper = createPlaintextV5(target)
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            assertThrows(ExistingPlaintextHistoryMigrationRequiredException::class.java) {
                PlaintextHistoryMigrator.prepare(target, passphrase)
            }
            assertTrue(source.isFile)
            assertTrue(DatabasePassphraseStore.isPlaintextSqliteDatabase(source))
            sourceHelper.writableDatabase.query("SELECT rawOcrText FROM scan_history WHERE id = 431").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("synthetic wal row", cursor.getString(0))
            }
        } finally {
            sourceHelper.close()
        }
    }

    @Test
    fun productionPreparePreservesUnmarkedEncryptedDatabaseAndEverySidecar() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encrypted = target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val sourceHelper = createPlaintextV5(target)
        val encryptedRoom = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            runBlocking {
                assertEquals(
                    731L,
                    encryptedRoom.scanHistoryDao().insert(
                        ScanHistoryEntity(
                            id = 731L,
                            timestamp = java.util.Date(987654321L),
                            pokemonName = "Protected encrypted History",
                            cp = 999,
                            hp = 77,
                            caughtDate = null,
                            rawOcrText = "synthetic encrypted row"
                        )
                    )
                )
            }
        } finally {
            encryptedRoom.close()
        }

        val masterJournal = File(encrypted.parentFile, "${encrypted.name}-mj-sprint6")
        val sidecars = listOf(
            File("${encrypted.path}-wal"),
            File("${encrypted.path}-shm"),
            File("${encrypted.path}-journal"),
            masterJournal
        )
        sidecars.forEachIndexed { index, file -> file.writeBytes(byteArrayOf(index.toByte(), 9, 8, 7)) }
        val protectedFiles = listOf(encrypted) + sidecars
        val protectedBytes = protectedFiles.associateWith(File::readBytes)

        try {
            assertThrows(ExistingPlaintextHistoryMigrationRequiredException::class.java) {
                PlaintextHistoryMigrator.prepare(target, passphrase)
            }
            assertTrue(source.isFile)
            assertTrue(DatabasePassphraseStore.isPlaintextSqliteDatabase(source))
            protectedFiles.forEach { file ->
                assertTrue("ambiguous encrypted artifact is preserved: ${file.name}", file.isFile)
                assertArrayEquals(protectedBytes.getValue(file), file.readBytes())
            }
            sourceHelper.writableDatabase.query("SELECT rawOcrText FROM scan_history WHERE id = 431").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("synthetic wal row", cursor.getString(0))
            }
        } finally {
            sourceHelper.close()
        }
    }

    @Test
    fun productionPrepareDoesNotConvertPlaintextWhenAtomicMarkerRecoveryIsPending() {
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val marker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME)
        val encrypted = target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val sourceHelper = createPlaintextV5(target)
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            File(marker.path + ".new").writeBytes(byteArrayOf(4, 3, 2, 1))

            assertThrows(ExistingPlaintextHistoryMigrationRequiredException::class.java) {
                PlaintextHistoryMigrator.prepare(target, passphrase)
            }
            assertTrue(source.isFile)
            assertTrue(DatabasePassphraseStore.isPlaintextSqliteDatabase(source))
            assertFalse("production gate must not create an encrypted stage", encrypted.exists())
            assertTrue("production gate must preserve the pending recovery marker", File(marker.path + ".new").isFile)
            sourceHelper.writableDatabase.query("SELECT rawOcrText FROM scan_history WHERE id = 431").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("synthetic wal row", cursor.getString(0))
            }
        } finally {
            sourceHelper.close()
        }
    }

    private fun createPlaintextV5(target: Context): SupportSQLiteOpenHelper =
        createPlaintextDatabase(target, version = 5)

    private fun createPlaintextDatabase(target: Context, version: Int): SupportSQLiteOpenHelper {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(target)
                .name(DatabasePassphraseStore.DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val schemaAsset = when (version) {
                            3 -> "history-schema-v3.sql"
                            5 -> "history-schema-v5.sql"
                            else -> error("Unsupported synthetic plaintext schema version")
                        }
                        val ddl = InstrumentationRegistry.getInstrumentation().context.assets
                            .open(schemaAsset).bufferedReader().use { it.readLines() }
                        ddl.filter { it.isNotBlank() && !it.startsWith("--") }
                            .forEach { db.execSQL(it.removeSuffix(";")) }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                error("Synthetic fixture must remain at version $version")
                }).build()
        )
        helper.writableDatabase
        return helper
    }

    private fun seedHistoryRow(db: SupportSQLiteDatabase) {
        db.execSQL(
            """INSERT INTO scan_history
                (id,timestamp,pokemonName,cp,hp,caughtDate,rawOcrText,isShiny,isShadow,isLucky,hasCostume,rarityScore,rarityTier)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            arrayOf(431L, 123456789L, "Pikachu", 0, 0, null, "synthetic wal row", 0, 0, 0, 0, 42, "RARE")
        )
    }

    private fun openCipherRoom(target: Context, databaseName: String): AppDatabase =
        Room.databaseBuilder(target, AppDatabase::class.java, databaseName)
            .openHelperFactory(SupportOpenHelperFactory(passphrase.clone()))
            .addMigrations(
                AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11
            )
            .build()

    private fun historyCount(database: AppDatabase): Int =
        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM scan_history").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun isolatedContext(): Context {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "plain-history-").toFile()
        return object : ContextWrapper(context) {
            override fun getDatabasePath(name: String): File = File(directory, name)
            override fun getApplicationContext(): Context = this
        }
    }
}
