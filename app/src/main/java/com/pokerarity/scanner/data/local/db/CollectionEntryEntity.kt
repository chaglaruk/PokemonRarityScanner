package com.pokerarity.scanner.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Represents a single captured Pokemon in the user's collection.
 * Serves as the foundation for Collector Intelligence (gap detection, duplicate detection).
 */
@Entity(tableName = "collection_entries")
data class CollectionEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanHistoryId: Long? = null,
    
    // Core identity
    val dex: Int,
    val speciesName: String,
    val formId: String?,
    val variantId: String?,
    val variantIdentityKey: String,
    
    // Traits
    val isShiny: Boolean = false,
    val isShadow: Boolean = false,
    val isPurified: Boolean = false,
    val isLucky: Boolean = false,
    val isCostume: Boolean = false,
    val isXXL: Boolean = false,
    val isXXS: Boolean = false,
    
    // Labels
    val costumeLabel: String? = null,
    val backgroundType: String? = null,
    val backgroundLabel: String? = null,
    val eventLabel: String? = null,
    
    // Stats and metadata
    val caughtDate: Date? = null,
    val ivExact: Int? = null,
    val ivMin: Int? = null,
    val ivMax: Int? = null,
    
    // Derived values at time of scan
    val rarityScore: Int,
    val rarityTierCode: String,
    
    val createdAt: Date = Date()
)
