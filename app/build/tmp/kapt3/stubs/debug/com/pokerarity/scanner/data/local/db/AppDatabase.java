package com.pokerarity.scanner.data.local.db;

import android.content.Context;
import android.util.Log;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\'\u0018\u0000 \u000b2\u00020\u0001:\u0001\u000bB\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H&J\b\u0010\u0005\u001a\u00020\u0006H&J\b\u0010\u0007\u001a\u00020\bH&J\b\u0010\t\u001a\u00020\nH&\u00a8\u0006\f"}, d2 = {"Lcom/pokerarity/scanner/data/local/db/AppDatabase;", "Landroidx/room/RoomDatabase;", "()V", "eventDao", "Lcom/pokerarity/scanner/data/local/db/EventDao;", "pokemonDao", "Lcom/pokerarity/scanner/data/local/db/PokemonDao;", "scanHistoryDao", "Lcom/pokerarity/scanner/data/local/db/ScanHistoryDao;", "telemetryUploadDao", "Lcom/pokerarity/scanner/data/local/db/TelemetryUploadDao;", "Companion", "app_debug"})
@androidx.room.Database(entities = {com.pokerarity.scanner.data.local.db.PokemonEntity.class, com.pokerarity.scanner.data.local.db.EventEntity.class, com.pokerarity.scanner.data.local.db.EventPokemonEntity.class, com.pokerarity.scanner.data.local.db.ScanHistoryEntity.class, com.pokerarity.scanner.data.local.db.TelemetryUploadEntity.class}, version = 3, exportSchema = false)
@androidx.room.TypeConverters(value = {com.pokerarity.scanner.data.local.db.Converters.class})
public abstract class AppDatabase extends androidx.room.RoomDatabase {
    @kotlin.jvm.Volatile()
    @org.jetbrains.annotations.Nullable()
    private static volatile com.pokerarity.scanner.data.local.db.AppDatabase INSTANCE;
    @org.jetbrains.annotations.NotNull()
    public static final com.pokerarity.scanner.data.local.db.AppDatabase.Companion Companion = null;
    
    public AppDatabase() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.pokerarity.scanner.data.local.db.PokemonDao pokemonDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.pokerarity.scanner.data.local.db.EventDao eventDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.pokerarity.scanner.data.local.db.ScanHistoryDao scanHistoryDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.pokerarity.scanner.data.local.db.TelemetryUploadDao telemetryUploadDao();
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0003\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\tH\u0002J\u000e\u0010\n\u001a\u00020\u00042\u0006\u0010\u0006\u001a\u00020\u0007J\u0010\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eH\u0002R\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000f"}, d2 = {"Lcom/pokerarity/scanner/data/local/db/AppDatabase$Companion;", "", "()V", "INSTANCE", "Lcom/pokerarity/scanner/data/local/db/AppDatabase;", "buildDatabase", "context", "Landroid/content/Context;", "factory", "Lnet/zetetic/database/sqlcipher/SupportOpenHelperFactory;", "getInstance", "isRecoverableDatabaseError", "", "error", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.pokerarity.scanner.data.local.db.AppDatabase getInstance(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
            return null;
        }
        
        private final com.pokerarity.scanner.data.local.db.AppDatabase buildDatabase(android.content.Context context, net.zetetic.database.sqlcipher.SupportOpenHelperFactory factory) {
            return null;
        }
        
        private final boolean isRecoverableDatabaseError(java.lang.Throwable error) {
            return false;
        }
    }
}