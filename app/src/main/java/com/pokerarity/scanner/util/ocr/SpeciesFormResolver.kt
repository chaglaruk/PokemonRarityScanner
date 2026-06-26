package com.pokerarity.scanner.util.ocr

import android.content.Context
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.AuthoritativeVariantDbLoader
import com.pokerarity.scanner.data.repository.PokemonFamilyRegistry
import com.pokerarity.scanner.data.repository.PokemonMoveRegistry
import com.pokerarity.scanner.data.repository.RarityCalculator

data class SpeciesFormResolution(
    val species: String?,
    val form: String?,
    val confidence: Float,
    val reasons: List<String>,
    val alternatives: List<SpeciesCandidateDiagnostic>,
    val trace: SpeciesResolverTrace
)

data class SpeciesResolverTrace(
    val displayNameCandidates: List<DisplayNameCandidateDiagnostic> = emptyList(),
    val canonicalCandidates: List<SpeciesCandidateDiagnostic> = emptyList(),
    val formCandidates: List<FormCandidateDiagnostic> = emptyList(),
    val winningSpecies: String? = null,
    val winningForm: String? = null,
    val confidence: Float = 0f,
    val winnerReason: String? = null,
    val loserReasons: List<String> = emptyList(),
    val evidenceUsed: List<String> = emptyList(),
    val evidenceMissing: List<String> = emptyList(),
    val fallbackPath: String = "resolver_trace_only"
)

data class DisplayNameCandidateDiagnostic(
    val field: String,
    val rawText: String?,
    val normalizedText: String?,
    val parsedSpecies: String?,
    val score: Float,
    val status: String,
    val source: String
)

data class SpeciesCandidateDiagnostic(
    val species: String,
    val form: String? = null,
    val score: Float,
    val winner: Boolean,
    val reasons: List<String>,
    val loserReason: String? = null
)

data class FormCandidateDiagnostic(
    val species: String,
    val form: String,
    val score: Float,
    val source: String,
    val reason: String
)

