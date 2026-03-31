package com.pokerarity.scanner.data.local.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.EntityUpsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
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

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class EventDao_Impl implements EventDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<EventEntity> __insertionAdapterOfEventEntity;

  private final Converters __converters = new Converters();

  private final EntityUpsertionAdapter<EventPokemonEntity> __upsertionAdapterOfEventPokemonEntity;

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
    this.__upsertionAdapterOfEventPokemonEntity = new EntityUpsertionAdapter<EventPokemonEntity>(new EntityInsertionAdapter<EventPokemonEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `event_pokemon` (`id`,`baseName`,`eventName`,`eventBonusScore`,`spriteKey`,`variantToken`,`eventStart`,`eventEnd`,`source`,`updatedAt`) VALUES (?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final EventPokemonEntity entity) {
        if (entity.getId() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getId());
        }
        if (entity.getBaseName() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getBaseName());
        }
        if (entity.getEventName() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getEventName());
        }
        statement.bindLong(4, entity.getEventBonusScore());
        if (entity.getSpriteKey() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getSpriteKey());
        }
        if (entity.getVariantToken() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getVariantToken());
        }
        final Long _tmp = __converters.dateToTimestamp(entity.getEventStart());
        if (_tmp == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, _tmp);
        }
        final Long _tmp_1 = __converters.dateToTimestamp(entity.getEventEnd());
        if (_tmp_1 == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, _tmp_1);
        }
        if (entity.getSource() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getSource());
        }
        final Long _tmp_2 = __converters.dateToTimestamp(entity.getUpdatedAt());
        if (_tmp_2 == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, _tmp_2);
        }
      }
    }, new EntityDeletionOrUpdateAdapter<EventPokemonEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `event_pokemon` SET `id` = ?,`baseName` = ?,`eventName` = ?,`eventBonusScore` = ?,`spriteKey` = ?,`variantToken` = ?,`eventStart` = ?,`eventEnd` = ?,`source` = ?,`updatedAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final EventPokemonEntity entity) {
        if (entity.getId() == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.getId());
        }
        if (entity.getBaseName() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getBaseName());
        }
        if (entity.getEventName() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getEventName());
        }
        statement.bindLong(4, entity.getEventBonusScore());
        if (entity.getSpriteKey() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getSpriteKey());
        }
        if (entity.getVariantToken() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getVariantToken());
        }
        final Long _tmp = __converters.dateToTimestamp(entity.getEventStart());
        if (_tmp == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, _tmp);
        }
        final Long _tmp_1 = __converters.dateToTimestamp(entity.getEventEnd());
        if (_tmp_1 == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, _tmp_1);
        }
        if (entity.getSource() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getSource());
        }
        final Long _tmp_2 = __converters.dateToTimestamp(entity.getUpdatedAt());
        if (_tmp_2 == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, _tmp_2);
        }
        if (entity.getId() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getId());
        }
      }
    });
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
  public Object upsertEventPokemon(final EventPokemonEntity eventPokemon,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfEventPokemonEntity.upsert(eventPokemon);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertEventPokemonAll(final List<EventPokemonEntity> eventPokemon,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfEventPokemonEntity.upsert(eventPokemon);
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

  @Override
  public Object getEventPokemonCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM event_pokemon";
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
  public Object getEventPokemonForBaseName(final String baseName,
      final Continuation<? super List<EventPokemonEntity>> $completion) {
    final String _sql = "SELECT * FROM event_pokemon WHERE baseName = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (baseName == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, baseName);
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<EventPokemonEntity>>() {
      @Override
      @NonNull
      public List<EventPokemonEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBaseName = CursorUtil.getColumnIndexOrThrow(_cursor, "baseName");
          final int _cursorIndexOfEventName = CursorUtil.getColumnIndexOrThrow(_cursor, "eventName");
          final int _cursorIndexOfEventBonusScore = CursorUtil.getColumnIndexOrThrow(_cursor, "eventBonusScore");
          final int _cursorIndexOfSpriteKey = CursorUtil.getColumnIndexOrThrow(_cursor, "spriteKey");
          final int _cursorIndexOfVariantToken = CursorUtil.getColumnIndexOrThrow(_cursor, "variantToken");
          final int _cursorIndexOfEventStart = CursorUtil.getColumnIndexOrThrow(_cursor, "eventStart");
          final int _cursorIndexOfEventEnd = CursorUtil.getColumnIndexOrThrow(_cursor, "eventEnd");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final List<EventPokemonEntity> _result = new ArrayList<EventPokemonEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EventPokemonEntity _item;
            final String _tmpId;
            if (_cursor.isNull(_cursorIndexOfId)) {
              _tmpId = null;
            } else {
              _tmpId = _cursor.getString(_cursorIndexOfId);
            }
            final String _tmpBaseName;
            if (_cursor.isNull(_cursorIndexOfBaseName)) {
              _tmpBaseName = null;
            } else {
              _tmpBaseName = _cursor.getString(_cursorIndexOfBaseName);
            }
            final String _tmpEventName;
            if (_cursor.isNull(_cursorIndexOfEventName)) {
              _tmpEventName = null;
            } else {
              _tmpEventName = _cursor.getString(_cursorIndexOfEventName);
            }
            final int _tmpEventBonusScore;
            _tmpEventBonusScore = _cursor.getInt(_cursorIndexOfEventBonusScore);
            final String _tmpSpriteKey;
            if (_cursor.isNull(_cursorIndexOfSpriteKey)) {
              _tmpSpriteKey = null;
            } else {
              _tmpSpriteKey = _cursor.getString(_cursorIndexOfSpriteKey);
            }
            final String _tmpVariantToken;
            if (_cursor.isNull(_cursorIndexOfVariantToken)) {
              _tmpVariantToken = null;
            } else {
              _tmpVariantToken = _cursor.getString(_cursorIndexOfVariantToken);
            }
            final Date _tmpEventStart;
            final Long _tmp;
            if (_cursor.isNull(_cursorIndexOfEventStart)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(_cursorIndexOfEventStart);
            }
            _tmpEventStart = __converters.fromTimestamp(_tmp);
            final Date _tmpEventEnd;
            final Long _tmp_1;
            if (_cursor.isNull(_cursorIndexOfEventEnd)) {
              _tmp_1 = null;
            } else {
              _tmp_1 = _cursor.getLong(_cursorIndexOfEventEnd);
            }
            _tmpEventEnd = __converters.fromTimestamp(_tmp_1);
            final String _tmpSource;
            if (_cursor.isNull(_cursorIndexOfSource)) {
              _tmpSource = null;
            } else {
              _tmpSource = _cursor.getString(_cursorIndexOfSource);
            }
            final Date _tmpUpdatedAt;
            final Long _tmp_2;
            if (_cursor.isNull(_cursorIndexOfUpdatedAt)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getLong(_cursorIndexOfUpdatedAt);
            }
            _tmpUpdatedAt = __converters.fromTimestamp(_tmp_2);
            _item = new EventPokemonEntity(_tmpId,_tmpBaseName,_tmpEventName,_tmpEventBonusScore,_tmpSpriteKey,_tmpVariantToken,_tmpEventStart,_tmpEventEnd,_tmpSource,_tmpUpdatedAt);
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
  public Object getEventPokemonForBaseNameOnDate(final String baseName, final Date date,
      final Continuation<? super List<EventPokemonEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM event_pokemon\n"
            + "        WHERE baseName = ?\n"
            + "          AND (\n"
            + "            (eventStart IS NULL AND eventEnd IS NULL)\n"
            + "            OR ((eventStart IS NULL OR eventStart <= ?) AND (eventEnd IS NULL OR eventEnd >= ?))\n"
            + "          )\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    if (baseName == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, baseName);
    }
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
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<EventPokemonEntity>>() {
      @Override
      @NonNull
      public List<EventPokemonEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBaseName = CursorUtil.getColumnIndexOrThrow(_cursor, "baseName");
          final int _cursorIndexOfEventName = CursorUtil.getColumnIndexOrThrow(_cursor, "eventName");
          final int _cursorIndexOfEventBonusScore = CursorUtil.getColumnIndexOrThrow(_cursor, "eventBonusScore");
          final int _cursorIndexOfSpriteKey = CursorUtil.getColumnIndexOrThrow(_cursor, "spriteKey");
          final int _cursorIndexOfVariantToken = CursorUtil.getColumnIndexOrThrow(_cursor, "variantToken");
          final int _cursorIndexOfEventStart = CursorUtil.getColumnIndexOrThrow(_cursor, "eventStart");
          final int _cursorIndexOfEventEnd = CursorUtil.getColumnIndexOrThrow(_cursor, "eventEnd");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final List<EventPokemonEntity> _result = new ArrayList<EventPokemonEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EventPokemonEntity _item;
            final String _tmpId;
            if (_cursor.isNull(_cursorIndexOfId)) {
              _tmpId = null;
            } else {
              _tmpId = _cursor.getString(_cursorIndexOfId);
            }
            final String _tmpBaseName;
            if (_cursor.isNull(_cursorIndexOfBaseName)) {
              _tmpBaseName = null;
            } else {
              _tmpBaseName = _cursor.getString(_cursorIndexOfBaseName);
            }
            final String _tmpEventName;
            if (_cursor.isNull(_cursorIndexOfEventName)) {
              _tmpEventName = null;
            } else {
              _tmpEventName = _cursor.getString(_cursorIndexOfEventName);
            }
            final int _tmpEventBonusScore;
            _tmpEventBonusScore = _cursor.getInt(_cursorIndexOfEventBonusScore);
            final String _tmpSpriteKey;
            if (_cursor.isNull(_cursorIndexOfSpriteKey)) {
              _tmpSpriteKey = null;
            } else {
              _tmpSpriteKey = _cursor.getString(_cursorIndexOfSpriteKey);
            }
            final String _tmpVariantToken;
            if (_cursor.isNull(_cursorIndexOfVariantToken)) {
              _tmpVariantToken = null;
            } else {
              _tmpVariantToken = _cursor.getString(_cursorIndexOfVariantToken);
            }
            final Date _tmpEventStart;
            final Long _tmp_2;
            if (_cursor.isNull(_cursorIndexOfEventStart)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getLong(_cursorIndexOfEventStart);
            }
            _tmpEventStart = __converters.fromTimestamp(_tmp_2);
            final Date _tmpEventEnd;
            final Long _tmp_3;
            if (_cursor.isNull(_cursorIndexOfEventEnd)) {
              _tmp_3 = null;
            } else {
              _tmp_3 = _cursor.getLong(_cursorIndexOfEventEnd);
            }
            _tmpEventEnd = __converters.fromTimestamp(_tmp_3);
            final String _tmpSource;
            if (_cursor.isNull(_cursorIndexOfSource)) {
              _tmpSource = null;
            } else {
              _tmpSource = _cursor.getString(_cursorIndexOfSource);
            }
            final Date _tmpUpdatedAt;
            final Long _tmp_4;
            if (_cursor.isNull(_cursorIndexOfUpdatedAt)) {
              _tmp_4 = null;
            } else {
              _tmp_4 = _cursor.getLong(_cursorIndexOfUpdatedAt);
            }
            _tmpUpdatedAt = __converters.fromTimestamp(_tmp_4);
            _item = new EventPokemonEntity(_tmpId,_tmpBaseName,_tmpEventName,_tmpEventBonusScore,_tmpSpriteKey,_tmpVariantToken,_tmpEventStart,_tmpEventEnd,_tmpSource,_tmpUpdatedAt);
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
