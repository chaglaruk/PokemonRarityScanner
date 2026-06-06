package com.pokerarity.scanner.data.local.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.pokerarity.scanner.util.SecurityAuditLogger
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        PokemonEntity::class,
        EventEntity::class,
        EventPokemonEntity::class,
        ScanHistoryEntity::class,
        TelemetryUploadEntity::class,
        OfflineTelemetryEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pokemonDao(): PokemonDao
    abstract fun eventDao(): EventDao
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun telemetryUploadDao(): TelemetryUploadDao
    abstract fun offlineTelemetryDao(): OfflineTelemetryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scan_history ADD COLUMN collectionScore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN collectionTier TEXT NOT NULL DEFAULT 'COMMON'")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN originalCollectionScore INTEGER")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN originalCatalogVersion TEXT")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN latestCatalogVersion TEXT")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN isPurified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN hasLocationCard INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN hasSpecialForm INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN editedDetailsJson TEXT")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN isEdited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE scan_history ADD COLUMN axisBreakdownJson TEXT")
                db.execSQL("UPDATE scan_history SET collectionScore = rarityScore, collectionTier = rarityTier WHERE rarityScore > 0")
            }
        }
        
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
                                Log.w("AppDatabase", "Recovering from unreadable existing database: ${error.message}")
                                auditLogger.log(
                                    SecurityAuditLogger.EventType.ENCRYPTION_FAILED,
                                    "Database recovery triggered",
                                    "Error: ${error.message}",
                                    success = false
                                )
                                instance.close()
                                DatabasePassphraseStore.deleteDatabaseFiles(appContext)
                                instance = buildDatabase(appContext, factory)
                                instance.openHelper.writableDatabase
                            } else {
                                throw error
                            }
                        }
                    INSTANCE = instance
                    Log.i("AppDatabase", "Database created with SQLCipher encryption")
                    auditLogger.logEncryptionInit(true, "SQLCipher 256-bit")
                    instance
                } catch (e: Exception) {
                    Log.e("AppDatabase", "Failed to initialize encrypted database", e)
                    auditLogger.logEncryptionInit(false, "Error: ${e.message}")
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
                        .addMigrations(MIGRATION_4_5)
                        .fallbackToDestructiveMigration()
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
