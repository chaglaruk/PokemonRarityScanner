package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.OcrConfidenceReasonsBuilder
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.ScanDecisionSupport
import com.pokerarity.scanner.data.model.toUiPokemon
import com.pokerarity.scanner.data.model.ivLevelSummaryText
import com.pokerarity.scanner.data.model.valuableSummary
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.IvSolveDetails
import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.model.RecognitionIdentityHistoryCodec
import com.pokerarity.scanner.data.model.VariantDecisionTrace
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.service.CaughtDatePresentation
import com.pokerarity.scanner.util.DateParseUtils.toLocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone

class ScanHistoryMapperTest {

    // ── Helper ────────────────────────────────────────────────────────────

    private fun pokemon(
        name: String? = "Pikachu",
        cp: Int? = 500,
        hp: Int? = 60,
        maxHp: Int? = 60,
        caughtDate: Date? = null,
        rawOcrText: String = "CP:500|HP:60/60|Name:Pikachu",
        ocrConfidenceReasons: com.pokerarity.scanner.data.model.OcrConfidenceReasons? = null,
        variantDecisionTrace: VariantDecisionTrace? = null
    ) = PokemonData(
        name = name,
        cp = cp,
        hp = hp,
        maxHp = maxHp,
        realName = name,
        candyName = null,
        megaEnergy = null,
        weight = null,
        height = null,
        stardust = null,
        caughtDate = caughtDate,
        rawOcrText = rawOcrText,
        ocrDiagnosticsDir = null,
        ocrDiagnosticsFiles = emptyMap(),
        ocrConfidenceReasons = ocrConfidenceReasons,
        fullVariantMatch = null,
        variantDecisionTrace = variantDecisionTrace
    )

    private fun score(
        total: Int = 42,
        tier: RarityTier = RarityTier.RARE
    ) = RarityScore(
        totalScore = total,
        tier = tier,
        breakdown = mapOf("species" to 20, "visual" to 22),
        explanation = listOf("Rare species", "Shiny bonus")
    )

    @Test
    fun toEntity_preservesReviewGuidanceWithoutInventingUnknownValues() {
        val baseline = ScanHistoryMapper.toEntity(
            pokemon(cp = null, hp = null), VisualFeatures(), score()
        )
        assertNull(baseline.scanConfidenceScore)
        assertNull(baseline.scanConfidenceLabel)
        assertNull(baseline.toUiPokemon().decisionSupport)

        val review = ScanDecisionSupport(
            eventConfidenceCode = "", eventConfidenceLabel = "", eventConfidenceDetail = "",
            scanConfidenceScore = 70, scanConfidenceLabel = "Review recommended",
            scanConfidenceDetail = "CP missing, HP missing"
        )
        val row = ScanHistoryMapper.toEntity(
            pokemon(cp = null, hp = null), VisualFeatures(), score().copy(decisionSupport = review)
        )
        assertEquals(70, row.scanConfidenceScore)
        assertEquals("Review recommended", row.scanConfidenceLabel)
        assertEquals("CP missing, HP missing", row.scanConfidenceDetail)
        val ui = row.toUiPokemon()
        assertEquals("Review recommended", ui.decisionSupport?.scanConfidenceLabel)
        assertEquals("Unknown", ui.cpDisplayText)
        assertNull(ui.hp)
    }

