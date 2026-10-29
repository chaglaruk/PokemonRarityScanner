package com.pokerarity.scanner.data.local.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pokerarity.scanner.BuildConfig
import com.pokerarity.scanner.util.SecurityAuditLogger
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.time.Instant
import java.time.ZoneId

private const val HISTORY_SHINY_TRUTH_VERSION = 6
private const val HISTORY_REVIEW_VERSION = 7
private const val HISTORY_IV_VERSION = 8
private const val HISTORY_IDENTITY_VERSION = 9
private const val HISTORY_LEGACY_SCHEMA_VERSION = 3
private const val HISTORY_OFFLINE_TELEMETRY_VERSION = 4
private const val HISTORY_EVENT_CONFIDENCE_VERSION = 10
private const val HISTORY_DECISION_SUPPORT_VERSION = 11
private const val HISTORY_RARITY_ANALYSIS_VERSION = 12
private const val HISTORY_CAUGHT_DATE_VERSION = 13
private const val HISTORY_LEVEL_ALTERNATIVES_VERSION = 14
private const val CAUGHT_DATE_MIGRATION_BATCH_SIZE = 256

private fun readCaughtDateBatch(
    database: SupportSQLiteDatabase,
    lastId: Long,
    migrationZone: ZoneId
): List<Pair<Long, String>> {
    val rows = mutableListOf<Pair<Long, String>>()
    database.query(
        "SELECT id, caughtDate FROM scan_history " +
            "WHERE id > ? AND caughtDate IS NOT NULL ORDER BY id LIMIT ?",
        arrayOf<Any>(lastId, CAUGHT_DATE_MIGRATION_BATCH_SIZE)
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val timestamp = cursor.getLong(1)
            val date = Instant.ofEpochMilli(timestamp).atZone(migrationZone).toLocalDate()
            rows += id to date.toString()
        }
    }
    return rows
}

private fun updateCaughtDateBatch(database: SupportSQLiteDatabase, rows: List<Pair<Long, String>>) {
    val update = database.compileStatement(
        "UPDATE scan_history SET caughtDateLocalDate = ? WHERE id = ?"
    )
    try {
        rows.forEach { (id, date) ->
            update.clearBindings()
            update.bindString(1, date)
            update.bindLong(2, id)
            update.executeUpdateDelete()
        }
    } finally {
        update.close()
    }
}

private fun backfillCaughtDateLocalDate(database: SupportSQLiteDatabase) {
    val migrationZone = ZoneId.systemDefault()
    var lastId = Long.MIN_VALUE
    var rows = readCaughtDateBatch(database, lastId, migrationZone)
    while (rows.isNotEmpty()) {
        updateCaughtDateBatch(database, rows)
        lastId = rows.last().first
        rows = if (rows.size < CAUGHT_DATE_MIGRATION_BATCH_SIZE) {
            emptyList()
        } else {
            readCaughtDateBatch(database, lastId, migrationZone)
        }
    }
}

