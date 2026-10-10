package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import android.os.SystemClock
import java.util.Date

private const val ANCHOR_CONFIDENCE = 0.9f

private fun extractionContext(bitmap: Bitmap, bar: Rect?, request: FrameOcrRequest,
    layout: MLKitOcrProvider.Layout): ExtractionContext = ExtractionContext(
    bar, bar?.let { ScreenGeometryBuilder.deriveNameBand(it, bitmap.width, bitmap.height) },
    request.geometry?.detailCardRect?.top, ActionResourceMatcher.observe(bitmap, layout))

/** The observation always carries typed evidence; a null would be a wiring defect. */
private fun levelEvidenceOf(observation: RecognitionObservation): StardustLevelEvidence =
    observation.powerUpStardustLevelEvidence
        ?: StardustLevelEvidence.Missing(listOf("level_evidence_absent"))

/** The raw textual name decision with the authority token it carries. */
private data class TextualName(val species: String?, val reason: String?)

private fun textualNameDecision(fields: AnchoredScreenText.Fields): TextualName {
    val accepted = fields.name as? SpeciesNameDecision.Accepted
    val token = when (accepted?.source) {
        SpeciesNameAcceptanceSource.EXACT_CANONICAL -> "exact_canonical"
        SpeciesNameAcceptanceSource.REVIEWED_ALIAS -> "reviewed_alias"
        SpeciesNameAcceptanceSource.SAFE_FUZZY -> "unique_structured_distance_one"
        null -> null
    }
    return TextualName(accepted?.species, token)
}

/**
 * Phase 3A: assemble the typed per-frame observation, including the legal level evidence
 * derived from the anchored POWER UP stardust read. The modifier context stays UNKNOWN —
 * no per-frame evidence is trustworthy cost-modifier provenance under the existing
 * contracts (tri-state identity is only established later in the pipeline), so the cost
 * ambiguity is preserved honestly instead of being resolved by a weak signal.
 */
internal fun anchoredRecognitionObservation(
    fields: AnchoredScreenText.Fields,
    frameIndex: Int,
    levelDomain: Set<Double>
): RecognitionObservation = RecognitionObservation(
    candySpecies = fields.candy,
    powerUpStardust = fields.powerUpCost,
    types = fields.types,
    detailScreen = fields.detailScreen,
    numericConflict = fields.numericConflict,
    frameIndex = frameIndex,
    evolutionCandyCost = fields.evolutionCandyCost,
    powerUpStardustLevelEvidence = StardustLevelWindowOracle.evaluate(
        fields.powerUpRead, StardustModifierContext.UNKNOWN, levelDomain)
)

