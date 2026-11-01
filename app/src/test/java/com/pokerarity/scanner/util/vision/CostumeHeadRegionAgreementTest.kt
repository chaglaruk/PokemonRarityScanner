package com.pokerarity.scanner.util.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CostumeHeadRegionAgreementTest {

    @Test
    fun rescuesCottoneeOnlyWhenHeadReferenceAndSpeciesPrototypeAgree() {
        val details = details(
            costumeKey = "546_00_SPRING_2024",
            normalKey = "546_00_shiny",
            costumeScore = 0.36047596f,
            normalScore = 0.33096454f,
            headDistances = 0.453125f to 0.5625f
        )

        val decision = CostumeHeadRegionAgreement.evaluate(
            details = details,
            requestedSpecies = "Cottonee",
            classifier = prototype("Cottonee", "546_00_SPRING_2024", isCostume = true)
        )

        assertTrue(decision.accepted)
        assertEquals("head_region_signature_prototype_agreement", decision.reasonCode)
        assertEquals(0.109375f, decision.headPHashAdvantage!!, 0.000001f)
        assertEquals(0.24f, CostumeHeadRegionAgreement.RESCUE_CONFIDENCE, 0f)
    }

    @Test
    fun rejectsShinyCottoneeWhenCostumeKeyAndHeadEvidenceDoNotAgree() {
        val details = details(
            costumeKey = "546_00_SPRING_2024",
            normalKey = "546_00_shiny",
            costumeScore = 0.3346f,
            normalScore = 0.2998f,
            headDistances = 0.46875f to 0.5f
        )

        val decision = CostumeHeadRegionAgreement.evaluate(
            details = details,
            requestedSpecies = "Cottonee",
            classifier = prototype("Cottonee", "546_00_SPRING_2024_shiny", isCostume = true)
        )

        assertFalse(decision.accepted)
        assertEquals("head_reference_advantage_below_bound", decision.reasonCode)
        assertEquals(0.03125f, decision.headPHashAdvantage!!, 0.000001f)
    }

    @Test
    fun rejectsAHeadMatchWhenSpeciesPrototypeSelectsAnotherCostume() {
        val details = details(
            costumeKey = "546_00_SPRING_2024",
            normalKey = "546_00_shiny",
            costumeScore = 0.36047596f,
            normalScore = 0.33096454f,
            headDistances = 0.453125f to 0.5625f
        )

        val decision = CostumeHeadRegionAgreement.evaluate(
            details = details,
            requestedSpecies = "Cottonee",
            classifier = prototype("Cottonee", "546_00_SPRING_2024_shiny", isCostume = true)
        )

        assertFalse(decision.accepted)
        assertEquals("species_prototype_costume_key_mismatch", decision.reasonCode)
    }

    @Test
    fun rejectsCostumedSlowpokeWhenWholeSpriteStillPrefersCostumeByTooLittle() {
        val details = details(
            costumeKey = "079_00_F_2020_shiny",
            normalKey = "079_00_shiny",
            costumeScore = 0.37790674f,
            normalScore = 0.38829f,
            headDistances = 0.5f to 0.5f
        )

        val decision = CostumeHeadRegionAgreement.evaluate(
            details = details,
            requestedSpecies = "Slowpoke",
            classifier = prototype("Slowpoke", "079_00_shiny", isCostume = false)
        )

        assertFalse(decision.accepted)
        assertEquals("whole_sprite_score_does_not_prefer_normal", decision.reasonCode)
    }

    @Test
    fun rejectsVaporeonNegativeEvenWithAHighHeadAdvantage() {
        val details = details(
            costumeKey = "134_00_07_shiny",
            normalKey = "134_00_shiny",
            costumeScore = 0.3635f,
            normalScore = 0.3731f,
            headDistances = 0.40625f to 0.5f
        )

        val decision = CostumeHeadRegionAgreement.evaluate(
            details = details,
            requestedSpecies = "Vaporeon",
            classifier = prototype("Vaporeon", "134_00_MAY_2023", isCostume = true)
        )

        assertFalse(decision.accepted)
        assertEquals("whole_sprite_score_does_not_prefer_normal", decision.reasonCode)
        assertNotNull(decision.headPHashAdvantage)
    }

    @Test
    fun rejectsDenseSpeciesAndMismatchedPrototypeIdentity() {
        val details = details(
            costumeKey = "025_00_08",
            normalKey = "025_00",
            costumeScore = 0.3825f,
            normalScore = 0.4119f,
            headDistances = 0.765625f to 0.40625f
        ).copy(denseVariantSpecies = true, costumeCandidateCount = 328, candidateCount = 336)

        val decision = CostumeHeadRegionAgreement.evaluate(
            details = details,
            requestedSpecies = "Pikachu",
            classifier = prototype("Pikachu", "025_00_25", isCostume = true)
        )

        assertFalse(decision.accepted)
        assertEquals("dense_species_not_eligible", decision.reasonCode)
    }

    private fun details(
        costumeKey: String,
        normalKey: String,
        costumeScore: Float,
        normalScore: Float,
        headDistances: Pair<Float, Float>
    ) = CostumeSignatureStore.MatchDetails(
        matched = false,
        confidence = 0f,
        bestCostume = costumeScore,
        bestNormal = normalScore,
        scoreGap = normalScore - costumeScore,
        costumeCandidateCount = 2,
        denseVariantSpecies = false,
        bestCostumeKey = costumeKey,
        bestNormalKey = normalKey,
        candidateCount = 4,
        rejectionReason = "normal_candidate_not_beaten_by_margin",
        bestCostumeHeadPHashDistance = headDistances.first,
        bestNormalHeadPHashDistance = headDistances.second
    )

    private fun prototype(species: String, spriteKey: String, isCostume: Boolean) =
        VariantPrototypeClassifier.MatchResult(
            species = species,
            assetKey = spriteKey,
            spriteKey = spriteKey,
            variantType = if (isCostume) "costume" else "base",
            isShiny = spriteKey.endsWith("_shiny"),
            isCostumeLike = isCostume,
            scope = "species",
            score = 0.42f,
            confidence = 0.57f,
            speciesMargin = 0f,
            variantMargin = 0.01f,
            topSpecies = listOf(species)
        )
}
