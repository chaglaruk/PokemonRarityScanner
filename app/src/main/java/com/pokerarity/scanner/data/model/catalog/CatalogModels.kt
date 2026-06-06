package com.pokerarity.scanner.data.model.catalog

import com.google.gson.annotations.SerializedName

data class CatalogVersion(
    val version: String,
    val generatedAt: String,
    val schemaVersion: Int = 1
)

data class CollectionCatalog(
    val version: CatalogVersion,
    val costumes: List<CostumeRecord>,
    val events: List<EventRecord>,
    val regionals: List<RegionalRecord>,
    val specialSpecies: List<SpecialSpeciesRecord>,
    val currentAvailability: List<CurrentAvailabilityRecord>,
    val metaDemand: List<MetaDemandRecord>
) {
    companion object {
        val EMPTY = CollectionCatalog(
            version = CatalogVersion(version = "bundled-empty", generatedAt = "", schemaVersion = 1),
            costumes = emptyList(),
            events = emptyList(),
            regionals = emptyList(),
            specialSpecies = emptyList(),
            currentAvailability = emptyList(),
            metaDemand = emptyList()
        )
    }
}

enum class VerificationStatus {
    @SerializedName("verified_official")
    VERIFIED_OFFICIAL,
    @SerializedName("verified_community")
    VERIFIED_COMMUNITY,
    @SerializedName("manual_review_needed")
    MANUAL_REVIEW_NEEDED,
    @SerializedName("unknown")
    UNKNOWN
}

data class CostumeRecord(
    val id: String,
    val species: String,
    val costumeName: String,
    val costumeType: String,
    val eventIds: List<String>,
    val sourceLinks: List<String>,
    val verificationStatus: VerificationStatus
)

data class EventRecord(
    val id: String,
    val name: String,
    val eventType: String,
    val startDate: String,
    val endDate: String,
    val costumeIds: List<String>,
    val featuredSpecies: List<String>,
    val isFirstRelease: Boolean,
    val sourceLinks: List<String>,
    val verificationStatus: VerificationStatus
)

data class RegionalRecord(
    val species: String,
    val region: String,
    val isCurrentlyLocked: Boolean,
    val globalEventAccess: List<String>,
    val sourceLinks: List<String>,
    val verificationStatus: VerificationStatus
)

data class SpecialSpeciesRecord(
    val species: String,
    val category: String,
    val baseSpeciesScore: Int,
    val isTradable: Boolean,
    val sourceLinks: List<String>,
    val verificationStatus: VerificationStatus
)

data class CurrentAvailabilityRecord(
    val species: String,
    val costumeId: String?,
    val availabilityType: String,
    val baseAvailabilityScore: Int,
    val currentPenalty: Int?,
    val currentPenaltyReason: String?,
    val activeEventId: String?,
    val sourceLinks: List<String>,
    val verificationStatus: VerificationStatus
)

data class MetaDemandRecord(
    val species: String,
    val formId: String?,
    val demandLevel: String,
    val metaScore: Int,
    val sourceLinks: List<String>,
    val verificationStatus: VerificationStatus
)
