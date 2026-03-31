package com.pokerarity.scanner.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "event_pokemon",
    indices = [
        Index("baseName"),
        Index("eventName"),
        Index("spriteKey"),
        Index("variantToken"),
    ],
)
data class EventPokemonEntity(
    @PrimaryKey
    val id: String,
    val baseName: String,
    val eventName: String,
    val eventBonusScore: Int,
    val spriteKey: String? = null,
    val variantToken: String? = null,
    val eventStart: Date? = null,
    val eventEnd: Date? = null,
    val source: String = "seed",
    val updatedAt: Date = Date(),
)