/** One OCR document supplies spatially related fields, including the non-editable candy label. */
internal class AnchoredScreenRecognizer(
    context: Context,
    private val provider: MLKitOcrProvider,
    private val parser: TextParser
) {
    private val calculator = RarityCalculator(context)
    private val resolver by lazy { FamilySpeciesResolver(calculator.recognitionSnapshot) }

    suspend fun recognize(request: FrameOcrRequest): OcrFrameResult {
        val bitmap = request.bitmap
        val frameIndex = request.frameIndex
        val role = request.frameRole
        val cpQuality = request.estimatedCpCropQuality
        val started = SystemClock.elapsedRealtime()
        val locatedBar = HealthBarLocator.locate(bitmap)
        // Phase 2B: a validated compatible persisted calibration may seed the extractor's
        // bar anchor only when live detection fails; provenance stays visible in diagnostics.
        val calibration = request.calibration
        val bar = locatedBar ?: calibration?.seededBarRect
        val barSource = when {
            locatedBar != null -> CalibrationDiagnostic.BAR_SOURCE_LIVE
            calibration?.seededBarRect != null -> CalibrationDiagnostic.BAR_SOURCE_CALIBRATED
            else -> null
        }
        val initialLayout = provider.recognizeLayout(bitmap)
        val layout = ActionResourceTextRecovery.recover(bitmap, initialLayout,
            bar?.let { ScreenGeometryBuilder.deriveNameBand(it, bitmap.width, bitmap.height) },
            provider::recognizeLayout)
        // Phase 2C: geometry-layer inputs for structured extraction — the bar-anchored
        // name band (action anchors must never sit in the title band) and the detail-card
        // top from the frame's ScreenGeometry (candy-row bound when the bar is missing).
        val extractionContext = extractionContext(bitmap, bar, request, layout)
        val fields = AnchoredScreenText.extract(
            layout, parser, bitmap.width, bitmap.height, extractionContext
        )
        // Phase 3A: typed observation assembly, including the POWER UP stardust level evidence.
        val observation = anchoredRecognitionObservation(
            fields,
            frameIndex,
            calculator.recognitionSnapshot?.cpMultipliers?.keys.orEmpty()
        )
        val date = caughtDate(layout, bitmap)
        val (size, lucky) = sizeTagAndLucky(layout)
        val initial = PokemonData(cp = fields.cp, hp = fields.hp?.first, maxHp = fields.hp?.second,
            name = fields.name?.acceptedSpeciesOrNull(), realName = fields.name?.acceptedSpeciesOrNull(),
            candyName = fields.candy, megaEnergy = null, weight = null, height = null,
            stardust = null, caughtDate = date,
            rawOcrText = listOfNotNull(size?.let { "SizeTag:$it" }, "LuckyDetected:$lucky").joinToString("|"),
            recognitionObservation = observation)
        val identity = resolver.resolve(initial)
        val textual = textualNameDecision(fields)
        val pokemon = initial.copy(
            name = identity.species ?: textual.species,
            realName = identity.species ?: textual.species)
        val levelEvidence = levelEvidenceOf(observation)
        val context = FrameRenderContext(
            started, bitmap, bar, fields, date, size, lucky,
            textual, identity, pokemon, frameIndex, role, cpQuality, barSource, levelEvidence,
            calibration?.let { hint ->
                CalibrationDiagnostic(
                    signatureKey = hint.signatureKey,
                    schemaRevision = hint.schemaRevision,
                    resolution = "PENDING",
                    provenance = CalibrationDiagnostic.PROVENANCE_PERSISTED,
                    barSource = barSource,
                    reasonCodes = emptyList(),
                    lookupMs = null,
                    validationMs = null
                )
            })
        return frameResult(context)
    }

    /** Visible size tag and Lucky label of the frame; text reads only, no classifier. */
    private fun sizeTagAndLucky(layout: MLKitOcrProvider.Layout): Pair<String?, Boolean> {
        val tags = layout.lines.map { it.text.trim().uppercase() }
        val size = tags.filter { it in setOf("XXS", "XS", "XL", "XXL") }.distinct().singleOrNull()
        val lucky = tags.any { it == "LUCKY POKÉMON" || it == "LUCKY POKEMON" }
        return size to lucky
    }

    private fun caughtDate(layout: MLKitOcrProvider.Layout, bitmap: Bitmap): Date? =
        layout.lines.filter { it.bounds?.top?.let { top -> top > bitmap.height / 2 } == true }
            .mapNotNull { TextParseUtils.parseDate(it.text) }.distinct().singleOrNull()

    private data class FrameRenderContext(
        val started: Long,
        val bitmap: Bitmap,
        val bar: Rect?,
        val fields: AnchoredScreenText.Fields,
        val date: Date?,
        val size: String?,
        val lucky: Boolean,
        val textual: TextualName,
        val identity: com.pokerarity.scanner.util.ocr.FamilySpeciesResolver.Result,
        val pokemon: PokemonData,
        val frameIndex: Int,
        val role: String,
        val cpQuality: Double?,
        val barSource: String?,
        val levelEvidence: StardustLevelEvidence,
        val calibration: CalibrationDiagnostic?
    )


    private fun frameResult(c: FrameRenderContext): OcrFrameResult = OcrFrameResult(c.pokemon, FrameDiagnostic(
        frameIndex = c.frameIndex, role = c.role,
        imageWidth = c.bitmap.width, imageHeight = c.bitmap.height,
        estimatedCpCropQuality = c.cpQuality,
        screenState = screenState(c.fields),
        screenConfidence = if (c.fields.detailScreen) .9f else 0f,
        anchors = anchorBar(c.bar, c.barSource),
        calibration = c.calibration,
        structuredFields = structuredFieldDiagnostics(c.fields) + listOf(levelEvidenceDiagnostic(c.levelEvidence)),
        crops = anchoredCrops(c.fields),
        fieldCandidates = anchoredCandidates(c),
        stageTimings = listOf(StageTimingDiagnostic("ocr_frame_total", SystemClock.elapsedRealtime() - c.started)),
        selected = PokemonSummary.from(c.pokemon)))

    private fun anchorBar(bar: Rect?, barSource: String?): List<AnchorDiagnostic> = bar?.let {
        listOf(
            AnchorDiagnostic("hp_bar", it.left, it.top, it.right, it.bottom,
                ANCHOR_CONFIDENCE,
                if (barSource == CalibrationDiagnostic.BAR_SOURCE_CALIBRATED) {
                    CALIBRATED_BAR_ANCHOR_REASON
                } else {
                    "green_bar_on_white"
                }))
    }.orEmpty()

    private fun anchoredCrops(fields: AnchoredScreenText.Fields): List<CropDiagnostic> {
        fun crop(field: String, rect: Rect?) = CropDiagnostic(field, "visible_label",
            rect?.left, rect?.top, rect?.right, rect?.bottom, if (rect == null) "missing" else "found",
            provenance = CropProvenance.AnchorDerived.diagnosticName.takeIf { rect != null },
            confidence = if (rect == null) 0f else .85f)
        return listOf(
            crop("Name", fields.nameRect), crop("HP", fields.hpRect),
            crop("Candy", fields.candyRect), crop("PowerUpCost", fields.costRect))
    }

    /** Phase 2C: typed per-field extraction states; values mirror the authoritative fields. */
    private fun structuredFieldDiagnostics(fields: AnchoredScreenText.Fields): List<FieldReadDiagnostic> {
        fun read(field: String, read: FieldRead<*>) = FieldReadDiagnostic(
            field = field,
            status = read.status.name,
            candidateCount = read.candidateCount,
            reasonCode = read.reasonCode,
            value = read.value?.toString()
        )
        return listOf(
            read("Cp", fields.cpRead),
            read("Hp", fields.hpRead),
            read("Candy", fields.candyRead),
            read("PowerUpCost", fields.powerUpRead),
            read("EvolveCost", fields.evolveRead)
        )
    }

    /** Phase 3A bounded diagnostic of the typed level evidence; never a raw/OCR value. */
    private fun levelEvidenceDiagnostic(evidence: StardustLevelEvidence): FieldReadDiagnostic =
        FieldReadDiagnostic(
            field = "PowerUpStardustLevel",
            status = when (evidence) {
                is StardustLevelEvidence.Levels ->
                    if (evidence.contiguous) "LEVEL_WINDOW" else "LEVEL_SET_DISJOINT"
                is StardustLevelEvidence.Invalid -> "INVALID"
                is StardustLevelEvidence.Conflict -> FieldReadStatus.CONFLICT.name
                is StardustLevelEvidence.Unreadable -> FieldReadStatus.VISIBLE_UNREADABLE.name
                is StardustLevelEvidence.Missing -> FieldReadStatus.MISSING_NOT_VISIBLE.name
                is StardustLevelEvidence.Unsupported -> FieldReadStatus.UNSUPPORTED.name
            },
            candidateCount = when (evidence) {
                is StardustLevelEvidence.Levels -> evidence.levels.size
                is StardustLevelEvidence.Conflict -> evidence.candidateCount
                else -> 0
            },
            reasonCode = evidence.reasonCodes.firstOrNull().orEmpty(),
            value = when (evidence) {
                is StardustLevelEvidence.Levels -> "${evidence.minLevel}..${evidence.maxLevel}"
                is StardustLevelEvidence.Invalid -> "cost=${evidence.observedCost}"
                else -> null
            }
        )

    private fun anchoredCandidates(c: FrameRenderContext): List<FieldCandidateDiagnostic> {
        val fields = c.fields
        val textual = c.textual
        val identity = c.identity
        val date = c.date
        val size = c.size
        val lucky = c.lucky
        fun candidate(
            field: String,
            value: Any?,
            reason: String = "anchored_label",
            rect: Rect? = null
        ) = FieldCandidateDiagnostic(
            field, "mlkit_spatial", null, value?.toString(), if (value == null) "missing" else "found",
            cropLeft = rect?.left, cropTop = rect?.top, cropRight = rect?.right, cropBottom = rect?.bottom,
            winner = value != null, reason = reason, selectedValue = value?.toString())
        val nameCandidate = candidate("Name", identity.species, identity.reason, fields.nameRect)
            .copy(status = if (identity.species == null) "uncertain" else "found")
        val nameTextualCandidate = candidate(
            "NameTextual",
            textual.species,
            textual.reason ?: "anchored_label",
            fields.nameRect
        ).copy(status = if (textual.species == null) "missing" else "found")
        return listOf(
            nameCandidate, nameTextualCandidate, candidate("CP", fields.cp),
            candidate("HP", fields.hp?.let { it.first.toString() + "/" + it.second.toString() },
                rect = fields.hpRect),
            candidate("Candy", fields.candy, rect = fields.candyRect),
            candidate("PowerUpCost", fields.powerUpCost, rect = fields.costRect),
            candidate("EvolutionCandyCost", fields.evolutionCandyCost), candidate("Date", date),
            candidate("SizeTag", size), candidate("LuckyDetected", lucky.takeIf { it }))
    }

    private fun screenState(fields: AnchoredScreenText.Fields): String = when {
        !fields.detailScreen -> ScreenType.Unknown.name
        fields.cp == null -> ScreenType.PokemonDetailScrolled.name
        else -> ScreenType.PokemonDetail.name
    }
}
