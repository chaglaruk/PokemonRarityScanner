package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.RecognitionSnapshotTestSupport
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RecognitionFormStatus
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus
import com.pokerarity.scanner.data.model.VisualFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SameSpeciesFormEvidenceTest {
    private val snapshot by lazy { RecognitionSnapshotTestSupport.loadRealSnapshot() }

    private fun pokemon(species: String, types: Set<String>?) = PokemonData(
        cp = null, hp = null, maxHp = null, name = species, realName = species, candyName = species,
        megaEnergy = null, weight = null, height = null, stardust = null, caughtDate = null,
        recognitionObservation = RecognitionObservation(species, null, types, true))

    private fun build(species: String, evidence: SameSpeciesFormEvidence?, accepted: Boolean = true) =
        RecognitionIdentityFactory.build(RecognitionIdentityFactory.Input(
            SpeciesEvidence(species, SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.COMPATIBLE,
                listOf(SpeciesEvidenceReason.EXACT), true, false),
            accepted, species, null, null, emptyList(), VisualFeatures(), false, null, evidence))

    private fun observed(species: String, types: Set<String>?) =
        FamilySpeciesResolver(snapshot).resolve(pokemon(species, types)).formEvidence[species]

    @Test
    fun supportedTypeDistinguishesRegionalFormWithinLockedSpecies() {
        assertEquals("Alolan Form", build("Vulpix", observed("Vulpix", setOf("ice"))).knownForm)
        assertEquals("Normal Form", build("Vulpix", observed("Vulpix", setOf("fire"))).knownForm)
    }

    @Test
    fun indistinguishableSupportedFormsRemainAmbiguous() {
        val result = build("Giratina", observed("Giratina", setOf("ghost", "dragon")))
        assertEquals(RecognitionFormStatus.AMBIGUOUS, result.formStatus)
        assertEquals(setOf("Altered Form", "Origin Form"), result.ambiguousForms.toSet())
    }

    @Test
    fun noEvidenceAndUnsupportedCostumesCannotBecomeNormal() {
        assertEquals(RecognitionFormStatus.UNKNOWN, build("Vulpix", observed("Vulpix", null)).formStatus)
        assertEquals(RecognitionFormStatus.UNKNOWN,
            build("Pikachu", observed("Pikachu", setOf("electric"))).formStatus)
    }

    @Test
    fun differentSpeciesStaleRevisionAndRejectedScanCannotPublishForm() {
        val evidence = observed("Vulpix", setOf("ice"))!!
        assertEquals(RecognitionFormStatus.UNKNOWN, build("Rotom", evidence).formStatus)
        assertEquals("Rotom", build("Rotom", evidence).canonicalSpecies)
        assertEquals(RecognitionFormStatus.UNKNOWN,
            build("Vulpix", evidence.copy(snapshotRevision = "old")).formStatus)
        val rejected = build("Vulpix", evidence, false)
        assertEquals(RecognitionSpeciesStatus.UNKNOWN, rejected.speciesStatus)
        assertNull(rejected.knownForm)
    }

    @Test
    fun anchoredFieldsCarrySameFrameFormThroughObservationAndFinalIdentity() {
        val parser = TextParser(ApplicationProvider.getApplicationContext<Context>())
        TextParser::class.java.getDeclaredField("pokemonNames").apply { isAccessible = true }
            .set(parser, RecognitionSnapshotTestSupport.canonicalSpecies().map { it.lowercase() })
        fun block(text: String, rect: Rect) = MLKitOcrProvider.RecognizedBlock(text, rect)
        val lines = listOf(block("Vulpix", Rect(320, 660, 760, 725)),
            block("ICE", Rect(435, 935, 645, 965)), block("WEIGHT", Rect(100, 985, 290, 1020)),
            block("HEIGHT", Rect(790, 985, 980, 1020)), block("VULPIX CANDY", Rect(490, 1140, 800, 1180)),
            block("POWER UP", Rect(130, 1370, 410, 1420)))
        val fields = AnchoredScreenText.extract(MLKitOcrProvider.Layout(lines, emptyList()), parser,
            1080, 2340, ExtractionContext(bar = Rect(310, 745, 770, 758)))
        val initial = pokemon("Vulpix", null).copy(recognitionObservation =
            anchoredRecognitionObservation(fields, 4, snapshot.cpMultipliers.keys))
        val selected = attachAnchoredIdentity(initial, FamilySpeciesResolver(snapshot).resolve(initial), "Vulpix")
        assertEquals(4, selected.recognitionObservation?.frameIndex)
        val result = build("Vulpix", selected.recognitionObservation?.formEvidence)
        assertEquals(RecognitionSpeciesStatus.KNOWN, result.speciesStatus)
        assertEquals(RecognitionFormStatus.KNOWN, result.formStatus)
        assertEquals("Alolan Form", result.knownForm)
    }
}
