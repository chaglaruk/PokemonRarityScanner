package com.pokerarity.scanner.data.local.db

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
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
        assertEquals(
            DatabasePassphraseStore.CIPHER_DB_NAME,
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        )
        val coldStart = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(coldStart))
        } finally {
            coldStart.close()
        }
    }

    @Test
    fun convertsPlaintextV4ThroughRoomAndPreservesLegacyHistoryRows() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val sourceHelper = createPlaintextDatabase(target, version = 4)
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            assertEquals(
                DatabasePassphraseStore.CIPHER_DB_NAME,
                PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
            )
        } finally {
            sourceHelper.close()
        }

        val migrated = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(migrated))
            migrated.openHelper.writableDatabase.query(
                "SELECT id, timestamp, cp, hp, caughtDate, rawOcrText FROM scan_history WHERE id = 431"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(431L, cursor.getLong(0))
                assertEquals(123456789L, cursor.getLong(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
                assertTrue(cursor.isNull(4))
                assertEquals("synthetic wal row", cursor.getString(5))
            }
            PlaintextHistoryMigrator.complete(
                target,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                migrated.openHelper.writableDatabase
            )
        } finally {
            migrated.close()
        }
        assertFalse("plaintext v4 source is removed after current Room validates the conversion", source.exists())
        val coldStart = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(coldStart))
        } finally {
            coldStart.close()
        }
    }

    @Test
    fun convertsCurrentPlaintextV11WithoutLosingHistoryRows() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val plaintextRoom = createPlaintextV11(target)
        try {
            seedHistoryRow(plaintextRoom.openHelper.writableDatabase)
            assertEquals(
                AppDatabase.CURRENT_SCHEMA_VERSION.toLong(),
                plaintextRoom.openHelper.writableDatabase.query("PRAGMA user_version").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getLong(0)
                }
            )
            assertEquals(
                DatabasePassphraseStore.CIPHER_DB_NAME,
                PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
            )
            assertTrue("the plaintext source remains until SQLCipher Room validates it", source.isFile)
        } finally {
            plaintextRoom.close()
        }

        val migrated = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(migrated))
            migrated.openHelper.writableDatabase.query(
                "SELECT id, timestamp, cp, hp, caughtDate, rawOcrText FROM scan_history WHERE id = 431"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(431L, cursor.getLong(0))
                assertEquals(123456789L, cursor.getLong(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
                assertTrue(cursor.isNull(4))
                assertEquals("synthetic wal row", cursor.getString(5))
            }
            PlaintextHistoryMigrator.complete(
                target,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                migrated.openHelper.writableDatabase
            )
        } finally {
            migrated.close()
        }
        assertFalse("plaintext v11 source is removed only after SQLCipher Room validates it", source.exists())
        val coldStart = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(coldStart))
        } finally {
            coldStart.close()
        }
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
        assertTrue("active cipher marker remains for future cold starts", target.getDatabasePath(
            DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME
        ).isFile)
        assertEquals(
            DatabasePassphraseStore.CIPHER_DB_NAME,
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        )
        val reopened = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(reopened))
            reopened.openHelper.writableDatabase.execSQL(
                "UPDATE scan_history SET rawOcrText = 'after activation write' WHERE id = 431"
            )
            PlaintextHistoryMigrator.complete(
                target,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                reopened.openHelper.writableDatabase
            )
        } finally {
            reopened.close()
        }
        assertEquals(
            DatabasePassphraseStore.CIPHER_DB_NAME,
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        )
        val updatedColdStart = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            updatedColdStart.openHelper.writableDatabase.query(
                "SELECT rawOcrText FROM scan_history WHERE id = 431"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("after activation write", cursor.getString(0))
            }
        } finally {
            updatedColdStart.close()
        }
    }

    @Test
    fun recoversAnInterruptedStageBeforeActivationAndRetriesFromPlaintext() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encrypted = target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val preparationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_PREPARATION_NAME)
        val sourceHelper = createPlaintextV5(target)
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            assertThrows(PlaintextHistoryConversionFailedException::class.java) {
                PlaintextHistoryMigrator.prepareForIsolatedValidation(
                    target,
                    passphrase,
                    PlaintextHistoryMigrator.ConversionHooks(
                        afterStageReady = { throw IOException("synthetic interruption before activation") }
                    )
                )
            }
            assertTrue("interruption keeps the plaintext source", source.isFile)
            assertTrue("preparation intent is durable before the stage", preparationMarker.isFile)
            assertFalse("the final database is not promoted before activation", encrypted.exists())
            assertTrue("the fully staged cipher file remains owned by the preparation intent", stageFile(target).isFile)
        } finally {
            sourceHelper.close()
        }

        assertEquals(
            DatabasePassphraseStore.CIPHER_DB_NAME,
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        )
        val recovered = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(recovered))
            recovered.openHelper.writableDatabase.query(
                "SELECT rawOcrText FROM scan_history WHERE id = 431"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("synthetic wal row", cursor.getString(0))
            }
            PlaintextHistoryMigrator.complete(
                target,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                recovered.openHelper.writableDatabase
            )
        } finally {
            recovered.close()
        }
        assertFalse("successful retry removes the source only after SQLCipher opens", source.exists())
    }

    @Test
    fun recoversWhenInterruptionOccursAfterAtomicPromotionBeforeActivation() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encrypted = target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val activationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME)
        val sourceHelper = createPlaintextV5(target)
        var promotionReached = false
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            assertThrows(PlaintextHistoryConversionFailedException::class.java) {
                PlaintextHistoryMigrator.prepareForIsolatedValidation(
                    target,
                    passphrase,
                    PlaintextHistoryMigrator.ConversionHooks(
                        markerDirectorySync = { file ->
                            if (file.name.matches(Regex("pokerarity_db_cipher\\.stage-[0-9a-f]{32}"))) {
                                promotionReached = true
                                throw IOException("synthetic interruption after atomic promotion")
                            }
                        }
                    )
                )
            }
            assertTrue("the interruption happened after the move", promotionReached)
            assertTrue("the plaintext source remains recoverable", source.isFile)
            assertTrue("the promoted encrypted file survives for validation", encrypted.isFile)
            assertFalse("activation is retried separately", activationMarker.exists())
            assertFalse("the stage name is absent after promotion", stageFile(target).exists())
        } finally {
            sourceHelper.close()
        }

        assertEquals(
            DatabasePassphraseStore.CIPHER_DB_NAME,
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        )
        val recovered = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(recovered))
            recovered.openHelper.writableDatabase.query(
                "SELECT rawOcrText FROM scan_history WHERE id = 431"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("synthetic wal row", cursor.getString(0))
            }
            PlaintextHistoryMigrator.complete(
                target,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                recovered.openHelper.writableDatabase
            )
        } finally {
            recovered.close()
        }
        assertFalse("source is removed only after Room validates the promoted database", source.exists())
    }

    @Test
    fun preservesPlaintextSourceWhenCipherExportFailsBeforeStageValidation() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encrypted = target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val preparationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_PREPARATION_NAME)
        val activationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME)
        val sourceHelper = createPlaintextV5(target).also { it.setWriteAheadLoggingEnabled(true) }
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            assertTrue("committed failure fixture row should be present in the WAL", File("${source.path}-wal").length() > 32)
            assertThrows(PlaintextHistoryConversionFailedException::class.java) {
                PlaintextHistoryMigrator.prepareForIsolatedValidation(
                    target,
                    passphrase,
                    PlaintextHistoryMigrator.ConversionHooks(
                        afterCipherExport = { throw IOException("synthetic export write failure") }
                    )
                )
            }

            assertTrue("plaintext source remains after export failure", source.isFile)
            assertTrue(DatabasePassphraseStore.isPlaintextSqliteDatabase(source))
            sourceHelper.close()
            val reopenedSourceHelper = createPlaintextV5(target)
            try {
                reopenedSourceHelper.writableDatabase.query(
                    "SELECT rawOcrText FROM scan_history WHERE id = 431"
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("synthetic wal row", cursor.getString(0))
                }
            } finally {
                reopenedSourceHelper.close()
            }
            assertTrue("unvalidated encrypted stage is retained for safe retry", stageFile(target).isFile)
            assertFalse("an unvalidated stage is not installed as the database", encrypted.exists())
            assertTrue("owned-stage retry state remains recorded", preparationMarker.isFile)
            assertEquals(
                "the stage has no validation fingerprint and is not eligible for activation",
                null,
                PlaintextHistoryMigrationMarkers.readPreparationMarker(preparationMarker)?.encryptedFingerprint
            )
            assertFalse("activation cannot be marked before validation", activationMarker.exists())
            assertEquals(
                "the owned incomplete stage can be discarded and rebuilt",
                DatabasePassphraseStore.CIPHER_DB_NAME,
                PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
            )
            val recovered = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
            try {
                assertEquals(1, historyCount(recovered))
                recovered.openHelper.writableDatabase.query(
                    "SELECT rawOcrText FROM scan_history WHERE id = 431"
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("synthetic wal row", cursor.getString(0))
                }
                PlaintextHistoryMigrator.complete(
                    target,
                    DatabasePassphraseStore.CIPHER_DB_NAME,
                    recovered.openHelper.writableDatabase
                )
            } finally {
                recovered.close()
            }
            assertFalse("source is removed only after the retry opens through Room", source.exists())
        } finally {
            sourceHelper.close()
        }
    }

    @Test
    fun preservesWalBackedPlaintextAndRetriesAfterSqlCipherExportPageLimit() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encrypted = target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val preparationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_PREPARATION_NAME)
        val activationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME)
        val sourceHelper = createPlaintextV5(target).also { it.setWriteAheadLoggingEnabled(true) }
        var exportReturned = false
        var pageLimitConfigured = false
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            assertTrue("committed fixture row should be in the WAL", File("${source.path}-wal").length() > 32)
            assertThrows(PlaintextHistoryConversionFailedException::class.java) {
                PlaintextHistoryMigrator.prepareForIsolatedValidation(
                    target,
                    passphrase,
                    PlaintextHistoryMigrator.ConversionHooks(
                        beforeCipherExport = { exporter ->
                            exporter.rawQuery("PRAGMA encrypted_copy.max_page_count = 1", null).use { cursor ->
                                assertTrue(cursor.moveToFirst())
                                assertEquals(1L, cursor.getLong(0))
                            }
                            pageLimitConfigured = true
                        },
                        afterCipherExport = { exportReturned = true }
                    )
                )
            }

            assertTrue("the encrypted export page limit was configured", pageLimitConfigured)
            assertFalse("the page-limited SQLCipher export must fail", exportReturned)
            assertTrue("plaintext source remains after export failure", source.isFile)
            assertTrue(DatabasePassphraseStore.isPlaintextSqliteDatabase(source))
            assertTrue("the incomplete cipher stage remains unvalidated", stageFile(target).isFile)
            assertFalse("the incomplete stage is not installed as the database", encrypted.exists())
            assertTrue("owned-stage retry state remains recorded", preparationMarker.isFile)
            assertEquals(
                "an incomplete export has no validation fingerprint",
                null,
                PlaintextHistoryMigrationMarkers.readPreparationMarker(preparationMarker)?.encryptedFingerprint
            )
            assertFalse("activation cannot be marked before validation", activationMarker.exists())
            sourceHelper.close()
            val reopenedSourceHelper = createPlaintextV5(target)
            try {
                reopenedSourceHelper.writableDatabase.query(
                    "SELECT rawOcrText FROM scan_history WHERE id = 431"
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("synthetic wal row", cursor.getString(0))
                }
            } finally {
                reopenedSourceHelper.close()
            }
            assertEquals(
                "retry discards only the owned incomplete stage and rebuilds from the preserved source",
                DatabasePassphraseStore.CIPHER_DB_NAME,
                PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
            )
            val recovered = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
            try {
                assertEquals(1, historyCount(recovered))
                recovered.openHelper.writableDatabase.query(
                    "SELECT rawOcrText FROM scan_history WHERE id = 431"
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("synthetic wal row", cursor.getString(0))
                }
                PlaintextHistoryMigrator.complete(
                    target,
                    DatabasePassphraseStore.CIPHER_DB_NAME,
                    recovered.openHelper.writableDatabase
                )
            } finally {
                recovered.close()
            }
            assertFalse("source is removed only after retry opens through Room", source.exists())
        } finally {
            sourceHelper.close()
        }
    }

    @Test
    fun recoversPartialActivationMarkerWhenPreparationAndSourceStillMatch() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encrypted = target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val preparationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_PREPARATION_NAME)
        val activationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME)
        val partialActivationWrite = File(activationMarker.path + ".new")
        val sourceHelper = createPlaintextV5(target)
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            assertThrows(PlaintextHistoryConversionFailedException::class.java) {
                PlaintextHistoryMigrator.prepareForIsolatedValidation(
                    target,
                    passphrase,
                    PlaintextHistoryMigrator.ConversionHooks(
                        afterStageReady = {
                            partialActivationWrite.writeBytes(byteArrayOf(1, 2, 3))
                            throw IOException("synthetic interruption during activation marker write")
                        }
                    )
                )
            }
            assertTrue(source.isFile)
            assertTrue(preparationMarker.isFile)
            assertTrue(stageFile(target).isFile)
            assertFalse(encrypted.exists())
            assertTrue("partial activation marker is present", partialActivationWrite.isFile)
        } finally {
            sourceHelper.close()
        }

        assertEquals(
            DatabasePassphraseStore.CIPHER_DB_NAME,
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        )
        assertFalse("owned partial activation marker is removed after source validation", partialActivationWrite.exists())
        val recovered = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(recovered))
            PlaintextHistoryMigrator.complete(
                target,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                recovered.openHelper.writableDatabase
            )
        } finally {
            recovered.close()
        }
        assertFalse("recovered conversion retains all data in SQLCipher", source.exists())
    }

    @Test
    fun retriesWhenPreparationMarkerWasInterruptedBeforeAnyStageWasCreated() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encrypted = target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val preparationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_PREPARATION_NAME)
        val partialPreparationWrite = File(preparationMarker.path + ".new")
        val sourceHelper = createPlaintextV5(target).also { it.setWriteAheadLoggingEnabled(true) }
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            partialPreparationWrite.writeBytes(byteArrayOf(7, 8, 9))

            assertEquals(
                DatabasePassphraseStore.CIPHER_DB_NAME,
                PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
            )
            assertTrue("plaintext source remains until Room validates its cipher copy", source.isFile)
            assertTrue("a successful retry creates the cipher stage", encrypted.isFile)
            assertFalse("unreadable preparation intent with no stage is replaced safely", partialPreparationWrite.exists())
        } finally {
            sourceHelper.close()
        }

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
    fun preservesUnownedStageWhenPreparationMarkerCannotProveOwnership() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val preparationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_PREPARATION_NAME)
        val unownedStage = target.getDatabasePath("${DatabasePassphraseStore.CIPHER_DB_NAME}.stage-${"a".repeat(32)}")
        val sourceHelper = createPlaintextV5(target)
        val stageBytes = byteArrayOf(9, 4, 1, 7)
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            preparationMarker.writeBytes(byteArrayOf(1, 2, 3))
            unownedStage.writeBytes(stageBytes)

            assertThrows(ExistingPlaintextHistoryMigrationRequiredException::class.java) {
                PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
            }

            assertTrue("plaintext remains available when stage ownership is unknown", source.isFile)
            assertArrayEquals("unowned stage bytes are preserved", stageBytes, unownedStage.readBytes())
        } finally {
            sourceHelper.close()
        }
    }

    @Test
    fun keepsCipherStageWhenActivationMarkerDirectorySyncFailsAfterMarkerCommit() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encrypted = target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val activationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME)
        val sourceHelper = createPlaintextV5(target)
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            assertThrows(PlaintextHistoryConversionFailedException::class.java) {
                PlaintextHistoryMigrator.prepareForIsolatedValidation(
                    target,
                    passphrase,
                    PlaintextHistoryMigrator.ConversionHooks(
                        markerDirectorySync = { file ->
                            if (file.name == activationMarker.name) {
                                throw IOException("synthetic activation directory sync failure")
                            }
                        }
                    )
                )
            }
            assertTrue("activation marker is observable after the simulated sync failure", activationMarker.isFile)
            assertTrue("a marker-referenced cipher stage is never deleted on failure", encrypted.isFile)
            assertTrue("plaintext remains available until Room validates the cipher stage", source.isFile)
        } finally {
            sourceHelper.close()
        }

        assertEquals(
            DatabasePassphraseStore.CIPHER_DB_NAME,
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        )
        val recovered = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(recovered))
            PlaintextHistoryMigrator.complete(
                target,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                recovered.openHelper.writableDatabase
            )
        } finally {
            recovered.close()
        }
        assertFalse("successful marker recovery removes plaintext only after Room validation", source.exists())
    }

    @Test
    fun stalePreparationCannotReplaceNewerEncryptedHistoryWhenActivationMarkerIsDamaged() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encrypted = target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val activationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME)
        val sourceHelper = createPlaintextV5(target)
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        } finally {
            sourceHelper.close()
        }

        val replacement = openCipherRoom(target, "newer_history.db")
        try {
            runBlocking {
                replacement.scanHistoryDao().insert(
                    ScanHistoryEntity(
                        id = 811L,
                        timestamp = java.util.Date(987654321L),
                        pokemonName = "Newer encrypted History row",
                        cp = 777,
                        hp = 55,
                        caughtDate = null,
                        rawOcrText = "synthetic newer encrypted row"
                    )
                )
            }
        } finally {
            replacement.close()
        }
        val replacementFile = target.getDatabasePath("newer_history.db")
        val newerEncryptedBytes = replacementFile.readBytes()
        encrypted.writeBytes(newerEncryptedBytes)
        activationMarker.writeBytes(byteArrayOf(1, 2, 3))

        assertThrows(ExistingPlaintextHistoryMigrationRequiredException::class.java) {
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        }
        assertTrue("plaintext source remains available for explicit recovery", source.isFile)
        assertArrayEquals(
            "stale preparation cannot replace the unrecognized newer encrypted database",
            newerEncryptedBytes,
            encrypted.readBytes()
        )

        val preserved = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(preserved))
            preserved.openHelper.writableDatabase.query(
                "SELECT pokemonName FROM scan_history WHERE id = 811"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Newer encrypted History row", cursor.getString(0))
            }
        } finally {
            preserved.close()
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
                    firstOpen.openHelper.writableDatabase,
                    cleanupHooks = PlaintextHistoryMigrator.CleanupHooks(
                        deleteSourceArtifacts = { file ->
                            assertTrue("the interrupted cleanup removed the main plaintext file", file.delete())
                            File("${file.path}-wal").writeBytes(byteArrayOf(1, 2, 3))
                            File("${file.path}-shm").writeBytes(byteArrayOf(4, 5, 6))
                            false
                        }
                    )
                )
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
        assertTrue(
            "completed conversion retains the active database marker",
            target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME).isFile
        )
        assertEquals(
            DatabasePassphraseStore.CIPHER_DB_NAME,
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        )
        val coldStart = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(coldStart))
        } finally {
            coldStart.close()
        }
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
                    cleanupHooks = PlaintextHistoryMigrator.CleanupHooks(
                        markerCommitter = { _, _ -> },
                        deleteSourceArtifacts = {
                            deletionAttempted = true
                            DatabasePassphraseStore.deleteDatabaseArtifacts(it)
                        }
                    )
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
    fun retainsCleanupMarkerWhenSourceRemovalDirectorySyncFails() {
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

        val marker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME)
        val firstOpen = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertThrows(PlaintextHistoryConversionFailedException::class.java) {
                PlaintextHistoryMigrator.complete(
                    context = target,
                    databaseName = DatabasePassphraseStore.CIPHER_DB_NAME,
                    openedDatabase = firstOpen.openHelper.writableDatabase,
                    cleanupHooks = PlaintextHistoryMigrator.CleanupHooks(
                        sourceDirectorySync = { throw PlaintextHistoryConversionFailedException() }
                    )
                )
            }
        } finally {
            firstOpen.close()
        }

        assertFalse("source artifacts are gone when the directory sync fails", DatabasePassphraseStore.databaseArtifactsExist(source))
        assertTrue("cleanup recovery marker remains after sync failure", marker.isFile)
        assertEquals(
            DatabasePassphraseStore.CIPHER_DB_NAME,
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        )
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
        assertEquals(
            DatabasePassphraseStore.CIPHER_DB_NAME,
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        )
        val coldStart = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(coldStart))
        } finally {
            coldStart.close()
        }
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
    fun preservesDamagedCipherStageAndSourceWhenStageFingerprintNoLongerMatches() {
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
        assertArrayEquals("the mismatched stage is preserved for explicit recovery", damagedStage, encrypted.readBytes())
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
    fun isolatedConversionRetainsPlaintextUntilRoomValidatesTheCipherCopy() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val sourceHelper = createPlaintextV5(target).also { it.setWriteAheadLoggingEnabled(true) }
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            assertEquals(
                DatabasePassphraseStore.CIPHER_DB_NAME,
                PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
            )
            assertTrue(source.isFile)
            assertTrue(DatabasePassphraseStore.isPlaintextSqliteDatabase(source))
            sourceHelper.writableDatabase.query("SELECT rawOcrText FROM scan_history WHERE id = 431").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("synthetic wal row", cursor.getString(0))
            }
        } finally {
            sourceHelper.close()
        }

        val migrated = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(1, historyCount(migrated))
            migrated.openHelper.writableDatabase.query("SELECT rawOcrText FROM scan_history WHERE id = 431").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("synthetic wal row", cursor.getString(0))
            }
            PlaintextHistoryMigrator.complete(
                target,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                migrated.openHelper.writableDatabase
            )
        } finally {
            migrated.close()
        }
        assertFalse("production conversion deletes plaintext only after Room opens the encrypted copy", source.exists())
    }

    @Test
    fun completedConversionMarkerNeverLetsStalePreparationReplaceNewerEncryptedHistory() {
        SqlCipherInitializer.ensureLoaded()
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encrypted = target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val preparationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_PREPARATION_NAME)
        val activationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME)
        val sourceHelper = createPlaintextV5(target)
        val sourceDb = sourceHelper.writableDatabase
        seedHistoryRow(sourceDb)
        assertEquals(
            DatabasePassphraseStore.CIPHER_DB_NAME,
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        )
        val stalePreparationContents = preparationMarker.readBytes()
        sourceHelper.close()

        val firstOpen = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            PlaintextHistoryMigrator.complete(
                target,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                firstOpen.openHelper.writableDatabase
            )
        } finally {
            firstOpen.close()
        }
        assertFalse(source.exists())
        assertFalse("completed conversion cleans its preparation marker", preparationMarker.exists())

        val newerHistory = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(
                732L,
                runBlocking {
                    newerHistory.scanHistoryDao().insert(
                        ScanHistoryEntity(
                            id = 732L,
                            timestamp = java.util.Date(987654322L),
                            pokemonName = "Newer encrypted History row",
                            cp = 777,
                            hp = 55,
                            caughtDate = null,
                            rawOcrText = "synthetic newer encrypted row"
                        )
                    )
                }
            )
        } finally {
            newerHistory.close()
        }
        val encryptedBeforeStaleRecovery = encrypted.readBytes()

        val reappearedSource = createPlaintextV5(target)
        try {
            seedHistoryRow(reappearedSource.writableDatabase)
        } finally {
            reappearedSource.close()
        }
        preparationMarker.writeBytes(stalePreparationContents)

        assertThrows(ExistingPlaintextHistoryMigrationRequiredException::class.java) {
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        }
        assertTrue("reappeared plaintext is preserved for explicit recovery", source.isFile)
        assertArrayEquals(
            "completed active marker prevents replacing newer encrypted rows",
            encryptedBeforeStaleRecovery,
            encrypted.readBytes()
        )

        val preserved = openCipherRoom(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        try {
            assertEquals(2, historyCount(preserved))
            preserved.openHelper.writableDatabase.query(
                "SELECT pokemonName FROM scan_history WHERE id = 732"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Newer encrypted History row", cursor.getString(0))
            }
        } finally {
            preserved.close()
        }

        preparationMarker.writeText(
            String(stalePreparationContents, Charsets.US_ASCII) + "\nPREPARATION_COMPLETE",
            Charsets.US_ASCII
        )
        activationMarker.writeBytes(byteArrayOf(1, 2, 3))
        assertThrows(ExistingPlaintextHistoryMigrationRequiredException::class.java) {
            PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
        }
        assertTrue("completed preparation evidence preserves a reappeared source", source.isFile)
        assertArrayEquals(
            "a damaged active marker cannot let stale preparation replace newer encrypted rows",
            encryptedBeforeStaleRecovery,
            encrypted.readBytes()
        )
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

    @Test
    fun productionPrepareKeepsNewPlaintextConversionDisabledAndPreservesWalData() {
        val target = isolatedContext()
        val source = target.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encrypted = target.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val activationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME)
        val preparationMarker = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_PREPARATION_NAME)
        val sourceHelper = createPlaintextV5(target).also { it.setWriteAheadLoggingEnabled(true) }
        try {
            seedHistoryRow(sourceHelper.writableDatabase)
            assertThrows(ExistingPlaintextHistoryMigrationRequiredException::class.java) {
                PlaintextHistoryMigrator.prepare(target, passphrase)
            }
            assertTrue(source.isFile)
            assertTrue(DatabasePassphraseStore.isPlaintextSqliteDatabase(source))
            assertFalse("production startup does not create a conversion stage", encrypted.exists())
            assertFalse(activationMarker.exists())
            assertFalse(preparationMarker.exists())
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
                            3, 4 -> "history-schema-v3.sql"
                            5 -> "history-schema-v5.sql"
                            else -> error("Unsupported synthetic plaintext schema version")
                        }
                        val ddl = InstrumentationRegistry.getInstrumentation().context.assets
                            .open(schemaAsset).bufferedReader().use { it.readLines() }
                        ddl.filter { it.isNotBlank() && !it.startsWith("--") }
                            .forEach { db.execSQL(it.removeSuffix(";")) }
                        if (version == 4) AppDatabase.MIGRATION_3_4.migrate(db)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                error("Synthetic fixture must remain at version $version")
                }).build()
        )
        helper.writableDatabase
        return helper
    }

    private fun createPlaintextV11(target: Context): AppDatabase =
        Room.databaseBuilder(target, AppDatabase::class.java, DatabasePassphraseStore.DB_NAME)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(
                AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11
            )
            .build()

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

    private fun stageFile(target: Context): File {
        val markerFile = target.getDatabasePath(DatabasePassphraseStore.CIPHER_MIGRATION_PREPARATION_NAME)
        val marker = PlaintextHistoryMigrationMarkers.readPreparationMarker(markerFile)
            ?: error("Expected a readable staged-conversion marker")
        val stageName = marker.stageDatabaseName ?: error("Expected a uniquely named conversion stage")
        return target.getDatabasePath(stageName)
    }

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
