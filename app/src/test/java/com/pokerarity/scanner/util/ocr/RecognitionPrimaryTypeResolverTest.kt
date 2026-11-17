package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.RecognitionSnapshotTestSupport
import com.pokerarity.scanner.data.model.RecognitionFormStatus
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus
import com.pokerarity.scanner.data.model.RecognitionTruth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class RecognitionPrimaryTypeResolverTest {
    private val snapshot = RecognitionSnapshotTestSupport.loadRealSnapshot()

    @Test
    fun knownAlolanMeowthUsesThePinnedFormType() {
        assertEquals(
            "dark",
            RecognitionPrimaryTypeResolver.resolve(snapshot, identity().copy(
                formStatus = RecognitionFormStatus.KNOWN,
                knownForm = "Alolan"
            ))
        )
    }

    @Test
    fun unresolvedMeowthFormsDoNotGuessTheBaseType() {
        assertNull(RecognitionPrimaryTypeResolver.resolve(snapshot, identity()))
    }

    @Test
    fun formsAgreeingOnPrimaryTypeRemainDisplayable() {
        assertEquals(
            "electric",
            RecognitionPrimaryTypeResolver.resolve(
                snapshot,
                identity().copy(canonicalSpecies = "Raichu")
            )
        )
    }

    private fun identity() = RecognitionIdentity(
        speciesStatus = RecognitionSpeciesStatus.KNOWN,
        canonicalSpecies = "Meowth",
        speciesAuthority = "exact_canonical",
        formStatus = RecognitionFormStatus.UNKNOWN,
        shiny = RecognitionTruth.UNKNOWN,
        shadow = RecognitionTruth.UNKNOWN,
        purified = RecognitionTruth.UNKNOWN,
        lucky = RecognitionTruth.UNKNOWN,
        costume = RecognitionTruth.UNKNOWN,
        specialForm = RecognitionTruth.TRUE,
        xxs = RecognitionTruth.UNKNOWN,
        xxl = RecognitionTruth.UNKNOWN,
        locationCard = RecognitionTruth.UNKNOWN
    )
}
