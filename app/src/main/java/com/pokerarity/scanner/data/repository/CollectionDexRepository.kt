package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.local.db.CollectionEntryDao
import com.pokerarity.scanner.data.local.db.CollectionEntryEntity

import com.pokerarity.scanner.domain.collector.CollectionContextLookup

/**
 * Manages the user's collection of captured Pokemon.
 */
class CollectionDexRepository(
    private val collectionEntryDao: CollectionEntryDao
) : CollectionContextLookup {

    suspend fun recordEntry(entry: CollectionEntryEntity): Long {
        return collectionEntryDao.insert(entry)
    }

    suspend fun getAllEntries(): List<CollectionEntryEntity> {
        return collectionEntryDao.getAllEntries()
    }

    override suspend fun countDuplicates(variantIdentityKey: String): Int {
        return collectionEntryDao.countByVariantKey(variantIdentityKey)
    }

    override suspend fun getEntriesByVariantKey(variantIdentityKey: String): List<CollectionEntryEntity> {
        return collectionEntryDao.getEntriesByVariantKey(variantIdentityKey)
    }

    suspend fun getXXLEntries(): List<CollectionEntryEntity> {
        return collectionEntryDao.getXXLEntries()
    }

    suspend fun getXXSEntries(): List<CollectionEntryEntity> {
        return collectionEntryDao.getXXSEntries()
    }
}
