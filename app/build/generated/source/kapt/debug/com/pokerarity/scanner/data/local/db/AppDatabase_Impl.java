package com.pokerarity.scanner.data.local.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile PokemonDao _pokemonDao;

  private volatile EventDao _eventDao;

  private volatile ScanHistoryDao _scanHistoryDao;

  private volatile TelemetryUploadDao _telemetryUploadDao;

  private volatile OfflineTelemetryDao _offlineTelemetryDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(4) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `pokemon` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `baseRarity` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `pokemonId` INTEGER NOT NULL, `rarityWeight` INTEGER NOT NULL, `isOneDayEvent` INTEGER NOT NULL, FOREIGN KEY(`pokemonId`) REFERENCES `pokemon`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_pokemonId` ON `events` (`pokemonId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `event_pokemon` (`id` TEXT NOT NULL, `baseName` TEXT NOT NULL, `eventName` TEXT NOT NULL, `eventBonusScore` INTEGER NOT NULL, `spriteKey` TEXT, `variantToken` TEXT, `eventStart` INTEGER, `eventEnd` INTEGER, `source` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_pokemon_baseName` ON `event_pokemon` (`baseName`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_pokemon_eventName` ON `event_pokemon` (`eventName`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_pokemon_spriteKey` ON `event_pokemon` (`spriteKey`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_pokemon_variantToken` ON `event_pokemon` (`variantToken`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `scan_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `pokemonName` TEXT, `cp` INTEGER, `hp` INTEGER, `caughtDate` INTEGER, `rawOcrText` TEXT NOT NULL, `isShiny` INTEGER NOT NULL, `isShadow` INTEGER NOT NULL, `isLucky` INTEGER NOT NULL, `hasCostume` INTEGER NOT NULL, `rarityScore` INTEGER NOT NULL, `rarityTier` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `telemetry_uploads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uploadId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `lastError` TEXT, `uploadedAt` INTEGER, `payloadJson` TEXT NOT NULL, `screenshotPath` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `offline_telemetry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uploadId` TEXT NOT NULL, `endpointUrl` TEXT NOT NULL, `statusCode` INTEGER, `payloadJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `flushedAt` INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'b20e3a55c76de57bbd08d2383044370b')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `pokemon`");
        db.execSQL("DROP TABLE IF EXISTS `events`");
        db.execSQL("DROP TABLE IF EXISTS `event_pokemon`");
        db.execSQL("DROP TABLE IF EXISTS `scan_history`");
        db.execSQL("DROP TABLE IF EXISTS `telemetry_uploads`");
        db.execSQL("DROP TABLE IF EXISTS `offline_telemetry`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsPokemon = new HashMap<String, TableInfo.Column>(3);
        _columnsPokemon.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPokemon.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPokemon.put("baseRarity", new TableInfo.Column("baseRarity", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPokemon = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPokemon = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPokemon = new TableInfo("pokemon", _columnsPokemon, _foreignKeysPokemon, _indicesPokemon);
        final TableInfo _existingPokemon = TableInfo.read(db, "pokemon");
        if (!_infoPokemon.equals(_existingPokemon)) {
          return new RoomOpenHelper.ValidationResult(false, "pokemon(com.pokerarity.scanner.data.local.db.PokemonEntity).\n"
                  + " Expected:\n" + _infoPokemon + "\n"
                  + " Found:\n" + _existingPokemon);
        }
        final HashMap<String, TableInfo.Column> _columnsEvents = new HashMap<String, TableInfo.Column>(7);
        _columnsEvents.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("startDate", new TableInfo.Column("startDate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("endDate", new TableInfo.Column("endDate", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("pokemonId", new TableInfo.Column("pokemonId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("rarityWeight", new TableInfo.Column("rarityWeight", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEvents.put("isOneDayEvent", new TableInfo.Column("isOneDayEvent", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysEvents = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysEvents.add(new TableInfo.ForeignKey("pokemon", "CASCADE", "NO ACTION", Arrays.asList("pokemonId"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesEvents = new HashSet<TableInfo.Index>(1);
        _indicesEvents.add(new TableInfo.Index("index_events_pokemonId", false, Arrays.asList("pokemonId"), Arrays.asList("ASC")));
        final TableInfo _infoEvents = new TableInfo("events", _columnsEvents, _foreignKeysEvents, _indicesEvents);
        final TableInfo _existingEvents = TableInfo.read(db, "events");
        if (!_infoEvents.equals(_existingEvents)) {
          return new RoomOpenHelper.ValidationResult(false, "events(com.pokerarity.scanner.data.local.db.EventEntity).\n"
                  + " Expected:\n" + _infoEvents + "\n"
                  + " Found:\n" + _existingEvents);
        }
        final HashMap<String, TableInfo.Column> _columnsEventPokemon = new HashMap<String, TableInfo.Column>(10);
        _columnsEventPokemon.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventPokemon.put("baseName", new TableInfo.Column("baseName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventPokemon.put("eventName", new TableInfo.Column("eventName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventPokemon.put("eventBonusScore", new TableInfo.Column("eventBonusScore", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventPokemon.put("spriteKey", new TableInfo.Column("spriteKey", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventPokemon.put("variantToken", new TableInfo.Column("variantToken", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventPokemon.put("eventStart", new TableInfo.Column("eventStart", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventPokemon.put("eventEnd", new TableInfo.Column("eventEnd", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventPokemon.put("source", new TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEventPokemon.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysEventPokemon = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesEventPokemon = new HashSet<TableInfo.Index>(4);
        _indicesEventPokemon.add(new TableInfo.Index("index_event_pokemon_baseName", false, Arrays.asList("baseName"), Arrays.asList("ASC")));
        _indicesEventPokemon.add(new TableInfo.Index("index_event_pokemon_eventName", false, Arrays.asList("eventName"), Arrays.asList("ASC")));
        _indicesEventPokemon.add(new TableInfo.Index("index_event_pokemon_spriteKey", false, Arrays.asList("spriteKey"), Arrays.asList("ASC")));
        _indicesEventPokemon.add(new TableInfo.Index("index_event_pokemon_variantToken", false, Arrays.asList("variantToken"), Arrays.asList("ASC")));
        final TableInfo _infoEventPokemon = new TableInfo("event_pokemon", _columnsEventPokemon, _foreignKeysEventPokemon, _indicesEventPokemon);
        final TableInfo _existingEventPokemon = TableInfo.read(db, "event_pokemon");
        if (!_infoEventPokemon.equals(_existingEventPokemon)) {
          return new RoomOpenHelper.ValidationResult(false, "event_pokemon(com.pokerarity.scanner.data.local.db.EventPokemonEntity).\n"
                  + " Expected:\n" + _infoEventPokemon + "\n"
                  + " Found:\n" + _existingEventPokemon);
        }
        final HashMap<String, TableInfo.Column> _columnsScanHistory = new HashMap<String, TableInfo.Column>(13);
        _columnsScanHistory.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScanHistory.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScanHistory.put("pokemonName", new TableInfo.Column("pokemonName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScanHistory.put("cp", new TableInfo.Column("cp", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScanHistory.put("hp", new TableInfo.Column("hp", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScanHistory.put("caughtDate", new TableInfo.Column("caughtDate", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScanHistory.put("rawOcrText", new TableInfo.Column("rawOcrText", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScanHistory.put("isShiny", new TableInfo.Column("isShiny", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScanHistory.put("isShadow", new TableInfo.Column("isShadow", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScanHistory.put("isLucky", new TableInfo.Column("isLucky", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScanHistory.put("hasCostume", new TableInfo.Column("hasCostume", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScanHistory.put("rarityScore", new TableInfo.Column("rarityScore", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScanHistory.put("rarityTier", new TableInfo.Column("rarityTier", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysScanHistory = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesScanHistory = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoScanHistory = new TableInfo("scan_history", _columnsScanHistory, _foreignKeysScanHistory, _indicesScanHistory);
        final TableInfo _existingScanHistory = TableInfo.read(db, "scan_history");
        if (!_infoScanHistory.equals(_existingScanHistory)) {
          return new RoomOpenHelper.ValidationResult(false, "scan_history(com.pokerarity.scanner.data.local.db.ScanHistoryEntity).\n"
                  + " Expected:\n" + _infoScanHistory + "\n"
                  + " Found:\n" + _existingScanHistory);
        }
        final HashMap<String, TableInfo.Column> _columnsTelemetryUploads = new HashMap<String, TableInfo.Column>(9);
        _columnsTelemetryUploads.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTelemetryUploads.put("uploadId", new TableInfo.Column("uploadId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTelemetryUploads.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTelemetryUploads.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTelemetryUploads.put("attempts", new TableInfo.Column("attempts", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTelemetryUploads.put("lastError", new TableInfo.Column("lastError", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTelemetryUploads.put("uploadedAt", new TableInfo.Column("uploadedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTelemetryUploads.put("payloadJson", new TableInfo.Column("payloadJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTelemetryUploads.put("screenshotPath", new TableInfo.Column("screenshotPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTelemetryUploads = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTelemetryUploads = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTelemetryUploads = new TableInfo("telemetry_uploads", _columnsTelemetryUploads, _foreignKeysTelemetryUploads, _indicesTelemetryUploads);
        final TableInfo _existingTelemetryUploads = TableInfo.read(db, "telemetry_uploads");
        if (!_infoTelemetryUploads.equals(_existingTelemetryUploads)) {
          return new RoomOpenHelper.ValidationResult(false, "telemetry_uploads(com.pokerarity.scanner.data.local.db.TelemetryUploadEntity).\n"
                  + " Expected:\n" + _infoTelemetryUploads + "\n"
                  + " Found:\n" + _existingTelemetryUploads);
        }
        final HashMap<String, TableInfo.Column> _columnsOfflineTelemetry = new HashMap<String, TableInfo.Column>(7);
        _columnsOfflineTelemetry.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfflineTelemetry.put("uploadId", new TableInfo.Column("uploadId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfflineTelemetry.put("endpointUrl", new TableInfo.Column("endpointUrl", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfflineTelemetry.put("statusCode", new TableInfo.Column("statusCode", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfflineTelemetry.put("payloadJson", new TableInfo.Column("payloadJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfflineTelemetry.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfflineTelemetry.put("flushedAt", new TableInfo.Column("flushedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysOfflineTelemetry = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesOfflineTelemetry = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoOfflineTelemetry = new TableInfo("offline_telemetry", _columnsOfflineTelemetry, _foreignKeysOfflineTelemetry, _indicesOfflineTelemetry);
        final TableInfo _existingOfflineTelemetry = TableInfo.read(db, "offline_telemetry");
        if (!_infoOfflineTelemetry.equals(_existingOfflineTelemetry)) {
          return new RoomOpenHelper.ValidationResult(false, "offline_telemetry(com.pokerarity.scanner.data.local.db.OfflineTelemetryEntity).\n"
                  + " Expected:\n" + _infoOfflineTelemetry + "\n"
                  + " Found:\n" + _existingOfflineTelemetry);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "b20e3a55c76de57bbd08d2383044370b", "b8ce1f197ee79b040870ba18c5925047");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "pokemon","events","event_pokemon","scan_history","telemetry_uploads","offline_telemetry");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `pokemon`");
      _db.execSQL("DELETE FROM `events`");
      _db.execSQL("DELETE FROM `event_pokemon`");
      _db.execSQL("DELETE FROM `scan_history`");
      _db.execSQL("DELETE FROM `telemetry_uploads`");
      _db.execSQL("DELETE FROM `offline_telemetry`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(PokemonDao.class, PokemonDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(EventDao.class, EventDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ScanHistoryDao.class, ScanHistoryDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TelemetryUploadDao.class, TelemetryUploadDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(OfflineTelemetryDao.class, OfflineTelemetryDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public PokemonDao pokemonDao() {
    if (_pokemonDao != null) {
      return _pokemonDao;
    } else {
      synchronized(this) {
        if(_pokemonDao == null) {
          _pokemonDao = new PokemonDao_Impl(this);
        }
        return _pokemonDao;
      }
    }
  }

  @Override
  public EventDao eventDao() {
    if (_eventDao != null) {
      return _eventDao;
    } else {
      synchronized(this) {
        if(_eventDao == null) {
          _eventDao = new EventDao_Impl(this);
        }
        return _eventDao;
      }
    }
  }

  @Override
  public ScanHistoryDao scanHistoryDao() {
    if (_scanHistoryDao != null) {
      return _scanHistoryDao;
    } else {
      synchronized(this) {
        if(_scanHistoryDao == null) {
          _scanHistoryDao = new ScanHistoryDao_Impl(this);
        }
        return _scanHistoryDao;
      }
    }
  }

  @Override
  public TelemetryUploadDao telemetryUploadDao() {
    if (_telemetryUploadDao != null) {
      return _telemetryUploadDao;
    } else {
      synchronized(this) {
        if(_telemetryUploadDao == null) {
          _telemetryUploadDao = new TelemetryUploadDao_Impl(this);
        }
        return _telemetryUploadDao;
      }
    }
  }

  @Override
  public OfflineTelemetryDao offlineTelemetryDao() {
    if (_offlineTelemetryDao != null) {
      return _offlineTelemetryDao;
    } else {
      synchronized(this) {
        if(_offlineTelemetryDao == null) {
          _offlineTelemetryDao = new OfflineTelemetryDao_Impl(this);
        }
        return _offlineTelemetryDao;
      }
    }
  }
}
