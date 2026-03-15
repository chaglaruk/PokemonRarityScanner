package com.pokerarity.scanner.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PokemonDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pokemon: PokemonEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pokemon: List<PokemonEntity>)

    @Query("SELECT * FROM pokemon WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): PokemonEntity?

    @Query("SELECT * FROM pokemon WHERE name LIKE '%' || :query || '%'")
    suspend fun searchByName(query: String): List<PokemonEntity>

    @Query("SELECT * FROM pokemon ORDER BY name ASC")
    fun getAll(): Flow<List<PokemonEntity>>

    @Query("SELECT COUNT(*) FROM pokemon")
    suspend fun count(): Int
}
