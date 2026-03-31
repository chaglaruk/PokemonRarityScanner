package com.pokerarity.scanner.data.model

data class BulbapediaEventArchive(
    val version: Int = 1,
    val count: Int = 0,
    val entries: List<BulbapediaEventArchiveEntry> = emptyList(),
)

data class BulbapediaEventArchiveEntry(
    val species: String,
    val normalizedToken: String? = null,
    val formLabel: String? = null,
    val appearances: List<HistoricalEventAppearance> = emptyList(),
)
