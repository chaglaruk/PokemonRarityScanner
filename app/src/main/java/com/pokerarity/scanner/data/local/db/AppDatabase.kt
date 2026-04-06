package com.pokerarity.scanner.data.local.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    val passphrase = DatabasePassphraseStore.getOrCreate(context.applicationContext)
                    val factory = SupportOpenHelperFactory(passphrase)
                    
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
                    throw e
                }
            }
        }
    }
}
