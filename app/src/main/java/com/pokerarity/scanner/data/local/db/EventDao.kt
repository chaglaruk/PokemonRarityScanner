package com.pokerarity.scanner.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
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

    @Upsert
    suspend fun upsertEventPokemon(eventPokemon: EventPokemonEntity)

    @Upsert
    suspend fun upsertEventPokemonAll(eventPokemon: List<EventPokemonEntity>)

    @Query("SELECT COUNT(*) FROM event_pokemon")
    suspend fun getEventPokemonCount(): Int

    @Query("SELECT * FROM event_pokemon WHERE baseName = :baseName")
    suspend fun getEventPokemonForBaseName(baseName: String): List<EventPokemonEntity>

    @Query(
        """
        SELECT * FROM event_pokemon
        WHERE baseName = :baseName
          AND (
            (eventStart IS NULL AND eventEnd IS NULL)
            OR ((eventStart IS NULL OR eventStart <= :date) AND (eventEnd IS NULL OR eventEnd >= :date))
          )
        """
    )
    suspend fun getEventPokemonForBaseNameOnDate(baseName: String, date: Date): List<EventPokemonEntity>
}