    @Test
    fun toEntityAndHistoryUiPreserveEventConfidenceDetails() {
        val eventDetail = "The catch date matches Spring event timing."
        val decisionSupport = ScanDecisionSupport(
            eventConfidenceCode = "DATE_BACKED_EVENT",
            eventConfidenceLabel = "Spring event",
            eventConfidenceDetail = eventDetail,
            scanConfidenceScore = 82,
            scanConfidenceLabel = "High confidence",
            scanConfidenceDetail = "Name and CP matched",
            mismatchGuardTitle = "Event blocked by catch date",
            mismatchGuardDetail = "Caught date conflicts with event timing.",
            recognitionSummary = "Event metadata was suppressed because it conflicts with the catch date."
        )

        val entity = ScanHistoryMapper.toEntity(
            pokemon(), VisualFeatures(), score().copy(decisionSupport = decisionSupport)
        )

        assertEquals("DATE_BACKED_EVENT", entity.eventConfidenceCode)
        assertEquals("Spring event", entity.eventConfidenceLabel)
        assertEquals(eventDetail, entity.eventConfidenceDetail)
        assertEquals("Event blocked by catch date", entity.mismatchGuardTitle)
        assertEquals("Caught date conflicts with event timing.", entity.mismatchGuardDetail)
        assertEquals(
            "Event metadata was suppressed because it conflicts with the catch date.",
            entity.recognitionSummary
        )
        val historyPokemon = entity.toUiPokemon()
        assertEquals("Spring event", historyPokemon.decisionSupport?.eventConfidenceLabel)
        assertEquals(eventDetail, historyPokemon.decisionSupport?.eventConfidenceDetail)
        assertEquals("Event blocked by catch date", historyPokemon.decisionSupport?.mismatchGuardTitle)
        assertEquals("Caught date conflicts with event timing.", historyPokemon.decisionSupport?.mismatchGuardDetail)
        assertEquals(
            "Event metadata was suppressed because it conflicts with the catch date.",
            historyPokemon.decisionSupport?.recognitionSummary
        )
        assertTrue(historyPokemon.valuableSummary().contains("caught date conflicts with event timing"))
    }

    // ── Basic mapping ─────────────────────────────────────────────────────

    @Test
    fun toEntity_mapsBasicFieldsCorrectly() {
        val testDate = Date()
        val p = pokemon(caughtDate = testDate)
        val features = VisualFeatures(isShiny = true, isLucky = true)
        val s = score()

        val entity = ScanHistoryMapper.toEntity(p, features, s)

        assertEquals("Pikachu", entity.pokemonName)
        assertEquals(500, entity.cp)
        assertEquals(60, entity.hp)
        assertEquals(testDate, entity.caughtDate)
        assertEquals(testDate.toLocalDate(), entity.caughtDateLocalDate)
        assertEquals("CP:500|HP:60/60|Name:Pikachu", entity.rawOcrText)
        assertTrue(entity.isShiny)
        assertFalse(entity.isShadow)
        assertTrue(entity.isLucky)
        assertFalse(entity.hasCostume)
        assertEquals(42, entity.rarityScore)
        assertEquals("RARE", entity.rarityTier)
    }

    @Test
    fun toEntityUsesTypedCalendarDateIfTimezoneChangesBeforeSave() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"))
            val caughtDate = Date.from(
                LocalDate.parse("2019-07-12").atStartOfDay(ZoneId.systemDefault()).toInstant()
            )
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val entity = ScanHistoryMapper.toEntity(
                pokemon(caughtDate = caughtDate).copy(caughtDateLocalDate = LocalDate.parse("2019-07-12")),
                VisualFeatures(),
                score()
            )

            assertEquals(LocalDate.parse("2019-07-12"), entity.caughtDateLocalDate)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun toEntityAndHistoryUiPreserveTypedIvRange() {
        val ivSolve = IvSolveDetails(
            ivMin = 84,
            ivMax = 91,
            ivCandidateCount = 4,
            levelMin = 30f,
            levelMax = 31f,
            levelAlternatives = listOf(30f, 30.5f, 31f),
            ivSolveMode = IvSolveMode.RANGE,
            ivSolveSignalsUsed = listOf("CP", "MAX_HP", "POWER_UP_STARDUST")
        )
        val row = ScanHistoryMapper.toEntity(
            pokemon(), VisualFeatures(), score().copy(ivEstimate = "84%–91%", ivSolve = ivSolve)
        )

        assertEquals(84, row.ivMin)
        assertEquals(91, row.ivMax)
        assertEquals(4, row.ivCandidateCount)
        assertEquals("RANGE", row.ivSolveMode)
        assertEquals(30f, row.ivLevelMin)
        assertEquals(31f, row.ivLevelMax)
        assertEquals("30.0,30.5,31.0", row.ivLevelAlternatives)
        val history = row.toUiPokemon()
        assertEquals("84%–91%", history.ivText)
        assertEquals(IvSolveMode.RANGE, history.ivSolveMode)
        assertNull(history.iv)
        assertEquals(4, history.ivCandidateCount)
        assertEquals(listOf(30f, 30.5f, 31f), history.ivLevelAlternatives)
    }

