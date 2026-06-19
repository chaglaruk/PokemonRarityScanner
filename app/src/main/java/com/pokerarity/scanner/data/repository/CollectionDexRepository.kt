package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.local.db.CollectionEntryDao
import com.pokerarity.scanner.data.local.db.CollectionEntryEntity

/**
 * Manages the user's collection of captured Pokemon.
 */
class CollectionDexRepository(
    private val collectionEntryDao: CollectionEntryDao
) {

    suspend fun recordEntry(entry: CollectionEntryEntity): Long {
        return collectionEntryDao.insert(entry)
    }

    suspend fun getAllEntries(): List<CollectionEntryEntity> {
        return collectionEntryDao.getAllEntries()
    }

    suspend fun countDuplicates(variantIdentityKey: String): Int {
        return collectionEntryDao.countByVariantKey(variantIdentityKey)
    }

    suspend fun getEntriesByVariantKey(variantIdentityKey: String): List<CollectionEntryEntity> {
        return collectionEntryDao.getEntriesByVariantKey(variantIdentityKey)
    }
}
