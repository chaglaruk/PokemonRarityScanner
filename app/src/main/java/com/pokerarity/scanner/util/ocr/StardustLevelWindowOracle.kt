package com.pokerarity.scanner.util.ocr

/**
 * Typed modifier context for POWER UP stardust interpretation (Phase 3A).
 *
 * [established] is set ONLY when the caller holds independently trustworthy modifier
 * provenance under the existing contracts (never a weak visual classifier output); a
 * weak or absent signal must stay [UNKNOWN], and the oracle then preserves the cost
 * ambiguity across every supported modifier instead of assuming one. [provenanceCode]
 * is a bounded, non-sensitive reason code required whenever [established] is present.
 */
data class StardustModifierContext(
    val established: EstablishedStardustModifiers?,
    val provenanceCode: String?
) {
    /** Cost-relevant modifier facts the caller has independently established. */
    data class EstablishedStardustModifiers(
        val lucky: Boolean = false,
        val purified: Boolean = false,
        val shadow: Boolean = false
    )

    companion object {
        /** No trustworthy modifier provenance: interpret costs under every supported modifier. */
        val UNKNOWN = StardustModifierContext(established = null, provenanceCode = null)
    }
}

/**
 * Phase 3A typed level evidence for one anchored POWER UP stardust field (plan section
 * 7.2). Stardust alone establishes a legal discrete level window, never an exact level;
 * invalid dust never becomes a guessed level; missing, unreadable, conflicting and
 * unsupported states stay explicit. The evidence carries bounded codes/values only —
 * never raw OCR, paths or secrets — and never derives from inventory stardust.
 */
sealed interface StardustLevelEvidence {
    /** Bounded, non-sensitive reason/provenance codes. */
    val reasonCodes: List<String>

    /**
     * Honest legal level evidence: the discrete legal levels (intersected with the
     * recognition snapshot's level domain) plus their bounding range, with the cost
     * modifier interpretations that produced the window.
     */
    data class Levels(
        /** Sorted distinct legal levels; every element is a legal half-level of the domain. */
        val levels: List<Double>,
        /** Convenience bounds of [levels]; non-null iff [levels] is non-empty. */
        val minLevel: Double?,
        val maxLevel: Double?,
        /** Cost modifiers that contributed at least one level (sorted by declaration). */
        val modifiersUsed: List<PowerUpCostModifier>,
        /** Bounded tier/modifier interpretations, e.g. "tier0_normal". */
        val interpretations: List<String>,
        override val reasonCodes: List<String>
    ) : StardustLevelEvidence {
        /** True when more than one cost modifier interpretation survives. */
        val modifierAmbiguous: Boolean get() = modifiersUsed.size > 1
    }

    /** A single value was read but is no displayed power-up cost under any supported modifier. */
    data class Invalid(
        val observedCost: Int,
        override val reasonCodes: List<String>
    ) : StardustLevelEvidence

    /** Credible anchored observations disagree; never silently resolved to a window. */
    data class Conflict(
        val candidateCount: Int,
        override val reasonCodes: List<String>
    ) : StardustLevelEvidence

    /** The anchored field exists but its value could not be read. */
    data class Unreadable(override val reasonCodes: List<String>) : StardustLevelEvidence

    /** No anchored POWER UP field was observed; distinct from [Unreadable]. */
    data class Missing(override val reasonCodes: List<String>) : StardustLevelEvidence

    /** Evidence exists but this component cannot interpret it under supported mechanics. */
    data class Unsupported(override val reasonCodes: List<String>) : StardustLevelEvidence
}

/**
 * Converts one typed anchored POWER UP stardust read into typed legal level evidence.
 *
 * The legal level domain MUST come from the recognition snapshot
 * ([RecognitionSnapshot.cpMultipliers] keys); this component owns no CPM table. The
 * input is the anchored field read — structurally, an inventory stardust balance can
 * never enter here because no inventory value is a [FieldRead] of the POWER UP row.
 */
internal object StardustLevelWindowOracle {

    private const val PROVENANCE_ANCHORED_POWER_UP_ROW = "anchored_power_up_row"
    private const val PROVENANCE_MODIFIER_UNKNOWN = "cost_modifier_unknown"

    fun evaluate(
        powerUpCostRead: FieldRead<Int>,
        modifierContext: StardustModifierContext,
        levelDomain: Set<Double>
    ): StardustLevelEvidence = when (powerUpCostRead.status) {
        FieldReadStatus.READ ->
            powerUpCostRead.value
                ?.let { interpretRead(it, modifierContext, levelDomain) }
                ?: StardustLevelEvidence.Unreadable(readCodes(powerUpCostRead, "read_state_without_value"))
        FieldReadStatus.MISSING_NOT_VISIBLE ->
            StardustLevelEvidence.Missing(readCodes(powerUpCostRead))
        FieldReadStatus.VISIBLE_UNREADABLE ->
            StardustLevelEvidence.Unreadable(readCodes(powerUpCostRead))
        FieldReadStatus.CONFLICT ->
            StardustLevelEvidence.Conflict(powerUpCostRead.candidateCount, readCodes(powerUpCostRead))
        FieldReadStatus.UNSUPPORTED ->
            StardustLevelEvidence.Unsupported(readCodes(powerUpCostRead))
    }

