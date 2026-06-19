package com.pokerarity.scanner.domain.collector

import com.pokerarity.scanner.data.local.db.CollectionEntryEntity

interface CollectionContextLookup {
    suspend fun countDuplicates(variantIdentityKey: String): Int
    suspend fun getEntriesByVariantKey(variantIdentityKey: String): List<CollectionEntryEntity>
}
