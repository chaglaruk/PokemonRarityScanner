package com.pokerarity.scanner.util.ocr

/** Resolves only an ambiguous Nidoran title when the adjacent screen glyph is visibly classified. */
internal object NidoranGenderMarkerRecovery {
    fun recover(
        request: NameCropOcrRecovery.Request,
        bounds: NameCropBounds,
        durationMs: Long,
        merged: MLKitOcrProvider.Layout,
        fields: AnchoredScreenText.Fields
    ): NameCropOcrRecovery.Result? {
        val decision = fields.name as? SpeciesNameDecision.Uncertain
        val hasAmbiguousTitle = fields.nameRaw?.trim()?.equals(NIDORAN_TITLE, ignoreCase = true) == true &&
            AMBIGUOUS_NIDORAN_REASON in (decision?.diagnostics?.reasonCodes ?: emptyList())
        val nameBand = request.extractionContext.nameBand
            ?: request.extractionContext.anchoredNameFallbackBand
        val marker = if (hasAmbiguousTitle && nameBand != null) {
            AdjacentGenderSymbolDetector.detect(request.bitmap, fields.nameTextBounds, nameBand)
        } else {
            null
        }
        return marker?.let { markerValue ->
            val glyph = if (markerValue == VisualGenderMarker.Male) MALE_GLYPH else FEMALE_GLYPH
            val resolved = request.parser.decideSpeciesName("$NIDORAN_TITLE $glyph")
            resolved.takeIf { name -> name.acceptedSpeciesOrNull() != null }?.let { accepted ->
                val markerReason = if (markerValue == VisualGenderMarker.Male) {
                    "visual_gender_marker_male"
                } else {
                    "visual_gender_marker_female"
                }
                val diagnostic = CropDiagnostic(
                    field = "NameFallback",
                    source = "targeted_ocr",
                    left = bounds.left,
                    top = bounds.top,
                    right = bounds.right,
                    bottom = bounds.bottom,
                    status = "recovered",
                    provenance = "source_resolution_title_band",
                    confidence = null,
                    reasons = listOf("full_frame_name_missing", markerReason)
                )
                NameCropOcrRecovery.Result(merged, fields.copy(name = accepted), diagnostic, durationMs)
            }
        }
    }

    private const val NIDORAN_TITLE = "Nidoran"
    private const val AMBIGUOUS_NIDORAN_REASON = "ambiguous_nidoran"
    private const val MALE_GLYPH = "\u2642"
    private const val FEMALE_GLYPH = "\u2640"
}
