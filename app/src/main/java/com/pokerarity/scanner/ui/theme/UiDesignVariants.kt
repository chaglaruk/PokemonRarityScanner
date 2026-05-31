// Purpose: Define crash-safe selectable UI design variants beyond color themes.
package com.pokerarity.scanner.ui.theme

enum class UiDesignVariantId(
    val storageValue: String,
    val displayName: String,
) {
    CLASSIC("classic", "Classic"),
    DEX_CONSOLE("dex_console", "Dex Console"),
    COLLECTOR_ALBUM("collector_album", "Collector Album"),
    RESEARCH_LAB("research_lab", "Research Lab"),
    BATTLE_HUD("battle_hud", "Battle HUD"),
    AURORA_SHOWCASE("aurora_showcase", "Aurora Showcase"),
}

fun safeDesignVariantId(raw: String?): UiDesignVariantId {
    val normalized = raw?.trim()?.lowercase().orEmpty()
    if (normalized.isBlank()) return UiDesignVariantId.CLASSIC
    return runCatching {
        UiDesignVariantId.entries.firstOrNull {
            it.storageValue == normalized || it.name.lowercase() == normalized
        } ?: UiDesignVariantId.CLASSIC
    }.getOrDefault(UiDesignVariantId.CLASSIC)
}

data class UiDesignVariant(
    val id: UiDesignVariantId,
    val displayName: String,
)

object UiDesignVariantRegistry {
    val classic = UiDesignVariant(
        id = UiDesignVariantId.CLASSIC,
        displayName = UiDesignVariantId.CLASSIC.displayName,
    )

    private val variants = listOf(
        classic,
        UiDesignVariant(
            id = UiDesignVariantId.DEX_CONSOLE,
            displayName = UiDesignVariantId.DEX_CONSOLE.displayName,
        ),
        UiDesignVariant(
            id = UiDesignVariantId.COLLECTOR_ALBUM,
            displayName = UiDesignVariantId.COLLECTOR_ALBUM.displayName,
        ),
        UiDesignVariant(
            id = UiDesignVariantId.RESEARCH_LAB,
            displayName = UiDesignVariantId.RESEARCH_LAB.displayName,
        ),
        UiDesignVariant(
            id = UiDesignVariantId.BATTLE_HUD,
            displayName = UiDesignVariantId.BATTLE_HUD.displayName,
        ),
        UiDesignVariant(
            id = UiDesignVariantId.AURORA_SHOWCASE,
            displayName = UiDesignVariantId.AURORA_SHOWCASE.displayName,
        ),
    )

    val allVariants: List<UiDesignVariant>
        get() = variants

    fun getDesignVariant(id: UiDesignVariantId): UiDesignVariant {
        return variants.firstOrNull { it.id == id } ?: classic
    }

    fun getDesignVariantByRaw(raw: String?): UiDesignVariant {
        return getDesignVariant(safeDesignVariantId(raw))
    }
}
