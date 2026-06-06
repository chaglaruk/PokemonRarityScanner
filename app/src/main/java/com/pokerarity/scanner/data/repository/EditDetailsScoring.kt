package com.pokerarity.scanner.data.repository

import com.google.gson.Gson
import com.pokerarity.scanner.data.model.EditDetailsCatalogOption
import com.pokerarity.scanner.data.model.EditDetailsCatalogOptionType
import com.pokerarity.scanner.data.model.EditDetailsCatalogOptions
import com.pokerarity.scanner.data.model.EditedScanDetails
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityAnalysisItem
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.model.catalog.CollectionCatalog
import com.pokerarity.scanner.data.model.catalog.VerificationStatus
import com.pokerarity.scanner.data.model.pokemonFromScanExtras
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EditDetailsScoring {
    fun catalogOptionsFor(
        catalog: CollectionCatalog,
        species: String
    ): EditDetailsCatalogOptions {
        val normalizedSpecies = species.trim()
        if (normalizedSpecies.isBlank() || normalizedSpecies.equals("Unknown", ignoreCase = true)) {
            return EditDetailsCatalogOptions.EMPTY
        }

        val costumes = catalog.costumes
            .filter { it.verificationStatus.isScoringVerified() }
            .filter { it.species.equals(normalizedSpecies, ignoreCase = true) }
            .map {
                EditDetailsCatalogOption(
                    id = it.id,
                    label = it.costumeName,
                    type = EditDetailsCatalogOptionType.COSTUME,
                    subtitle = it.costumeType.replace('_', ' ')
                )
            }

        val events = catalog.events
            .filter { it.verificationStatus.isScoringVerified() }
            .filter { event ->
                event.featuredSpecies.any { it.equals(normalizedSpecies, ignoreCase = true) } ||
                    event.costumeIds.any { costumeId -> costumes.any { it.id.equals(costumeId, ignoreCase = true) } }
            }
            .map {
                EditDetailsCatalogOption(
                    id = it.id,
                    label = it.name,
                    type = EditDetailsCatalogOptionType.EVENT,
                    subtitle = "${it.startDate} to ${it.endDate}"
                )
            }

        val specialStatuses = catalog.specialSpecies
            .filter { it.verificationStatus.isScoringVerified() }
            .filter { it.species.equals(normalizedSpecies, ignoreCase = true) }
            .map {
                EditDetailsCatalogOption(
                    id = it.category,
                    label = it.category.replace('_', ' '),
                    type = EditDetailsCatalogOptionType.SPECIAL_STATUS,
                    subtitle = if (it.isTradable) "Tradable" else "Not tradable"
                )
            }

        val regionals = catalog.regionals
            .filter { it.verificationStatus.isScoringVerified() }
            .filter { it.species.equals(normalizedSpecies, ignoreCase = true) }
            .map {
                EditDetailsCatalogOption(
                    id = it.species,
                    label = it.region,
                    type = EditDetailsCatalogOptionType.REGIONAL,
                    subtitle = if (it.isCurrentlyLocked) "Region locked" else "Regional"
                )
            }

        return EditDetailsCatalogOptions(
            costumes = costumes,
            events = events,
            specialStatuses = specialStatuses,
            regionals = regionals
        )
    }

    fun preview(
        basePokemon: Pokemon,
        editedDetails: EditedScanDetails,
        catalog: CollectionCatalog,
        engine: CollectionScoreEngine = CollectionScoreEngine(),
        gson: Gson = Gson(),
        currentDate: Date = Date()
    ): EditedScanScorePreview {
        val safeEdits = sanitizeSelections(basePokemon, editedDetails, catalog)
        val species = safeEdits.species ?: basePokemon.name
        val features = VisualFeatures(
            isShiny = safeEdits.isShiny ?: ("SHINY" in basePokemon.tags),
            isShadow = safeEdits.isShadow ?: ("SHADOW" in basePokemon.tags),
            isPurified = safeEdits.isPurified ?: ("PURIFIED" in basePokemon.tags),
            isLucky = safeEdits.isLucky ?: ("LUCKY" in basePokemon.tags),
            hasCostume = safeEdits.costumeId != null || ("COSTUME" in basePokemon.tags),
            hasSpecialForm = safeEdits.formId != null || ("FORM" in basePokemon.tags),
            hasLocationCard = safeEdits.hasLocationCard ?: ("LOCATION" in basePokemon.tags),
            confidence = 1.0f
        )
        val pokemonData = PokemonData(
            cp = basePokemon.cp.takeIf { it > 0 },
            hp = basePokemon.hp,
            maxHp = basePokemon.hp,
            name = species,
            realName = species,
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            gender = null,
            stardust = null,
            caughtDate = safeEdits.caughtDate ?: parseDate(basePokemon.caughtDate),
            rawOcrText = ""
        )
        val result = engine.calculate(
            pokemonData = pokemonData,
            features = features,
            catalog = catalog,
            editedDetails = safeEdits,
            currentDate = currentDate
        )
        val pokemon = pokemonFromScanExtras(
            name = result.detectedSpecies,
            cp = basePokemon.cp,
            hp = basePokemon.hp,
            ivText = basePokemon.ivText,
            ivSolveMode = basePokemon.ivSolveMode,
            ivSignalsUsed = basePokemon.ivSignalsUsed,
            ivCandidateCount = basePokemon.ivCandidateCount,
            ivLevelMin = basePokemon.ivLevelMin,
            ivLevelMax = basePokemon.ivLevelMax,
            hasArcSignal = basePokemon.hasArcSignal,
            pvpSummary = basePokemon.pvpSummary,
            score = result.totalScore,
            tier = result.tier.name,
            isShiny = features.isShiny,
            isLucky = features.isLucky,
            hasCostume = features.hasCostume,
            hasSpecialForm = features.hasSpecialForm,
            isShadow = features.isShadow,
            isPurified = features.isPurified,
            hasLocationCard = features.hasLocationCard,
            dateText = safeEdits.caughtDate?.let(::formatDateForDisplay) ?: basePokemon.caughtDate,
            analysisOverride = analysisFromResult(result),
            collectionResult = result,
            collectionAxes = result.axes,
            isEdited = true
        ).copy(
            id = basePokemon.id,
            sourceId = basePokemon.sourceId,
            telemetryUploadId = basePokemon.telemetryUploadId
        )
        return EditedScanScorePreview(
            pokemon = pokemon,
            pokemonData = pokemonData,
            visualFeatures = features,
            editedDetails = safeEdits,
            editedDetailsJson = gson.toJson(safeEdits),
            axisBreakdownJson = gson.toJson(result.axes),
            result = result
        )
    }

    private fun sanitizeSelections(
        basePokemon: Pokemon,
        editedDetails: EditedScanDetails,
        catalog: CollectionCatalog
    ): EditedScanDetails {
        val species = editedDetails.species?.trim()?.ifBlank { null } ?: basePokemon.name
        val options = catalogOptionsFor(catalog, species)
        return editedDetails.copy(
            species = species,
            costumeId = editedDetails.costumeId?.takeIf { id -> options.costumes.any { it.id == id } },
            eventId = editedDetails.eventId?.takeIf { id -> options.events.any { it.id == id } },
            regionalRecordId = editedDetails.regionalRecordId?.takeIf { id -> options.regionals.any { it.id == id } },
            specialStatusOverride = editedDetails.specialStatusOverride
                ?.takeIf { id -> options.specialStatuses.any { it.id == id } }
        )
    }

    private fun analysisFromResult(
        result: com.pokerarity.scanner.data.model.CollectionResult
    ): List<RarityAnalysisItem> {
        val positive = result.axes.flatMap { axis ->
            axis.details.map { detail ->
                RarityAnalysisItem(axis.axis.label, detail, axis.score > 0)
            }
        }
        val warnings = listOfNotNull(
            result.eventDateMismatchMessage?.let {
                RarityAnalysisItem("Event date mismatch", it, false)
            }
        )
        return (warnings + positive).ifEmpty {
            listOf(RarityAnalysisItem("No verified collection score signal", null, false))
        }
    }

    private fun VerificationStatus.isScoringVerified(): Boolean =
        this == VerificationStatus.VERIFIED_OFFICIAL || this == VerificationStatus.VERIFIED_COMMUNITY

    private fun parseDate(value: String): Date? {
        if (value.isBlank() || value.equals("Unknown", ignoreCase = true)) return null
        val formats = listOf("MMM d, yyyy", "MMM dd, yyyy", "yyyy-MM-dd")
        return formats.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value.trim())
            }.getOrNull()
        }
    }

    private fun formatDateForDisplay(date: Date): String =
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(date)
}

data class EditedScanScorePreview(
    val pokemon: Pokemon,
    val pokemonData: PokemonData,
    val visualFeatures: VisualFeatures,
    val editedDetails: EditedScanDetails,
    val editedDetailsJson: String,
    val axisBreakdownJson: String,
    val result: com.pokerarity.scanner.data.model.CollectionResult
)
