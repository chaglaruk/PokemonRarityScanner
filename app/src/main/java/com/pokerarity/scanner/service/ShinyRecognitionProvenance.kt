package com.pokerarity.scanner.service

import android.graphics.Bitmap
import android.util.Log
import com.pokerarity.scanner.data.model.FullVariantMatch
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier
import com.pokerarity.scanner.util.vision.VisualFeatureDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Suppress("LongParameterList")
internal fun logShinyProvenance(
    rawVisual: VisualFeatures,
    fullMatch: FullVariantMatch?,
    prototypeMatch: VariantPrototypeClassifier.MatchResult?,
    prePhase2Shiny: Boolean,
    unresolvedReference: Boolean,
    postPhase2: VisualFeatures,
    explicitNegative: Boolean,
    identity: RecognitionTruth
) {
    val message = buildString {
        append("Shiny provenance: rawVisual=")
        append(rawVisual.isShiny)
        append(':')
        append(rawVisual.confidence)
        append(" form=")
        append(fullMatch?.finalSpriteKey)
        append(':')
        append(fullMatch?.resolvedForm)
        append(" fullShiny=")
        append(fullMatch?.resolvedShiny)
        append(':')
        append(fullMatch?.shinyConfidence)
        append(" prototype=")
        append(prototypeMatch?.spriteKey)
        append(':')
        append(prototypeMatch?.isShiny)
        append(" prePhase2=")
        append(prePhase2Shiny)
        append(" referenceUnresolved=")
        append(unresolvedReference)
        append(" phase2Allowed=")
        append(!unresolvedReference)
        append(" postPhase2=")
        append(postPhase2.isShiny)
        append(" explicitNegative=")
        append(explicitNegative)
        append(" identity=")
        append(identity)
    }
    Log.d("ScanManager", message)
}

@Suppress("TooGenericExceptionCaught")
internal suspend fun detectVisualFeatures(
    detector: VisualFeatureDetector,
    bitmap: Bitmap?,
    speciesName: String?,
    sizeTag: String?,
    resolvedFormSpriteKey: String?
): VisualFeatures = coroutineScope {
    val visualDeferred = async(Dispatchers.Default) {
        try {
            if (bitmap != null) {
                detector.detect(
                    bitmap,
                    speciesName,
                    sizeTag,
                    resolvedFormSpriteKey = resolvedFormSpriteKey
                )
            } else {
                VisualFeatures()
            }
        } catch (e: Exception) {
            Log.e("ScanManager", "Visual detection failed", e)
            VisualFeatures()
        }
    }
    visualDeferred.await()
}
