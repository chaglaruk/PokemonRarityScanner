package com.pokerarity.scanner.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Room entity representing a Pokemon GO event window.
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = PokemonEntity::class,
            parentColumns = ["id"],
            childColumns = ["pokemonId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pokemonId")]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: Date,
    val endDate: Date,
    val pokemonId: Long,
    val rarityWeight: Int, // 0-20 scale
    val isOneDayEvent: Boolean = false
)
