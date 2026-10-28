package com.pokerarity.scanner.util.ocr

/** Visible, same-frame inputs for identity. Never persisted or sent off the device. */
data class RecognitionObservation(
    val candySpecies: String?,
    val powerUpStardust: Int?,
    val types: Set<String>?,
    val detailScreen: Boolean,
    val numericConflict: Boolean = false,
    val frameIndex: Int = 0,
    val evolutionCandyCost: Int? = null,
    /** Same-frame typed EVOLVE-cost read; conflict values never enter profile evaluation. */
    val evolutionCandyCostRead: FieldRead<Int> = evolutionCandyCost?.let { FieldRead.read(it) }
        ?: FieldRead.missing("not_evaluated"),
    /**
     * Phase 3A typed level evidence derived from the anchored POWER UP stardust field.
     * Never derived from inventory stardust; carries bounded codes/values only.
     */
    val powerUpStardustLevelEvidence: StardustLevelEvidence? = null,
    val formEvidence: SameSpeciesFormEvidence? = null,
    /** CP/HP conflicts that invalidate identity-level numeric witnesses. */
    val identityBlockingNumericConflict: Boolean = numericConflict
)
