package com.pokerarity.scanner.util.ocr

/**
 * A trailing edit pencil can appear as a slash on a spatially anchored title.
 * General species-name parsing remains strict; only the anchored label uses this cleanup.
 */
internal fun cleanAnchoredNameLabel(text: String): String =
    text.trim().removeSuffix("/").trim()
