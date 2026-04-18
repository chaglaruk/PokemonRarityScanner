package com.pokerarity.scanner.data.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Upsert;
import java.util.Date;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000B\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\t\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0002\b\u0007\bg\u0018\u00002\u00020\u0001J\u0014\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00a7@\u00a2\u0006\u0002\u0010\u0005J\u000e\u0010\u0006\u001a\u00020\u0007H\u00a7@\u00a2\u0006\u0002\u0010\u0005J\u001c\u0010\b\u001a\b\u0012\u0004\u0012\u00020\t0\u00032\u0006\u0010\n\u001a\u00020\u000bH\u00a7@\u00a2\u0006\u0002\u0010\fJ$\u0010\r\u001a\b\u0012\u0004\u0012\u00020\t0\u00032\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\u000e\u001a\u00020\u000fH\u00a7@\u00a2\u0006\u0002\u0010\u0010J\u001c\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\u0006\u0010\u000e\u001a\u00020\u000fH\u00a7@\u00a2\u0006\u0002\u0010\u0012J$\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\u0006\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u000e\u001a\u00020\u000fH\u00a7@\u00a2\u0006\u0002\u0010\u0016J\u0016\u0010\u0017\u001a\u00020\u00152\u0006\u0010\u0018\u001a\u00020\u0004H\u00a7@\u00a2\u0006\u0002\u0010\u0019J\u001c\u0010\u001a\u001a\u00020\u001b2\f\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00a7@\u00a2\u0006\u0002\u0010\u001dJ\u0016\u0010\u001e\u001a\u00020\u001b2\u0006\u0010\u001f\u001a\u00020\tH\u00a7@\u00a2\u0006\u0002\u0010 J\u001c\u0010!\u001a\u00020\u001b2\f\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\t0\u0003H\u00a7@\u00a2\u0006\u0002\u0010\u001d\u00a8\u0006\""}, d2 = {"Lcom/pokerarity/scanner/data/local/db/EventDao;", "", "getAll", "", "Lcom/pokerarity/scanner/data/local/db/EventEntity;", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getEventPokemonCount", "", "getEventPokemonForBaseName", "Lcom/pokerarity/scanner/data/local/db/EventPokemonEntity;", "baseName", "", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getEventPokemonForBaseNameOnDate", "date", "Ljava/util/Date;", "(Ljava/lang/String;Ljava/util/Date;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getEventsForDate", "(Ljava/util/Date;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getEventsForPokemonOnDate", "pokemonId", "", "(JLjava/util/Date;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "insert", "event", "(Lcom/pokerarity/scanner/data/local/db/EventEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "insertAll", "", "events", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "upsertEventPokemon", "eventPokemon", "(Lcom/pokerarity/scanner/data/local/db/EventPokemonEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "upsertEventPokemonAll", "PokeRarityScanner-v1.8.2_debug"})
@androidx.room.Dao()
public abstract interface EventDao {
    
    @androidx.room.Insert(onConflict = 1)
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object insert(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.local.db.EventEntity event, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Long> $completion);
    
    @androidx.room.Insert(onConflict = 1)
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object insertAll(@org.jetbrains.annotations.NotNull()
    java.util.List<com.pokerarity.scanner.data.local.db.EventEntity> events, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM events WHERE startDate <= :date AND endDate >= :date")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getEventsForDate(@org.jetbrains.annotations.NotNull()
    java.util.Date date, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.pokerarity.scanner.data.local.db.EventEntity>> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM events WHERE pokemonId = :pokemonId AND startDate <= :date AND endDate >= :date")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getEventsForPokemonOnDate(long pokemonId, @org.jetbrains.annotations.NotNull()
    java.util.Date date, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.pokerarity.scanner.data.local.db.EventEntity>> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM events ORDER BY startDate DESC")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getAll(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.pokerarity.scanner.data.local.db.EventEntity>> $completion);
    
    @androidx.room.Upsert()
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object upsertEventPokemon(@org.jetbrains.annotations.NotNull()
    com.pokerarity.scanner.data.local.db.EventPokemonEntity eventPokemon, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Upsert()
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object upsertEventPokemonAll(@org.jetbrains.annotations.NotNull()
    java.util.List<com.pokerarity.scanner.data.local.db.EventPokemonEntity> eventPokemon, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "SELECT COUNT(*) FROM event_pokemon")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getEventPokemonCount(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM event_pokemon WHERE baseName = :baseName")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getEventPokemonForBaseName(@org.jetbrains.annotations.NotNull()
    java.lang.String baseName, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.pokerarity.scanner.data.local.db.EventPokemonEntity>> $completion);
    
    @androidx.room.Query(value = "\n        SELECT * FROM event_pokemon\n        WHERE baseName = :baseName\n          AND (\n            (eventStart IS NULL AND eventEnd IS NULL)\n            OR ((eventStart IS NULL OR eventStart <= :date) AND (eventEnd IS NULL OR eventEnd >= :date))\n          )\n        ")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getEventPokemonForBaseNameOnDate(@org.jetbrains.annotations.NotNull()
    java.lang.String baseName, @org.jetbrains.annotations.NotNull()
    java.util.Date date, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.pokerarity.scanner.data.local.db.EventPokemonEntity>> $completion);
}