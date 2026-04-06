package com.pokerarity.scanner.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scan: ScanHistoryEntity): Long

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE id = :id")
    suspend fun getById(id: Long): ScanHistoryEntity?

    @Query("SELECT * FROM scan_history WHERE pokemonName = :name ORDER BY timestamp DESC")
    suspend fun getByPokemonName(name: String): List<ScanHistoryEntity>

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM scan_history")
    suspend fun count(): Int

    @Query("SELECT * FROM scan_history WHERE rarityScore >= :minScore ORDER BY rarityScore DESC")
    fun getByMinRarity(minScore: Int): Flow<List<ScanHistoryEntity>>
    
    @Query("DELETE FROM scan_history WHERE timestamp < :beforeEpochMs")
    suspend fun deleteOlderThan(beforeEpochMs: Long): Int
}
