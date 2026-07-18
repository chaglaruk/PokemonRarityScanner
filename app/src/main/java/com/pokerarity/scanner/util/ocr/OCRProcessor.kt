package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.pokerarity.scanner.data.model.PokemonData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.Collections

private const val DYNAMIC_RANKING_WEIGHT = 0.60f
private const val DYNAMIC_POSITION_WEIGHT = 0.40f
private const val STATIC_RANKING_WEIGHT = 0.75f
private const val STATIC_CROP_WEIGHT = 0.25f
private const val SPECIES_AGREEMENT_BOOST = 0.04f

class OCRProcessor(private val context: Context) {

    private val textParser = TextParser(context)
    private val mlKitOcrProvider by lazy { MLKitOcrProvider(context) }
    private val screenGeometryBuilder = ScreenGeometryBuilder()

    private val initLock = Any()
    private var isInitialized = false

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        var shouldWarmUp = false
        synchronized(initLock) {
            if (!isInitialized) {
                ImagePreprocessor.ensureOpenCvReady()
                shouldWarmUp = true
            }
        }
        if (shouldWarmUp) {
            mlKitOcrProvider.warmUp()
            synchronized(initLock) { isInitialized = true }
        }
        Log.d("OCRProcessor", "ML Kit OCR ready")
    }

    suspend fun ensureInitialized() = initialize()

    fun release() {
        mlKitOcrProvider.close()
        isInitialized = false
    }

    suspend fun processImage(bitmap: Bitmap, includeSecondaryFields: Boolean = true): PokemonData =
        processImageWithDiagnostics(bitmap, includeSecondaryFields).pokemon

    suspend fun processImageWithDiagnostics(
        bitmap: Bitmap,
        includeSecondaryFields: Boolean = true,
        frameIndex: Int = 0,
        frameRole: String = "fast",
        estimatedCpCropQuality: Double? = null
    ): OcrFrameResult = withContext(Dispatchers.Default) {
        initialize()

        val frameStart = System.currentTimeMillis()
        val timings = Collections.synchronizedList(mutableListOf<StageTimingDiagnostic>())
        suspend fun <T> timed(stage: String, block: suspend () -> T): T {
            val started = System.currentTimeMillis()
            return try {
                block()
            } finally {
                timings += StageTimingDiagnostic(stage, System.currentTimeMillis() - started)
            }
        }

        val geometry = timed("screen_geometry") { screenGeometryBuilder.build(bitmap) }

        val cpDeferred = async { timed("ocr_cp") { recognizeCp(bitmap, geometry) } }
        val nameDeferred = async { timed("ocr_name") { recognizeName(bitmap, geometry) } }
        val dateDeferred = async { timed("ocr_date") { recognizeDate(bitmap, geometry) } }
        val candyDeferred = async { timed("ocr_candy") { recognizeCandy(bitmap, geometry, includeSecondaryFields) } }
        val stardustDeferred = async { timed("ocr_stardust") { recognizeStardust(bitmap, geometry, includeSecondaryFields) } }
        val sizeTagDeferred = async { timed("ocr_size_tag") { recognizeSizeTag(bitmap, geometry, includeSecondaryFields) } }
        val appraisalDeferred = async { timed("ocr_appraisal") { recognizeAppraisalStats(bitmap, geometry, includeSecondaryFields) } }
        val luckyDeferred = async { timed("ocr_lucky") { recognizeLuckyLabel(bitmap, geometry, includeSecondaryFields) } }

        val cpResult = cpDeferred.await()
        val hpResult = timed("ocr_hp") { recognizeHp(bitmap, geometry, cpResult.value) }
        val nameResult = nameDeferred.await()
        val caughtDateResult = dateDeferred.await()
        val candyResult = candyDeferred.await()
        val stardustResult = stardustDeferred.await()
        val sizeTagResult = sizeTagDeferred.await()
        val appraisalResults = appraisalDeferred.await()
        val luckyResult = luckyDeferred.await()

        val raw = buildRawOcrText(
            cpResult = cpResult,
            hpResult = hpResult,
            nameResult = nameResult,
            candyResult = candyResult,
            caughtDateResult = caughtDateResult,
            stardustResult = stardustResult,
            sizeTagResult = sizeTagResult,
            appraisalResults = appraisalResults,
            luckyResult = luckyResult
        )

        val pokemon = PokemonData(
            cp = cpResult.value,
            hp = hpResult.value?.first,
            maxHp = hpResult.value?.second,
            name = nameResult.value,
            realName = nameResult.value,
            candyName = candyResult.value,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = stardustResult.value,
            arcLevel = null,
            caughtDate = caughtDateResult.value,
            rawOcrText = raw,
            fullVariantMatch = null,
            powerUpCandyCost = null,
            powerUpCandySource = null,
            powerUpStardustSource = null,
            appraisalAttack = appraisalResults.attack.value,
            appraisalDefense = appraisalResults.defense.value,
            appraisalStamina = appraisalResults.stamina.value,
            appraisalConfidence = appraisalResults.confidence,
            arcEstimatedLevel = null,
            arcSource = null,
            ocrDiagnosticsDir = null,
            ocrDiagnosticsFiles = emptyMap()
        )
        val diagnostic = FrameDiagnostic(
            frameIndex = frameIndex,
            role = frameRole,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            estimatedCpCropQuality = estimatedCpCropQuality,
            screenState = geometry.classification.screenType.name,
            screenConfidence = geometry.classification.confidence,
            anchors = geometry.anchors.map { it.toDiagnostic() },
            geometryFallbackReasons = geometry.fallbackReasons,
            crops = (
                cpResult.crops +
                    hpResult.crops +
                    nameResult.crops +
                    candyResult.crops +
                    caughtDateResult.crops +
                    stardustResult.crops +
                    sizeTagResult.crops +
                    appraisalResults.crops +
                    luckyResult.crops +
                    appraisalCropDiagnostic(geometry) +
                    notRunCropDiagnostics(geometry)
                )
                .distinctBy { "${it.field}:${it.source}:${it.left}:${it.top}:${it.right}:${it.bottom}:${it.status}" },
            ocrBlocks = nameResult.blocks,
            fieldCandidates = cpResult.candidates +
                hpResult.candidates +
                nameResult.candidates +
                candyResult.candidates +
                caughtDateResult.candidates +
                stardustResult.candidates +
                sizeTagResult.candidates +
                appraisalResults.candidates +
                luckyResult.candidates +
                FieldCandidateDiagnostic(
                    "NameDynamic",
                    nameResult.source,
                    rawText = nameResult.candidates.firstOrNull { it.source == "mlkit_dynamic" }?.rawText,
                    parsedValue = nameResult.source.takeIf { it != "missing" },
                    status = if (nameResult.source != "missing") "found" else "missing",
                    reason = "dynamic_name_source"
                ) +
                FieldCandidateDiagnostic("Arc", "detector_unavailable", null, null, "not-run", reason = "phase_c_not_implemented") +
                FieldCandidateDiagnostic("RawText", "rawOcrText", raw, raw.takeIf { it.isNotBlank() }, if (raw.isNotBlank()) "found" else "missing"),
            stageTimings = timings.toList() + StageTimingDiagnostic("ocr_frame_total", System.currentTimeMillis() - frameStart),
            selected = PokemonSummary.from(pokemon)
        )
        OcrFrameResult(pokemon, diagnostic)
    }

    private suspend fun recognizeCp(bitmap: Bitmap, geometry: ScreenGeometry): OcrValue<Int> {
        val attempts = listOf(
            OcrAttempt("cp_original", ScreenField.CP, ScreenRegions.REGION_CP) { it },
            OcrAttempt("cp_mask", ScreenField.CP, ScreenRegions.REGION_CP) { ImagePreprocessor.processWhiteMask(it) },
            OcrAttempt("cp_hc", ScreenField.CP, ScreenRegions.REGION_CP) { ImagePreprocessor.processHighContrast(it) },
            OcrAttempt("cp_adaptive", ScreenField.CP, ScreenRegions.REGION_CP) { ImagePreprocessor.applyAdaptiveThresholding(it) }
        )
        val drafts = mutableListOf<CandidateDraft<Int>>()
        val crops = mutableListOf<CropDiagnostic>()
        attempts.forEach { attempt ->
            val (crop, cropDiagnostic) = cropAndProcess(bitmap, geometry, attempt.screenField, attempt.region, "CP", attempt.source, attempt.transform)
            crops += cropDiagnostic
            val raw = mlKitOcrProvider.recognizeText(crop).orEmpty()
            crop.recycle()
            val normalized = FieldCandidateNormalizer.normalizeCp(raw)
            drafts += candidateDraft(
                field = "CP",
                source = attempt.source,
                raw = raw,
                normalization = normalized,
                crop = cropDiagnostic,
                value = normalized.parsedValue?.toIntOrNull()
            )
        }
        return selectBestValue(drafts, crops, "missing")
    }

    private suspend fun recognizeHp(
        bitmap: Bitmap,
        geometry: ScreenGeometry,
        cp: Int?
    ): OcrValue<Pair<Int, Int>> {
        val attempts = listOf(
            OcrAttempt("hp_original", ScreenField.HP, ScreenRegions.REGION_HP) { it },
            OcrAttempt("hp_text", ScreenField.HP, ScreenRegions.REGION_HP) { ImagePreprocessor.processHpText(it) },
            OcrAttempt("hp_hc", ScreenField.HP, ScreenRegions.REGION_HP) { ImagePreprocessor.processHighContrast(it) },
            OcrAttempt("hp_alt", null, ScreenRegions.REGION_HP_ALT) { ImagePreprocessor.processHpText(it) },
            OcrAttempt("hp_lower", null, ScreenRegions.REGION_HP_LOWER) { ImagePreprocessor.processHpText(it) }
        )
        val drafts = mutableListOf<CandidateDraft<Pair<Int, Int>>>()
        val crops = mutableListOf<CropDiagnostic>()
        attempts.forEach { attempt ->
            val (crop, cropDiagnostic) = cropAndProcess(bitmap, geometry, attempt.screenField, attempt.region, "HP", attempt.source, attempt.transform)
            crops += cropDiagnostic
            val raw = mlKitOcrProvider.recognizeText(crop).orEmpty()
            crop.recycle()
            val normalized = FieldCandidateNormalizer.normalizeHp(raw, cp)
            drafts += candidateDraft(
                field = "HP",
                source = attempt.source,
                raw = raw,
                normalization = normalized,
                crop = cropDiagnostic,
                value = normalized.parsedValue?.let(::parseHpPairValue)
            )
        }
        return selectBestValue(drafts, crops, "missing")
    }

    private suspend fun recognizeName(bitmap: Bitmap, geometry: ScreenGeometry): OcrValue<String> {
        val blocks = mlKitOcrProvider.recognizeBlocks(bitmap)
        val blockDiagnostics = blocks.map { block ->
            OcrBlockDiagnostic(
                text = block.text,
                left = block.bounds?.left,
                top = block.bounds?.top,
                right = block.bounds?.right,
                bottom = block.bounds?.bottom
            )
        }
        val drafts = mutableListOf<CandidateDraft<String>>()
        val dynamicCropDiagnostic = nameDynamicSearchCrop(bitmap, geometry)
        val dynamicRect = diagnosticRect(dynamicCropDiagnostic)
        val crops = mutableListOf(dynamicCropDiagnostic)
        blocks
            .filter { block ->
                val bounds = block.bounds ?: return@filter false
                boundsIntersects(bounds, dynamicRect) &&
                    bounds.centerX() in dynamicRect.left..dynamicRect.right
            }
            .forEach { block ->
                val decision = textParser.decideDynamicOcrSpeciesName(block.text)
                val selected = decision.acceptedSpeciesOrNull()
                val evidence = (decision.rankingEvidence() * DYNAMIC_RANKING_WEIGHT) +
                    (namePositionScore(block.bounds, dynamicRect) * DYNAMIC_POSITION_WEIGHT)
                drafts += CandidateDraft(
                    field = "NameDynamic",
                    source = "mlkit_dynamic",
                    rawText = block.text,
                    normalizedText = block.text.trim().takeIf { it.isNotBlank() },
                    parsedValue = selected,
                    value = selected,
                    status = decision.status(),
                    score = decision.acceptedSelectionScore(evidence),
                    reason = decision.diagnostics.reasonCodes.joinToString(","),
                    crop = dynamicCropDiagnostic
                )
            }
        val dynamicDrafts = withSpeciesAgreementBoost(drafts)
        val dynamicCandidate = selectSpeciesNameCandidate(dynamicDrafts)

        if (dynamicCandidate != null) {
            return OcrValue(
                dynamicCandidate.value,
                dynamicCandidate.rawText.orEmpty(),
                "mlkit_dynamic",
                dynamicDrafts.toDiagnostics(dynamicCandidate),
                crops,
                blockDiagnostics
            )
        }

        val staticAttempts = listOf(
            OcrAttempt("static_name_original", ScreenField.Name, ScreenRegions.REGION_NAME) { it },
            OcrAttempt("static_name_crop", ScreenField.Name, ScreenRegions.REGION_NAME) { ImagePreprocessor.processWhiteMask(it) },
            OcrAttempt("static_name_strict", ScreenField.Name, ScreenRegions.REGION_NAME) { ImagePreprocessor.processWhiteMaskStrict(it) }
        )
        staticAttempts.forEach { attempt ->
            val (fallbackCrop, fallbackCropDiagnostic) = cropAndProcess(bitmap, geometry, attempt.screenField, attempt.region, "Name", attempt.source, attempt.transform)
            crops += fallbackCropDiagnostic
            val raw = mlKitOcrProvider.recognizeText(fallbackCrop).orEmpty()
            fallbackCrop.recycle()
            val decision = textParser.decideStaticOcrSpeciesName(raw)
            val selected = decision.acceptedSpeciesOrNull()
            val evidence = (decision.rankingEvidence() * STATIC_RANKING_WEIGHT) +
                ((fallbackCropDiagnostic.confidence ?: 0f) * STATIC_CROP_WEIGHT)
            drafts += CandidateDraft(
                field = "NameHC",
                source = attempt.source,
                rawText = raw.takeIf { it.isNotBlank() },
                normalizedText = raw.trim().takeIf { it.isNotBlank() },
                parsedValue = selected,
                value = selected,
                status = decision.status(),
                score = decision.acceptedSelectionScore(evidence),
                reason = decision.diagnostics.reasonCodes.joinToString(","),
                crop = fallbackCropDiagnostic
            )
        }
        val scoredDrafts = withSpeciesAgreementBoost(drafts)
        val winner = selectSpeciesNameCandidate(scoredDrafts)
        return OcrValue(
            winner?.value,
            winner?.rawText.orEmpty(),
            winner?.source ?: "missing",
            scoredDrafts.toDiagnostics(winner),
            crops,
            blockDiagnostics
        )
    }

    private suspend fun recognizeCandy(bitmap: Bitmap, geometry: ScreenGeometry, includeSecondaryFields: Boolean): OcrValue<String> {
        if (!includeSecondaryFields) {
            return OcrValue(
                null,
                "",
                "skipped",
                candidates = listOf(FieldCandidateDiagnostic("Candy", "secondary_fields", null, null, "not-run"))
            )
        }
        val attempts = listOf(
            "candy" to ScreenRegions.REGION_CANDY,
            "candy_wide" to ScreenRegions.REGION_CANDY_WIDE
        )
        val raws = mutableListOf<String>()
        val candidates = mutableListOf<FieldCandidateDiagnostic>()
        val crops = mutableListOf<CropDiagnostic>()
        for ((label, region) in attempts) {
            val field = if (label == "candy") ScreenField.Candy else null
            val (crop, cropDiagnostic) = cropAndProcess(bitmap, geometry, field, region, "Candy", label) { ImagePreprocessor.processCandyText(it) }
            crops += cropDiagnostic
            val raw = mlKitOcrProvider.recognizeText(crop).orEmpty()
            crop.recycle()
            raws += raw
            val parsed = textParser.parseCandyName(raw) ?: textParser.parseCandyNameLoose(raw)
            candidates += fieldCandidate("Candy", label, raw, parsed, cropDiagnostic, raw.trim().takeIf { it.isNotBlank() }, reason = if (parsed != null) "winner:candy_parser" else "loser:no_parse:candy_no_parse", winner = parsed != null)
            if (parsed != null) return OcrValue(parsed, raw, label, candidates, crops)
        }
        val joined = raws.joinToString(" || ")
        val parsed = textParser.parseCandyNameLoose(joined)
        return OcrValue(parsed, joined, "missing", candidates, crops)
    }

    private suspend fun recognizeDate(bitmap: Bitmap, geometry: ScreenGeometry): OcrValue<java.util.Date> {
        val attempts = listOf(
            OcrAttempt("date_badge", ScreenField.Date, ScreenRegions.REGION_DATE_BADGE) { ImagePreprocessor.processDateBadge(it) },
            OcrAttempt("date_original", ScreenField.Date, ScreenRegions.REGION_DATE_BADGE) { it }
        )
        val drafts = mutableListOf<CandidateDraft<java.util.Date>>()
        val crops = mutableListOf<CropDiagnostic>()
        attempts.forEach { attempt ->
            val (crop, cropDiagnostic) = cropAndProcess(bitmap, geometry, attempt.screenField, attempt.region, "Date", attempt.source, attempt.transform)
            crops += cropDiagnostic
            val raw = mlKitOcrProvider.recognizeText(crop).orEmpty()
            crop.recycle()
            val normalized = FieldCandidateNormalizer.normalizeDate(raw)
            drafts += candidateDraft(
                field = "Date",
                source = attempt.source,
                raw = raw,
                normalization = normalized,
                crop = cropDiagnostic,
                value = normalized.parsedValue?.let { TextParseUtils.parseDate(it) }
            )
        }
        return selectBestValue(drafts, crops, "missing")
    }

    private suspend fun recognizeStardust(
        bitmap: Bitmap,
        geometry: ScreenGeometry,
        includeSecondaryFields: Boolean
    ): OcrValue<Int> {
        if (!includeSecondaryFields) {
            return skippedValue("Stardust", "secondary_fields")
        }
        val attempts = listOf(
            OcrAttempt("stardust_original", ScreenField.Stardust, ScreenRegions.REGION_STARDUST) { it },
            OcrAttempt("stardust_text", ScreenField.Stardust, ScreenRegions.REGION_STARDUST) { ImagePreprocessor.processStardust(it) },
            OcrAttempt("stardust_hc", ScreenField.Stardust, ScreenRegions.REGION_STARDUST) { ImagePreprocessor.processHighContrast(it) }
        )
        val drafts = mutableListOf<CandidateDraft<Int>>()
        val crops = mutableListOf<CropDiagnostic>()
        attempts.forEach { attempt ->
            val (crop, cropDiagnostic) = cropAndProcess(bitmap, geometry, attempt.screenField, attempt.region, "Stardust", attempt.source, attempt.transform)
            crops += cropDiagnostic
            val raw = mlKitOcrProvider.recognizeText(crop).orEmpty()
            crop.recycle()
            val normalized = FieldCandidateNormalizer.normalizeStardust(raw) { textParser.parseStardust(it) }
            drafts += candidateDraft(
                field = "Stardust",
                source = attempt.source,
                raw = raw,
                normalization = normalized,
                crop = cropDiagnostic,
                value = normalized.parsedValue?.toIntOrNull()
            )
        }
        return selectBestValue(drafts, crops, "missing")
    }

    private suspend fun recognizeSizeTag(
        bitmap: Bitmap,
        geometry: ScreenGeometry,
        includeSecondaryFields: Boolean
    ): OcrValue<String> {
        if (!includeSecondaryFields) {
            return skippedValue("SizeTag", "secondary_fields")
        }
        val screenCrop = geometry.crop(ScreenField.SizeTag)
        if (screenCrop?.rect == null) {
            return unavailableValue("SizeTag", "geometry", screenCrop)
        }
        val attempts = listOf<Pair<String, (Bitmap) -> Bitmap>>(
            "size_original" to { it },
            "size_hc" to { ImagePreprocessor.processHighContrast(it) }
        )
        val drafts = mutableListOf<CandidateDraft<String>>()
        val crops = mutableListOf<CropDiagnostic>()
        attempts.forEach { (source, transform) ->
            val (crop, cropDiagnostic) = cropAndProcessScreenCrop(bitmap, screenCrop, "SizeTag", source, transform)
            crops += cropDiagnostic
            val raw = mlKitOcrProvider.recognizeText(crop).orEmpty()
            crop.recycle()
            val parsed = textParser.parseSizeTag(raw)
            drafts += CandidateDraft(
                field = "SizeTag",
                source = source,
                rawText = raw.takeIf { it.isNotBlank() },
                normalizedText = raw.uppercase().trim().takeIf { it.isNotBlank() },
                parsedValue = parsed,
                value = parsed,
                status = if (parsed != null) "found" else "missing",
                score = ((if (parsed != null) 0.70f else 0f) + screenCrop.confidence * 0.15f).coerceIn(0f, 1f),
                reason = if (parsed != null) "size_tag_parser" else "size_tag_no_parse",
                crop = cropDiagnostic
            )
        }
        return selectBestValue(drafts, crops, "missing")
    }

    private suspend fun recognizeAppraisalStats(
        bitmap: Bitmap,
        geometry: ScreenGeometry,
        includeSecondaryFields: Boolean
    ): AppraisalOcrValues {
        if (!includeSecondaryFields) {
            val attack = skippedValue<Int>("AppraisalAttack", "secondary_fields")
            val defense = skippedValue<Int>("AppraisalDefense", "secondary_fields")
            val stamina = skippedValue<Int>("AppraisalStamina", "secondary_fields")
            return AppraisalOcrValues(attack, defense, stamina)
        }
        return AppraisalOcrValues(
            attack = recognizeAppraisalField(bitmap, geometry, ScreenField.AppraisalAttack, "AppraisalAttack"),
            defense = recognizeAppraisalField(bitmap, geometry, ScreenField.AppraisalDefense, "AppraisalDefense"),
            stamina = recognizeAppraisalField(bitmap, geometry, ScreenField.AppraisalStamina, "AppraisalStamina")
        )
    }

    private suspend fun recognizeAppraisalField(
        bitmap: Bitmap,
        geometry: ScreenGeometry,
        screenField: ScreenField,
        field: String
    ): OcrValue<Int> {
        val screenCrop = geometry.crop(screenField)
        if (screenCrop?.rect == null) {
            return unavailableValue(field, "geometry", screenCrop)
        }
        val attempts = listOf<Pair<String, (Bitmap) -> Bitmap>>(
            "${field}_original" to { it },
            "${field}_hc" to { ImagePreprocessor.processHighContrast(it) },
            "${field}_adaptive" to { ImagePreprocessor.applyAdaptiveThresholding(it) }
        )
        val drafts = mutableListOf<CandidateDraft<Int>>()
        val crops = mutableListOf<CropDiagnostic>()
        attempts.forEach { (source, transform) ->
            val (crop, cropDiagnostic) = cropAndProcessScreenCrop(bitmap, screenCrop, field, source, transform)
            crops += cropDiagnostic
            val raw = mlKitOcrProvider.recognizeText(crop).orEmpty()
            crop.recycle()
            val normalized = FieldCandidateNormalizer.normalizeAppraisal(raw)
            drafts += candidateDraft(
                field = field,
                source = source,
                raw = raw,
                normalization = normalized,
                crop = cropDiagnostic,
                value = normalized.parsedValue?.toIntOrNull()
            )
        }
        return selectBestValue(drafts, crops, "missing")
    }

    private suspend fun recognizeLuckyLabel(
        bitmap: Bitmap,
        geometry: ScreenGeometry,
        includeSecondaryFields: Boolean
    ): OcrValue<Boolean> {
        if (!includeSecondaryFields) {
            return skippedValue("LuckyDetected", "secondary_fields")
        }
        val attempts = listOf(
            OcrAttempt("lucky_original", null, ScreenRegions.REGION_LUCKY_LABEL) { it },
            OcrAttempt("lucky_mask", null, ScreenRegions.REGION_LUCKY_LABEL) { ImagePreprocessor.processWhiteMaskStrict(it) }
        )
        val drafts = mutableListOf<CandidateDraft<Boolean>>()
        val crops = mutableListOf<CropDiagnostic>()
        attempts.forEach { attempt ->
            val (crop, cropDiagnostic) = cropAndProcess(bitmap, geometry, attempt.screenField, attempt.region, "LuckyDetected", attempt.source, attempt.transform)
            crops += cropDiagnostic
            val raw = mlKitOcrProvider.recognizeText(crop).orEmpty()
            crop.recycle()
            val detected = textParser.parseLuckyLabel(raw)
            drafts += CandidateDraft(
                field = "LuckyDetected",
                source = attempt.source,
                rawText = raw.takeIf { it.isNotBlank() },
                normalizedText = raw.uppercase().trim().takeIf { it.isNotBlank() },
                parsedValue = if (detected) "true" else null,
                value = detected.takeIf { it },
                status = if (detected) "found" else "missing",
                score = if (detected) 0.86f else 0f,
                reason = if (detected) "lucky_label_parser" else "lucky_label_no_parse",
                crop = cropDiagnostic
            )
        }
        return selectBestValue(drafts, crops, "missing")
    }

    private fun buildRawOcrText(
        cpResult: OcrValue<Int>,
        hpResult: OcrValue<Pair<Int, Int>>,
        nameResult: OcrValue<String>,
        candyResult: OcrValue<String>,
        caughtDateResult: OcrValue<java.util.Date>,
        stardustResult: OcrValue<Int>,
        sizeTagResult: OcrValue<String>,
        appraisalResults: AppraisalOcrValues,
        luckyResult: OcrValue<Boolean>
    ): String {
        val fields = linkedMapOf(
            "CP" to rawOrMarker(cpResult),
            "HP" to rawOrMarker(hpResult),
            "Name" to rawOrMarker(nameResult),
            "NameDynamic" to rawForCandidate(nameResult, "NameDynamic", emptyMarker = "missing"),
            "NameHC" to rawForCandidate(nameResult, "NameHC", emptyMarker = "not-run"),
            "Candy" to rawOrMarker(candyResult),
            "Date" to rawOrMarker(caughtDateResult),
            "Stardust" to rawOrMarker(stardustResult),
            "SizeTag" to rawOrMarker(sizeTagResult),
            "Arc" to "not-run",
            "AppraisalAttack" to rawOrMarker(appraisalResults.attack),
            "AppraisalDefense" to rawOrMarker(appraisalResults.defense),
            "AppraisalStamina" to rawOrMarker(appraisalResults.stamina),
            "LuckyDetected" to (if (luckyResult.value == true) "true" else rawOrMarker(luckyResult))
        )
        fields["RawText"] = "present"
        return fields.entries.joinToString("|") { (key, value) -> "$key:${sanitizeRawFieldValue(value)}" }
    }

    private fun rawForCandidate(result: OcrValue<*>, field: String, emptyMarker: String): String {
        val candidates = result.candidates.filter { it.field == field }
        if (candidates.isEmpty()) return emptyMarker
        candidates.mapNotNull { it.rawText }.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let {
            return it.joinToString(" || ")
        }
        return when {
            candidates.any { it.status == "found" } -> candidates.firstNotNullOfOrNull { it.parsedValue }.orMissing()
            candidates.any { it.status == "not-run" } -> "not-run"
            else -> "missing"
        }
    }

    private fun rawOrMarker(result: OcrValue<*>): String =
        when {
            result.raw.isNotBlank() -> result.raw
            result.source == "skipped" || result.source == "not-run" || result.candidates.any { it.status == "not-run" } -> "not-run"
            else -> "missing"
        }

    private fun String?.orMissing(): String =
        if (isNullOrBlank()) "missing" else this

    private fun sanitizeRawFieldValue(value: String): String =
        value.replace('|', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
            .ifBlank { "missing" }

    private fun SpeciesNameDecision.rankingEvidence(): Float = when (this) {
        is SpeciesNameDecision.Accepted -> {
            val top = diagnostics.topCandidate
            if (top == null) 1f else top.score.toFloat()
        }
        is SpeciesNameDecision.Uncertain -> if (candidates.isEmpty()) 0f else candidates.first().score.toFloat()
        is SpeciesNameDecision.NoMatch -> 0f
    }

    private fun withSpeciesAgreementBoost(drafts: List<CandidateDraft<String>>): List<CandidateDraft<String>> {
        val counts = drafts.mapNotNull { it.parsedValue }.groupingBy { it }.eachCount()
        return drafts.map { draft ->
            val boost = if (draft.parsedValue != null && (counts[draft.parsedValue] ?: 0) >= 2) {
                SPECIES_AGREEMENT_BOOST
            } else {
                0f
            }
            draft.copy(score = (draft.score + boost).coerceAtMost(1f))
        }
    }

    private fun selectSpeciesNameCandidate(drafts: List<CandidateDraft<String>>): CandidateDraft<String>? {
        val accepted = drafts.filter { it.value != null }
        val winner = accepted.maxWithOrNull(compareBy<CandidateDraft<String>> { it.score }.thenBy { it.value })
            ?: return null
        return winner.takeUnless { best ->
            accepted.any { candidate -> candidate.value != best.value && candidate.score == best.score }
        }
    }

    private fun cropAndProcess(
        bitmap: Bitmap,
        geometry: ScreenGeometry,
        screenField: ScreenField?,
        region: ScreenRegions.Region,
        field: String,
        source: String,
        transform: (Bitmap) -> Bitmap
    ): Pair<Bitmap, CropDiagnostic> {
        val screenCrop = screenField?.let { geometry.crop(it) }?.takeIf { it.rect != null }
        val rect = screenCrop?.rect ?: ScreenRegions.getRectForRegion(bitmap, region)
        val crop = cropBitmap(bitmap, rect)
        val processed = transform(crop)
        if (processed !== crop) crop.recycle()
        return processed to cropDiagnostic(field, source, rect, "used", screenCrop, fallbackReason = "legacy_screen_region")
    }

    private fun cropAndProcessScreenCrop(
        bitmap: Bitmap,
        screenCrop: ScreenCrop,
        field: String,
        source: String,
        transform: (Bitmap) -> Bitmap
    ): Pair<Bitmap, CropDiagnostic> {
        val rect = requireNotNull(screenCrop.rect) { "screen crop rect required for $field" }
        val crop = cropBitmap(bitmap, rect)
        val processed = transform(crop)
        if (processed !== crop) crop.recycle()
        return processed to cropDiagnostic(field, source, rect, "used", screenCrop)
    }

    private fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        val safe = Rect(
            rect.left.coerceIn(0, bitmap.width - 1),
            rect.top.coerceIn(0, bitmap.height - 1),
            rect.right.coerceIn(1, bitmap.width),
            rect.bottom.coerceIn(1, bitmap.height)
        )
        val width = (safe.right - safe.left).coerceAtLeast(1)
        val height = (safe.bottom - safe.top).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, safe.left, safe.top, width, height)
    }

    private fun fieldCandidate(
        field: String,
        source: String,
        raw: String?,
        parsed: String?,
        crop: CropDiagnostic? = null,
        normalizedText: String? = null,
        score: Float? = null,
        reason: String? = null,
        winner: Boolean = false
    ): FieldCandidateDiagnostic {
        val status = when {
            !parsed.isNullOrBlank() -> "found"
            !raw.isNullOrBlank() -> "missing"
            else -> "missing"
        }
        return FieldCandidateDiagnostic(
            field = field,
            source = source,
            rawText = raw?.takeIf { it.isNotBlank() },
            parsedValue = parsed,
            status = status,
            cropName = crop?.source,
            cropLeft = crop?.left,
            cropTop = crop?.top,
            cropRight = crop?.right,
            cropBottom = crop?.bottom,
            cropProvenance = crop?.provenance,
            cropConfidence = crop?.confidence,
            preprocessing = source,
            normalizedText = normalizedText,
            parserResult = parsed,
            candidateScore = score,
            winner = winner,
            reason = reason,
            selectedValue = parsed.takeIf { winner }
        )
    }

    private fun <T> candidateDraft(
        field: String,
        source: String,
        raw: String,
        normalization: FieldCandidateNormalizer.Result,
        crop: CropDiagnostic,
        value: T?
    ): CandidateDraft<T> =
        CandidateDraft(
            field = field,
            source = source,
            rawText = raw.takeIf { it.isNotBlank() },
            normalizedText = normalization.normalizedText,
            parsedValue = normalization.parsedValue,
            value = value,
            status = normalization.status,
            score = (normalization.score + ((crop.confidence ?: 0f) * 0.15f)).coerceIn(0f, 1f),
            reason = normalization.reason,
            crop = crop
        )

    private fun <T> selectBestValue(
        drafts: List<CandidateDraft<T>>,
        crops: List<CropDiagnostic>,
        missingSource: String
    ): OcrValue<T> {
        val scored = withAgreementBoost(drafts)
        val winner = scored
            .filter { it.value != null }
            .maxWithOrNull(
                compareBy<CandidateDraft<T>> { it.score }
                    .thenBy { it.crop.confidence ?: 0f }
            )
        val raw = winner?.rawText ?: scored.mapNotNull { it.rawText }.joinToString(" || ")
        return OcrValue(
            value = winner?.value,
            raw = raw,
            source = winner?.source ?: missingSource,
            candidates = scored.toDiagnostics(winner),
            crops = crops
        )
    }

    private fun <T> withAgreementBoost(drafts: List<CandidateDraft<T>>): List<CandidateDraft<T>> {
        val counts = drafts.mapNotNull { it.parsedValue }.groupingBy { it }.eachCount()
        return drafts.map { draft ->
            val boost = if (draft.parsedValue != null && (counts[draft.parsedValue] ?: 0) >= 2) 0.10f else 0f
            draft.copy(score = (draft.score + boost).coerceIn(0f, 1f))
        }
    }

    private fun <T> List<CandidateDraft<T>>.toDiagnostics(winner: CandidateDraft<T>?): List<FieldCandidateDiagnostic> =
        map { draft ->
            val isWinner = winner === draft
            val selected = winner?.parsedValue
            FieldCandidateDiagnostic(
                field = draft.field,
                source = draft.source,
                rawText = draft.rawText,
                parsedValue = draft.parsedValue,
                status = draft.status,
                cropName = draft.crop.source,
                cropLeft = draft.crop.left,
                cropTop = draft.crop.top,
                cropRight = draft.crop.right,
                cropBottom = draft.crop.bottom,
                cropProvenance = draft.crop.provenance,
                cropConfidence = draft.crop.confidence,
                preprocessing = draft.source,
                normalizedText = draft.normalizedText,
                parserResult = draft.parsedValue,
                candidateScore = draft.score,
                winner = isWinner,
                reason = when {
                    isWinner -> "winner:${draft.reason}"
                    draft.parsedValue == null -> "loser:no_parse:${draft.reason}"
                    else -> "loser:lower_score:${draft.reason}"
                },
                selectedValue = selected.takeIf { isWinner }
            )
        }

    private fun <T> skippedValue(field: String, source: String): OcrValue<T> =
        OcrValue(
            value = null,
            raw = "",
            source = "skipped",
            candidates = listOf(FieldCandidateDiagnostic(field, source, null, null, "not-run", reason = "secondary_fields_skipped"))
        )

    private fun <T> unavailableValue(field: String, source: String, screenCrop: ScreenCrop?): OcrValue<T> {
        val rect = screenCrop?.rect
        val crop = CropDiagnostic(
            field = field,
            source = source,
            left = rect?.left,
            top = rect?.top,
            right = rect?.right,
            bottom = rect?.bottom,
            status = "not-run",
            provenance = (screenCrop?.provenance ?: CropProvenance.NotAvailable).diagnosticName,
            confidence = screenCrop?.confidence ?: 0f,
            reasons = screenCrop?.reasons ?: listOf("crop_not_available")
        )
        return OcrValue(
            value = null,
            raw = "",
            source = "not-run",
            candidates = listOf(
                FieldCandidateDiagnostic(
                    field = field,
                    source = source,
                    rawText = null,
                    parsedValue = null,
                    status = "not-run",
                    cropName = crop.source,
                    cropLeft = crop.left,
                    cropTop = crop.top,
                    cropRight = crop.right,
                    cropBottom = crop.bottom,
                    cropProvenance = crop.provenance,
                    cropConfidence = crop.confidence,
                    preprocessing = source,
                    reason = "crop_not_available"
                )
            ),
            crops = listOf(crop)
        )
    }

    private fun parseHpPairValue(value: String): Pair<Int, Int>? {
        val parts = value.split("/")
        if (parts.size != 2) return null
        val current = parts[0].toIntOrNull() ?: return null
        val max = parts[1].toIntOrNull() ?: return null
        return current to max
    }

    private fun diagnosticRect(crop: CropDiagnostic): Rect =
        Rect(
            crop.left ?: 0,
            crop.top ?: 0,
            crop.right ?: ((crop.left ?: 0) + 1),
            crop.bottom ?: ((crop.top ?: 0) + 1)
        )

    private fun boundsIntersects(bounds: Rect, crop: Rect): Boolean =
        bounds.left < crop.right &&
            bounds.right > crop.left &&
            bounds.top < crop.bottom &&
            bounds.bottom > crop.top

    private fun namePositionScore(bounds: Rect?, crop: Rect): Float {
        if (bounds == null) return 0.4f
        val centerY = bounds.centerY()
        val idealY = crop.top + (crop.height() * 0.58f)
        val distance = kotlin.math.abs(centerY - idealY)
        return (1f - (distance.toFloat() / crop.height().coerceAtLeast(1))).coerceIn(0f, 1f)
    }

    private fun cropDiagnostic(
        field: String,
        source: String,
        rect: Rect,
        status: String,
        screenCrop: ScreenCrop? = null,
        fallbackReason: String? = null
    ): CropDiagnostic {
        val provenance = screenCrop?.provenance ?: CropProvenance.LegacyFallback
        val reasons = screenCrop?.reasons ?: listOfNotNull(fallbackReason)
        return CropDiagnostic(
            field = field,
            source = source,
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
            status = status,
            provenance = provenance.diagnosticName,
            confidence = screenCrop?.confidence ?: 0.45f,
            reasons = reasons
        )
    }

    private fun nameDynamicSearchCrop(bitmap: Bitmap, geometry: ScreenGeometry): CropDiagnostic {
        val screenCrop = geometry.crop(ScreenField.DynamicName)
        val rect = screenCrop?.rect ?: Rect(
            (bitmap.width * 0.12f).toInt(),
            0,
            (bitmap.width * 0.88f).toInt(),
            (bitmap.height * 0.58f).toInt()
        )
        return CropDiagnostic(
            field = "NameDynamic",
            source = "mlkit_dynamic_search",
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
            status = "used",
            provenance = (screenCrop?.provenance ?: CropProvenance.LegacyFallback).diagnosticName,
            confidence = screenCrop?.confidence ?: 0.45f,
            reasons = screenCrop?.reasons ?: listOf("legacy_dynamic_search_band")
        )
    }

    private fun appraisalCropDiagnostic(geometry: ScreenGeometry): List<CropDiagnostic> {
        val crop = geometry.crop(ScreenField.AppraisalBox)
        return listOf(
            if (crop?.rect == null) {
                CropDiagnostic(
                    "Appraisal",
                    "appraisal_box",
                    null,
                    null,
                    null,
                    null,
                    "missing",
                    provenance = CropProvenance.NotAvailable.diagnosticName,
                    confidence = 0f,
                    reasons = crop?.reasons.orEmpty()
                )
            } else {
                CropDiagnostic(
                    "Appraisal",
                    "appraisal_box",
                    crop.rect.left,
                    crop.rect.top,
                    crop.rect.right,
                    crop.rect.bottom,
                    "detected",
                    provenance = crop.provenance.diagnosticName,
                    confidence = crop.confidence,
                    reasons = crop.reasons
                )
            }
        )
    }

    private fun notRunCropDiagnostics(geometry: ScreenGeometry): List<CropDiagnostic> {
        val fields = listOf(
            ScreenField.Arc
        )
        return fields.mapNotNull { field ->
            val crop = geometry.crop(field) ?: return@mapNotNull null
            val rect = crop.rect
            CropDiagnostic(
                field = field.diagnosticName,
                source = "geometry",
                left = rect?.left,
                top = rect?.top,
                right = rect?.right,
                bottom = rect?.bottom,
                status = if (rect == null) "not-available" else "not-run",
                provenance = crop.provenance.diagnosticName,
                confidence = crop.confidence,
                reasons = crop.reasons
            )
        }
    }

    private fun ScreenAnchor.toDiagnostic(): AnchorDiagnostic =
        AnchorDiagnostic(
            name = name.name,
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
            confidence = confidence,
            reason = reason
        )

    private data class OcrAttempt(
        val source: String,
        val screenField: ScreenField?,
        val region: ScreenRegions.Region,
        val transform: (Bitmap) -> Bitmap
    )

    private data class CandidateDraft<T>(
        val field: String,
        val source: String,
        val rawText: String?,
        val normalizedText: String?,
        val parsedValue: String?,
        val value: T?,
        val status: String,
        val score: Float,
        val reason: String,
        val crop: CropDiagnostic
    )

    private data class AppraisalOcrValues(
        val attack: OcrValue<Int>,
        val defense: OcrValue<Int>,
        val stamina: OcrValue<Int>
    ) {
        val candidates: List<FieldCandidateDiagnostic>
            get() = attack.candidates + defense.candidates + stamina.candidates
        val crops: List<CropDiagnostic>
            get() = attack.crops + defense.crops + stamina.crops
        val confidence: Float?
            get() = candidates
                .filter { it.winner }
                .mapNotNull { it.candidateScore }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.toFloat()
    }

    private data class OcrValue<T>(
        val value: T?,
        val raw: String,
        val source: String,
        val candidates: List<FieldCandidateDiagnostic> = emptyList(),
        val crops: List<CropDiagnostic> = emptyList(),
        val blocks: List<OcrBlockDiagnostic> = emptyList()
    )
}
