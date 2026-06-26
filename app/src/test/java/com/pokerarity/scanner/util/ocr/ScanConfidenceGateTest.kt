package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.PokemonData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanConfidenceGateTest {
    private val gate = ScanConfidenceGate()

    @Test
    fun strongDetailScreenWithCoreEvidenceAccepts() {
        val pokemon = pokemon(
            cp = 777,
            hp = 88,
            maxHp = 88,
            name = "Pikachu",
            resolverTrace = resolverTrace("Pikachu", 0.92f)
        )
        val decision = gate.evaluate(
            input(
                pokemon = pokemon,
                frames = listOf(detailFrame(pokemon), detailFrame(pokemon, frameIndex = 1)),
                cpCropQuality = 0.82
            )
        )

        assertEquals(ScanDecisionType.ACCEPT, decision.decision)
        assertTrue(decision.confidence >= 0.76f)
        assertTrue(decision.mayShowOverlay)
        assertTrue(decision.maySaveScan)
        assertTrue(decision.collectionSafe)
    }

    @Test
    fun strongButIncompleteEvidenceIsNotFullAccept() {
        val pokemon = pokemon(
            cp = null,
            hp = null,
            name = "Pikachu",
            resolverTrace = resolverTrace("Pikachu", 0.90f)
        )
        val decision = gate.evaluate(
            input(
                pokemon = pokemon,
                frames = listOf(detailFrame(pokemon, fields = listOf(nameCandidate()))),
                cpCropQuality = 0.80
            )
        )

        assertTrue(decision.decision == ScanDecisionType.ACCEPT_LOW_CONFIDENCE || decision.decision == ScanDecisionType.UNCERTAIN)
        assertFalse(decision.collectionSafe)
    }

    @Test
    fun unknownScreenWithWeakOcrDoesNotAccept() {
        val pokemon = pokemon(cp = null, hp = null, name = null)
        val decision = gate.evaluate(
            input(
                pokemon = pokemon,
                frames = listOf(frame(ScreenType.Unknown.name, 0.20f, pokemon)),
                cpCropQuality = 0.20
            )
        )

        assertTrue(decision.decision == ScanDecisionType.RETRY || decision.decision == ScanDecisionType.UNCERTAIN)
        assertFalse(decision.maySaveScan)
        assertFalse(decision.collectionSafe)
    }

    @Test
    fun transitionScreenRetries() {
        val pokemon = pokemon(cp = 777, hp = 88, name = "Pikachu")
        val decision = gate.evaluate(
            input(
                pokemon = pokemon,
                frames = listOf(frame(ScreenType.Transition.name, 0.72f, pokemon)),
                cpCropQuality = 0.80
            )
        )

        assertEquals(ScanDecisionType.RETRY, decision.decision)
        assertTrue(decision.retryEligible)
        assertFalse(decision.mayShowOverlay)
    }

    @Test
    fun storageListScreenRejectsAsNotPokemonDetail() {
        val pokemon = pokemon(cp = 777, hp = 88, name = "Pikachu")
        val decision = gate.evaluate(
            input(
                pokemon = pokemon,
                frames = listOf(frame(ScreenType.StorageList.name, 0.80f, pokemon)),
                cpCropQuality = 0.80
            )
        )

        assertEquals(ScanDecisionType.REJECT_NOT_POKEMON_SCREEN, decision.decision)
        assertFalse(decision.retryEligible)
        assertFalse(decision.maySaveScan)
    }

    @Test
    fun markerOnlyFieldsNeverAccept() {
        val pokemon = pokemon(cp = null, hp = null, name = "missing")
        val decision = gate.evaluate(
            input(
                pokemon = pokemon,
                frames = listOf(
                    frame(
                        screen = ScreenType.PokemonDetail.name,
                        confidence = 0.84f,
                        pokemon = pokemon,
                        fields = listOf(
                            FieldCandidateDiagnostic("Name", "test", "missing", null, "missing"),
                            FieldCandidateDiagnostic("NameDynamic", "test", "not-run", null, "missing"),
                            FieldCandidateDiagnostic("RawText", "test", "RawText", "RawText", "found")
                        )
                    )
                ),
                cpCropQuality = 0.75
            )
        )

        assertTrue(decision.decision != ScanDecisionType.ACCEPT)
        assertFalse(decision.collectionSafe)
    }

    @Test
    fun rawTextOnlyNeverAccepts() {
        val pokemon = pokemon(cp = null, hp = null, name = null, rawOcrText = "RawText:some noisy block")
        val decision = gate.evaluate(
            input(
                pokemon = pokemon,
                frames = listOf(
                    frame(
                        screen = ScreenType.PokemonDetail.name,
                        confidence = 0.82f,
                        pokemon = pokemon,
                        fields = listOf(FieldCandidateDiagnostic("RawText", "test", "some noisy block", "some noisy block", "found"))
                    )
                ),
                cpCropQuality = 0.75
            )
        )

        assertTrue(decision.decision != ScanDecisionType.ACCEPT)
        assertFalse(decision.maySaveScan)
    }

    @Test
    fun legacyGeometryFallbackReducesScoreButDoesNotCrash() {
        val pokemon = pokemon(
            cp = 777,
            hp = 88,
            maxHp = 88,
            name = "Pikachu",
            resolverTrace = resolverTrace("Pikachu", 0.92f)
        )
        val anchorDecision = gate.evaluate(input(pokemon, listOf(detailFrame(pokemon)), cpCropQuality = 0.82))
        val fallbackDecision = gate.evaluate(
            input(
                pokemon = pokemon,
                frames = listOf(detailFrame(pokemon, provenance = CropProvenance.LegacyFallback.diagnosticName, cropConfidence = 0.45f)),
                cpCropQuality = 0.82
            )
        )

        assertTrue(fallbackDecision.confidence < anchorDecision.confidence)
        assertTrue(fallbackDecision.evidenceUsed.contains("legacy_geometry_fallback"))
        assertTrue(fallbackDecision.decision == ScanDecisionType.ACCEPT || fallbackDecision.decision == ScanDecisionType.ACCEPT_LOW_CONFIDENCE)
    }

    @Test
    fun conflictingSpeciesCandidatesDowngradeDecision() {
        val trace = resolverTrace(
            species = "Pikachu",
            confidence = 0.86f,
            candidates = listOf(
                SpeciesCandidateDiagnostic("Pikachu", score = 0.70f, winner = true, reasons = listOf("name")),
                SpeciesCandidateDiagnostic("Raichu", score = 0.66f, winner = false, reasons = listOf("name"))
            )
        )
        val pokemon = pokemon(cp = 777, hp = 88, maxHp = 88, name = "Pikachu", resolverTrace = trace)
        val decision = gate.evaluate(input(pokemon, listOf(detailFrame(pokemon)), cpCropQuality = 0.82))

        assertEquals(ScanDecisionType.UNCERTAIN, decision.decision)
        assertFalse(decision.maySaveScan)
        assertTrue(decision.developerReasons.any { it.startsWith("resolver_candidates_close") })
    }

    @Test
    fun appraisalEvidenceHelpsOnlyOnCompatibleScreen() {
        val appraisalPokemon = pokemon(
            cp = 777,
            hp = 88,
            maxHp = 88,
            name = "Pikachu",
            resolverTrace = resolverTrace("Pikachu", 0.92f),
            appraisalAttack = 15
        )
        val appraisalDecision = gate.evaluate(
            input(
                appraisalPokemon,
                listOf(detailFrame(appraisalPokemon, screen = ScreenType.Appraisal.name)),
                cpCropQuality = 0.82
            )
        )
        val unknownDecision = gate.evaluate(
            input(
                appraisalPokemon,
                listOf(detailFrame(appraisalPokemon, screen = ScreenType.Unknown.name, screenConfidence = 0.30f)),
                cpCropQuality = 0.82
            )
        )

        assertTrue(appraisalDecision.confidence > unknownDecision.confidence)
        assertTrue(unknownDecision.developerReasons.contains("appraisal_ignored_for_screen:Unknown"))
    }

    private fun input(
        pokemon: PokemonData,
        frames: List<FrameDiagnostic>,
        cpCropQuality: Double? = null
    ): ScanConfidenceInput =
        ScanConfidenceInput(
            pokemon = pokemon,
            frames = frames,
            consistencyReason = "accepted",
            cpCropQuality = cpCropQuality,
            visualSummary = VariantVisualSummary(
                isShiny = false,
                isShadow = false,
                isPurified = false,
                isLucky = false,
                hasCostume = false,
                hasSpecialForm = false,
                isXXS = false,
                isXXL = false,
                hasLocationCard = false,
                confidence = 0.70f,
                classifierSpecies = pokemon.realName ?: pokemon.name,
                classifierScope = "test",
                classifierConfidence = 0.70f,
                fullVariantSpecies = pokemon.realName ?: pokemon.name,
                fullVariantClass = null
            )
        )

    private fun detailFrame(
        pokemon: PokemonData,
        frameIndex: Int = 0,
        screen: String = ScreenType.PokemonDetail.name,
        screenConfidence: Float = 0.84f,
        provenance: String = CropProvenance.AnchorDerived.diagnosticName,
        cropConfidence: Float = 0.78f,
        fields: List<FieldCandidateDiagnostic> = listOf(
            field("CP", pokemon.cp?.toString()),
            field("HP", pokemon.hp?.toString()),
            nameCandidate(pokemon.name ?: pokemon.realName)
        )
    ): FrameDiagnostic =
        frame(screen, screenConfidence, pokemon, frameIndex, fields, provenance, cropConfidence)

    private fun frame(
        screen: String,
        confidence: Float,
        pokemon: PokemonData,
        frameIndex: Int = 0,
        fields: List<FieldCandidateDiagnostic> = emptyList(),
        provenance: String = CropProvenance.AnchorDerived.diagnosticName,
        cropConfidence: Float = 0.78f
    ): FrameDiagnostic =
        FrameDiagnostic(
            frameIndex = frameIndex,
            imageWidth = 1080,
            imageHeight = 2340,
            screenState = screen,
            screenConfidence = confidence,
            crops = listOf(
                crop("CP", provenance, cropConfidence),
                crop("HP", provenance, cropConfidence),
                crop("Name", provenance, cropConfidence)
            ),
            fieldCandidates = fields,
            selected = PokemonSummary.from(pokemon)
        )

    private fun crop(field: String, provenance: String, confidence: Float): CropDiagnostic =
        CropDiagnostic(field, "test", 0, 0, 100, 40, "used", provenance, confidence)

    private fun field(field: String, value: String?): FieldCandidateDiagnostic =
        FieldCandidateDiagnostic(
            field = field,
            source = "test",
            rawText = value,
            parsedValue = value,
            status = if (value.isNullOrBlank()) "missing" else "found",
            candidateScore = 0.90f,
            winner = !value.isNullOrBlank(),
            selectedValue = value
        )

    private fun nameCandidate(value: String? = "Pikachu"): FieldCandidateDiagnostic =
        FieldCandidateDiagnostic(
            field = "Name",
            source = "test",
            rawText = value,
            parsedValue = value,
            status = if (value.isNullOrBlank()) "missing" else "found",
            candidateScore = 0.92f,
            winner = !value.isNullOrBlank(),
            selectedValue = value
        )

    private fun resolverTrace(
        species: String,
        confidence: Float,
        candidates: List<SpeciesCandidateDiagnostic> = listOf(
            SpeciesCandidateDiagnostic(species, score = confidence, winner = true, reasons = listOf("exact_name_match"))
        )
    ): SpeciesResolverTrace =
        SpeciesResolverTrace(
            canonicalCandidates = candidates,
            winningSpecies = species,
            confidence = confidence,
            winnerReason = "exact_name_match",
            evidenceUsed = listOf("name_text")
        )

    private fun pokemon(
        cp: Int?,
        hp: Int?,
        maxHp: Int? = hp,
        name: String?,
        rawOcrText: String = "",
        resolverTrace: SpeciesResolverTrace? = null,
        appraisalAttack: Int? = null
    ): PokemonData =
        PokemonData(
            cp = cp,
            hp = hp,
            maxHp = maxHp,
            name = name,
            realName = name,
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = null,
            rawOcrText = rawOcrText,
            appraisalAttack = appraisalAttack,
            appraisalDefense = null,
            appraisalStamina = null,
            speciesResolverTrace = resolverTrace
        )
}
