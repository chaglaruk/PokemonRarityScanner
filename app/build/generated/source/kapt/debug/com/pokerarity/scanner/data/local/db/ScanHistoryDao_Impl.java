package com.pokerarity.scanner.data.local.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ScanHistoryDao_Impl implements ScanHistoryDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ScanHistoryEntity> __insertionAdapterOfScanHistoryEntity;

  private final Converters __converters = new Converters();

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  public ScanHistoryDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfScanHistoryEntity = new EntityInsertionAdapter<ScanHistoryEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `scan_history` (`id`,`timestamp`,`pokemonName`,`cp`,`hp`,`caughtDate`,`rawOcrText`,`isShiny`,`isShadow`,`isLucky`,`hasCostume`,`rarityScore`,`rarityTier`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ScanHistoryEntity entity) {
        statement.bindLong(1, entity.getId());
        final Long _tmp = __converters.dateToTimestamp(entity.getTimestamp());
        if (_tmp == null) {
          statement.bindNull(2);
        } else {
          statement.bindLong(2, _tmp);
        }
        if (entity.getPokemonName() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getPokemonName());
        }
        if (entity.getCp() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getCp());
        }
        if (entity.getHp() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getHp());
        }
        final Long _tmp_1 = __converters.dateToTimestamp(entity.getCaughtDate());
        if (_tmp_1 == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, _tmp_1);
        }
        if (entity.getRawOcrText() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getRawOcrText());
        }
        final int _tmp_2 = entity.isShiny() ? 1 : 0;
        statement.bindLong(8, _tmp_2);
        final int _tmp_3 = entity.isShadow() ? 1 : 0;
        statement.bindLong(9, _tmp_3);
        final int _tmp_4 = entity.isLucky() ? 1 : 0;
        statement.bindLong(10, _tmp_4);
        final int _tmp_5 = entity.getHasCostume() ? 1 : 0;
        statement.bindLong(11, _tmp_5);
        statement.bindLong(12, entity.getRarityScore());
        if (entity.getRarityTier() == null) {
          statement.bindNull(13);
        } else {
          statement.bindString(13, entity.getRarityTier());
        }
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM scan_history WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final ScanHistoryEntity scan, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfScanHistoryEntity.insertAndReturnId(scan);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ScanHistoryEntity>> getAll() {
    final String _sql = "SELECT * FROM scan_history ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"scan_history"}, new Callable<List<ScanHistoryEntity>>() {
      @Override
      @NonNull
      public List<ScanHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfPokemonName = CursorUtil.getColumnIndexOrThrow(_cursor, "pokemonName");
          final int _cursorIndexOfCp = CursorUtil.getColumnIndexOrThrow(_cursor, "cp");
          final int _cursorIndexOfHp = CursorUtil.getColumnIndexOrThrow(_cursor, "hp");
          final int _cursorIndexOfCaughtDate = CursorUtil.getColumnIndexOrThrow(_cursor, "caughtDate");
          final int _cursorIndexOfRawOcrText = CursorUtil.getColumnIndexOrThrow(_cursor, "rawOcrText");
          final int _cursorIndexOfIsShiny = CursorUtil.getColumnIndexOrThrow(_cursor, "isShiny");
          final int _cursorIndexOfIsShadow = CursorUtil.getColumnIndexOrThrow(_cursor, "isShadow");
          final int _cursorIndexOfIsLucky = CursorUtil.getColumnIndexOrThrow(_cursor, "isLucky");
          final int _cursorIndexOfHasCostume = CursorUtil.getColumnIndexOrThrow(_cursor, "hasCostume");
          final int _cursorIndexOfRarityScore = CursorUtil.getColumnIndexOrThrow(_cursor, "rarityScore");
          final int _cursorIndexOfRarityTier = CursorUtil.getColumnIndexOrThrow(_cursor, "rarityTier");
          final List<ScanHistoryEntity> _result = new ArrayList<ScanHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ScanHistoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final Date _tmpTimestamp;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfTimestamp)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfTimestamp);
            }
            _tmpTimestamp = __converters.fromTimestamp(_tmp);
            final String _tmpPokemonName;
            if (_cursor.isNull(_cursorIndexOfPokemonName)) {
              _tmpPokemonName = null;
            } else {
              _tmpPokemonName = _cursor.getString(_cursorIndexOfPokemonName);
            }
            final Integer _tmpCp;
            if (_cursor.isNull(_cursorIndexOfCp)) {
              _tmpCp = null;
            } else {
              _tmpCp = _cursor.getInt(_cursorIndexOfCp);
            }
            final Integer _tmpHp;
            if (_cursor.isNull(_cursorIndexOfHp)) {
              _tmpHp = null;
            } else {
              _tmpHp = _cursor.getInt(_cursorIndexOfHp);
            }
            final Date _tmpCaughtDate;
            final Long _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCaughtDate)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getLong(_cursorIndexOfCaughtDate);
            }
            _tmpCaughtDate = __converters.fromTimestamp(_tmp_1);
            final String _tmpRawOcrText;
            if (_cursor.isNull(_cursorIndexOfRawOcrText)) {
              _tmpRawOcrText = null;
            } else {
              _tmpRawOcrText = _cursor.getString(_cursorIndexOfRawOcrText);
            }
            final boolean _tmpIsShiny;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsShiny);
            _tmpIsShiny = _tmp_2 != 0;
            final boolean _tmpIsShadow;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsShadow);
            _tmpIsShadow = _tmp_3 != 0;
            final boolean _tmpIsLucky;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsLucky);
            _tmpIsLucky = _tmp_4 != 0;
            final boolean _tmpHasCostume;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfHasCostume);
            _tmpHasCostume = _tmp_5 != 0;
            final int _tmpRarityScore;
            _tmpRarityScore = _cursor.getInt(_cursorIndexOfRarityScore);
            final String _tmpRarityTier;
            if (_cursor.isNull(_cursorIndexOfRarityTier)) {
              _tmpRarityTier = null;
            } else {
              _tmpRarityTier = _cursor.getString(_cursorIndexOfRarityTier);
            }
            _item = new ScanHistoryEntity(_tmpId,_tmpTimestamp,_tmpPokemonName,_tmpCp,_tmpHp,_tmpCaughtDate,_tmpRawOcrText,_tmpIsShiny,_tmpIsShadow,_tmpIsLucky,_tmpHasCostume,_tmpRarityScore,_tmpRarityTier);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<ScanHistoryEntity>> getRecent(final int limit) {
    final String _sql = "SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"scan_history"}, new Callable<List<ScanHistoryEntity>>() {
      @Override
      @NonNull
      public List<ScanHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfPokemonName = CursorUtil.getColumnIndexOrThrow(_cursor, "pokemonName");
          final int _cursorIndexOfCp = CursorUtil.getColumnIndexOrThrow(_cursor, "cp");
          final int _cursorIndexOfHp = CursorUtil.getColumnIndexOrThrow(_cursor, "hp");
          final int _cursorIndexOfCaughtDate = CursorUtil.getColumnIndexOrThrow(_cursor, "caughtDate");
          final int _cursorIndexOfRawOcrText = CursorUtil.getColumnIndexOrThrow(_cursor, "rawOcrText");
          final int _cursorIndexOfIsShiny = CursorUtil.getColumnIndexOrThrow(_cursor, "isShiny");
          final int _cursorIndexOfIsShadow = CursorUtil.getColumnIndexOrThrow(_cursor, "isShadow");
          final int _cursorIndexOfIsLucky = CursorUtil.getColumnIndexOrThrow(_cursor, "isLucky");
          final int _cursorIndexOfHasCostume = CursorUtil.getColumnIndexOrThrow(_cursor, "hasCostume");
          final int _cursorIndexOfRarityScore = CursorUtil.getColumnIndexOrThrow(_cursor, "rarityScore");
          final int _cursorIndexOfRarityTier = CursorUtil.getColumnIndexOrThrow(_cursor, "rarityTier");
          final List<ScanHistoryEntity> _result = new ArrayList<ScanHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ScanHistoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final Date _tmpTimestamp;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfTimestamp)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfTimestamp);
            }
            _tmpTimestamp = __converters.fromTimestamp(_tmp);
            final String _tmpPokemonName;
            if (_cursor.isNull(_cursorIndexOfPokemonName)) {
              _tmpPokemonName = null;
            } else {
              _tmpPokemonName = _cursor.getString(_cursorIndexOfPokemonName);
            }
            final Integer _tmpCp;
            if (_cursor.isNull(_cursorIndexOfCp)) {
              _tmpCp = null;
            } else {
              _tmpCp = _cursor.getInt(_cursorIndexOfCp);
            }
            final Integer _tmpHp;
            if (_cursor.isNull(_cursorIndexOfHp)) {
              _tmpHp = null;
            } else {
              _tmpHp = _cursor.getInt(_cursorIndexOfHp);
            }
            final Date _tmpCaughtDate;
            final Long _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCaughtDate)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getLong(_cursorIndexOfCaughtDate);
            }
            _tmpCaughtDate = __converters.fromTimestamp(_tmp_1);
            final String _tmpRawOcrText;
            if (_cursor.isNull(_cursorIndexOfRawOcrText)) {
              _tmpRawOcrText = null;
            } else {
              _tmpRawOcrText = _cursor.getString(_cursorIndexOfRawOcrText);
            }
            final boolean _tmpIsShiny;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsShiny);
            _tmpIsShiny = _tmp_2 != 0;
            final boolean _tmpIsShadow;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsShadow);
            _tmpIsShadow = _tmp_3 != 0;
            final boolean _tmpIsLucky;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsLucky);
            _tmpIsLucky = _tmp_4 != 0;
            final boolean _tmpHasCostume;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfHasCostume);
            _tmpHasCostume = _tmp_5 != 0;
            final int _tmpRarityScore;
            _tmpRarityScore = _cursor.getInt(_cursorIndexOfRarityScore);
            final String _tmpRarityTier;
            if (_cursor.isNull(_cursorIndexOfRarityTier)) {
              _tmpRarityTier = null;
            } else {
              _tmpRarityTier = _cursor.getString(_cursorIndexOfRarityTier);
            }
            _item = new ScanHistoryEntity(_tmpId,_tmpTimestamp,_tmpPokemonName,_tmpCp,_tmpHp,_tmpCaughtDate,_tmpRawOcrText,_tmpIsShiny,_tmpIsShadow,_tmpIsLucky,_tmpHasCostume,_tmpRarityScore,_tmpRarityTier);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getById(final long id, final Continuation<? super ScanHistoryEntity> $completion) {
    final String _sql = "SELECT * FROM scan_history WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ScanHistoryEntity>() {
      @Override
      @Nullable
      public ScanHistoryEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfPokemonName = CursorUtil.getColumnIndexOrThrow(_cursor, "pokemonName");
          final int _cursorIndexOfCp = CursorUtil.getColumnIndexOrThrow(_cursor, "cp");
          final int _cursorIndexOfHp = CursorUtil.getColumnIndexOrThrow(_cursor, "hp");
          final int _cursorIndexOfCaughtDate = CursorUtil.getColumnIndexOrThrow(_cursor, "caughtDate");
          final int _cursorIndexOfRawOcrText = CursorUtil.getColumnIndexOrThrow(_cursor, "rawOcrText");
          final int _cursorIndexOfIsShiny = CursorUtil.getColumnIndexOrThrow(_cursor, "isShiny");
          final int _cursorIndexOfIsShadow = CursorUtil.getColumnIndexOrThrow(_cursor, "isShadow");
          final int _cursorIndexOfIsLucky = CursorUtil.getColumnIndexOrThrow(_cursor, "isLucky");
          final int _cursorIndexOfHasCostume = CursorUtil.getColumnIndexOrThrow(_cursor, "hasCostume");
          final int _cursorIndexOfRarityScore = CursorUtil.getColumnIndexOrThrow(_cursor, "rarityScore");
          final int _cursorIndexOfRarityTier = CursorUtil.getColumnIndexOrThrow(_cursor, "rarityTier");
          final ScanHistoryEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final Date _tmpTimestamp;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfTimestamp)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfTimestamp);
            }
            _tmpTimestamp = __converters.fromTimestamp(_tmp);
            final String _tmpPokemonName;
            if (_cursor.isNull(_cursorIndexOfPokemonName)) {
              _tmpPokemonName = null;
            } else {
              _tmpPokemonName = _cursor.getString(_cursorIndexOfPokemonName);
            }
            final Integer _tmpCp;
            if (_cursor.isNull(_cursorIndexOfCp)) {
              _tmpCp = null;
            } else {
              _tmpCp = _cursor.getInt(_cursorIndexOfCp);
            }
            final Integer _tmpHp;
            if (_cursor.isNull(_cursorIndexOfHp)) {
              _tmpHp = null;
            } else {
              _tmpHp = _cursor.getInt(_cursorIndexOfHp);
            }
            final Date _tmpCaughtDate;
            final Long _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCaughtDate)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getLong(_cursorIndexOfCaughtDate);
            }
            _tmpCaughtDate = __converters.fromTimestamp(_tmp_1);
            final String _tmpRawOcrText;
            if (_cursor.isNull(_cursorIndexOfRawOcrText)) {
              _tmpRawOcrText = null;
            } else {
              _tmpRawOcrText = _cursor.getString(_cursorIndexOfRawOcrText);
            }
            final boolean _tmpIsShiny;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsShiny);
            _tmpIsShiny = _tmp_2 != 0;
            final boolean _tmpIsShadow;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsShadow);
            _tmpIsShadow = _tmp_3 != 0;
            final boolean _tmpIsLucky;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsLucky);
            _tmpIsLucky = _tmp_4 != 0;
            final boolean _tmpHasCostume;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfHasCostume);
            _tmpHasCostume = _tmp_5 != 0;
            final int _tmpRarityScore;
            _tmpRarityScore = _cursor.getInt(_cursorIndexOfRarityScore);
            final String _tmpRarityTier;
            if (_cursor.isNull(_cursorIndexOfRarityTier)) {
              _tmpRarityTier = null;
            } else {
              _tmpRarityTier = _cursor.getString(_cursorIndexOfRarityTier);
            }
            _result = new ScanHistoryEntity(_tmpId,_tmpTimestamp,_tmpPokemonName,_tmpCp,_tmpHp,_tmpCaughtDate,_tmpRawOcrText,_tmpIsShiny,_tmpIsShadow,_tmpIsLucky,_tmpHasCostume,_tmpRarityScore,_tmpRarityTier);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getByPokemonName(final String name,
      final Continuation<? super List<ScanHistoryEntity>> $completion) {
    final String _sql = "SELECT * FROM scan_history WHERE pokemonName = ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (name == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, name);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ScanHistoryEntity>>() {
      @Override
      @NonNull
      public List<ScanHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfPokemonName = CursorUtil.getColumnIndexOrThrow(_cursor, "pokemonName");
          final int _cursorIndexOfCp = CursorUtil.getColumnIndexOrThrow(_cursor, "cp");
          final int _cursorIndexOfHp = CursorUtil.getColumnIndexOrThrow(_cursor, "hp");
          final int _cursorIndexOfCaughtDate = CursorUtil.getColumnIndexOrThrow(_cursor, "caughtDate");
          final int _cursorIndexOfRawOcrText = CursorUtil.getColumnIndexOrThrow(_cursor, "rawOcrText");
          final int _cursorIndexOfIsShiny = CursorUtil.getColumnIndexOrThrow(_cursor, "isShiny");
          final int _cursorIndexOfIsShadow = CursorUtil.getColumnIndexOrThrow(_cursor, "isShadow");
          final int _cursorIndexOfIsLucky = CursorUtil.getColumnIndexOrThrow(_cursor, "isLucky");
          final int _cursorIndexOfHasCostume = CursorUtil.getColumnIndexOrThrow(_cursor, "hasCostume");
          final int _cursorIndexOfRarityScore = CursorUtil.getColumnIndexOrThrow(_cursor, "rarityScore");
          final int _cursorIndexOfRarityTier = CursorUtil.getColumnIndexOrThrow(_cursor, "rarityTier");
          final List<ScanHistoryEntity> _result = new ArrayList<ScanHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ScanHistoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final Date _tmpTimestamp;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfTimestamp)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfTimestamp);
            }
            _tmpTimestamp = __converters.fromTimestamp(_tmp);
            final String _tmpPokemonName;
            if (_cursor.isNull(_cursorIndexOfPokemonName)) {
              _tmpPokemonName = null;
            } else {
              _tmpPokemonName = _cursor.getString(_cursorIndexOfPokemonName);
            }
            final Integer _tmpCp;
            if (_cursor.isNull(_cursorIndexOfCp)) {
              _tmpCp = null;
            } else {
              _tmpCp = _cursor.getInt(_cursorIndexOfCp);
            }
            final Integer _tmpHp;
            if (_cursor.isNull(_cursorIndexOfHp)) {
              _tmpHp = null;
            } else {
              _tmpHp = _cursor.getInt(_cursorIndexOfHp);
            }
            final Date _tmpCaughtDate;
            final Long _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCaughtDate)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getLong(_cursorIndexOfCaughtDate);
            }
            _tmpCaughtDate = __converters.fromTimestamp(_tmp_1);
            final String _tmpRawOcrText;
            if (_cursor.isNull(_cursorIndexOfRawOcrText)) {
              _tmpRawOcrText = null;
            } else {
              _tmpRawOcrText = _cursor.getString(_cursorIndexOfRawOcrText);
            }
            final boolean _tmpIsShiny;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsShiny);
            _tmpIsShiny = _tmp_2 != 0;
            final boolean _tmpIsShadow;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsShadow);
            _tmpIsShadow = _tmp_3 != 0;
            final boolean _tmpIsLucky;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsLucky);
            _tmpIsLucky = _tmp_4 != 0;
            final boolean _tmpHasCostume;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfHasCostume);
            _tmpHasCostume = _tmp_5 != 0;
            final int _tmpRarityScore;
            _tmpRarityScore = _cursor.getInt(_cursorIndexOfRarityScore);
            final String _tmpRarityTier;
            if (_cursor.isNull(_cursorIndexOfRarityTier)) {
              _tmpRarityTier = null;
            } else {
              _tmpRarityTier = _cursor.getString(_cursorIndexOfRarityTier);
            }
            _item = new ScanHistoryEntity(_tmpId,_tmpTimestamp,_tmpPokemonName,_tmpCp,_tmpHp,_tmpCaughtDate,_tmpRawOcrText,_tmpIsShiny,_tmpIsShadow,_tmpIsLucky,_tmpHasCostume,_tmpRarityScore,_tmpRarityTier);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object count(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM scan_history";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ScanHistoryEntity>> getByMinRarity(final int minScore) {
    final String _sql = "SELECT * FROM scan_history WHERE rarityScore >= ? ORDER BY rarityScore DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, minScore);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"scan_history"}, new Callable<List<ScanHistoryEntity>>() {
      @Override
      @NonNull
      public List<ScanHistoryEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfPokemonName = CursorUtil.getColumnIndexOrThrow(_cursor, "pokemonName");
          final int _cursorIndexOfCp = CursorUtil.getColumnIndexOrThrow(_cursor, "cp");
          final int _cursorIndexOfHp = CursorUtil.getColumnIndexOrThrow(_cursor, "hp");
          final int _cursorIndexOfCaughtDate = CursorUtil.getColumnIndexOrThrow(_cursor, "caughtDate");
          final int _cursorIndexOfRawOcrText = CursorUtil.getColumnIndexOrThrow(_cursor, "rawOcrText");
          final int _cursorIndexOfIsShiny = CursorUtil.getColumnIndexOrThrow(_cursor, "isShiny");
          final int _cursorIndexOfIsShadow = CursorUtil.getColumnIndexOrThrow(_cursor, "isShadow");
          final int _cursorIndexOfIsLucky = CursorUtil.getColumnIndexOrThrow(_cursor, "isLucky");
          final int _cursorIndexOfHasCostume = CursorUtil.getColumnIndexOrThrow(_cursor, "hasCostume");
          final int _cursorIndexOfRarityScore = CursorUtil.getColumnIndexOrThrow(_cursor, "rarityScore");
          final int _cursorIndexOfRarityTier = CursorUtil.getColumnIndexOrThrow(_cursor, "rarityTier");
          final List<ScanHistoryEntity> _result = new ArrayList<ScanHistoryEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ScanHistoryEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final Date _tmpTimestamp;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfTimestamp)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfTimestamp);
            }
            _tmpTimestamp = __converters.fromTimestamp(_tmp);
            final String _tmpPokemonName;
            if (_cursor.isNull(_cursorIndexOfPokemonName)) {
              _tmpPokemonName = null;
            } else {
              _tmpPokemonName = _cursor.getString(_cursorIndexOfPokemonName);
            }
            final Integer _tmpCp;
            if (_cursor.isNull(_cursorIndexOfCp)) {
              _tmpCp = null;
            } else {
              _tmpCp = _cursor.getInt(_cursorIndexOfCp);
            }
            final Integer _tmpHp;
            if (_cursor.isNull(_cursorIndexOfHp)) {
              _tmpHp = null;
            } else {
              _tmpHp = _cursor.getInt(_cursorIndexOfHp);
            }
            final Date _tmpCaughtDate;
            final Long _tmp_1;
            if (_cursor.isNull(_cursorIndexOfCaughtDate)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getLong(_cursorIndexOfCaughtDate);
            }
            _tmpCaughtDate = __converters.fromTimestamp(_tmp_1);
            final String _tmpRawOcrText;
            if (_cursor.isNull(_cursorIndexOfRawOcrText)) {
              _tmpRawOcrText = null;
            } else {
              _tmpRawOcrText = _cursor.getString(_cursorIndexOfRawOcrText);
            }
            final boolean _tmpIsShiny;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsShiny);
            _tmpIsShiny = _tmp_2 != 0;
            final boolean _tmpIsShadow;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfIsShadow);
            _tmpIsShadow = _tmp_3 != 0;
            final boolean _tmpIsLucky;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsLucky);
            _tmpIsLucky = _tmp_4 != 0;
            final boolean _tmpHasCostume;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfHasCostume);
            _tmpHasCostume = _tmp_5 != 0;
            final int _tmpRarityScore;
            _tmpRarityScore = _cursor.getInt(_cursorIndexOfRarityScore);
            final String _tmpRarityTier;
            if (_cursor.isNull(_cursorIndexOfRarityTier)) {
              _tmpRarityTier = null;
            } else {
              _tmpRarityTier = _cursor.getString(_cursorIndexOfRarityTier);
            }
            _item = new ScanHistoryEntity(_tmpId,_tmpTimestamp,_tmpPokemonName,_tmpCp,_tmpHp,_tmpCaughtDate,_tmpRawOcrText,_tmpIsShiny,_tmpIsShadow,_tmpIsLucky,_tmpHasCostume,_tmpRarityScore,_tmpRarityTier);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
