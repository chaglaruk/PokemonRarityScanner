package com.pokerarity.scanner.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecognitionIdentityHistoryCodecTest {
    @Test
    fun knownAndAmbiguousFormsRoundTripWithExplicitPresentation() {
        val known = identity(RecognitionFormStatus.KNOWN, knownForm = "Alolan")
        val ambiguous = identity(RecognitionFormStatus.AMBIGUOUS, forms = listOf("Origin", "Altered"))

        val knownCopy = RecognitionIdentityHistoryCodec.decode(RecognitionIdentityHistoryCodec.encode(known))
        val ambiguousCopy = RecognitionIdentityHistoryCodec.decode(RecognitionIdentityHistoryCodec.encode(ambiguous))

        assertEquals(known, knownCopy)
        assertEquals("Form: Alolan", RecognitionIdentityHistoryCodec.formPresentation(knownCopy))
        assertEquals(ambiguous, ambiguousCopy)
        assertEquals(
            "Possible forms: Origin / Altered",
            RecognitionIdentityHistoryCodec.formPresentation(ambiguousCopy)
        )
    }

    @Test
    fun unknownAndMalformedIdentityDoNotProduceFormClaims() {
        val unknown = identity(RecognitionFormStatus.UNKNOWN)
        val unknownCopy = RecognitionIdentityHistoryCodec.decode(RecognitionIdentityHistoryCodec.encode(unknown))

        assertEquals(unknown, unknownCopy)
        assertNull(RecognitionIdentityHistoryCodec.formPresentation(unknownCopy))
        assertNull(RecognitionIdentityHistoryCodec.decode("{\"formStatus\":\"KNOWN\",\"knownForm\":\"Alolan\"}"))
        assertNull(RecognitionIdentityHistoryCodec.formPresentation(null))
    }

    private fun identity(
        status: RecognitionFormStatus,
        knownForm: String? = null,
        forms: List<String> = emptyList()
    ) = RecognitionIdentity(
        speciesStatus = RecognitionSpeciesStatus.KNOWN,
        canonicalSpecies = "Palkia",
        speciesAuthority = "species_exact_authority",
        formStatus = status,
        knownForm = knownForm,
        formProvenance = "authoritative_variant_db",
        ambiguousForms = forms,
        shiny = RecognitionTruth.UNKNOWN,
        shadow = RecognitionTruth.UNKNOWN,
        purified = RecognitionTruth.UNKNOWN,
        lucky = RecognitionTruth.UNKNOWN,
        costume = RecognitionTruth.UNKNOWN,
        specialForm = RecognitionTruth.UNKNOWN,
        xxs = RecognitionTruth.UNKNOWN,
        xxl = RecognitionTruth.UNKNOWN,
        locationCard = RecognitionTruth.UNKNOWN
    )
}
