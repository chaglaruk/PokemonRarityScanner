package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import android.os.SystemClock

/** One OCR document supplies spatially related fields, including the non-editable candy label. */
internal class AnchoredScreenRecognizer(context: Context, private val provider: MLKitOcrProvider, private val parser: TextParser) {
    private val calculator = RarityCalculator(context)
    private val resolver by lazy { FamilySpeciesResolver(calculator.recognitionProfiles, calculator) }

    suspend fun recognize(bitmap: Bitmap, frameIndex: Int, role: String, cpQuality: Double?): OcrFrameResult {
        val started = SystemClock.elapsedRealtime()
        val bar = HealthBarLocator.locate(bitmap)
        val layout = provider.recognizeLayout(bitmap)
        val fields = AnchoredScreenText.extract(layout, parser, bitmap.width, bitmap.height, bar)
        val observation = RecognitionObservation(fields.candy, fields.powerUpCost, fields.types, fields.detailScreen, fields.numericConflict, frameIndex)
        val date = layout.lines.filter { it.bounds?.top?.let { top -> top > bitmap.height / 2 } == true }
            .mapNotNull { TextParseUtils.parseDate(it.text) }.distinct().singleOrNull()
        val tags = layout.lines.map { it.text.trim().uppercase() }
        val size = tags.filter { it in setOf("XXS", "XS", "XL", "XXL") }.distinct().singleOrNull()
        val lucky = tags.any { it == "LUCKY POKÉMON" || it == "LUCKY POKEMON" }
        val parsedName = fields.name?.acceptedSpeciesOrNull()
        val initial = PokemonData(cp = fields.cp, hp = fields.hp?.first, maxHp = fields.hp?.second,
            name = parsedName, realName = parsedName, candyName = fields.candy, megaEnergy = null,
            weight = null, height = null, stardust = null, caughtDate = date,
            rawOcrText = listOfNotNull(size?.let { "SizeTag:$it" }, "LuckyDetected:$lucky").joinToString("|"),
            recognitionObservation = observation)
        val identity = resolver.resolve(initial)
        val pokemon = initial.copy(name = identity.species ?: parsedName, realName = identity.species ?: parsedName)
        fun candidate(field: String, value: Any?, reason: String = "anchored_label", rect: Rect? = null) = FieldCandidateDiagnostic(
            field, "mlkit_spatial", null, value?.toString(), if (value == null) "missing" else "found",
            cropLeft = rect?.left, cropTop = rect?.top, cropRight = rect?.right, cropBottom = rect?.bottom,
            winner = value != null, reason = reason, selectedValue = value?.toString())
        fun crop(field: String, rect: Rect?) = CropDiagnostic(field, "visible_label", rect?.left, rect?.top,
            rect?.right, rect?.bottom, if (rect == null) "missing" else "found",
            provenance = CropProvenance.AnchorDerived.diagnosticName.takeIf { rect != null }, confidence = if (rect == null) 0f else .85f)
        val nameCandidate = candidate("Name", identity.species, identity.reason, fields.nameRect).copy(
            status = if (identity.species == null) "uncertain" else "found")
        return OcrFrameResult(pokemon, FrameDiagnostic(frameIndex = frameIndex, role = role,
            imageWidth = bitmap.width, imageHeight = bitmap.height, estimatedCpCropQuality = cpQuality,
            screenState = if (fields.detailScreen) {
                if (fields.cp == null) ScreenType.PokemonDetailScrolled.name else ScreenType.PokemonDetail.name
            } else ScreenType.Unknown.name,
            screenConfidence = if (fields.detailScreen) .9f else 0f,
            anchors = bar?.let { listOf(AnchorDiagnostic("hp_bar", it.left, it.top, it.right, it.bottom, .9f, "green_bar_on_white")) }.orEmpty(),
            crops = listOf(crop("Name", fields.nameRect), crop("HP", fields.hpRect), crop("Candy", fields.candyRect), crop("PowerUpCost", fields.costRect)),
            fieldCandidates = listOf(nameCandidate, candidate("CP", fields.cp), candidate("HP", fields.hp?.let { "${it.first}/${it.second}" }, rect = fields.hpRect),
                candidate("Candy", fields.candy, rect = fields.candyRect), candidate("PowerUpCost", fields.powerUpCost, rect = fields.costRect),
                candidate("Date", date), candidate("SizeTag", size), candidate("LuckyDetected", lucky.takeIf { it })),
            stageTimings = listOf(StageTimingDiagnostic("ocr_frame_total", SystemClock.elapsedRealtime() - started)),
            selected = PokemonSummary.from(pokemon)))
    }
}
