package com.pokerarity.scanner.domain.collector

interface CollectionContextLookup {
    suspend fun countDuplicates(variantIdentityKey: String): Int
    suspend fun getLookupEntriesByVariantKey(variantIdentityKey: String): List<CollectionLookupEntry>
}
