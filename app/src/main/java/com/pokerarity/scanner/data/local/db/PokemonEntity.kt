package com.pokerarity.scanner.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a Pokemon species and its base rarity.
 */
@Entity(tableName = "pokemon")
data class PokemonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseRarity: Int // 0-25 scale
)
