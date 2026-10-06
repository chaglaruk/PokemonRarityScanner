package com.pokerarity.scanner.util.ocr

private const val MAX_REASON_CODE_LENGTH = 48
private val BOUNDED_REASON_CODE = Regex("^[a-z0-9_.]{1,$MAX_REASON_CODE_LENGTH}$")

/**
 * Typed modifier context for POWER UP stardust interpretation (Phase 3A).
 *
 * UNKNOWN preserves all modeled modifier interpretations. An established modifier is
 * mutually exclusive and can only be created with a bounded, non-sensitive provenance
 * code. Invalid provenance becomes [Unsupported] and therefore can never narrow level
 * evidence.
 */
sealed interface StardustModifierContext {
    data object Unknown : StardustModifierContext

    data class Established private constructor(
        val modifier: PowerUpCostModifier,
        val provenanceCode: String
    ) : StardustModifierContext {
        companion object {
            internal fun create(
                modifier: PowerUpCostModifier,
                provenanceCode: String?
            ): StardustModifierContext =
                if (provenanceCode != null && BOUNDED_REASON_CODE.matches(provenanceCode)) {
                    Established(modifier, provenanceCode)
                } else {
                    Unsupported("modifier_provenance_invalid")
                }
        }
    }

    data class Unsupported(val reasonCode: String) : StardustModifierContext

    companion object {
        /** No trustworthy modifier provenance: preserve ambiguity across supported modifiers. */
        val UNKNOWN: StardustModifierContext = Unknown

        /** Build an established, mutually exclusive cost state with validated provenance. */
        fun established(
            modifier: PowerUpCostModifier,
            provenanceCode: String?
        ): StardustModifierContext = Established.create(modifier, provenanceCode)
    }
}

/**
 * Phase 3A typed level evidence for one anchored POWER UP stardust field (plan section
 * 7.2). Stardust alone establishes legal underlying/base levels, never an exact level;
 * invalid dust never becomes a guessed level; missing, unreadable, conflicting and
 * unsupported states stay explicit. The evidence carries bounded codes/values only and
 * never derives from inventory stardust.
 */
sealed interface StardustLevelEvidence {
    val reasonCodes: List<String>

    data class Levels(
        /** Sorted distinct underlying/base levels from the recognition snapshot domain. */
        val levels: List<Double>,
        val minLevel: Double?,
        val maxLevel: Double?,
        val modifiersUsed: List<PowerUpCostModifier>,
        val interpretations: List<String>,
        override val reasonCodes: List<String>
    ) : StardustLevelEvidence {
        val modifierAmbiguous: Boolean get() = modifiersUsed.size > 1
        val contiguous: Boolean
            get() = levels.zipWithNext().all { (first, second) -> second - first == 0.5 }
    }

    data class Invalid(
        val observedCost: Int,
        override val reasonCodes: List<String>
    ) : StardustLevelEvidence

    data class Conflict(
        val candidateCount: Int,
        override val reasonCodes: List<String>
    ) : StardustLevelEvidence

    data class Unreadable(override val reasonCodes: List<String>) : StardustLevelEvidence

    data class Missing(override val reasonCodes: List<String>) : StardustLevelEvidence

    data class Unsupported(override val reasonCodes: List<String>) : StardustLevelEvidence
}

/**
 * Converts one typed anchored POWER UP stardust read into typed legal underlying/base
 * level evidence.
 *
 * The legal level domain MUST come from [RecognitionSnapshot.cpMultipliers]. This oracle
 * intentionally does not apply the Best Buddy +1 CP/HP witnessed-level offset: that
 * compatibility behavior belongs to consumers that explicitly reason about CP/HP
 * effective levels, such as the legacy family resolver.
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
        if (modifierContext is StardustModifierContext.Unsupported) {
            return StardustLevelEvidence.Unsupported(
                listOf(
                    PROVENANCE_ANCHORED_POWER_UP_ROW,
                    modifierContext.reasonCode.takeIf(BOUNDED_REASON_CODE::matches)
                        ?: "modifier_context_reason_invalid"
                )
            )
        }
        if (levelDomain.isEmpty()) {
            return StardustLevelEvidence.Unsupported(
                listOf(PROVENANCE_ANCHORED_POWER_UP_ROW, "level_domain_unavailable"))
        }

        val allowed = allowedModifiers(modifierContext)
        val levels = levelDomain
            .filter { PowerUpStardustRules.baseLevelCostMatches(cost, it, allowed) }
            .sorted()

        return when {
            levels.isEmpty() && displayedAnywhere(cost, levelDomain) ->
                StardustLevelEvidence.Conflict(
                    1,
                    listOf(
                        PROVENANCE_ANCHORED_POWER_UP_ROW,
                        "cost_conflicts_with_established_modifier"
                    )
                )
            levels.isEmpty() ->
                StardustLevelEvidence.Invalid(
                    cost,
                    listOf(PROVENANCE_ANCHORED_POWER_UP_ROW, "cost_not_a_displayed_power_up_value")
                )
            else -> levelWindow(
                cost,
                allowed,
                levelDomain,
                levels,
                contextCodesOf(modifierContext)
            )
        }
    }

    private fun allowedModifiers(context: StardustModifierContext): Set<PowerUpCostModifier> =
        when (context) {
            StardustModifierContext.Unknown -> PowerUpStardustRules.SUPPORTED_MODIFIERS
            is StardustModifierContext.Established -> setOf(context.modifier)
            is StardustModifierContext.Unsupported -> emptySet()
        }

    private fun contextCodesOf(context: StardustModifierContext): List<String> =
        when (context) {
            StardustModifierContext.Unknown -> listOf(PROVENANCE_MODIFIER_UNKNOWN)
            is StardustModifierContext.Established ->
                listOf("modifier_established_${context.provenanceCode}")
            is StardustModifierContext.Unsupported ->
                listOf(
                    context.reasonCode.takeIf(BOUNDED_REASON_CODE::matches)
                        ?: "modifier_context_reason_invalid"
                )
        }

    /** Whether the cost is a displayed value under ANY supported modifier. */
    private fun displayedAnywhere(cost: Int, levelDomain: Set<Double>): Boolean =
        levelDomain.any {
            PowerUpStardustRules.baseLevelCostMatches(
                cost,
                it,
                PowerUpStardustRules.SUPPORTED_MODIFIERS
            )
        }

    private fun levelWindow(
        cost: Int,
        allowed: Set<PowerUpCostModifier>,
        levelDomain: Set<Double>,
        levels: List<Double>,
        contextCodes: List<String>
    ): StardustLevelEvidence.Levels {
        val used = allowed.filter { modifier ->
            levelDomain.any {
                PowerUpStardustRules.baseLevelCostMatches(cost, it, setOf(modifier))
            }
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

    private fun readCodes(read: FieldRead<Int>, extra: String? = null): List<String> =
        listOfNotNull(
            PROVENANCE_ANCHORED_POWER_UP_ROW,
            extra,
            boundedReadReason(read.reasonCode)
        )

    private fun boundedReadReason(reasonCode: String): String =
        reasonCode.takeIf(BOUNDED_REASON_CODE::matches) ?: "field_read_reason_invalid"
}
