package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.IvSolveDetails
import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VisualFeatures
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrDiagnosticsExporterTest {

    @Test
    fun buildSummaryJson_includesRecognitionFields() {
        val pokemon = PokemonData(
            cp = 3266,
            hp = 168,
            maxHp = 168,
            name = "Porygon-z",
            realName = "Porygon-Z",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = 10000,
            caughtDate = null,
            rawOcrText = "ClassifierSpecies:Porygon-Z|FullVariantSpecies:Porygon-Z|FullVariantShiny:false|FullVariantCostume:false|FullVariantForm:false",
            powerUpCandyCost = 10,
            powerUpCandySource = "row_pair_alt",
            powerUpStardustSource = "row_pair_alt"
        )
        val solve = IvSolveDetails(
            ivMin = 80,
            ivMax = 100,
            ivCandidateCount = 3,
            levelMin = 39.0f,
            levelMax = 40.5f,
            ivSolveMode = IvSolveMode.RANGE,
            ivSolveSignalsUsed = listOf("cp", "hp", "stardust", "candy")
        )

        val summary = OcrDiagnosticsExporter.buildSummaryJsonForTest(
            screenshotPath = "C:/tmp/test.png",
            pokemon = pokemon,
            solve = solve,
            whyNotExact = "Multiple candidates remain."
        )
        val json = JsonParser.parseString(summary).asJsonObject

        assertEquals("Porygon-Z", json.get("species").asString)
        assertEquals("Porygon-Z", json.get("classifierSpecies").asString)
        assertEquals("Porygon-Z", json.get("fullVariantSpecies").asString)
        assertEquals(false, json.get("shiny").asBoolean)
        assertEquals(false, json.get("costume").asBoolean)
        assertEquals(false, json.get("form").asBoolean)
        assertTrue(json.getAsJsonObject("selectedSources").has("powerUpStardust"))
    }

    @Test
    fun buildSummaryJson_prefersStructuredTrace() {
        val trace = com.pokerarity.scanner.data.model.VariantDecisionTrace(
            classifierSpecies = "Pikachu",
            fullVariantSpecies = "Pikachu",
            fullVariantShiny = true,
            fullVariantCostume = true,
            fullVariantForm = false
        )
        val pokemon = PokemonData(
            cp = 10,
            hp = 10,
            maxHp = 10,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = 200,
            caughtDate = null,
            rawOcrText = "ClassifierSpecies:Wrong|FullVariantSpecies:Wrong|FullVariantShiny:false|FullVariantCostume:false|FullVariantForm:true",
            variantDecisionTrace = trace
        )

        val summary = OcrDiagnosticsExporter.buildSummaryJsonForTest(
            screenshotPath = "C:/tmp/test.png",
            pokemon = pokemon,
            solve = null,
            whyNotExact = null
        )
        val json = JsonParser.parseString(summary).asJsonObject

        assertEquals("Pikachu", json.get("species").asString)
        assertEquals("Pikachu", json.get("classifierSpecies").asString)
        assertEquals("Pikachu", json.get("fullVariantSpecies").asString)
        assertEquals(true, json.get("shiny").asBoolean)
        assertEquals(true, json.get("costume").asBoolean)
        assertEquals(false, json.get("form").asBoolean)
        
        // Ensure structured trace itself wasn't bluntly dumped with paths or secrets
        assertTrue(!json.has("variantDecisionTrace")) 
    }

    @Test
    fun buildSummaryJson_tracePrivacyBoundaries() {
        // Create a trace populated with fake secrets/paths to ensure the exporter selectively 
        // extracts only the safe classification fields and doesn't leak the whole object or its debug info.
        val trace = com.pokerarity.scanner.data.model.VariantDecisionTrace(
            classifierSpecies = "Pikachu",
            fullVariantDebug = "Debug path: C:/Users/name/Desktop or /tmp/secret token=XYZ123",
            fullVariantSpecies = "Pikachu",
            fullVariantShiny = false,
            fullVariantCostume = false,
            fullVariantForm = false
        )
        val pokemon = PokemonData(
            cp = 10,
            hp = 10,
            maxHp = 10,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = 200,
            caughtDate = null,
            rawOcrText = "Name:Pikachu",
            variantDecisionTrace = trace
        )

        val summary = OcrDiagnosticsExporter.buildSummaryJsonForTest(
            screenshotPath = "fake.png",
            pokemon = pokemon,
            solve = null,
            whyNotExact = null
        )
        
        // Exporter output should NOT contain any of the private data passed in fullVariantDebug
        assertTrue(!summary.contains("C:/Users"))
        assertTrue(!summary.contains("/tmp"))
        assertTrue(!summary.contains("token="))
        assertTrue(!summary.contains("XYZ123"))
        
        // It should still have the legitimate exported fields
        val json = JsonParser.parseString(summary).asJsonObject
        assertEquals("Pikachu", json.get("species").asString)
        assertEquals("Pikachu", json.get("classifierSpecies").asString)
        
        // Ensure no variantDecisionTrace block was serialized directly
        assertTrue(!json.has("variantDecisionTrace"))
    }

    @Test
    fun buildSummaryJson_traceFlowDoesNotPolluteRawOcrText() {
        val trace = com.pokerarity.scanner.data.model.VariantDecisionTrace(
            classifierSpecies = "Charizard",
            fullVariantSpecies = "Charizard"
        )
        val cleanRawText = "CP:1500|HP:120"
        val pokemon = PokemonData(
            cp = 1500,
            hp = 120,
            maxHp = 120,
            name = "Charizard",
            realName = "Charizard",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = 200,
            caughtDate = null,
            rawOcrText = cleanRawText,
            variantDecisionTrace = trace
        )

        val summary = OcrDiagnosticsExporter.buildSummaryJsonForTest(
            screenshotPath = "fake.png",
            pokemon = pokemon,
            solve = null,
            whyNotExact = null
        )
        val json = JsonParser.parseString(summary).asJsonObject
        
        // Assert rawOcrText in export strictly remains the clean input, 
        // free from legacy Classifier*/FullVariant* appends
        val exportedRawText = json.get("rawOcrText").asString
        assertEquals(cleanRawText, exportedRawText)
        assertTrue(!exportedRawText.contains("Classifier"))
        assertTrue(!exportedRawText.contains("FullVariant"))
    }

    @Test
    fun buildSummaryJson_includesPhaseAFrameDiagnostics() {
        val pokemon = PokemonData(
            cp = 25,
            hp = null,
            maxHp = null,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = null,
            rawOcrText = "CP:25|Name:Pikachu"
        )
        val report = ScanDiagnosticReport(
            diagnosticId = "local-test",
            screenState = "PokemonDetail",
            screenConfidence = 0.82f,
            stageTimings = listOf(StageTimingDiagnostic("total", 1234L)),
            frames = listOf(
                FrameDiagnostic(
                    frameIndex = 0,
                    timestampEpochMs = 123L,
                    imageWidth = 1080,
                    imageHeight = 2340,
                    estimatedCpCropQuality = 0.75,
                    screenState = "PokemonDetail",
                    screenConfidence = 0.82f,
                    anchors = listOf(AnchorDiagnostic("DetailCard", 0, 980, 1080, 2100, 0.74f, "large_neutral_bright_panel")),
                    geometryFallbackReasons = emptyList(),
                    crops = listOf(CropDiagnostic("CP", "cp_mask", 108, 128, 972, 221, "used", "anchor-derived", 0.78f, listOf("cp_header_anchor"))),
                    ocrBlocks = listOf(OcrBlockDiagnostic("CP 25", 100, 120, 220, 160)),
                    fieldCandidates = listOf(
                        FieldCandidateDiagnostic(
                            field = "CP",
                            source = "cp_mask",
                            rawText = "CP 25",
                            parsedValue = "25",
                            status = "found",
                            cropName = "cp_mask",
                            cropLeft = 108,
                            cropTop = 128,
                            cropRight = 972,
                            cropBottom = 221,
                            cropProvenance = "anchor-derived",
                            cropConfidence = 0.78f,
                            preprocessing = "cp_mask",
                            normalizedText = "25",
                            parserResult = "25",
                            candidateScore = 0.92f,
                            winner = true,
                            reason = "winner:cp_numeric_parsed",
                            selectedValue = "25"
                        )
                    ),
                    stageTimings = listOf(StageTimingDiagnostic("ocr_date", 42L)),
                    selected = PokemonSummary.from(pokemon)
                )
            ),
            finalPokemon = PokemonSummary.from(pokemon),
            rarityBreakdown = mapOf("Age Score" to 21),
            variantSummary = VariantVisualSummary.from(VisualFeatures(isShiny = true, confidence = 0.82f), pokemon.variantDecisionTrace)
        )

        val json = JsonParser.parseString(
            OcrDiagnosticsExporter.buildSummaryJsonForTest(
                screenshotPath = "fake.png",
                pokemon = pokemon,
                solve = null,
                whyNotExact = null,
                scanReport = report
            )
        ).asJsonObject

        val diagnostics = json.getAsJsonObject("scanDiagnostics")
        assertEquals("PokemonDetail", diagnostics.get("screenState").asString)
        val frame = diagnostics.getAsJsonArray("frames")[0].asJsonObject
        assertEquals(1080, frame.get("imageWidth").asInt)
        assertEquals(2340, frame.get("imageHeight").asInt)
        assertEquals("PokemonDetail", frame.get("screenState").asString)
        assertEquals("DetailCard", frame.getAsJsonArray("anchors")[0].asJsonObject.get("name").asString)
        assertEquals("CP", frame.getAsJsonArray("crops")[0].asJsonObject.get("field").asString)
        assertEquals("anchor-derived", frame.getAsJsonArray("crops")[0].asJsonObject.get("provenance").asString)
        assertEquals("CP 25", frame.getAsJsonArray("ocrBlocks")[0].asJsonObject.get("text").asString)
        val candidate = frame.getAsJsonArray("fieldCandidates")[0].asJsonObject
        assertEquals("cp_mask", candidate.get("preprocessing").asString)
        assertEquals("25", candidate.get("normalizedText").asString)
        assertEquals("winner:cp_numeric_parsed", candidate.get("reason").asString)
        assertTrue(candidate.get("winner").asBoolean)
        assertEquals(1234L, diagnostics.getAsJsonArray("stageTimings")[0].asJsonObject.get("durationMs").asLong)
        assertEquals(42L, frame.getAsJsonArray("stageTimings")[0].asJsonObject.get("durationMs").asLong)
        assertEquals(21, diagnostics.getAsJsonObject("rarityBreakdown").get("Age Score").asInt)
        assertTrue(diagnostics.getAsJsonObject("variantSummary").get("isShiny").asBoolean)
    }

    @Test
    fun buildSummaryJson_stableOcrFieldsMarkUnavailableValuesAsNotRun() {
        val pokemon = PokemonData(
            cp = 25,
            hp = null,
            maxHp = null,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = null,
            rawOcrText = "CP:25|Name:Pikachu"
        )

        val json = JsonParser.parseString(
            OcrDiagnosticsExporter.buildSummaryJsonForTest(
                screenshotPath = "fake.png",
                pokemon = pokemon,
                solve = null,
                whyNotExact = null
            )
        ).asJsonObject
        val stable = json.getAsJsonObject("stableOcrFields")

        assertEquals("found", stable.getAsJsonObject("CP").get("status").asString)
        assertEquals("missing", stable.getAsJsonObject("HP").get("status").asString)
        assertEquals("not-run", stable.getAsJsonObject("AppraisalAttack").get("status").asString)
        assertTrue(stable.getAsJsonObject("AppraisalAttack").get("value").isJsonNull)
        assertTrue(stable.has("RawText"))
        assertTrue(stable.has("LuckyDetected"))
    }

    @Test
    fun buildSummaryJson_includesResolverTraceWhenAvailable() {
        val trace = SpeciesResolverTrace(
            displayNameCandidates = listOf(
                DisplayNameCandidateDiagnostic(
                    field = "NameDynamic",
                    rawText = "Pikachu",
                    normalizedText = "pikachu",
                    parsedSpecies = "Pikachu",
                    score = 0.98f,
                    status = "found",
                    source = "mlkit_dynamic"
                )
            ),
            canonicalCandidates = listOf(
                SpeciesCandidateDiagnostic(
                    species = "Pikachu",
                    form = null,
                    score = 0.98f,
                    winner = true,
                    reasons = listOf("exact_name_match"),
                    loserReason = null
                )
            ),
            formCandidates = emptyList(),
            winningSpecies = "Pikachu",
            winningForm = null,
            confidence = 0.98f,
            winnerReason = "exact_name_match",
            loserReasons = emptyList(),
            evidenceUsed = listOf("name_dynamic"),
            evidenceMissing = listOf("form_label"),
            fallbackPath = "resolver_trace_only"
        )
        val pokemon = PokemonData(
            cp = null,
            hp = null,
            maxHp = null,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = null,
            rawOcrText = "NameDynamic:Pikachu",
            speciesResolverTrace = trace
        )

        val json = JsonParser.parseString(
            OcrDiagnosticsExporter.buildSummaryJsonForTest(
                screenshotPath = "fake.png",
                pokemon = pokemon,
                solve = null,
                whyNotExact = null
            )
        ).asJsonObject

        val resolverTrace = json.getAsJsonObject("resolverTrace")
        assertEquals("Pikachu", resolverTrace.get("winningSpecies").asString)
        assertEquals("exact_name_match", resolverTrace.get("winnerReason").asString)
    }

    @Test
    fun buildSummaryJson_includesLocalScanDecisionWhenAvailable() {
        val decision = ScanDecision(
            decision = ScanDecisionType.ACCEPT_LOW_CONFIDENCE,
            confidence = 0.63f,
            severity = ScanDecisionSeverity.WARNING,
            userSafeReason = "Pokemon scan accepted with limited supporting evidence.",
            developerReasons = listOf("geometry_legacy_fallback"),
            evidenceUsed = listOf("screen_state", "legacy_geometry_fallback"),
            evidenceMissing = listOf("hp"),
            recommendedNextAction = "show_result_with_review",
            retryEligible = false,
            mayShowOverlay = true,
            maySaveScan = true,
            collectionSafe = false
        )
        val pokemon = PokemonData(
            cp = 25,
            hp = null,
            maxHp = null,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = null,
            rawOcrText = "CP:25|Name:Pikachu",
            scanDecision = decision
        )
        val report = ScanDiagnosticReport(
            diagnosticId = "local-gate-test",
            screenState = "PokemonDetail",
            screenConfidence = 0.82f,
            frames = emptyList(),
            finalPokemon = PokemonSummary.from(pokemon),
            scanDecision = decision
        )

        val json = JsonParser.parseString(
            OcrDiagnosticsExporter.buildSummaryJsonForTest(
                screenshotPath = "fake.png",
                pokemon = pokemon,
                solve = null,
                whyNotExact = null,
                scanReport = report
            )
        ).asJsonObject

        val rootDecision = json.getAsJsonObject("scanDecision")
        assertEquals("ACCEPT_LOW_CONFIDENCE", rootDecision.get("decision").asString)
        assertEquals(false, rootDecision.get("collectionSafe").asBoolean)
        val diagnosticDecision = json.getAsJsonObject("scanDiagnostics").getAsJsonObject("scanDecision")
        assertEquals("show_result_with_review", diagnosticDecision.get("recommendedNextAction").asString)
        assertEquals("geometry_legacy_fallback", diagnosticDecision.getAsJsonArray("developerReasons")[0].asString)
    }
}