    @Test
    fun toEntityAndHistoryUiPreserveStardustOnlyDiscreteLevelAlternatives() {
        val estimate = IvSolveDetails(
            ivCandidateCount = 0,
            levelAlternatives = listOf(34f, 30f, 33f, 31f),
            ivSolveMode = IvSolveMode.INSUFFICIENT,
            ivSolveSignalsUsed = listOf("POWER_UP_STARDUST")
        )
        val row = ScanHistoryMapper.toEntity(
            pokemon(), VisualFeatures(), score().copy(ivSolve = estimate)
        )

        assertNull(row.ivMin)
        assertNull(row.ivMax)
        assertEquals("INSUFFICIENT", row.ivSolveMode)
        assertNull(row.ivLevelMin)
        assertNull(row.ivLevelMax)
        assertEquals("30.0,31.0,33.0,34.0", row.ivLevelAlternatives)
        assertEquals("IV Unknown | Level 30 or 31 or 33 or 34", row.toUiPokemon().ivLevelSummaryText)
    }

    // ── Local path stripping ──────────────────────────────────────────────

    @Test
    fun toEntity_stripsLocalPathsFromRawOcrText() {
        val p = pokemon(
            name = "Bulbasaur",
            cp = null, hp = null, maxHp = null,
            rawOcrText = "Bulbasaur\nC:/Users/TestUser/Desktop/img.png\n/tmp/ocr.txt\nExtraText"
        )

        val entity = ScanHistoryMapper.toEntity(p, VisualFeatures(), score(0, RarityTier.COMMON))

        assertEquals("Bulbasaur\nExtraText", entity.rawOcrText)
        assertFalse(entity.rawOcrText.contains("C:/Users"))
        assertFalse(entity.rawOcrText.contains("/tmp"))
    }

    // ── Null field preservation ────────────────────────────────────────────

    @Test
    fun toEntity_preservesNullFields() {
        val p = pokemon(name = null, cp = null, hp = null, maxHp = null, caughtDate = null, rawOcrText = "")

        val entity = ScanHistoryMapper.toEntity(p, VisualFeatures(), score(0, RarityTier.COMMON))

        assertNull(entity.pokemonName)
        assertNull(entity.cp)
        assertNull(entity.hp)
        assertNull(entity.caughtDate)
        assertEquals("", entity.rawOcrText)
    }

    // ── Visual flag combinations ──────────────────────────────────────────

    @Test
    fun toEntity_mapsAllVisualFlagsFalse() {
        val features = VisualFeatures(
            isShiny = false, isShadow = false, isLucky = false, hasCostume = false
        )
        val entity = ScanHistoryMapper.toEntity(pokemon(), features, score())

        assertFalse(entity.isShiny)
        assertFalse(entity.isShadow)
        assertFalse(entity.isLucky)
        assertFalse(entity.hasCostume)
    }

    @Test
    fun toEntity_mapsAllVisualFlagsTrue() {
        val features = VisualFeatures(
            isShiny = true, isShadow = true, isLucky = true, hasCostume = true
        )
        val entity = ScanHistoryMapper.toEntity(pokemon(), features, score())

        assertTrue(entity.isShiny)
        assertTrue(entity.isShadow)
        assertTrue(entity.isLucky)
        assertTrue(entity.hasCostume)
    }

    @Test
    fun toEntity_mapsShadowOnlyCorrectly() {
        val features = VisualFeatures(isShadow = true)
        val entity = ScanHistoryMapper.toEntity(pokemon(), features, score())

        assertFalse(entity.isShiny)
        assertTrue(entity.isShadow)
        assertFalse(entity.isLucky)
        assertFalse(entity.hasCostume)
    }

