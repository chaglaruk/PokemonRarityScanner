package com.pokerarity.scanner.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CollectionEntryDao {

    @Insert
    suspend fun insert(entry: CollectionEntryEntity): Long

    @Query("SELECT * FROM collection_entries ORDER BY createdAt DESC")
    suspend fun getAllEntries(): List<CollectionEntryEntity>

    @Query("SELECT * FROM collection_entries WHERE variantIdentityKey = :key ORDER BY createdAt DESC")
    suspend fun getEntriesByVariantKey(key: String): List<CollectionEntryEntity>

    @Query("SELECT COUNT(*) FROM collection_entries WHERE variantIdentityKey = :key")
    suspend fun countByVariantKey(key: String): Int

    @Query("SELECT * FROM collection_entries WHERE isXXL = 1 ORDER BY createdAt DESC")
    suspend fun getXXLEntries(): List<CollectionEntryEntity>

    @Query("SELECT * FROM collection_entries WHERE isXXS = 1 ORDER BY createdAt DESC")
    suspend fun getXXSEntries(): List<CollectionEntryEntity>
}
