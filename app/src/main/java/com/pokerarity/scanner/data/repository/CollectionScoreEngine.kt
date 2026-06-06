package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.CollectionAxisScore
import com.pokerarity.scanner.data.model.CollectionResult
import com.pokerarity.scanner.data.model.CollectionTier
import com.pokerarity.scanner.data.model.EditedScanDetails
import com.pokerarity.scanner.data.model.EventMatchLevel
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.ScoreAxis
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.model.catalog.CollectionCatalog
import com.pokerarity.scanner.data.model.catalog.CostumeRecord
import com.pokerarity.scanner.data.model.catalog.CurrentAvailabilityRecord
import com.pokerarity.scanner.data.model.catalog.EventRecord
import com.pokerarity.scanner.data.model.catalog.MetaDemandRecord
import com.pokerarity.scanner.data.model.catalog.RegionalRecord
import com.pokerarity.scanner.data.model.catalog.SpecialSpeciesRecord
import com.pokerarity.scanner.data.model.catalog.VerificationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

class CollectionScoreEngine(
    private val speciesRarityLookup: (String?) -> Int = RarityManifestLoader::getSpeciesRarity,
    private val currentDateProvider: () -> Date = { Date() }
) {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }

    fun calculate(
        pokemonData: PokemonData,
        features: VisualFeatures,
        catalog: CollectionCatalog,
        editedDetails: EditedScanDetails? = null,
        currentDate: Date = currentDateProvider()
    ): CollectionResult {
        val species = resolveSpecies(pokemonData, editedDetails)
        if (species.equals("Unknown", ignoreCase = true)) {
            return emptyResult(species, editedDetails != null)
        }

        val isShiny = editedDetails?.isShiny ?: features.isShiny
        val isLucky = editedDetails?.isLucky ?: features.isLucky
        val isShadow = editedDetails?.isShadow ?: features.isShadow
        val isPurified = editedDetails?.isPurified ?: features.isPurified
        val hasLocationCard = editedDetails?.hasLocationCard ?: features.hasLocationCard
        val caughtDate = editedDetails?.caughtDate ?: pokemonData.caughtDate
        val costumeId = editedDetails?.costumeId
        val formId = editedDetails?.formId ?: formId(isShadow, species, features)
        val hasSpecialForm = !formId.isNullOrBlank() || features.hasSpecialForm

        val costume = verifiedCostume(catalog, costumeId)
        val hasCostume = if (costumeId != null) costume != null else features.hasCostume
        val regional = verifiedRegional(catalog, editedDetails?.regionalRecordId, species)
        val specialSpecies = verifiedSpecialSpecies(catalog, species, editedDetails?.specialStatusOverride)
        val availability = verifiedAvailability(catalog, species, costumeId)
        val metaDemand = verifiedMetaDemand(catalog, species, formId)
        val eventMatch = resolveEventMatch(catalog, species, costumeId, editedDetails?.eventId, caughtDate)

        val baseAxis = baseSpeciesAxis(species, specialSpecies)
        val availabilityAxis = availabilityAxis(availability, eventMatch.matchedEvent, caughtDate)
        val variantAxis = variantAxis(
            isShiny = isShiny,
            isLucky = isLucky,
            isShadow = isShadow,
            isPurified = isPurified,
            hasLocationCard = hasLocationCard,
            hasCostume = hasCostume,
            hasSpecialForm = hasSpecialForm,
            costume = costume,
            regional = regional,
            isLegendary = isLegendary(specialSpecies)
        )
        val legacyAxis = legacyAxis(caughtDate, currentDate)
        val contextAxis = collectorContextAxis(
            pokemonData = pokemonData,
            features = features,
            eventMatch = eventMatch,
            specialSpecies = specialSpecies,
            regional = regional,
            hasLocationCard = hasLocationCard
        )
        val metaAxis = metaAxis(metaDemand)
        val axes = listOf(baseAxis, availabilityAxis, variantAxis, legacyAxis, contextAxis, metaAxis)
        val rawTotal = axes.sumOf { it.score }.coerceIn(0, CollectionScoreConstants.TOTAL_MAX)

        val trophyInput = TrophyGate.Input(
            totalScore = rawTotal,
            isShiny = isShiny,
            hasLocationCard = hasLocationCard,
            isLegendary = isLegendary(specialSpecies),
            legacyScore = legacyAxis.score,
            eventMatchLevel = eventMatch.level,
            matchedEvent = eventMatch.matchedEvent,
            costumeRecord = costume,
            specialSpecies = specialSpecies
        )
        val trophySignals = TrophyGate.trophySignals(trophyInput)
        val trophyQualified = TrophyGate.qualifies(trophyInput)
        val tentativeTier = CollectionTier.fromScore(rawTotal)
        val finalTier = if (tentativeTier == CollectionTier.TROPHY && !trophyQualified) {
            CollectionTier.ULTRA_RARE
        } else {
            tentativeTier
        }

        return CollectionResult(
            totalScore = rawTotal,
            tier = finalTier,
            axes = axes,
            trophyQualified = trophyQualified,
            trophySignals = trophySignals,
            detectedSpecies = species,
            costumeOrForm = costume?.costumeName ?: formId?.replace('_', ' ')?.replaceFirstChar { it.titlecase(Locale.US) },
            eventName = eventMatch.matchedEvent?.name,
            eventWindow = eventMatch.matchedEvent?.let { "${it.startDate} to ${it.endDate}" },
            firstReleased = eventMatch.matchedEvent?.takeIf { it.isFirstRelease }?.startDate,
            currentStatus = availability?.currentPenaltyReason ?: availability?.availabilityType?.replace('_', ' '),
            legacyCatchLabel = legacyAxis.details.firstOrNull { it.startsWith("Legacy catch:") },
            tradeInfo = specialSpecies?.takeIf { !it.isTradable }?.let { "Not tradable" },
            isEdited = editedDetails != null,
            eventMatchLevel = eventMatch.level,
            eventDateMismatchMessage = eventMatch.mismatchMessage,
            catalogVersion = catalog.version.version
        )
    }

    private fun emptyResult(species: String, isEdited: Boolean): CollectionResult {
        val axes = ScoreAxis.entries.map { axis ->
            CollectionAxisScore(axis, 0, axis.maxPoints, listOf("No verified scoring signal"))
        }
        return CollectionResult(
            totalScore = 0,
            tier = CollectionTier.COMMON,
            axes = axes,
            trophyQualified = false,
            trophySignals = emptyList(),
            detectedSpecies = species.ifBlank { "Unknown" },
            costumeOrForm = null,
            eventName = null,
            eventWindow = null,
            firstReleased = null,
            currentStatus = null,
            legacyCatchLabel = null,
            tradeInfo = null,
            isEdited = isEdited,
            eventMatchLevel = EventMatchLevel.NONE,
            eventDateMismatchMessage = null,
            catalogVersion = null
        )
    }

    private fun resolveSpecies(pokemonData: PokemonData, editedDetails: EditedScanDetails?): String =
        editedDetails?.species
            ?: pokemonData.realName
            ?: pokemonData.name
            ?: "Unknown"

    private fun baseSpeciesAxis(species: String, specialSpecies: SpecialSpeciesRecord?): CollectionAxisScore {
        val details = mutableListOf<String>()
        val score = if (specialSpecies != null) {
            details += specialSpecies.category.replace('_', ' ')
            specialSpecies.baseSpeciesScore
        } else {
            val manifestScore = ((speciesRarityLookup(species).coerceIn(0, 25) / 25.0) *
                CollectionScoreConstants.BASE_SPECIES_MAX).roundToInt()
            details += "Manifest species rarity fallback"
            manifestScore
        }.coerceIn(0, CollectionScoreConstants.BASE_SPECIES_MAX)
        return CollectionAxisScore(ScoreAxis.BASE_SPECIES, score, CollectionScoreConstants.BASE_SPECIES_MAX, details)
    }

    private fun availabilityAxis(
        availability: CurrentAvailabilityRecord?,
        activeEvent: EventRecord?,
        caughtDate: Date?
    ): CollectionAxisScore {
        if (availability == null) {
            return CollectionAxisScore(ScoreAxis.AVAILABILITY, 0, CollectionScoreConstants.AVAILABILITY_MAX)
        }
        val applyPenalty = availability.currentPenalty != null && when {
            caughtDate == null -> true
            availability.activeEventId != null && activeEvent != null -> isDateInWindow(caughtDate, activeEvent)
            availability.activeEventId != null -> false
            else -> false
        }
        val penalty = if (applyPenalty) availability.currentPenalty ?: 0 else 0
        val score = (availability.baseAvailabilityScore - penalty).coerceIn(0, CollectionScoreConstants.AVAILABILITY_MAX)
        val details = buildList {
            add(availability.availabilityType.replace('_', ' '))
            if (penalty > 0) add(availability.currentPenaltyReason ?: "Current availability penalty")
        }
        return CollectionAxisScore(ScoreAxis.AVAILABILITY, score, CollectionScoreConstants.AVAILABILITY_MAX, details)
    }

    private fun variantAxis(
        isShiny: Boolean,
        isLucky: Boolean,
        isShadow: Boolean,
        isPurified: Boolean,
        hasLocationCard: Boolean,
        hasCostume: Boolean,
        hasSpecialForm: Boolean,
        costume: CostumeRecord?,
        regional: RegionalRecord?,
        isLegendary: Boolean
    ): CollectionAxisScore {
        var score = 0
        val details = mutableListOf<String>()
        fun add(points: Int, detail: String) {
            score += points
            details += detail
        }
        if (isShiny) add(CollectionScoreConstants.SHINY_BONUS, "Shiny")
        if (isLucky) add(CollectionScoreConstants.LUCKY_BONUS, "Lucky")
        if (isShadow) add(CollectionScoreConstants.SHADOW_BONUS, "Shadow")
        if (isPurified) add(CollectionScoreConstants.PURIFIED_BONUS, "Purified")
        if (hasLocationCard) add(CollectionScoreConstants.LOCATION_CARD_BONUS, "Location card")
        if (hasCostume) {
            add(costumeBonus(costume), costume?.costumeName ?: "Costume")
        }
        if (regional != null) add(CollectionScoreConstants.REGIONAL_FORM, "Regional form")
        if (hasSpecialForm && !hasCostume) add(CollectionScoreConstants.SPECIAL_FORM, "Special form")
        if (isShiny && hasCostume) {
            add(
                if (costume?.costumeType == "retired") {
                    CollectionScoreConstants.COMBO_SHINY_RETIRED_COSTUME
                } else {
                    CollectionScoreConstants.COMBO_SHINY_COSTUME
                },
                "Shiny costume combo"
            )
        }
        if (hasCostume && hasLocationCard) add(CollectionScoreConstants.COMBO_COSTUME_LOCATION, "Costume location-card combo")
        if (isShadow && isLegendary) add(CollectionScoreConstants.COMBO_SHADOW_LEGENDARY, "Shadow legendary combo")
        if (regional != null && hasCostume) add(CollectionScoreConstants.COMBO_REGIONAL_EVENT, "Regional event combo")
        return CollectionAxisScore(
            ScoreAxis.VARIANT,
            score.coerceIn(0, CollectionScoreConstants.VARIANT_MAX),
            CollectionScoreConstants.VARIANT_MAX,
            details
        )
    }

    private fun legacyAxis(caughtDate: Date?, currentDate: Date): CollectionAxisScore {
        if (caughtDate == null) {
            return CollectionAxisScore(ScoreAxis.LEGACY, 0, CollectionScoreConstants.LEGACY_MAX)
        }
        val caughtYear = year(caughtDate)
        val currentYear = year(currentDate)
        val points = CollectionScoreConstants.legacyPoints(caughtYear, currentYear)
        val details = if (points >= 3) listOf("Legacy catch: $caughtYear") else emptyList()
        return CollectionAxisScore(ScoreAxis.LEGACY, points, CollectionScoreConstants.LEGACY_MAX, details)
    }

    private fun collectorContextAxis(
        pokemonData: PokemonData,
        features: VisualFeatures,
        eventMatch: EventResolution,
        specialSpecies: SpecialSpeciesRecord?,
        regional: RegionalRecord?,
        hasLocationCard: Boolean
    ): CollectionAxisScore {
        var score = 0
        val details = mutableListOf<String>()
        val event = eventMatch.matchedEvent
        if (event != null && eventMatch.level == EventMatchLevel.EXACT) {
            val eventPoints = when (event.eventType) {
                "in_person" -> CollectionScoreConstants.IN_PERSON_HISTORICAL
                "ticket_global", "go_fest", "safari_zone" -> CollectionScoreConstants.TICKET_HISTORICAL
                else -> CollectionScoreConstants.EXACT_EVENT_MATCH
            }
            score += eventPoints
            details += event.name
            if (event.isFirstRelease) {
                score += CollectionScoreConstants.FIRST_RELEASE_CATCH
                details += "First release catch"
            }
        }
        if (specialSpecies?.category == "mythical_one_time") {
            score += CollectionScoreConstants.SPECIAL_RESEARCH_PROVENANCE
            details += "One-time mythical provenance"
        }
        if (regional != null) {
            score += CollectionScoreConstants.REGIONAL_AREA_KNOWN
            details += regional.region
        }
        if (isRareGender(pokemonData.realName ?: pokemonData.name.orEmpty(), pokemonData.gender)) {
            score += CollectionScoreConstants.RARE_GENDER
            details += "Rare gender"
        }
        if (features.isXXL) {
            score += CollectionScoreConstants.XXL_XXS
            details += "XXL size"
        }
        if (features.isXXS) {
            score += CollectionScoreConstants.XXL_XXS
            details += "XXS size"
        }
        if (hasLocationCard && eventMatch.level == EventMatchLevel.EXACT) {
            score += CollectionScoreConstants.BACKGROUND_EVENT_MATCH
            details += "Location card event match"
        }
        return CollectionAxisScore(
            ScoreAxis.COLLECTOR_CONTEXT,
            score.coerceIn(0, CollectionScoreConstants.COLLECTOR_CONTEXT_MAX),
            CollectionScoreConstants.COLLECTOR_CONTEXT_MAX,
            details
        )
    }

    private fun metaAxis(metaDemand: MetaDemandRecord?): CollectionAxisScore {
        if (metaDemand == null) {
            return CollectionAxisScore(ScoreAxis.META, 0, CollectionScoreConstants.META_MAX)
        }
        val score = metaDemand.metaScore.coerceIn(0, CollectionScoreConstants.META_MAX)
        return CollectionAxisScore(
            ScoreAxis.META,
            score,
            CollectionScoreConstants.META_MAX,
            listOf(metaDemand.demandLevel.replace('_', ' '))
        )
    }

    private fun costumeBonus(costume: CostumeRecord?): Int = when (costume?.costumeType) {
        "recurring" -> CollectionScoreConstants.COSTUME_RECURRING
        "notable" -> CollectionScoreConstants.COSTUME_NOTABLE
        "not_available" -> CollectionScoreConstants.COSTUME_NOT_AVAILABLE
        "retired" -> CollectionScoreConstants.COSTUME_RETIRED
        else -> CollectionScoreConstants.COSTUME_RECURRING
    }

    private data class EventResolution(
        val matchedEvent: EventRecord?,
        val level: EventMatchLevel,
        val mismatchMessage: String?
    )

    private fun resolveEventMatch(
        catalog: CollectionCatalog,
        species: String,
        costumeId: String?,
        eventId: String?,
        caughtDate: Date?
    ): EventResolution {
        val selected = eventId?.let { id -> catalog.events.firstOrNull { it.id == id && it.isVerified() } }
        val matchedEvent = selected ?: catalog.events.firstOrNull { event ->
            event.isVerified() &&
                (event.featuredSpecies.any { it.equals(species, ignoreCase = true) } ||
                    (!costumeId.isNullOrBlank() && event.costumeIds.any { it.equals(costumeId, ignoreCase = true) })) &&
                caughtDate != null &&
                isDateInWindow(caughtDate, event)
        } ?: catalog.events.firstOrNull { event ->
            event.isVerified() &&
                caughtDate == null &&
                (event.featuredSpecies.any { it.equals(species, ignoreCase = true) } ||
                    (!costumeId.isNullOrBlank() && event.costumeIds.any { it.equals(costumeId, ignoreCase = true) }))
        }

        if (matchedEvent == null) return EventResolution(null, EventMatchLevel.NONE, null)
        if (caughtDate == null) return EventResolution(matchedEvent, EventMatchLevel.POSSIBLE, null)
        if (isDateInWindow(caughtDate, matchedEvent)) return EventResolution(matchedEvent, EventMatchLevel.EXACT, null)
        val mismatch = if (eventId != null) "Selected event date does not match caught date" else null
        return EventResolution(matchedEvent, EventMatchLevel.NONE, mismatch)
    }

    private fun verifiedCostume(catalog: CollectionCatalog, costumeId: String?): CostumeRecord? =
        costumeId?.let { id -> catalog.costumes.firstOrNull { it.id.equals(id, ignoreCase = true) && it.isVerified() } }

    private fun verifiedRegional(catalog: CollectionCatalog, regionalRecordId: String?, species: String): RegionalRecord? =
        catalog.regionals.firstOrNull { record ->
            record.isVerified() &&
                (record.species.equals(regionalRecordId, ignoreCase = true) ||
                    record.species.equals(species, ignoreCase = true))
        }

    private fun verifiedSpecialSpecies(
        catalog: CollectionCatalog,
        species: String,
        specialStatusOverride: String?
    ): SpecialSpeciesRecord? =
        catalog.specialSpecies.firstOrNull { record ->
            record.isVerified() &&
                record.species.equals(species, ignoreCase = true) &&
                (specialStatusOverride == null || record.category.equals(specialStatusOverride, ignoreCase = true))
        }

    private fun verifiedAvailability(
        catalog: CollectionCatalog,
        species: String,
        costumeId: String?
    ): CurrentAvailabilityRecord? =
        catalog.currentAvailability.firstOrNull { record ->
            record.isVerified() &&
                record.species.equals(species, ignoreCase = true) &&
                record.costumeId.equals(costumeId, ignoreCase = true)
        }

    private fun verifiedMetaDemand(catalog: CollectionCatalog, species: String, formId: String?): MetaDemandRecord? =
        catalog.metaDemand.firstOrNull { record ->
            record.isVerified() &&
                record.species.equals(species, ignoreCase = true) &&
                (record.formId == null || record.formId.equals(formId, ignoreCase = true))
        }

    private fun isDateInWindow(date: Date, event: EventRecord): Boolean {
        val start = parseDate(event.startDate) ?: return false
        val end = parseDate(event.endDate) ?: return false
        return !date.before(start) && date.before(Date(end.time + DAY_MS))
    }

    private fun parseDate(value: String): Date? =
        runCatching { isoFormat.parse(value) }.getOrNull()

    private fun year(date: Date): Int {
        val calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
        calendar.time = date
        return calendar.get(java.util.Calendar.YEAR)
    }

    private fun formId(isShadow: Boolean, species: String, features: VisualFeatures): String? = when {
        isShadow -> "shadow"
        species.equals("Armored Mewtwo", ignoreCase = true) -> "armored"
        features.hasSpecialForm -> "special"
        else -> null
    }

    private fun isLegendary(record: SpecialSpeciesRecord?): Boolean =
        record?.category in setOf("legendary", "ultra_beast")

    private fun isRareGender(species: String, gender: String?): Boolean =
        gender == "Female" && species in setOf("Combee", "Salandit", "Vespiquen", "Salazzle")

    private fun CostumeRecord.isVerified(): Boolean = verificationStatus.isScoringVerified()
    private fun EventRecord.isVerified(): Boolean = verificationStatus.isScoringVerified()
    private fun RegionalRecord.isVerified(): Boolean = verificationStatus.isScoringVerified()
    private fun SpecialSpeciesRecord.isVerified(): Boolean = verificationStatus.isScoringVerified()
    private fun CurrentAvailabilityRecord.isVerified(): Boolean = verificationStatus.isScoringVerified()
    private fun MetaDemandRecord.isVerified(): Boolean = verificationStatus.isScoringVerified()

    private fun VerificationStatus.isScoringVerified(): Boolean =
        this == VerificationStatus.VERIFIED_OFFICIAL || this == VerificationStatus.VERIFIED_COMMUNITY

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
