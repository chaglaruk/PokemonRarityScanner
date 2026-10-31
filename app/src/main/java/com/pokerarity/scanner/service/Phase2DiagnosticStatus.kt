package com.pokerarity.scanner.service

internal fun phase2DiagnosticStatus(
    frameMissing: Boolean,
    authorityAllowed: Boolean,
    speciesMissing: Boolean,
    classifierRan: Boolean,
    resultAvailable: Boolean
): String = when {
    frameMissing -> "not_run_frame_missing"
    !authorityAllowed -> "not_run_authority_gate"
    speciesMissing -> "not_run_locked_species_missing"
    classifierRan && !resultAvailable -> "classifier_returned_no_result"
    classifierRan -> "classifier_completed"
    else -> "not_run_unclassified"
}
