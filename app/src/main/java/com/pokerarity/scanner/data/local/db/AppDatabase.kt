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

private const val DATABASE_VERSION = 6
private const val DATABASE_VERSION_BEFORE_SHINY_TRUTH = 5

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
    version = DATABASE_VERSION,
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

        val MIGRATION_5_6 = object : Migration(DATABASE_VERSION_BEFORE_SHINY_TRUTH, DATABASE_VERSION) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `scan_history` ADD COLUMN `shinyTruth` TEXT NOT NULL DEFAULT 'UNKNOWN'"
                )
                db.execSQL(
                    "UPDATE `scan_history` SET `shinyTruth` = CASE WHEN `isShiny` = 1 THEN 'TRUE' ELSE 'UNKNOWN' END"
                )
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
                        .onFailure { error ->
                            if (isRecoverableDatabaseError(error)) {
                                if (BuildConfig.DEBUG) {
                                    Log.e(
                                        "AppDatabase",
                                        "Existing encrypted database is unreadable; preserving its files",
                                        error
                                    )
                                }
                            } else {
                                if (BuildConfig.DEBUG) {
                                    Log.e("AppDatabase", "Failed to open existing database", error)
                                }
                            }
                            throw error
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
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
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
