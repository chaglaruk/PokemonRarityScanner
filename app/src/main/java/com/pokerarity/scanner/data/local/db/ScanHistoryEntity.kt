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
    val rarityTier: String = "COMMON",
    /** Null means legacy/missing typed evidence; Boolean false never proves absence. */
    val shinyTruth: String? = null,
    /** Historical records have no reliable scan-review status: keep the new fields nullable. */
    val scanConfidenceScore: Int? = null,
    val scanConfidenceLabel: String? = null,
    val scanConfidenceDetail: String? = null,
    /** Event matching context shown with the result; null on records saved before schema v10. */
    val eventConfidenceCode: String? = null,
    val eventConfidenceLabel: String? = null,
    val eventConfidenceDetail: String? = null,
    /** Nullable explanations shown with a result; null means unavailable or legacy. */
    val mismatchGuardTitle: String? = null,
    val mismatchGuardDetail: String? = null,
    val recognitionSummary: String? = null,
    /** Null on rows created before typed IV estimates were persisted. */
    val ivMin: Int? = null,
    val ivMax: Int? = null,
    val ivCandidateCount: Int? = null,
    val ivSolveMode: String? = null,
    val ivLevelMin: Float? = null,
    val ivLevelMax: Float? = null,
    /** Nullable on old rows, which predate authoritative species/form identity persistence. */
    val recognitionIdentityJson: String? = null
)
