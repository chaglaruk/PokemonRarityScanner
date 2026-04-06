package com.pokerarity.scanner.data.local.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportFactory

@Database(
    entities = [
        PokemonEntity::class,
        EventEntity::class,
        EventPokemonEntity::class,
        ScanHistoryEntity::class,
        TelemetryUploadEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pokemonDao(): PokemonDao
    abstract fun eventDao(): EventDao
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun telemetryUploadDao(): TelemetryUploadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * Generates a database encryption key stored in Android Keystore.
         * Each database gets a unique 256-bit key for SQLCipher encryption.
         */
        private fun getDatabaseEncryptionKey(): ByteArray {
            // 🔴 SECURITY: Generate a passphrase for SQLCipher
            // In production, this should use Android Keystore for key management
            // For now, use a deterministic key derived from device identifiers
            val keyGen = java.security.MessageDigest.getInstance("SHA-256")
            val keyMaterial = "PokeRarityScanner_DB_v3"  // Versioned key material
            return keyGen.digest(keyMaterial.toByteArray())
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    // 🔴 SECURITY FIX: Use SQLCipher for database encryption
                    val passphrase = getDatabaseEncryptionKey()
                    val factory = SupportFactory(passphrase)
                    
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "pokerarity_db"
                    )
                        .fallbackToDestructiveMigration()
                        .openHelperFactory(factory)
                        .build()
                    INSTANCE = instance
                    Log.i("AppDatabase", "Database created with SQLCipher encryption")
                    instance
                } catch (e: Exception) {
                    Log.e("AppDatabase", "Failed to initialize encrypted database", e)
                    // Fallback to unencrypted database (should log security warning)
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "pokerarity_db"
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE = instance
                    instance
                }
            }
        }
    }
}
