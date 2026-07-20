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
    fun resolverConfidenceWithoutHardNameProvenanceNeverAccepts() {
        val trace = resolverTrace("Pikachu", 0.99f).copy(
            winnerReason = "resolver_score_only",
            canonicalCandidates = listOf(
                SpeciesCandidateDiagnostic("Pikachu", score = 0.99f, winner = true, reasons = listOf("profile_fit"))
            )
        )
        val pokemon = pokemon(cp = 777, hp = 88, maxHp = 88, name = "Pikachu", resolverTrace = trace)
        val genericNameEvidence = SpeciesEvidence.fromFieldCandidates(
            candidates = listOf(nameCandidate("Pikachu")),
            profileStatus = SpeciesProfileStatus.COMPATIBLE
        )

        val decision = gate.evaluate(
            input(
                pokemon = pokemon,
                frames = listOf(detailFrame(pokemon), detailFrame(pokemon, frameIndex = 1)),
                cpCropQuality = 0.82,
                speciesEvidence = genericNameEvidence
            )
        )

        assertTrue(decision.decision != ScanDecisionType.ACCEPT)
        assertFalse(decision.collectionSafe)
    }

    @Test
    fun authorityFactoryRequiresAcceptedStatusAndMapsReviewedAlias() {
        val rejectedExact = SpeciesEvidence.fromFieldCandidates(
            listOf(nameDiagnostic("rejected", "winner:exact_canonical")),
            SpeciesProfileStatus.COMPATIBLE
        )
        val reviewed = SpeciesEvidence.fromFieldCandidates(
            listOf(nameDiagnostic("found", "winner:reviewed_alias")),
            SpeciesProfileStatus.COMPATIBLE
        )

        assertEquals(SpeciesAuthority.NO_MATCH, rejectedExact.authority)
        assertEquals(SpeciesAuthority.REVIEWED_ALIAS, reviewed.authority)
        assertTrue(reviewed.hasHardAuthority)
    }

    @Test
    fun repeatedSafeFuzzyDiagnosticsRemainSoft() {
        val evidence = SpeciesEvidence.fromFieldCandidates(
            listOf(
                nameDiagnostic("found", "winner:unique_structured_distance_one"),
                nameDiagnostic("found", "winner:unique_structured_distance_one")
            ),
            SpeciesProfileStatus.COMPATIBLE
        )

        assertEquals(SpeciesAuthority.SAFE_FUZZY, evidence.authority)
        assertFalse(evidence.hasHardAuthority)
    }

    @Test
    fun uncertainNoMatchAndBadProfilesBlockAccept() {
        val pokemon = pokemon(777, 88, 88, "Pikachu", resolverTrace = resolverTrace("Pikachu", 0.99f))
        val blocked = listOf(
            exactCompatibleEvidence(pokemon).copy(authority = SpeciesAuthority.UNCERTAIN),
            SpeciesEvidence.failClosed(SpeciesProfileStatus.COMPATIBLE),
            exactCompatibleEvidence(pokemon).withProfileStatus(SpeciesProfileStatus.CONTRADICTORY),
            exactCompatibleEvidence(pokemon).withProfileStatus(SpeciesProfileStatus.IMPOSSIBLE)
        )

        blocked.forEach { evidence ->
            val decision = gate.evaluate(
                input(pokemon, listOf(detailFrame(pokemon)), 0.82, evidence)
            )
            assertTrue(evidence.toString(), decision.decision != ScanDecisionType.ACCEPT)
            assertFalse(evidence.toString(), decision.collectionSafe)
        }
    }

    @Test
    fun matchingVisualEvidenceDoesNotPromoteFuzzyAuthority() {
        val pokemon = pokemon(777, 88, 88, "Pikachu", resolverTrace = resolverTrace("Pikachu", 0.99f))
        val fuzzy = exactCompatibleEvidence(pokemon).copy(authority = SpeciesAuthority.SAFE_FUZZY)

        val decision = gate.evaluate(input(pokemon, listOf(detailFrame(pokemon)), 0.82, fuzzy))

        assertTrue(decision.decision != ScanDecisionType.ACCEPT)
        assertFalse(decision.collectionSafe)
    }

    @Test
    fun contradictoryProfileNeverAccepts() {
        val pokemon = pokemon(
            cp = 10,
            hp = 999,
            maxHp = 999,
            name = "Pikachu",
            resolverTrace = resolverTrace("Pikachu", 0.92f)
        ).copy(arcLevel = 1.5f)

        val decision = gate.evaluate(
            input(
                pokemon = pokemon,
                frames = listOf(detailFrame(pokemon), detailFrame(pokemon, frameIndex = 1)),
                cpCropQuality = 0.82,
                speciesEvidence = exactCompatibleEvidence(pokemon).copy(
                    profileStatus = SpeciesProfileStatus.IMPOSSIBLE,
                    reasonCodes = listOf(
                        SpeciesEvidenceReason.EXACT,
                        SpeciesEvidenceReason.PROFILE_IMPOSSIBLE
                    )
                )
            )
        )

        assertTrue(decision.decision != ScanDecisionType.ACCEPT)
        assertFalse(decision.collectionSafe)
    }

    @Test
    fun developerReasonsAreStablePrivacySafeCodes() {
        val selectedToken = "raw_candidate_secret"
        val combinedToken = "C_Users_alice_scan_png_2026_07_19_token_credential"
        val visualToken = "username_alice"
        val trace = resolverTrace(
            species = selectedToken,
            confidence = 0.99f,
            candidates = listOf(
                SpeciesCandidateDiagnostic(selectedToken, score = 0.72f, winner = true, reasons = listOf("name")),
                SpeciesCandidateDiagnostic(combinedToken, score = 0.70f, winner = false, reasons = listOf("name"))
            )
        )
        val pokemon = pokemon(cp = 777, hp = 88, maxHp = 88, name = selectedToken, resolverTrace = trace)
        val baseInput = input(pokemon, listOf(detailFrame(pokemon)), cpCropQuality = 0.82)

        val decision = gate.evaluate(
            baseInput.copy(
                visualSummary = baseInput.visualSummary?.copy(
                    classifierSpecies = visualToken,
                    classifierConfidence = 0.99f
                )
            )
        )
        val safeCode = Regex("^[a-z0-9]+(?:_[a-z0-9]+)*$")
        val injectedTokens = listOf(selectedToken, combinedToken, visualToken)

        assertTrue(
            decision.developerReasons.toString(),
            decision.developerReasons.all { reason ->
                safeCode.matches(reason) && injectedTokens.none { token -> reason.contains(token, ignoreCase = true) }
            }
        )
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
        assertTrue(decision.developerReasons.contains(SpeciesEvidenceReason.CANDIDATES_CLOSE))
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
        assertTrue(unknownDecision.developerReasons.contains("appraisal_ignored_for_screen"))
    }

    private fun input(
        pokemon: PokemonData,
        frames: List<FrameDiagnostic>,
        cpCropQuality: Double? = null,
        speciesEvidence: SpeciesEvidence = exactCompatibleEvidence(pokemon)
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
            ),
            speciesEvidence = speciesEvidence
        )

    private fun exactCompatibleEvidence(pokemon: PokemonData): SpeciesEvidence {
        val species = pokemon.realName ?: pokemon.name
        if (species.isNullOrBlank() || species.equals("Unknown", ignoreCase = true) || species == "missing") {
            return SpeciesEvidence.failClosed(SpeciesProfileStatus.COMPATIBLE)
        }
        return SpeciesEvidence(
            selectedCanonicalSpecies = species,
            authority = SpeciesAuthority.EXACT_CANONICAL,
            profileStatus = SpeciesProfileStatus.COMPATIBLE,
            reasonCodes = listOf(
                SpeciesEvidenceReason.EXACT,
                SpeciesEvidenceReason.PROFILE_COMPATIBLE
            ),
            observationsAgree = true,
            authorityConflict = false
        )
    }

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

    private fun nameDiagnostic(status: String, reason: String): FieldCandidateDiagnostic =
        FieldCandidateDiagnostic(
            field = "Name",
            source = "test",
            rawText = "ignored",
            parsedValue = "Pikachu",
            status = status,
            candidateScore = 0.92f,
            winner = true,
            reason = reason,
            selectedValue = "Pikachu"
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
