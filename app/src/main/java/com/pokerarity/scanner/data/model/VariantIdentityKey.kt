package com.pokerarity.scanner.data.model

/**
 * Uniquely identifies a specific collector variant of a Pokemon.
 * Prevents collapsing visually distinct collections (e.g., normal vs special background).
 */
data class VariantIdentityKey(
    val dex: Int,
    val formId: String?,
    val variantId: String?,
    val isShiny: Boolean,
    val isShadow: Boolean,
    val isPurified: Boolean,
    val isLucky: Boolean,
    val isCostume: Boolean,
    val backgroundType: String?,
    val backgroundLabel: String?
) {
    /**
     * Serializes this identity into a stable string key for database storage and lookups.
     * Format: DEX-FORM-VARIANT-SHINY_SHADOW_PURIFIED_LUCKY_COSTUME-[BG_TYPE:BG_LABEL]
     */
    fun asStringKey(): String {
        val formPart = formId?.takeIf { it.isNotBlank() } ?: "BASE"
        val variantPart = variantId?.takeIf { it.isNotBlank() } ?: "NONE"
        
        val flags = buildString {
            if (isShiny) append("S")
            if (isShadow) append("H")
            if (isPurified) append("P")
            if (isLucky) append("L")
            if (isCostume) append("C")
        }.ifEmpty { "NORMAL" }

        val bgPart = if (backgroundType != null || backgroundLabel != null) {
            val type = backgroundType ?: "NONE"
            val label = backgroundLabel ?: "NONE"
            "-[BG:${type}_${label}]"
        } else {
            ""
        }

        return "$dex-$formPart-$variantPart-$flags$bgPart"
    }
}
