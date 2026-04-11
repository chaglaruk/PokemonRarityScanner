package com.pokerarity.scanner.util.ocr

object ScanAuthorityLogic {

    private const val SAME_FAMILY_SCOPE_CONFIDENCE_MIN = 0.40f
    private const val SAME_FAMILY_SCOPE_SCORE_MARGIN = 0.08f

    fun shouldAcceptClassifierSpeciesOverride(
        currentSpecies: String?,
        parsedRawSpecies: String?,
        parsedFallbackSpecies: String?,
        candyName: String?,
        classifierSpecies: String?,
        classifierInCandyFamily: Boolean
    ): Boolean {
        if (classifierSpecies.isNullOrBlank()) return false
        if (currentSpecies.isNullOrBlank() || currentSpecies.equals("Unknown", ignoreCase = true)) {
            return true
        }
        if (currentSpecies.equals(classifierSpecies, ignoreCase = true)) {
            return true
        }

        val exactParsedLock =
            parsedRawSpecies.equals(currentSpecies, ignoreCase = true) ||
                parsedFallbackSpecies.equals(currentSpecies, ignoreCase = true)

        if (exactParsedLock && candyName.isNullOrBlank() && !classifierInCandyFamily) {
            return false
        }

        // If OCR clearly identified a specific species (parsedRawSpecies matches currentSpecies)
        // and the classifier is trying to downgrade to a known confused/related species,
        // block the override. This prevents Pikachu -> Pichu, Slowpoke -> Slowbro, etc.
        if (exactParsedLock && classifierInCandyFamily) {
            if (isBlockedFamilyDowngrade(currentSpecies, classifierSpecies)) {
                return false
            }
        }

        return true
    }

    fun shouldPreferClassifierSpeciesForScopedPass(
        currentSpecies: String?,
        parsedRawSpecies: String?,
        parsedFallbackSpecies: String?,
        candyName: String?,
        classifierSpecies: String?,
        classifierConfidence: Float,
        classifierScore: Float,
        currentSpeciesScore: Float?,
        sameFamilyWithCurrent: Boolean
    ): Boolean {
        if (currentSpecies.isNullOrBlank() || classifierSpecies.isNullOrBlank()) return false
        if (currentSpecies.equals(classifierSpecies, ignoreCase = true)) return false
        if (!sameFamilyWithCurrent) return false
        if (!candyName.isNullOrBlank()) return false
        if (classifierConfidence < SAME_FAMILY_SCOPE_CONFIDENCE_MIN) return false
        val exactParsedLock =
            parsedRawSpecies.equals(currentSpecies, ignoreCase = true) ||
                parsedFallbackSpecies.equals(currentSpecies, ignoreCase = true)
        if (exactParsedLock) return false
        // Also guard scoped pass against known family downgrades
        if (isBlockedFamilyDowngrade(currentSpecies, classifierSpecies)) return false
        val currentScore = currentSpeciesScore ?: return false
        return classifierScore + SAME_FAMILY_SCOPE_SCORE_MARGIN <= currentScore
    }

    fun shouldSkipGlobalClassifierForLockedOcr(
        currentSpecies: String?,
        parsedRawSpecies: String?,
        parsedFallbackSpecies: String?,
        candyName: String?
    ): Boolean {
        if (currentSpecies.isNullOrBlank() || currentSpecies.equals("Unknown", ignoreCase = true)) {
            return false
        }
        if (!candyName.isNullOrBlank()) {
            return false
        }
        return parsedRawSpecies.equals(currentSpecies, ignoreCase = true) ||
            parsedFallbackSpecies.equals(currentSpecies, ignoreCase = true)
    }

    /**
     * Returns true if the classifier is trying to replace a correctly OCR-identified species
     * with a visually similar but different family member. Common cases seen in telemetry:
     *   Pikachu  -> Pichu     (baby)
     *   Slowpoke -> Slowbro   (evolution)
     *   Ponyta   -> Rapidash  (evolution)
     *   Snorlax  -> Munchlax  (baby)
     *   Eevee    -> Flareon/Espeon/etc (evolution)
     *   Rowlet   -> Dartrix   (evolution)
     *   Cubchoo  -> Beartic   (evolution)
     *   Cottonee -> Whimsicott (evolution)
     *   Slakoth  -> Vigoroth  (evolution)
     *   Raichu   -> Pichu     (baby)
     *   Chansey  -> Happiny   (baby)
     */
    private fun isBlockedFamilyDowngrade(
        ocrSpecies: String?,
        classifierSpecies: String?
    ): Boolean {
        if (ocrSpecies.isNullOrBlank() || classifierSpecies.isNullOrBlank()) return false
        val ocr = ocrSpecies.lowercase()
        val cl  = classifierSpecies.lowercase()
        val blocks = mapOf(
            "pikachu"    to setOf("pichu"),
            "raichu"     to setOf("pichu", "pikachu"),
            "slowpoke"   to setOf("slowbro", "slowking"),
            "eevee"      to setOf("flareon", "vaporeon", "jolteon", "espeon", "umbreon",
                                   "leafeon", "glaceon", "sylveon"),
            "snorlax"    to setOf("munchlax"),
            "ponyta"     to setOf("rapidash"),
            "rowlet"     to setOf("dartrix", "decidueye"),
            "cubchoo"    to setOf("beartic"),
            "cottonee"   to setOf("whimsicott"),
            "slakoth"    to setOf("vigoroth", "slaking"),
            "chansey"    to setOf("happiny", "blissey"),
            "wurmple"    to setOf("silcoon", "cascoon"),
        )
        return blocks[ocr]?.contains(cl) == true
    }
}
