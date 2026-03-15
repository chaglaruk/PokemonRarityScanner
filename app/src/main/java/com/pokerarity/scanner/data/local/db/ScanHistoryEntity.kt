package com.pokerarity.scanner.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Room entity storing a single scan result with all extracted data.
 */
@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Date = Date(),

    // OCR results
    val pokemonName: String?,
    val cp: Int?,
    val hp: Int?,
    val caughtDate: Date?,
    val rawOcrText: String,

    // Visual features
    val isShiny: Boolean = false,
    val isShadow: Boolean = false,
    val isLucky: Boolean = false,
    val hasCostume: Boolean = false,

    // Rarity
    val rarityScore: Int = 0,
    val rarityTier: String = "COMMON"
)
