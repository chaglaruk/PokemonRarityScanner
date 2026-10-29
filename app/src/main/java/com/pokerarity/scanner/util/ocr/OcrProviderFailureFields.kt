package com.pokerarity.scanner.util.ocr

/** A failed OCR document cannot supply any positive field or species evidence. */
internal fun ocrProviderFailedFields(fields: AnchoredScreenText.Fields): AnchoredScreenText.Fields =
    fields.copy(
        cpRead = FieldRead.unreadable("ocr_provider_failed"),
        hpRead = FieldRead.unreadable("ocr_provider_failed"),
        candyRead = FieldRead.unreadable("ocr_provider_failed"),
        powerUpRead = FieldRead.unreadable("ocr_provider_failed"),
        evolveRead = FieldRead.unreadable("ocr_provider_failed")
    )