class SpeciesFormResolver(
    private val context: Context,
    private val rarityCalculator: RarityCalculator? = null,
    private val textParser: TextParser = TextParser(context)
) {

    fun resolve(
        pokemon: PokemonData,
        fieldCandidates: List<FieldCandidateDiagnostic> = emptyList()
    ): SpeciesFormResolution {
        val rawFields = rawFieldMap(pokemon.rawOcrText)
        val displayCandidates = displayCandidates(rawFields, fieldCandidates)
        val scores = linkedMapOf<String, CandidateScore>()

        displayCandidates
            .filter { it.status == "found" && !it.rawText.isNullOrBlank() }
            .forEach { display ->
                val raw = display.rawText.orEmpty()
                val sourceWeight = sourceWeight(display.field) * display.score.coerceIn(0.20f, 1.0f)
                val strong = textParser.parseStrongSpeciesName(raw)
                if (strong != null) {
                    val exact = normalize(raw) == normalize(strong)
                    addScore(
                        scores = scores,
                        species = strong,
                        score = if (exact) 1.0f * sourceWeight else 0.90f * sourceWeight,
                        reason = if (exact) "exact_name_match:${display.field}" else "strong_name_match:${display.field}"
                    )
                }
                textParser.rankNameCandidates(raw, limit = 5).forEach { ranked ->
                    addScore(
                        scores = scores,
                        species = ranked.name,
                        score = (ranked.score.toFloat() * sourceWeight).coerceIn(0f, 1f),
                        reason = "fuzzy_name_match:${display.field}"
                    )
                }
            }

        val candySpecies = canonicalCandySpecies(pokemon.candyName ?: rawFields["Candy"])
        val candyFamily = PokemonFamilyRegistry.getFamilyMembers(context, candySpecies)
        if (!candySpecies.isNullOrBlank()) {
            scores.values.forEach { score ->
                if (score.species.equals(candySpecies, ignoreCase = true)) {
                    score.bonus = maxOf(score.bonus, 0.20f)
                    score.reasons += "candy_family"
                } else if (PokemonFamilyRegistry.isSameFamily(context, score.species, candySpecies)) {
                    score.bonus = maxOf(score.bonus, if (score.species.equals(candySpecies, true)) 0.20f else 0.12f)
                    score.reasons += "candy_family"
                }
            }
            if (PokemonFamilyRegistry.familySize(context, candySpecies) == 1) {
                addScore(scores, candySpecies, 0.62f, "unique_candy_species")
            } else if (scores.isNotEmpty() && candyFamily.any { it.equals(candySpecies, true) }) {
                addScore(scores, candySpecies, 0.42f, "candy_species_hint")
            }
        }

        val moveHint = PokemonMoveRegistry.extractMoveHint(context, rawFields["Bottom"])
        if (moveHint != null) {
            scores.values.forEach { score ->
                if (PokemonMoveRegistry.moveMatchScore(context, score.species, moveHint) >= 1.0) {
                    score.bonus = maxOf(score.bonus, score.bonus + 0.08f)
                    score.reasons += "move_profile"
                }
            }
        }

        rarityCalculator?.let { calculator ->
            scores.values.forEach { score ->
                val fit = calculator.scoreSpeciesFit(pokemon, score.species)
                if (fit.score >= 0.40) {
                    score.bonus = maxOf(score.bonus, (fit.score * 0.10).toFloat())
                    score.reasons += "cp_hp_profile"
                }
            }
        }

        val ranked = scores.values
            .map { it.toDiagnostic(winner = false) }
            .sortedWith(compareByDescending<SpeciesCandidateDiagnostic> { it.score }.thenBy { it.species })
        val best = ranked.firstOrNull()
        val accepted = best != null && best.score >= 0.50f
        val formCandidates = best?.takeIf { accepted }?.let { findFormCandidates(it.species, displayCandidates) }.orEmpty()
        val winningForm = formCandidates.firstOrNull { it.score >= 0.70f }?.form
        val finalCandidates = ranked.mapIndexed { index, candidate ->
            candidate.copy(
                form = if (index == 0 && accepted) winningForm else candidate.form,
                winner = index == 0 && accepted,
                loserReason = if (index == 0 && accepted) null else "lower_score"
            )
        }
        val evidenceUsed = buildList {
            if (displayCandidates.any { it.status == "found" }) add("name_text")
            if (displayCandidates.any { it.field.equals("NameDynamic", true) && it.status == "found" }) add("name_dynamic")
            if (!candySpecies.isNullOrBlank()) add("candy_family")
            if (moveHint != null) add("move_profile")
            if (winningForm != null) add("form_label")
        }
        val evidenceMissing = buildList {
            if (displayCandidates.none { it.status == "found" }) add("usable_name_text")
            if (candySpecies.isNullOrBlank()) add("candy_family")
            if (moveHint == null) add("move_profile")
            if (winningForm == null) add("form_label")
        }
        val trace = SpeciesResolverTrace(
            displayNameCandidates = displayCandidates,
            canonicalCandidates = finalCandidates,
            formCandidates = formCandidates,
            winningSpecies = best?.species?.takeIf { accepted },
            winningForm = winningForm,
            confidence = best?.score?.takeIf { accepted } ?: 0f,
            winnerReason = best?.reasons?.firstOrNull()?.takeIf { accepted },
            loserReasons = finalCandidates.filterNot { it.winner }.map { "${it.species}:lower_score" },
            evidenceUsed = evidenceUsed,
            evidenceMissing = evidenceMissing,
            fallbackPath = if (accepted) "resolver_candidate_selected" else "legacy_refiner_fallback"
        )

        return SpeciesFormResolution(
            species = trace.winningSpecies,
            form = winningForm,
            confidence = trace.confidence,
            reasons = best?.reasons.orEmpty(),
            alternatives = finalCandidates,
            trace = trace
        )
    }

    private fun displayCandidates(
        rawFields: Map<String, String>,
        fieldCandidates: List<FieldCandidateDiagnostic>
    ): List<DisplayNameCandidateDiagnostic> {
        val result = mutableListOf<DisplayNameCandidateDiagnostic>()
        listOf("Name", "NameDynamic", "NameHC").forEach { field ->
            result += displayCandidate(
                field = field,
                source = "raw_key",
                raw = rawFields[field],
                score = 1f
            )
        }
        fieldCandidates
            .filter { it.field in setOf("Name", "NameDynamic", "NameHC") }
            .forEach { candidate ->
                val raw = candidate.rawText ?: candidate.normalizedText ?: candidate.parsedValue ?: candidate.selectedValue
                result += displayCandidate(
                    field = candidate.field,
                    source = candidate.source,
                    raw = raw,
                    score = candidate.candidateScore ?: if (candidate.winner) 0.85f else 0.55f
                )
            }
        return result.distinctBy { "${it.field}:${it.source}:${it.rawText}" }
    }

    private fun displayCandidate(field: String, source: String, raw: String?, score: Float): DisplayNameCandidateDiagnostic {
        val normalized = raw?.trim()?.takeUnless(::isMarkerOrNonName)?.let(::normalize)
        val parsed = raw?.takeIf { normalized != null }?.let {
            textParser.parseStrongSpeciesName(it) ?: textParser.rankNameCandidates(it, limit = 1).firstOrNull()?.name
        }
        return DisplayNameCandidateDiagnostic(
            field = field,
            rawText = raw,
            normalizedText = normalized,
            parsedSpecies = parsed,
            score = score.coerceIn(0f, 1f),
            status = if (normalized != null && parsed != null) "found" else "missing",
            source = source
        )
    }

    private fun findFormCandidates(
        species: String,
        displayCandidates: List<DisplayNameCandidateDiagnostic>
    ): List<FormCandidateDiagnostic> {
        val displayText = displayCandidates.joinToString(" ") { it.rawText.orEmpty() }
        val normalizedDisplay = normalize(displayText)
        if (normalizedDisplay.isBlank()) return emptyList()
        return runCatching {
            AuthoritativeVariantDbLoader.load(context).entries
                .asSequence()
                .filter { it.species.equals(species, ignoreCase = true) }
                .flatMap { entry ->
                    sequenceOf(entry.gameMasterFormName, entry.variantLabel, entry.eventLabel)
                        .plus(entry.aliases.asSequence())
                        .filterNotNull()
                        .filter { looksLikeFormLabel(it) }
                        .map { label -> entry to label }
                }
                .mapNotNull { (entry, label) ->
                    val normalizedLabel = normalize(label)
                    if (normalizedLabel.length < 4 || !normalizedDisplay.contains(normalizedLabel)) return@mapNotNull null
                    FormCandidateDiagnostic(
                        species = entry.species,
                        form = label,
                        score = 0.76f,
                        source = "authoritative_variant_db",
                        reason = "owned_form_label_match"
                    )
                }
                .distinctBy { normalize(it.form) }
                .take(5)
                .toList()
        }.getOrDefault(emptyList())
    }

    private fun looksLikeFormLabel(label: String): Boolean {
        val normalized = normalize(label)
        return listOf("alolan", "galarian", "hisuian", "paldean", "origin", "altered", "form")
            .any { normalized.contains(it) } &&
            !normalized.contains("costume")
    }

    private fun canonicalCandySpecies(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return textParser.parseCandyNameLoose(value) ?: textParser.parseName(value.replace("Candy", "", ignoreCase = true))
    }

    private fun addScore(
        scores: MutableMap<String, CandidateScore>,
        species: String?,
        score: Float,
        reason: String
    ) {
        val clean = species?.trim()?.takeUnless { it.equals("Unknown", ignoreCase = true) } ?: return
        val key = clean.lowercase()
        val candidate = scores.getOrPut(key) { CandidateScore(clean) }
        candidate.score = maxOf(candidate.score, score.coerceIn(0f, 1f))
        candidate.hits += 1
        candidate.reasons += reason
    }

    private fun rawFieldMap(rawOcrText: String): Map<String, String> =
        rawOcrText.split("|").mapNotNull { part ->
            val separator = part.indexOf(':')
            if (separator <= 0) null else part.substring(0, separator) to part.substring(separator + 1)
        }.toMap()

    private fun sourceWeight(field: String): Float =
        when (field) {
            "Name" -> 1.0f
            "NameDynamic" -> 0.96f
            "NameHC" -> 0.90f
            else -> 0.75f
        }

    private fun isMarkerOrNonName(value: String): Boolean {
        val trimmed = value.trim()
        val compact = normalize(trimmed)
        if (compact.isBlank()) return true
        if (compact in setOf("missing", "notrun", "skipped", "rawtext", "present", "unknown")) return true
        if (compact.all(Char::isDigit)) return true
        if (compact.startsWith("cp") && compact.any(Char::isDigit)) return true
        if (compact.startsWith("hp") && compact.any(Char::isDigit)) return true
        if (trimmed.contains("/") && trimmed.any(Char::isDigit)) return true
        if (Regex("""\b(201[6-9]|202[0-9])\b""").containsMatchIn(trimmed)) return true
        if (compact.contains("stardust") || compact.contains("candy") || compact.contains("powerup")) return true
        return false
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]"), "")

    private data class CandidateScore(
        val species: String,
        var score: Float = 0f,
        var bonus: Float = 0f,
        var hits: Int = 0,
        val reasons: MutableList<String> = mutableListOf()
    ) {
        fun toDiagnostic(winner: Boolean): SpeciesCandidateDiagnostic {
            val agreementBoost = if (hits >= 2) 0.06f else 0f
            val total = (score + bonus + agreementBoost).coerceIn(0f, 1f)
            return SpeciesCandidateDiagnostic(
                species = species,
                score = total,
                winner = winner,
                reasons = reasons.distinct()
            )
        }
    }
}
