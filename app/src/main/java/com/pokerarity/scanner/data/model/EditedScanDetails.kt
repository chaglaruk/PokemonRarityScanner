package com.pokerarity.scanner.data.model

import java.util.Date

data class EditedScanDetails(
    val species: String? = null,
    val costumeId: String? = null,
    val eventId: String? = null,
    val formId: String? = null,
    val isShiny: Boolean? = null,
    val isLucky: Boolean? = null,
    val isShadow: Boolean? = null,
    val isPurified: Boolean? = null,
    val hasLocationCard: Boolean? = null,
    val caughtDate: Date? = null,
    val regionalRecordId: String? = null,
    val specialStatusOverride: String? = null
)

enum class EditDetailsCatalogOptionType {
    COSTUME,
    EVENT,
    SPECIAL_STATUS,
    REGIONAL
}

data class EditDetailsCatalogOption(
    val id: String,
    val label: String,
    val type: EditDetailsCatalogOptionType,
    val subtitle: String? = null
)

data class EditDetailsCatalogOptions(
    val costumes: List<EditDetailsCatalogOption> = emptyList(),
    val events: List<EditDetailsCatalogOption> = emptyList(),
    val specialStatuses: List<EditDetailsCatalogOption> = emptyList(),
    val regionals: List<EditDetailsCatalogOption> = emptyList()
) {
    val all: List<EditDetailsCatalogOption>
        get() = costumes + events + specialStatuses + regionals

    companion object {
        val EMPTY = EditDetailsCatalogOptions()
    }
}