    private fun interpretRead(
        cost: Int,
        modifierContext: StardustModifierContext,
        levelDomain: Set<Double>
    ): StardustLevelEvidence {
        val established = modifierContext.established
        val allowed = allowedModifiers(established)
        val levels = if (allowed.isEmpty()) {
            emptyList()
        } else {
            levelDomain.filter { PowerUpStardustRules.costMatches(cost, it, allowed) }.sorted()
        }
        // The combined Lucky+Shadow cost state (0.6x) is not a modeled mechanic; refusing
        // it is honest, while guessing the nearest modeled multiplier is not.
        return when {
            established?.lucky == true && established.shadow ->
                StardustLevelEvidence.Unsupported(
                    listOf(PROVENANCE_ANCHORED_POWER_UP_ROW, "lucky_shadow_cost_unsupported"))
            levelDomain.isEmpty() ->
                StardustLevelEvidence.Unsupported(
                    listOf(PROVENANCE_ANCHORED_POWER_UP_ROW, "level_domain_unavailable"))
            allowed.isEmpty() ->
                StardustLevelEvidence.Unsupported(
                    listOf(PROVENANCE_ANCHORED_POWER_UP_ROW, "modifier_context_unsupported"))
            levels.isEmpty() && displayedAnywhere(cost, levelDomain) ->
                // A cost that is a displayed value only under excluded modifiers
                // contradicts the established context (two credible typed observations
                // disagree); a cost that is no displayed value anywhere is invalid.
                StardustLevelEvidence.Conflict(
                    1, listOf(PROVENANCE_ANCHORED_POWER_UP_ROW, "cost_conflicts_with_established_modifier"))
            levels.isEmpty() ->
                StardustLevelEvidence.Invalid(
                    cost, listOf(PROVENANCE_ANCHORED_POWER_UP_ROW, "cost_not_a_displayed_power_up_value"))
            else -> levelWindow(
                cost, allowed, levelDomain, levels, contextCodesOf(established, modifierContext))
        }
    }

    private fun contextCodesOf(
        established: StardustModifierContext.EstablishedStardustModifiers?,
        modifierContext: StardustModifierContext
    ): List<String> = when (established) {
        null -> listOf(PROVENANCE_MODIFIER_UNKNOWN)
        else -> listOfNotNull(
            modifierContext.provenanceCode?.let { "modifier_established_$it" } ?: "modifier_established")
    }

    /** Whether the cost is a displayed value under ANY supported modifier. */
    private fun displayedAnywhere(cost: Int, levelDomain: Set<Double>): Boolean =
        levelDomain.any {
            PowerUpStardustRules.costMatches(cost, it, PowerUpStardustRules.SUPPORTED_MODIFIERS)
        }

    private fun levelWindow(
        cost: Int,
        allowed: Set<PowerUpCostModifier>,
        levelDomain: Set<Double>,
        levels: List<Double>,
        contextCodes: List<String>
    ): StardustLevelEvidence.Levels {
        val used = allowed.filter { modifier ->
            levelDomain.any { PowerUpStardustRules.costMatches(cost, it, setOf(modifier)) }
        }
        val interpretations = used.flatMap { modifier ->
            PowerUpStardustRules.TIER_COSTS.indices
                .filter { tier -> cost in PowerUpStardustRules.displayedCosts(tier, modifier) }
                .map { tier -> "tier${tier}_${modifier.name.lowercase()}" }
        }.sorted()
        return StardustLevelEvidence.Levels(
            levels = levels,
            minLevel = levels.first(),
            maxLevel = levels.last(),
            modifiersUsed = used,
            interpretations = interpretations,
            reasonCodes = listOf(PROVENANCE_ANCHORED_POWER_UP_ROW) + contextCodes
        )
    }

    private fun allowedModifiers(
        established: StardustModifierContext.EstablishedStardustModifiers?
    ): Set<PowerUpCostModifier> {
        if (established == null) return PowerUpStardustRules.SUPPORTED_MODIFIERS
        return buildSet {
            if (!established.lucky && !established.purified && !established.shadow) {
                add(PowerUpCostModifier.NORMAL)
            }
            if (established.lucky && !established.shadow) {
                add(if (established.purified) PowerUpCostModifier.LUCKY_PURIFIED else PowerUpCostModifier.LUCKY)
            }
            if (established.purified && !established.lucky) add(PowerUpCostModifier.PURIFIED)
            if (established.shadow && !established.lucky) add(PowerUpCostModifier.SHADOW)
        }
        // An established context that maps to no modeled modifier yields the empty set;
        // interpretRead then refuses honestly instead of widening back to every modifier.
    }

    private fun readCodes(read: FieldRead<Int>, extra: String? = null): List<String> =
        listOfNotNull(PROVENANCE_ANCHORED_POWER_UP_ROW, extra, read.reasonCode)
}
