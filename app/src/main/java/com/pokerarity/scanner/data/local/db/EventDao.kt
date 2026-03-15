package com.pokerarity.scanner.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.util.Date

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    @Query("SELECT * FROM events WHERE startDate <= :date AND endDate >= :date")
    suspend fun getEventsForDate(date: Date): List<EventEntity>

    @Query("SELECT * FROM events WHERE pokemonId = :pokemonId AND startDate <= :date AND endDate >= :date")
    suspend fun getEventsForPokemonOnDate(pokemonId: Long, date: Date): List<EventEntity>

    @Query("SELECT * FROM events ORDER BY startDate DESC")
    suspend fun getAll(): List<EventEntity>
}
