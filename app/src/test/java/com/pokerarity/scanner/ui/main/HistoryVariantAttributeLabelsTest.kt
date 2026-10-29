package com.pokerarity.scanner.ui.main

import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.model.RecognitionFormStatus
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus
import com.pokerarity.scanner.data.model.RecognitionTruth
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryVariantAttributeLabelsTest {
    @Test
    fun historyLabelsOnlyExplicitlyConfirmedVariantStates() {
        val identity = RecognitionIdentity(
            speciesStatus = RecognitionSpeciesStatus.KNOWN,
            canonicalSpecies = "Pikachu",
            formStatus = RecognitionFormStatus.UNKNOWN,
            shiny = RecognitionTruth.UNKNOWN,
            shadow = RecognitionTruth.UNKNOWN,
            purified = RecognitionTruth.TRUE,
            lucky = RecognitionTruth.UNKNOWN,
            costume = RecognitionTruth.UNKNOWN,
            specialForm = RecognitionTruth.UNKNOWN,
            xxs = RecognitionTruth.TRUE,
            xxl = RecognitionTruth.FALSE,
            locationCard = RecognitionTruth.TRUE
        )

        assertEquals(
            listOf("Purified", "XXS", "Background"),
            historyIdentityAttributeLabels(identity, ::labelForResource)
        )
        assertEquals(emptyList<String>(), historyIdentityAttributeLabels(null, ::labelForResource))
    }

    private fun labelForResource(resourceId: Int): String = when (resourceId) {
        R.string.attribute_purified -> "Purified"
        R.string.attribute_xxs -> "XXS"
        R.string.attribute_xxl -> "XXL"
        R.string.attribute_special_background -> "Background"
        else -> error("Unexpected history attribute resource: $resourceId")
    }
}
