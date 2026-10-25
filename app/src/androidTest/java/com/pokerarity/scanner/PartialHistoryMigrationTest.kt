package com.pokerarity.scanner

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.RecognitionFormStatus
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionIdentityCompat
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.data.model.toUiPokemon
import com.pokerarity.scanner.data.model.valuableSummary
import com.pokerarity.scanner.data.repository.ScanHistoryMapper
import java.io.File
import java.nio.file.Files
import java.util.Date
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Exact previous schema + synthetic rows only; production builder uses a private test directory. */
@RunWith(AndroidJUnit4::class)
class PartialHistoryMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val publicTestPassphrase = "public-partial-history-test-only"

    private fun factory(encrypted: Boolean): SupportSQLiteOpenHelper.Factory {
        if (!encrypted) return FrameworkSQLiteOpenHelperFactory()
        System.loadLibrary("sqlcipher")
        return SupportOpenHelperFactory(publicTestPassphrase.toByteArray())
    }

    private fun isolatedContext(): Context = object : ContextWrapper(context) {
        private val directory = Files.createTempDirectory(context.cacheDir.toPath(), "partial-history-").toFile()
        override fun getDatabasePath(name: String): File = File(directory, name)
        override fun getApplicationContext(): Context = this
    }

    private fun createOldDatabase(target: Context, encrypted: Boolean, version: Int = 5) {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(target).name("pokerarity_db")
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val ddl = InstrumentationRegistry.getInstrumentation().context.assets
                        .open("history-schema-v5.sql").bufferedReader().use { it.readLines() }
                    ddl.filter { it.isNotBlank() && !it.startsWith("--") }
                        .forEach { db.execSQL(it.removeSuffix(";")) }
                    if (version >= 6) db.execSQL("ALTER TABLE scan_history ADD COLUMN shinyTruth TEXT")
                    if (version >= 7) {
                        db.execSQL("ALTER TABLE scan_history ADD COLUMN scanConfidenceScore INTEGER")
                        db.execSQL("ALTER TABLE scan_history ADD COLUMN scanConfidenceLabel TEXT")
                        db.execSQL("ALTER TABLE scan_history ADD COLUMN scanConfidenceDetail TEXT")
                    }
                    if (version >= 8) {
                        db.execSQL("ALTER TABLE scan_history ADD COLUMN ivMin INTEGER")
                        db.execSQL("ALTER TABLE scan_history ADD COLUMN ivMax INTEGER")
                        db.execSQL("ALTER TABLE scan_history ADD COLUMN ivCandidateCount INTEGER")
                        db.execSQL("ALTER TABLE scan_history ADD COLUMN ivSolveMode TEXT")
                        db.execSQL("ALTER TABLE scan_history ADD COLUMN ivLevelMin REAL")
                        db.execSQL("ALTER TABLE scan_history ADD COLUMN ivLevelMax REAL")
                    }
                    if (version >= 9) db.execSQL("ALTER TABLE scan_history ADD COLUMN recognitionIdentityJson TEXT")
                    if (version >= 10) {
                        db.execSQL("ALTER TABLE scan_history ADD COLUMN eventConfidenceCode TEXT")
                        db.execSQL("ALTER TABLE scan_history ADD COLUMN eventConfidenceLabel TEXT")
                        db.execSQL("ALTER TABLE scan_history ADD COLUMN eventConfidenceDetail TEXT")
                    }
                    seedOldRows(db)
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    error("Fixture is already at its requested previous version")
            }).build()
        factory(encrypted).create(configuration).use { it.writableDatabase }
    }

    private fun seedOldRows(db: SupportSQLiteDatabase) {
        listOf(null, 640, 0).forEachIndexed { index, cp ->
            db.execSQL("""INSERT INTO scan_history
                (id,timestamp,pokemonName,cp,hp,caughtDate,rawOcrText,isShiny,isShadow,isLucky,hasCostume,rarityScore,rarityTier)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(), arrayOf(
                42 + index, 123456789L, "Pikachu", cp, 60, null, "synthetic-old-$index",
                if (index == 1) 1 else 0, 0, 0, 0, 42, "RARE"
            ))
        }
        db.execSQL("INSERT INTO pokemon (id,name,baseRarity) VALUES (12,'Pikachu',4)")
    }

    private fun openCurrent(target: Context, encrypted: Boolean): AppDatabase {
        if (!encrypted) return Room.databaseBuilder(target, AppDatabase::class.java, "pokerarity_db")
            .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10).build()
        // Exercise the REAL production registration/no-destructive-fallback path, without its app path/key store.
        val method = AppDatabase.Companion.javaClass.getDeclaredMethod(
            "buildDatabase", Context::class.java, SupportOpenHelperFactory::class.java
        ).apply { isAccessible = true }
        return method.invoke(AppDatabase.Companion, target, factory(true)) as AppDatabase
    }

    private inline fun withCurrent(target: Context, encrypted: Boolean, action: (AppDatabase) -> Unit) {
        val database = openCurrent(target, encrypted)
        try { action(database) } finally { database.close() }
    }

    private fun identity(truth: RecognitionTruth) = RecognitionIdentity(
        speciesStatus = RecognitionSpeciesStatus.KNOWN, canonicalSpecies = "Pikachu",
        speciesAuthority = "exact_canonical", formStatus = RecognitionFormStatus.UNKNOWN,
        shiny = truth, shadow = RecognitionTruth.UNKNOWN, purified = RecognitionTruth.UNKNOWN,
        lucky = RecognitionTruth.UNKNOWN, costume = RecognitionTruth.UNKNOWN,
        specialForm = RecognitionTruth.UNKNOWN, xxs = RecognitionTruth.UNKNOWN,
        xxl = RecognitionTruth.UNKNOWN, locationCard = RecognitionTruth.UNKNOWN
    )

    private fun insertTypedRows(database: AppDatabase) = runBlocking {
        RecognitionTruth.values().forEach { truth ->
            val source = PokemonData(cp = null, hp = 60, maxHp = 60, name = "Pikachu",
                realName = "Pikachu", candyName = "Pikachu", megaEnergy = null,
                weight = null, height = null, stardust = null, caughtDate = null,
                recognitionIdentity = identity(truth))
            val score = RarityScore(totalScore = 42, tier = RarityTier.RARE,
        breakdown = mapOf("species" to 42), explanation = listOf("Species rarity"))
            val row = ScanHistoryMapper.toEntity(source, RecognitionIdentityCompat.toVisualFeatures(source.recognitionIdentity!!), score)
            val id = database.scanHistoryDao().insert(row)
            assertTrue("autoincrement must continue after historical IDs", id > 44)
            val loaded = database.scanHistoryDao().getById(id)!!
            assertEquals(truth.name, loaded.shinyTruth)
            assertEquals(truth, loaded.toUiPokemon().shinyTruth)
            assertNull(loaded.cp)
            assertNull(loaded.toUiPokemon().cp)
            assertEquals("Unknown", loaded.toUiPokemon().cpDisplayText)
            assertEquals(42, loaded.rarityScore)
            assertEquals("RARE", loaded.rarityTier)
        }
    }

    private fun assertLegacyRows(database: AppDatabase) = runBlocking {
        val dao = database.scanHistoryDao()
        (42L..44L).forEach { id ->
            val row = dao.getById(id)!!
            assertEquals("synthetic-old-${id - 42}", row.rawOcrText)
            assertEquals(Date(123456789), row.timestamp)
            assertNull(row.shinyTruth)
            assertNull(row.scanConfidenceLabel)
            assertNull(row.scanConfidenceDetail)
            assertNull(row.scanConfidenceScore)
            assertNull(row.eventConfidenceCode)
            assertNull(row.eventConfidenceLabel)
            assertNull(row.eventConfidenceDetail)
            assertNull(row.toUiPokemon().decisionSupport)
            assertEquals(42, row.rarityScore)
            assertEquals("RARE", row.rarityTier)
        }
        assertNull(dao.getById(42)!!.toUiPokemon().cp)
        assertEquals(RecognitionTruth.UNKNOWN, dao.getById(42)!!.toUiPokemon().shinyTruth)
        assertEquals(640, dao.getById(43)!!.toUiPokemon().cp)
        assertEquals(RecognitionTruth.TRUE, dao.getById(43)!!.toUiPokemon().shinyTruth)
        assertEquals(0, dao.getById(44)!!.cp)
        assertNull(dao.getById(44)!!.toUiPokemon().cp)
        database.openHelper.writableDatabase.query("SELECT name,baseRarity FROM pokemon WHERE id=12").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("Pikachu", c.getString(0)); assertEquals(4, c.getInt(1))
        }
    }

    private fun exerciseMigration(encrypted: Boolean, oldVersion: Int = 5) = runBlocking {
        val target = isolatedContext()
        createOldDatabase(target, encrypted, oldVersion)
        withCurrent(target, encrypted) { database ->
            assertEquals(3, database.scanHistoryDao().count())
            assertLegacyRows(database)
            insertTypedRows(database)
            assertEquals(6, database.scanHistoryDao().count())
        }
        withCurrent(target, encrypted) { reopened ->
            assertEquals("reopen cannot duplicate or drop records", 6, reopened.scanHistoryDao().count())
            assertLegacyRows(reopened)
            assertEquals(setOf("TRUE", "FALSE", "UNKNOWN"),
                reopened.scanHistoryDao().getByPokemonName("Pikachu").mapNotNull { it.shinyTruth }.toSet())
        }
        if (encrypted) {
            val header = target.getDatabasePath("pokerarity_db").inputStream().use { stream -> ByteArray(16).also { stream.read(it) } }
            assertFalse(String(header).startsWith("SQLite format 3"))
        }
    }

    @Test fun realRoomMigratesVersionFiveAndPreservesRows() { exerciseMigration(false) }
    @Test fun realSqlCipherProductionBuilderMigratesVersionFiveAndPreservesRows() { exerciseMigration(true) }
    @Test fun realRoomMigratesVersionSixAndPreservesRows() { exerciseMigration(false, 6) }
    @Test fun realSqlCipherProductionBuilderMigratesVersionSixAndPreservesRows() { exerciseMigration(true, 6) }
    @Test fun realRoomMigratesVersionSevenAndPreservesRows() { exerciseMigration(false, 7) }
    @Test fun realSqlCipherProductionBuilderMigratesVersionSevenAndPreservesRows() { exerciseMigration(true, 7) }

    @Test fun reviewMetadataSurvivesSqlCipherDatabaseReopen() = runBlocking {
        val target = isolatedContext()
        createOldDatabase(target, true, 6)
        withCurrent(target, true) { database ->
            database.scanHistoryDao().insert(ScanHistoryEntity(
                pokemonName = "Farfetch'd", cp = null, hp = null, caughtDate = null,
                rawOcrText = "", scanConfidenceScore = 70,
                scanConfidenceLabel = "Review recommended",
                scanConfidenceDetail = "CP missing, HP missing",
                eventConfidenceCode = "DATE_BACKED_EVENT",
                eventConfidenceLabel = "Spring event",
                eventConfidenceDetail = "Catch date matched event timing"
            ))
            assertEquals(4, database.scanHistoryDao().count())
        }
        withCurrent(target, true) { database ->
            assertEquals(4, database.scanHistoryDao().count())
            val row = database.scanHistoryDao().getByPokemonName("Farfetch'd").single()
            assertEquals(70, row.scanConfidenceScore)
            assertEquals("Review recommended", row.toUiPokemon().decisionSupport?.scanConfidenceLabel)
            assertEquals("CP missing, HP missing", row.toUiPokemon().decisionSupport?.scanConfidenceDetail)
            assertEquals("DATE_BACKED_EVENT", row.eventConfidenceCode)
            assertEquals("Spring event", row.toUiPokemon().decisionSupport?.eventConfidenceLabel)
            assertEquals("Catch date matched event timing", row.toUiPokemon().decisionSupport?.eventConfidenceDetail)
            assertTrue(row.toUiPokemon().valuableSummary().contains("Catch date matched event timing"))
            assertEquals("Unknown", row.toUiPokemon().cpDisplayText)
            assertNull(row.hp)
        }
    }

    @Test fun unsupportedVersionFailsClosedWithoutDroppingEncryptedRows() {
        val target = isolatedContext()
        createOldDatabase(target, true, version = 99)
        val database = openCurrent(target, true)
        try {
            database.openHelper.writableDatabase
            fail("unsupported version must not fall back to destructive migration")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("migration", ignoreCase = true))
        } finally { database.close() }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(target).name("pokerarity_db")
            .callback(object : SupportSQLiteOpenHelper.Callback(99) {
                override fun onCreate(db: SupportSQLiteDatabase) = error("old encrypted data must still exist")
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("no upgrade")
            }).build()
        factory(true).create(configuration).use { helper ->
            helper.readableDatabase.query("SELECT COUNT(*) FROM scan_history").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(3, c.getInt(0))
            }
        }
    }
}
