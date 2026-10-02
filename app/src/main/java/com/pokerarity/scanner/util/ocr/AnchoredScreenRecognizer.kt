package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import android.os.SystemClock
import java.util.Date

private const val ANCHOR_CONFIDENCE = 0.9f

/** One OCR document supplies spatially related fields, including the non-editable candy label. */
internal class AnchoredScreenRecognizer(
    context: Context,
    private val provider: MLKitOcrProvider,
    private val parser: TextParser
) {
    private val calculator = RarityCalculator(context)
    private val resolver by lazy { FamilySpeciesResolver(calculator.recognitionProfiles, calculator) }

    suspend fun recognize(bitmap: Bitmap, frameIndex: Int, role: String, cpQuality: Double?): OcrFrameResult {
        val started = SystemClock.elapsedRealtime()
        val bar = HealthBarLocator.locate(bitmap)
        val layout = provider.recognizeLayout(bitmap)
        val fields = AnchoredScreenText.extract(layout, parser, bitmap.width, bitmap.height, bar)
        val observation = RecognitionObservation(fields.candy, fields.powerUpCost, fields.types,
            fields.detailScreen, fields.numericConflict, frameIndex, fields.evolutionCandyCost)
        val date = caughtDate(layout, bitmap)
        val tags = layout.lines.map { it.text.trim().uppercase() }
        val size = tags.filter { it in setOf("XXS", "XS", "XL", "XXL") }.distinct().singleOrNull()
        val lucky = tags.any { it == "LUCKY POKÉMON" || it == "LUCKY POKEMON" }
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
        val context = FrameRenderContext(
            started, bitmap, bar, fields, date, size, lucky,
            textual, identity, pokemon, frameIndex, role, cpQuality)
        return frameResult(context)
    }

    private fun caughtDate(layout: MLKitOcrProvider.Layout, bitmap: Bitmap): Date? =
        layout.lines.filter { it.bounds?.top?.let { top -> top > bitmap.height / 2 } == true }
            .mapNotNull { TextParseUtils.parseDate(it.text) }.distinct().singleOrNull()

    /** The raw textual name decision with the authority token it carries. */
    private data class TextualName(val species: String?, val reason: String?)

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
        val cpQuality: Double?
    )

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

    private fun frameResult(c: FrameRenderContext): OcrFrameResult = OcrFrameResult(c.pokemon, FrameDiagnostic(
        frameIndex = c.frameIndex, role = c.role,
        imageWidth = c.bitmap.width, imageHeight = c.bitmap.height,
        estimatedCpCropQuality = c.cpQuality,
        screenState = screenState(c.fields),
        screenConfidence = if (c.fields.detailScreen) .9f else 0f,
        anchors = anchorBar(c.bar),
        crops = anchoredCrops(c.fields),
        fieldCandidates = anchoredCandidates(c),
        stageTimings = listOf(StageTimingDiagnostic("ocr_frame_total", SystemClock.elapsedRealtime() - c.started)),
        selected = PokemonSummary.from(c.pokemon)))

    private fun anchorBar(bar: Rect?): List<AnchorDiagnostic> = bar?.let {
        listOf(
            AnchorDiagnostic("hp_bar", it.left, it.top, it.right, it.bottom,
                ANCHOR_CONFIDENCE, "green_bar_on_white"))
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