    // ── Rarity tier serialization ─────────────────────────────────────────

    @Test
    fun toEntity_serializesAllRarityTierNames() {
        for (tier in RarityTier.entries) {
            val entity = ScanHistoryMapper.toEntity(
                pokemon(), VisualFeatures(), score(tier.minScore, tier)
            )
            assertEquals(
                "Tier ${tier.name} should serialize to its enum name",
                tier.name, entity.rarityTier
            )
            assertEquals(tier.minScore, entity.rarityScore)
        }
    }

    // ── Pipe-delimited rawOcrText format ──────────────────────────────────

    @Test
    fun toEntity_preservesPipeDelimitedOcrFormat() {
        val raw = "CP:1234|HP:100/120|Name:Charizard|Candy:Charmander Candy|Date:1700000000000"
        val p = pokemon(rawOcrText = raw)

        val entity = ScanHistoryMapper.toEntity(p, VisualFeatures(), score())

        assertEquals(raw, entity.rawOcrText)
        assertTrue(entity.rawOcrText.contains("CP:1234"))
        assertTrue(entity.rawOcrText.contains("Name:Charizard"))
        assertTrue(entity.rawOcrText.contains("Candy:Charmander Candy"))
    }

    // ── Structured fields intentionally excluded ──────────────────────────

    @Test
    fun toEntity_excludesOcrConfidenceReasons() {
        val reasons = OcrConfidenceReasonsBuilder()
            .withCp(500)
            .withHp(60, 60)
            .addWarning("Test warning")
            .build()
        val p = pokemon(ocrConfidenceReasons = reasons)

        val entity = ScanHistoryMapper.toEntity(p, VisualFeatures(), score())

        // Entity has no ocrConfidenceReasons field — if it compiled, it's excluded.
        // Verify the rawOcrText doesn't leak confidence data.
        assertFalse(entity.rawOcrText.contains("Test warning"))
        assertFalse(entity.rawOcrText.contains("cp_parsed"))
    }

    @Test
    fun toEntity_excludesVariantDecisionTrace() {
        val trace = VariantDecisionTrace(
            classifierScope = "species",
            classifierSpecies = "Charizard",
            classifierSpriteKey = "charizard_mega_x",
            fullVariantDebug = "secret-debug-info"
        )
        val p = pokemon(variantDecisionTrace = trace)

        val entity = ScanHistoryMapper.toEntity(p, VisualFeatures(), score())

        // Entity has no variantDecisionTrace field — verify rawOcrText doesn't leak.
        assertFalse(entity.rawOcrText.contains("charizard_mega_x"))
        assertFalse(entity.rawOcrText.contains("secret-debug-info"))
        assertFalse(entity.rawOcrText.contains("ClassifierScope"))
    }

    // ── Edge cases ────────────────────────────────────────────────────────

    @Test
    fun toEntity_handlesEmptyRawOcrText() {
        val p = pokemon(rawOcrText = "")
        val entity = ScanHistoryMapper.toEntity(p, VisualFeatures(), score())
        assertEquals("", entity.rawOcrText)
    }

    @Test
    fun toEntity_pathStrippingIsCaseInsensitive() {
        val p = pokemon(rawOcrText = "c:/users/dev/scan.png\nGoodLine")
        val entity = ScanHistoryMapper.toEntity(p, VisualFeatures(), score())
        assertFalse(entity.rawOcrText.contains("c:/users", ignoreCase = true))
        assertEquals("GoodLine", entity.rawOcrText)
    }

    // ── Phase 2E: explicit identity contract compatibility ────────────────

