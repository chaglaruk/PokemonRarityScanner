package com.pokerarity.scanner.util.ocr

/** Parses a complete type label from one spatially anchored OCR line. */
internal fun parseAnchoredTypeSet(line: MLKitOcrProvider.RecognizedBlock): Set<String>? {
    val text = line.text.trim().lowercase()
    if (!text.matches(Regex("[a-z]+(?:(?:\\s*/\\s*|\\s+)[a-z]+)?"))) return null
    val words = text.split(Regex("\\s*/\\s*|\\s+"))
    return words.toSet().takeIf {
        it.size == words.size && RecognitionSnapshot.TYPES.containsAll(words)
    }
}
