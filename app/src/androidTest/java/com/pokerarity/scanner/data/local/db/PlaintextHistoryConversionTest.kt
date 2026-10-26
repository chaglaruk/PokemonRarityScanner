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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaintextHistoryConversionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val passphrase = ByteArray(32) { index -> (index * 37 + 129).toByte() }

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

            // Simulate a restart followed by an invalid staged copy: the plaintext source must let us retry.
            assertEquals(
                DatabasePassphraseStore.CIPHER_DB_NAME,
                PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
            )
            encrypted.writeBytes(byteArrayOf(1, 2, 3))
            assertEquals(
                DatabasePassphraseStore.CIPHER_DB_NAME,
                PlaintextHistoryMigrator.prepareForIsolatedValidation(target, passphrase)
            )
            assertFalse(DatabasePassphraseStore.isPlaintextSqliteDatabase(encrypted))
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
        } finally {
            migrated.close()
        }

        PlaintextHistoryMigrator.complete(target, DatabasePassphraseStore.CIPHER_DB_NAME)
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
        } finally {
            migrated.close()
        }
        PlaintextHistoryMigrator.complete(target, DatabasePassphraseStore.CIPHER_DB_NAME)
        assertFalse(source.exists())
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

    private fun createPlaintextV5(target: Context): SupportSQLiteOpenHelper {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(target)
                .name(DatabasePassphraseStore.DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val ddl = InstrumentationRegistry.getInstrumentation().context.assets
                            .open("history-schema-v5.sql").bufferedReader().use { it.readLines() }
                        ddl.filter { it.isNotBlank() && !it.startsWith("--") }
                            .forEach { db.execSQL(it.removeSuffix(";")) }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                        error("Synthetic fixture must remain at v5")
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
                AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6,
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