    private fun knownIdentity() = com.pokerarity.scanner.data.model.RecognitionIdentity(
        speciesStatus = com.pokerarity.scanner.data.model.RecognitionSpeciesStatus.KNOWN,
        canonicalSpecies = "Raichu",
        speciesAuthority = "species_exact_authority",
        formStatus = com.pokerarity.scanner.data.model.RecognitionFormStatus.KNOWN,
        knownForm = "Alolan",
        formProvenance = "authoritative_variant_db",
        shiny = com.pokerarity.scanner.data.model.RecognitionTruth.TRUE,
        shadow = com.pokerarity.scanner.data.model.RecognitionTruth.UNKNOWN,
        purified = com.pokerarity.scanner.data.model.RecognitionTruth.UNKNOWN,
        lucky = com.pokerarity.scanner.data.model.RecognitionTruth.FALSE,
        costume = com.pokerarity.scanner.data.model.RecognitionTruth.UNKNOWN,
        specialForm = com.pokerarity.scanner.data.model.RecognitionTruth.UNKNOWN,
        xxs = com.pokerarity.scanner.data.model.RecognitionTruth.UNKNOWN,
        xxl = com.pokerarity.scanner.data.model.RecognitionTruth.UNKNOWN,
        locationCard = com.pokerarity.scanner.data.model.RecognitionTruth.UNKNOWN
    )

    @Test
    fun toEntity_identityDoesNotChangeCompatibilityPersistence() {
        val withIdentity = pokemon(name = "Raichu").copy(recognitionIdentity = knownIdentity())
        val withoutIdentity = pokemon(name = "Raichu")

        val mappedWith = ScanHistoryMapper.toEntity(withIdentity, VisualFeatures(isShiny = true), score())
        val mappedWithout = ScanHistoryMapper.toEntity(withoutIdentity, VisualFeatures(isShiny = true), score())

        // Persisting the authority contract leaves compatibility fields stable.
        assertEquals(mappedWithout.pokemonName, mappedWith.pokemonName)
        assertEquals(mappedWithout.cp, mappedWith.cp)
        assertEquals(mappedWithout.hp, mappedWith.hp)
        assertEquals(mappedWithout.rawOcrText, mappedWith.rawOcrText)
        assertEquals(mappedWithout.isShiny, mappedWith.isShiny)
        assertEquals(mappedWithout.isShadow, mappedWith.isShadow)
        assertEquals(mappedWithout.isLucky, mappedWith.isLucky)
        assertEquals(mappedWithout.hasCostume, mappedWith.hasCostume)
        assertEquals(mappedWithout.rarityScore, mappedWith.rarityScore)
        assertEquals(mappedWithout.rarityTier, mappedWith.rarityTier)
        assertTrue(mappedWith.isShiny)
        assertEquals("Raichu", mappedWith.pokemonName)
        assertEquals(knownIdentity(), RecognitionIdentityHistoryCodec.decode(mappedWith.recognitionIdentityJson))
        assertEquals("Form: Alolan", mappedWith.toUiPokemon().formPresentationText)
    }

    @Test
    fun toEntity_compatibilityBooleansRemainStableAcrossContractStates() {
        // FALSE and UNKNOWN both map to the historical false in the persistence view;
        // the tri-state distinction lives only in the runtime identity contract.
        val falseIdentity = knownIdentity()
        val unknownIdentity = falseIdentity.copy(lucky = com.pokerarity.scanner.data.model.RecognitionTruth.UNKNOWN)

        val featuresFromFalse = com.pokerarity.scanner.data.model.RecognitionIdentityCompat
            .toVisualFeatures(falseIdentity)
        val featuresFromUnknown = com.pokerarity.scanner.data.model.RecognitionIdentityCompat
            .toVisualFeatures(unknownIdentity)

        val entityFromFalse = ScanHistoryMapper.toEntity(pokemon(), featuresFromFalse, score())
        val entityFromUnknown = ScanHistoryMapper.toEntity(pokemon(), featuresFromUnknown, score())
        assertFalse(entityFromFalse.isLucky)
        assertFalse(entityFromUnknown.isLucky)
        assertTrue(entityFromFalse.isShiny)
        assertEquals(entityFromUnknown.isShiny, entityFromFalse.isShiny)
        assertEquals(entityFromUnknown.isShadow, entityFromFalse.isShadow)
        assertEquals(entityFromUnknown.hasCostume, entityFromFalse.hasCostume)
    }
}

