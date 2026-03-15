package com.pokerarity.scanner.data.local.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
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

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class EventDao_Impl implements EventDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<EventEntity> __insertionAdapterOfEventEntity;

  private final Converters __converters = new Converters();

  public EventDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfEventEntity = new EntityInsertionAdapter<EventEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `events` (`id`,`name`,`startDate`,`endDate`,`pokemonId`,`rarityWeight`,`isOneDayEvent`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final EventEntity entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getName() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getName());
        }
        final Long _tmp = __converters.dateToTimestamp(entity.getStartDate());
        if (_tmp == null) {
          statement.bindNull(3);
        } else {
          statement.bindLong(3, _tmp);
        }
        final Long _tmp_1 = __converters.dateToTimestamp(entity.getEndDate());
        if (_tmp_1 == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, _tmp_1);
        }
        statement.bindLong(5, entity.getPokemonId());
        statement.bindLong(6, entity.getRarityWeight());
        final int _tmp_2 = entity.isOneDayEvent() ? 1 : 0;
        statement.bindLong(7, _tmp_2);
      }
    };
  }

  @Override
  public Object insert(final EventEntity event, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfEventEntity.insertAndReturnId(event);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<EventEntity> events,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfEventEntity.insert(events);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object getEventsForDate(final Date date,
      final Continuation<? super List<EventEntity>> $completion) {
    final String _sql = "SELECT * FROM events WHERE startDate <= ? AND endDate >= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    final Long _tmp = __converters.dateToTimestamp(date);
    if (_tmp == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, _tmp);
    }
    _argIndex = 2;
    final Long _tmp_1 = __converters.dateToTimestamp(date);
    if (_tmp_1 == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, _tmp_1);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<EventEntity>>() {
      @Override
      @NonNull
      public List<EventEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfEndDate = CursorUtil.getColumnIndexOrThrow(_cursor, "endDate");
          final int _cursorIndexOfPokemonId = CursorUtil.getColumnIndexOrThrow(_cursor, "pokemonId");
          final int _cursorIndexOfRarityWeight = CursorUtil.getColumnIndexOrThrow(_cursor, "rarityWeight");
          final int _cursorIndexOfIsOneDayEvent = CursorUtil.getColumnIndexOrThrow(_cursor, "isOneDayEvent");
          final List<EventEntity> _result = new ArrayList<EventEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EventEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final Date _tmpStartDate;
            final Long _tmp_2;
            if (_cursor.isNull(_cursorIndexOfStartDate)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getLong(_cursorIndexOfStartDate);
            }
            _tmpStartDate = __converters.fromTimestamp(_tmp_2);
            final Date _tmpEndDate;
            final Long _tmp_3;
            if (_cursor.isNull(_cursorIndexOfEndDate)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getLong(_cursorIndexOfEndDate);
            }
            _tmpEndDate = __converters.fromTimestamp(_tmp_3);
            final long _tmpPokemonId;
            _tmpPokemonId = _cursor.getLong(_cursorIndexOfPokemonId);
            final int _tmpRarityWeight;
            _tmpRarityWeight = _cursor.getInt(_cursorIndexOfRarityWeight);
            final boolean _tmpIsOneDayEvent;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsOneDayEvent);
            _tmpIsOneDayEvent = _tmp_4 != 0;
            _item = new EventEntity(_tmpId,_tmpName,_tmpStartDate,_tmpEndDate,_tmpPokemonId,_tmpRarityWeight,_tmpIsOneDayEvent);
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
  public Object getEventsForPokemonOnDate(final long pokemonId, final Date date,
      final Continuation<? super List<EventEntity>> $completion) {
    final String _sql = "SELECT * FROM events WHERE pokemonId = ? AND startDate <= ? AND endDate >= ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, pokemonId);
    _argIndex = 2;
    final Long _tmp = __converters.dateToTimestamp(date);
    if (_tmp == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, _tmp);
    }
    _argIndex = 3;
    final Long _tmp_1 = __converters.dateToTimestamp(date);
    if (_tmp_1 == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, _tmp_1);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<EventEntity>>() {
      @Override
      @NonNull
      public List<EventEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfEndDate = CursorUtil.getColumnIndexOrThrow(_cursor, "endDate");
          final int _cursorIndexOfPokemonId = CursorUtil.getColumnIndexOrThrow(_cursor, "pokemonId");
          final int _cursorIndexOfRarityWeight = CursorUtil.getColumnIndexOrThrow(_cursor, "rarityWeight");
          final int _cursorIndexOfIsOneDayEvent = CursorUtil.getColumnIndexOrThrow(_cursor, "isOneDayEvent");
          final List<EventEntity> _result = new ArrayList<EventEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EventEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final Date _tmpStartDate;
            final Long _tmp_2;
            if (_cursor.isNull(_cursorIndexOfStartDate)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getLong(_cursorIndexOfStartDate);
            }
            _tmpStartDate = __converters.fromTimestamp(_tmp_2);
            final Date _tmpEndDate;
            final Long _tmp_3;
            if (_cursor.isNull(_cursorIndexOfEndDate)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getLong(_cursorIndexOfEndDate);
            }
            _tmpEndDate = __converters.fromTimestamp(_tmp_3);
            final long _tmpPokemonId;
            _tmpPokemonId = _cursor.getLong(_cursorIndexOfPokemonId);
            final int _tmpRarityWeight;
            _tmpRarityWeight = _cursor.getInt(_cursorIndexOfRarityWeight);
            final boolean _tmpIsOneDayEvent;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsOneDayEvent);
            _tmpIsOneDayEvent = _tmp_4 != 0;
            _item = new EventEntity(_tmpId,_tmpName,_tmpStartDate,_tmpEndDate,_tmpPokemonId,_tmpRarityWeight,_tmpIsOneDayEvent);
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
  public Object getAll(final Continuation<? super List<EventEntity>> $completion) {
    final String _sql = "SELECT * FROM events ORDER BY startDate DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<EventEntity>>() {
      @Override
      @NonNull
      public List<EventEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfEndDate = CursorUtil.getColumnIndexOrThrow(_cursor, "endDate");
          final int _cursorIndexOfPokemonId = CursorUtil.getColumnIndexOrThrow(_cursor, "pokemonId");
          final int _cursorIndexOfRarityWeight = CursorUtil.getColumnIndexOrThrow(_cursor, "rarityWeight");
          final int _cursorIndexOfIsOneDayEvent = CursorUtil.getColumnIndexOrThrow(_cursor, "isOneDayEvent");
          final List<EventEntity> _result = new ArrayList<EventEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EventEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            if (_cursor.isNull(_cursorIndexOfName)) {
              _tmpName = null;
            } else {
              _tmpName = _cursor.getString(_cursorIndexOfName);
            }
            final Date _tmpStartDate;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfStartDate)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfStartDate);
            }
            _tmpStartDate = __converters.fromTimestamp(_tmp);
            final Date _tmpEndDate;
            final Long _tmp_1;
            if (_cursor.isNull(_cursorIndexOfEndDate)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getLong(_cursorIndexOfEndDate);
            }
            _tmpEndDate = __converters.fromTimestamp(_tmp_1);
            final long _tmpPokemonId;
            _tmpPokemonId = _cursor.getLong(_cursorIndexOfPokemonId);
            final int _tmpRarityWeight;
            _tmpRarityWeight = _cursor.getInt(_cursorIndexOfRarityWeight);
            final boolean _tmpIsOneDayEvent;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsOneDayEvent);
            _tmpIsOneDayEvent = _tmp_2 != 0;
            _item = new EventEntity(_tmpId,_tmpName,_tmpStartDate,_tmpEndDate,_tmpPokemonId,_tmpRarityWeight,_tmpIsOneDayEvent);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
