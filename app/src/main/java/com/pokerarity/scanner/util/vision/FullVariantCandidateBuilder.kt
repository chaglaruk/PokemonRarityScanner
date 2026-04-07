package com.pokerarity.scanner.util.vision

import com.pokerarity.scanner.data.model.AuthoritativeVariantEntry
import com.pokerarity.scanner.data.model.FullVariantCandidate
import com.pokerarity.scanner.data.model.GlobalRarityLegacyEntry
import com.pokerarity.scanner.data.model.PokemonData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object FullVariantCandidateBuilder {
    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private const val MAX_SECONDARY_NON_BASE_GAP = 0.14f
    private const val DATE_RESCUE_MIN_CLASSIFIER_CONFIDENCE = 0.40f
    private data class MatchingAppearance(
        val eventLabel: String?,
        val start: String?,
        val end: String?
    )

    fun build(
        pokemon: PokemonData,
        finalSpecies: String,
        globalMatch: VariantPrototypeClassifier.MatchResult?,
        speciesMatch: VariantPrototypeClassifier.MatchResult?,
        authoritativeBySpecies: Map<String, List<AuthoritativeVariantEntry>>,
        globalLegacyBySpecies: Map<String, List<GlobalRarityLegacyEntry>> = emptyMap()
    ): List<FullVariantCandidate> {
        val candidates = mutableListOf<FullVariantCandidate>()
        val authoritativeForSpecies = authoritativeBySpecies[finalSpecies].orEmpty()
        val authoritativeBySprite = authoritativeForSpecies.associateBy { it.spriteKey }

        val classifierCandidates = buildList {
            globalMatch
                ?.toCandidate(
                    source = "classifier_global",
                    pokemon = pokemon,
                    authoritativeBySprite = authoritativeBySprite
                )
                ?.let(::add)
            speciesMatch
                ?.toCandidate(
                    source = "classifier_species",
                    pokemon = pokemon,
                    authoritativeBySprite = authoritativeBySprite
                )
                ?.let(::add)
            speciesMatch
                ?.toSecondaryNonBaseCandidate(
                    pokemon = pokemon,
                    authoritativeBySprite = authoritativeBySprite
                )
                ?.let(::add)
        }
        candidates += classifierCandidates
        candidates += buildAuthoritativeRemapCandidates(
            finalSpecies = finalSpecies,
            classifierCandidates = classifierCandidates,
            authoritativeForSpecies = authoritativeForSpecies,
            caughtDate = pokemon.caughtDate
        )

        if (pokemon.caughtDate != null && hasStrongNonBaseClassifierSignal(candidates)) {
            authoritativeForSpecies
                .filter { it.isCostumeLike }
                .mapNotNull { entry ->
                    resolveMatchingAppearance(entry, pokemon.caughtDate)?.let { appearance ->
                        entry to appearance
                    }
                }
                .forEach { (entry, appearance) ->
                    candidates += FullVariantCandidate(
                        species = entry.species,
                        spriteKey = entry.spriteKey,
                        variantClass = resolveVariantClass(entry, entry.variantClass),
                        isShiny = entry.isShiny,
                        isCostumeLike = resolveIsCostumeLike(entry, entry.isCostumeLike),
                        eventLabel = appearance.eventLabel ?: entry.eventLabel,
                        eventStart = appearance.start ?: entry.eventStart,
                        eventEnd = appearance.end ?: entry.eventEnd,
                        matchScore = 0f,
                        rescueKind = "species_date_match",
                        source = "authoritative_species_date",
                        classifierConfidence = 1f
                    )
                }
        }

        buildActiveLiveSpeciesEventCandidate(finalSpecies, globalLegacyBySpecies, classifierCandidates)
            ?.let { candidates += it }

        return candidates.distinctBy { "${it.source}|${it.spriteKey}|${it.species}|${it.eventLabel.orEmpty()}" }
    }

    private fun buildAuthoritativeRemapCandidates(
        finalSpecies: String,
        classifierCandidates: List<FullVariantCandidate>,
        authoritativeForSpecies: List<AuthoritativeVariantEntry>,
        caughtDate: Date?
    ): List<FullVariantCandidate> {
        return classifierCandidates
            .asSequence()
            .filter { it.source.startsWith("classifier") }
            .filter { it.variantClass != "base" || it.isCostumeLike }
            .mapNotNull { classifierCandidate ->
                val authoritative = resolveAuthoritativeTokenMatch(classifierCandidate, authoritativeForSpecies)
                    ?: return@mapNotNull null
                if (isImpossibleForCaughtDate(caughtDate, authoritative)) return@mapNotNull null
                val sameSpecies = classifierCandidate.species.equals(finalSpecies, ignoreCase = true)
                val remapSource = when {
                    !sameSpecies -> "classifier_family_authoritative_remap"
                    classifierCandidate.source == "classifier_global" -> "classifier_global_authoritative_remap"
                    classifierCandidate.source == "classifier_species_secondary_non_base" -> "classifier_species_secondary_authoritative_remap"
                    else -> "classifier_species_authoritative_remap"
                }
                FullVariantCandidate(
                    species = authoritative.species,
                    spriteKey = authoritative.spriteKey,
                    variantClass = resolveVariantClass(authoritative, classifierCandidate.variantClass),
                    isShiny = authoritative.isShiny,
                    isCostumeLike = resolveIsCostumeLike(authoritative, classifierCandidate.isCostumeLike),
                    eventLabel = authoritative.eventLabel,
                    eventStart = authoritative.eventStart,
                    eventEnd = authoritative.eventEnd,
                    matchScore = classifierCandidate.matchScore,
                    rescueKind = if (sameSpecies) null else "family_variant_token_remap",
                    source = remapSource,
                    classifierConfidence = classifierCandidate.classifierConfidence
                )
            }
            .toList()
    }

    private fun resolveAuthoritativeTokenMatch(
        classifierCandidate: FullVariantCandidate,
        authoritativeForSpecies: List<AuthoritativeVariantEntry>
    ): AuthoritativeVariantEntry? {
        if (authoritativeForSpecies.isEmpty()) return null
        authoritativeForSpecies.firstOrNull {
            it.spriteKey.equals(classifierCandidate.spriteKey, ignoreCase = true)
        }?.let { return it }

        val candidateTokens = extractVariantTokenVariants(classifierCandidate.spriteKey)
        if (candidateTokens.isEmpty()) return null

        return authoritativeForSpecies.firstOrNull { entry ->
            entry.isShiny == classifierCandidate.isShiny &&
                extractVariantTokenVariants(entry.spriteKey, entry.variantId).any(candidateTokens::contains)
        } ?: authoritativeForSpecies.firstOrNull { entry ->
            // Fallback: relax shiny constraint so shiny costume entries aren't missed
            // when classifier fires with isShiny=false but sprite key has _shiny suffix.
            extractVariantTokenVariants(entry.spriteKey, entry.variantId).any(candidateTokens::contains)
        }
    }

    private fun extractVariantTokenVariants(
        spriteKey: String,
        explicitVariantId: String? = null
    ): Set<String> {
        val normalized = spriteKey.removeSuffix("_shiny")
        val parts = normalized.split('_')
        val token = explicitVariantId?.takeIf { it.isNotBlank() }
            ?: parts.drop(2).joinToString("_").takeIf { it.isNotBlank() }
            ?: return emptySet()
        return buildSet {
            add(token.uppercase(Locale.US))
            if (token.startsWith("C") && token.length > 1) {
                add(token.drop(1).uppercase(Locale.US))
            }
        }
    }

    private fun hasStrongNonBaseClassifierSignal(candidates: List<FullVariantCandidate>): Boolean {
        return candidates.any { candidate ->
            candidate.source.startsWith("classifier") &&
                candidate.variantClass != "base" &&
                candidate.classifierConfidence >= DATE_RESCUE_MIN_CLASSIFIER_CONFIDENCE
        }
    }

    private fun buildActiveLiveSpeciesEventCandidate(
        finalSpecies: String,
        globalLegacyBySpecies: Map<String, List<GlobalRarityLegacyEntry>>,
        classifierCandidates: List<FullVariantCandidate>
    ): FullVariantCandidate? {
        if (!hasSameSpeciesNonBaseClassifierSignal(classifierCandidates, finalSpecies)) {
            return null
        }
        val support = globalLegacyBySpecies[finalSpecies].orEmpty()
            .firstOrNull { !it.activeEventLabel.isNullOrBlank() }
            ?: return null

        return FullVariantCandidate(
            species = finalSpecies,
            spriteKey = support.spriteKey,
            variantClass = "costume",
            isShiny = false,
            isCostumeLike = true,
            eventLabel = support.activeEventLabel,
            eventStart = support.activeEventStart,
            eventEnd = support.activeEventEnd,
            matchScore = 0.40f,
            rescueKind = "active_species_live_event",
            source = "authoritative_live_species_event",
            classifierConfidence = 1f
        )
    }

    private fun hasSameSpeciesNonBaseClassifierSignal(
        candidates: List<FullVariantCandidate>,
        finalSpecies: String
    ): Boolean {
        return candidates.any { candidate ->
            candidate.source.startsWith("classifier") &&
                candidate.species.equals(finalSpecies, ignoreCase = true) &&
                candidate.variantClass != "base" &&
                candidate.classifierConfidence >= DATE_RESCUE_MIN_CLASSIFIER_CONFIDENCE
        }
    }

    private fun VariantPrototypeClassifier.MatchResult.toCandidate(
        source: String,
        pokemon: PokemonData,
        authoritativeBySprite: Map<String, AuthoritativeVariantEntry>
    ): FullVariantCandidate? {
        val authoritative = authoritativeBySprite[spriteKey]
        val impossibleForDate =
            if (authoritative != null) {
                isImpossibleForCaughtDate(pokemon.caughtDate, authoritative)
            } else {
                isImpossibleForCaughtDate(pokemon.caughtDate, null, null, spriteKey)
            }
        if (impossibleForDate) {
            return null
        }

        return FullVariantCandidate(
            species = species,
            spriteKey = spriteKey,
            variantClass = resolveVariantClass(authoritative, variantType),
            isShiny = authoritative?.isShiny ?: isShiny,
            isCostumeLike = resolveIsCostumeLike(authoritative, isCostumeLike),
            eventLabel = authoritative?.eventLabel,
            eventStart = authoritative?.eventStart,
            eventEnd = authoritative?.eventEnd,
            matchScore = score,
            rescueKind = rescueKind,
            source = source,
            classifierConfidence = confidence
        )
    }

    private fun VariantPrototypeClassifier.MatchResult.toSecondaryNonBaseCandidate(
        pokemon: PokemonData,
        authoritativeBySprite: Map<String, AuthoritativeVariantEntry>
    ): FullVariantCandidate? {
        if (variantType != "base") return null
        val nonBaseSpriteKey = bestNonBaseSpriteKey ?: return null
        val nonBaseScore = bestNonBaseScore ?: return null
        val nonBaseSpecies = bestNonBaseSpecies ?: species
        val nonBaseVariantType = bestNonBaseVariantType ?: return null
        if (nonBaseSpecies != species) return null
        if (nonBaseVariantType == "base") return null
        if (nonBaseScore - score > MAX_SECONDARY_NON_BASE_GAP) return null
        val authoritative = authoritativeBySprite[nonBaseSpriteKey]
        val impossibleForDate =
            if (authoritative != null) {
                isImpossibleForCaughtDate(pokemon.caughtDate, authoritative)
            } else {
                isImpossibleForCaughtDate(pokemon.caughtDate, null, null, nonBaseSpriteKey)
            }
        if (impossibleForDate) return null

        return FullVariantCandidate(
            species = nonBaseSpecies,
            spriteKey = nonBaseSpriteKey,
            variantClass = resolveVariantClass(authoritative, nonBaseVariantType),
            isShiny = authoritative?.isShiny ?: bestNonBaseIsShiny,
            isCostumeLike = resolveIsCostumeLike(authoritative, bestNonBaseIsCostumeLike),
            eventLabel = authoritative?.eventLabel,
            eventStart = authoritative?.eventStart,
            eventEnd = authoritative?.eventEnd,
            matchScore = nonBaseScore,
            rescueKind = "species_secondary_non_base",
            source = "classifier_species_secondary_non_base",
            classifierConfidence = confidence
        )
    }

    private fun isImpossibleForCaughtDate(
        caughtDate: Date?,
        eventLabel: String?,
        variantLabel: String?,
        spriteKey: String? = null
    ): Boolean {
        if (caughtDate == null) return false
        val hintedYear = extractYearHint(eventLabel, variantLabel, spriteKey) ?: return false
        val calendar = Calendar.getInstance(Locale.US).apply { time = caughtDate }
        val caughtYear = calendar.get(Calendar.YEAR)
        val caughtMonth = calendar.get(Calendar.MONTH)
        return when {
            caughtYear < hintedYear -> true
            caughtYear == hintedYear -> false
            caughtYear == hintedYear + 1 && caughtMonth == Calendar.JANUARY -> false
            else -> true
        }
    }

    private fun isImpossibleForCaughtDate(caughtDate: Date?, authoritative: AuthoritativeVariantEntry?): Boolean {
        if (caughtDate == null || authoritative == null || !authoritative.isCostumeLike) return false
        if (authoritative.historicalEvents.isNotEmpty()) {
            return resolveMatchingAppearance(authoritative, caughtDate) == null
        }
        val start = authoritative.eventStart?.let(::parseIsoDate)
        val end = authoritative.eventEnd?.let(::parseIsoDate)
        if (start != null && caughtDate.before(start)) return true
        if (end != null && caughtDate.after(end)) return true
        return isImpossibleForCaughtDate(caughtDate, authoritative.eventLabel, authoritative.variantLabel, authoritative.spriteKey)
    }

    private fun extractYearHint(vararg values: String?): Int? {
        values.forEach { value ->
            if (value.isNullOrBlank()) return@forEach
            val match = Regex("""(?:^|[^0-9])(20\d{2})(?:[^0-9]|$)""").find(value) ?: return@forEach
            return match.groupValues.getOrNull(1)?.toIntOrNull()
        }
        return null
    }

    private fun resolveMatchingAppearance(
        entry: AuthoritativeVariantEntry,
        caughtDate: Date
    ): MatchingAppearance? {
        return buildAppearances(entry)
            .mapNotNull { appearance ->
                val start = appearance.start?.let(::parseIsoDate) ?: return@mapNotNull null
                val end = appearance.end?.let(::parseIsoDate) ?: start
                if (caughtDate.before(start) || caughtDate.after(end)) return@mapNotNull null
                val spanDays = ((end.time - start.time) / 86_400_000L).coerceAtLeast(0L)
                appearance to spanDays
            }
            .minByOrNull { it.second }
            ?.first
    }

    private fun buildAppearances(entry: AuthoritativeVariantEntry): List<MatchingAppearance> {
        val appearances = entry.historicalEvents.map {
            MatchingAppearance(
                eventLabel = it.eventLabel,
                start = it.startDate,
                end = it.endDate
            )
        }.toMutableList()
        if (!entry.eventStart.isNullOrBlank() || !entry.eventEnd.isNullOrBlank()) {
            appearances += MatchingAppearance(
                eventLabel = entry.eventLabel,
                start = entry.eventStart,
                end = entry.eventEnd
            )
        }
        return appearances
    }

    private fun resolveVariantClass(authoritative: AuthoritativeVariantEntry?, fallback: String): String {
        if (authoritative == null) return fallback
        return when {
            authoritative.isCostumeLike || authoritative.variantClass == "costume" -> "costume"
            authoritative.variantClass == "form" -> "form"
            else -> fallback
        }
    }

    private fun resolveIsCostumeLike(authoritative: AuthoritativeVariantEntry?, fallback: Boolean): Boolean {
        return authoritative?.let { it.isCostumeLike || it.variantClass == "costume" } ?: fallback
    }

    private fun parseIsoDate(value: String): Date? = runCatching { isoDate.parse(value) }.getOrNull()
}
