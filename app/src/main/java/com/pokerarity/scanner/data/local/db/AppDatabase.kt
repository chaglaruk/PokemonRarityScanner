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

private const val HISTORY_SHINY_TRUTH_VERSION = 6
private const val HISTORY_REVIEW_VERSION = 7
private const val HISTORY_IV_VERSION = 8
private const val HISTORY_IDENTITY_VERSION = 9
private const val HISTORY_EVENT_CONFIDENCE_VERSION = 10

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
    version = HISTORY_EVENT_CONFIDENCE_VERSION,
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
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
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
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_entries_variantIdentityKey` ON `collection_entries` (`variantIdentityKey`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_entries_dex` ON `collection_entries` (`dex`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_entries_backgroundType` ON `collection_entries` (`backgroundType`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_entries_isXXL` ON `collection_entries` (`isXXL`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_collection_entries_isXXS` ON `collection_entries` (`isXXS`)"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, HISTORY_SHINY_TRUTH_VERSION) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Nullable/additive: preserve every legacy row and Boolean, no inferred negatives.
                database.execSQL("ALTER TABLE scan_history ADD COLUMN shinyTruth TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(HISTORY_SHINY_TRUTH_VERSION, HISTORY_REVIEW_VERSION) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add-only: existing scan rows have unknown review status.
                database.execSQL("ALTER TABLE scan_history ADD COLUMN scanConfidenceScore INTEGER")
                database.execSQL("ALTER TABLE scan_history ADD COLUMN scanConfidenceLabel TEXT")
                database.execSQL("ALTER TABLE scan_history ADD COLUMN scanConfidenceDetail TEXT")
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
                // Additive and nullable: older History rows have no persisted event context.
                db.execSQL("ALTER TABLE scan_history ADD COLUMN eventConfidenceCode TEXT")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN eventConfidenceLabel TEXT")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN eventConfidenceDetail TEXT")
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

                    var instance = buildDatabase(appContext, factory)
                    runCatching { instance.openHelper.writableDatabase }
                        .onFailure { error ->
                            if (isRecoverableDatabaseError(error)) {
                                if (BuildConfig.DEBUG) {
                                    Log.w("AppDatabase", "Recovering from unreadable existing database", error)
                                }
                                auditLogger.logEncryptionInit(false, error.message)
                                instance.close()
                                DatabasePassphraseStore.deleteDatabaseFiles(appContext)
                                instance = buildDatabase(appContext, factory)
                                instance.openHelper.writableDatabase
                            } else {
                                throw error
                            }
                        }
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
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                    MIGRATION_8_9, MIGRATION_9_10
                )
                .build()
        }

        private fun isRecoverableDatabaseError(error: Throwable): Boolean {
            var current: Throwable? = error
            while (current != null) {
                val message = current.message.orEmpty()
                if ("file is not a database" in message || "file is encrypted or is not a database" in message) {
                    return true
                }
                current = current.cause
            }
            return false
        }
    }
}
