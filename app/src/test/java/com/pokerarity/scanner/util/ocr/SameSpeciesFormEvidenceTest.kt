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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun build(
        species: String,
        evidence: SameSpeciesFormEvidence?,
        accepted: Boolean = true,
        formCandidates: List<FormCandidateDiagnostic> = emptyList()
    ) =
        RecognitionIdentityFactory.build(RecognitionIdentityFactory.Input(
            SpeciesEvidence(species, SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.COMPATIBLE,
                listOf(SpeciesEvidenceReason.EXACT), true, false),
            accepted, species, null, null, formCandidates, VisualFeatures(), false, null, evidence))

    private fun observed(species: String, types: Set<String>?) =
        FamilySpeciesResolver(snapshot).resolve(pokemon(species, types)).formEvidence[species]

    @Test
    fun supportedTypeDistinguishesRegionalFormWithinLockedSpecies() {
        assertEquals("Alolan Form", build("Vulpix", observed("Vulpix", setOf("ice"))).knownForm)
        assertEquals("Normal Form", build("Vulpix", observed("Vulpix", setOf("fire"))).knownForm)
    }

    @Test
    fun ownedAliasAndSnapshotLabelForSameSlowbroFormKeepFormAndNarrowProfiles() {
        val evidence = SameSpeciesFormEvidence(
            species = "Slowbro",
            snapshotRevision = "${RecognitionSnapshot.SUPPORTED_SCHEMA_VERSION}:" +
                RecognitionSnapshot.EXPECTED_SOURCE_REVISION,
            alternatives = listOf("Galarian Form"),
            reasonCode = "same_species_profile_witness"
        )
        val ownedAlias = listOf(
            FormCandidateDiagnostic(
                species = "Slowbro",
                form = "Slowbro Galarian",
                score = 0.76f,
                source = "authoritative_variant_db",
                reason = "owned_form_label_match"
            )
        )

        val identity = build(
            "Slowbro",
            evidence,
            formCandidates = ownedAlias
        )

        assertEquals(RecognitionFormStatus.KNOWN, identity.formStatus)
        assertEquals("Galarian Form", identity.knownForm)
        val supported = SameSpeciesFormDecision.profilesSupportedByIdentity(snapshot.forSpecies("Slowbro"), identity)
        assertEquals(setOf("SLOWBRO_GALARIAN"), supported.flatMap { it.forms }.toSet())
        assertEquals(setOf(RecognitionSnapshot.ProfileStats(182, 156, 216)), supported.map { it.stats }.toSet())

        val conflicting = build(
            "Slowbro",
            evidence.copy(alternatives = listOf("Normal Form")),
            formCandidates = ownedAlias
        )
        assertEquals(RecognitionFormStatus.UNKNOWN, conflicting.formStatus)
        assertTrue(conflicting.formReasonCodes.contains("form_evidence_conflict"))
    }

    @Test
    fun indistinguishableSupportedFormsRemainAmbiguous() {
        val result = build("Giratina", observed("Giratina", setOf("ghost", "dragon")))
        assertEquals(RecognitionFormStatus.AMBIGUOUS, result.formStatus)
        assertEquals(setOf("Altered Form", "Origin Form"), result.ambiguousForms.toSet())
    }

    @Test
    fun compoundSnapshotFormIdsSelectEveryProfileForTheirKnownRegionalForm() {
        val taurosIdentity = build("Tauros", observed("Tauros", null)).copy(
            formStatus = RecognitionFormStatus.KNOWN,
            knownForm = "Paldean Form"
        )
        val paldeanTauros = SameSpeciesFormDecision.profilesSupportedByIdentity(
            snapshot.forSpecies("Tauros"), taurosIdentity
        )

        assertEquals(
            setOf("TAUROS_PALDEA_COMBAT", "TAUROS_PALDEA_BLAZE", "TAUROS_PALDEA_AQUA"),
            paldeanTauros.flatMap { it.forms }.toSet()
        )
        assertEquals(setOf(RecognitionSnapshot.ProfileStats(210, 193, 181)), paldeanTauros.map { it.stats }.toSet())
        assertFalse(paldeanTauros.any { "TAUROS_NORMAL" in it.forms })

        val darmanitanIdentity = build("Darmanitan", observed("Darmanitan", null)).copy(
            formStatus = RecognitionFormStatus.KNOWN,
            knownForm = "Galarian Form"
        )
        val galarianDarmanitan = SameSpeciesFormDecision.profilesSupportedByIdentity(
            snapshot.forSpecies("Darmanitan"), darmanitanIdentity
        )

        assertEquals(
            setOf("DARMANITAN_GALARIAN_STANDARD", "DARMANITAN_GALARIAN_ZEN"),
            galarianDarmanitan.flatMap { it.forms }.toSet()
        )
        assertEquals(
            setOf(RecognitionSnapshot.ProfileStats(263, 114, 233), RecognitionSnapshot.ProfileStats(323, 123, 233)),
            galarianDarmanitan.map { it.stats }.toSet()
        )
        val standardForms = setOf("DARMANITAN_STANDARD", "DARMANITAN_ZEN")
        assertFalse(galarianDarmanitan.any { profile -> profile.forms.any(standardForms::contains) })
    }

    @Test
    fun sameWitnessTypesResolveCompoundPaldeanFormIdentifiers() {
        val evidence = observed("Tauros", setOf("fighting"))

        assertTrue(evidence?.alternatives?.isNotEmpty() == true)
        assertEquals("same_species_profile_witness", evidence?.reasonCode)
        assertEquals(listOf("Paldean Form"), evidence?.alternatives)
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