@Database(
    entities = [
        PokemonEntity::class,
        EventEntity::class,
        EventPokemonEntity::class,
        ScanHistoryEntity::class,
        TelemetryUploadEntity::class,
        OfflineTelemetryEntity::class,
        CollectionEntryEntity::class
    ],
    version = HISTORY_LEVEL_ALTERNATIVES_VERSION,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pokemonDao(): PokemonDao
    abstract fun eventDao(): EventDao
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun telemetryUploadDao(): TelemetryUploadDao
    abstract fun offlineTelemetryDao(): OfflineTelemetryDao
    abstract fun collectionEntryDao(): CollectionEntryDao

    companion object {
        val MIGRATION_3_4 = object : Migration(
            HISTORY_LEGACY_SCHEMA_VERSION,
            HISTORY_OFFLINE_TELEMETRY_VERSION
        ) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 4 added this entity; legacy v3 rows remain untouched.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `offline_telemetry` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `uploadId` TEXT NOT NULL,
                        `endpointUrl` TEXT NOT NULL,
                        `statusCode` INTEGER,
                        `payloadJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `flushedAt` INTEGER
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `collection_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `scanHistoryId` INTEGER,
                        `dex` INTEGER NOT NULL,
                        `speciesName` TEXT NOT NULL,
                        `formId` TEXT,
                        `variantId` TEXT,
                        `variantIdentityKey` TEXT NOT NULL,
                        `isShiny` INTEGER NOT NULL,
                        `isShadow` INTEGER NOT NULL,
                        `isPurified` INTEGER NOT NULL,
                        `isLucky` INTEGER NOT NULL,
                        `isCostume` INTEGER NOT NULL,
                        `isXXL` INTEGER NOT NULL,
                        `isXXS` INTEGER NOT NULL,
                        `costumeLabel` TEXT,
                        `backgroundType` TEXT,
                        `backgroundLabel` TEXT,
                        `eventLabel` TEXT,
                        `caughtDate` INTEGER,
                        `ivExact` INTEGER,
                        `ivMin` INTEGER,
                        `ivMax` INTEGER,
                        `rarityScore` INTEGER NOT NULL,
                        `rarityTierCode` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_entries_variantIdentityKey` ON `collection_entries` (`variantIdentityKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_entries_dex` ON `collection_entries` (`dex`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_entries_backgroundType` ON `collection_entries` (`backgroundType`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_entries_isXXL` ON `collection_entries` (`isXXL`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_entries_isXXS` ON `collection_entries` (`isXXS`)"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, HISTORY_SHINY_TRUTH_VERSION) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Nullable/additive: preserve every legacy row and Boolean, no inferred negatives.
                db.execSQL("ALTER TABLE scan_history ADD COLUMN shinyTruth TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(HISTORY_SHINY_TRUTH_VERSION, HISTORY_REVIEW_VERSION) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add-only: existing scan rows have unknown review status.
                db.execSQL("ALTER TABLE scan_history ADD COLUMN scanConfidenceScore INTEGER")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN scanConfidenceLabel TEXT")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN scanConfidenceDetail TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(HISTORY_REVIEW_VERSION, HISTORY_IV_VERSION) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add-only: historical records have no recoverable IV estimate.
                db.execSQL("ALTER TABLE scan_history ADD COLUMN ivMin INTEGER")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN ivMax INTEGER")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN ivCandidateCount INTEGER")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN ivSolveMode TEXT")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN ivLevelMin REAL")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN ivLevelMax REAL")
            }
        }

        val MIGRATION_8_9 = object : Migration(HISTORY_IV_VERSION, HISTORY_IDENTITY_VERSION) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add-only: existing History rows have no recoverable recognition identity.
                db.execSQL("ALTER TABLE scan_history ADD COLUMN recognitionIdentityJson TEXT")
            }
        }

        val MIGRATION_9_10 = object : Migration(HISTORY_IDENTITY_VERSION, HISTORY_EVENT_CONFIDENCE_VERSION) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Additive and nullable: older History rows have no persisted event confidence.
                db.execSQL("ALTER TABLE scan_history ADD COLUMN eventConfidenceCode TEXT")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN eventConfidenceLabel TEXT")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN eventConfidenceDetail TEXT")
            }
        }

        val MIGRATION_10_11 = object : Migration(HISTORY_EVENT_CONFIDENCE_VERSION, HISTORY_DECISION_SUPPORT_VERSION) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existing v10 rows have no persisted mismatch explanation or recognition summary.
                db.execSQL("ALTER TABLE scan_history ADD COLUMN mismatchGuardTitle TEXT")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN mismatchGuardDetail TEXT")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN recognitionSummary TEXT")
            }
        }

        val MIGRATION_11_12 = object : Migration(HISTORY_DECISION_SUPPORT_VERSION, HISTORY_RARITY_ANALYSIS_VERSION) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existing History rows have no saved rarity explanation or per-axis breakdown.
                db.execSQL("ALTER TABLE scan_history ADD COLUMN rarityAnalysisJson TEXT")
            }
        }

        val MIGRATION_12_13 = object : Migration(HISTORY_RARITY_ANALYSIS_VERSION, HISTORY_CAUGHT_DATE_VERSION) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_history ADD COLUMN caughtDateLocalDate TEXT")

                // Freeze the date currently shown to the user. Keep the legacy epoch value intact
                // for compatibility with existing calculations and data consumers.
                backfillCaughtDateLocalDate(db)
            }
        }

        val MIGRATION_13_14 = object : Migration(HISTORY_CAUGHT_DATE_VERSION, HISTORY_LEVEL_ALTERNATIVES_VERSION) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Older rows have no recoverable discrete level set; preserve unknown as null.
                db.execSQL("ALTER TABLE scan_history ADD COLUMN ivLevelAlternatives TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                val auditLogger = SecurityAuditLogger.getInstance(appContext)

                try {
                    SqlCipherInitializer.ensureLoaded()
                    val passphrase = DatabasePassphraseStore.getOrCreate(appContext)
                    val factory = SupportOpenHelperFactory(passphrase)

                    val instance = buildDatabase(appContext, factory)
                    runCatching { instance.openHelper.writableDatabase }
                        .onFailure { instance.close() }
                        .getOrThrow()
                    INSTANCE = instance
                    if (BuildConfig.DEBUG) {
                        Log.i("AppDatabase", "Database created with SQLCipher encryption")
                    }
                    auditLogger.logEncryptionInit(true, "SQLCipher initialized")
                    instance
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.e("AppDatabase", "Failed to initialize encrypted database", e)
                    }
                    auditLogger.logEncryptionInit(false, e.message)
                    throw e
                }
            }
        }

        private fun buildDatabase(
            context: Context,
            factory: SupportOpenHelperFactory
        ): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "pokerarity_db"
            )
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                    MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                    MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14
                )
                .build()
        }

    }
}
