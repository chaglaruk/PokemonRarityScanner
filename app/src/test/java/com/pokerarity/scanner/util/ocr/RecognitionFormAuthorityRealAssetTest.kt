// Purpose: Real-asset regression pinning WHY raw Game Master form ids are non-authoritative.
package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.RecognitionSnapshotTestSupport
import com.pokerarity.scanner.data.model.RecognitionFormStatus
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2E real-asset regression (project-chat live review finding).
 *
 * The checked Phase 2D recognition asset's raw Game Master `forms` field is NOT a typed
 * list of ordinary canonical forms: it mixes ordinary forms with costume/event ids and
 * temporary evolutions (Pikachu carries dozens of event/costume ids next to
 * PIKACHU_NORMAL; Charizard carries COPY_2019 plus TEMP_EVOLUTION_MEGA_X/Y). The
 * earlier "raw snapshot form multiplicity -> FORM AMBIGUOUS" fallback was therefore
 * unsafe and has been removed. These tests pin that the raw lists — checked from the
 * real asset — never reach Phase 2E canonical form state: without trustworthy owned
 * form-label evidence, the contract must stay FORM UNKNOWN.
 */
class RecognitionFormAuthorityRealAssetTest {

    /**
     * Raw Game Master `forms` ids straight from the checked asset file — read through
     * raw JSON on purpose, so this characterization is independent of any typed loader.
     */
    private fun rawFormIds(species: String): Set<String> {
        val file = java.io.File(RecognitionSnapshotTestSupport.assetDir(), "recognition_profiles.json")
        val root = com.google.gson.JsonParser.parseString(file.readText()).asJsonObject
        val rows = root.getAsJsonArray("profiles")
        val forms = linkedSetOf<String>()
        for (row in rows) {
            val obj = row.asJsonObject
            if (obj.getAsJsonPrimitive("species").asString.equals(species, ignoreCase = true)) {
                for (form in obj.getAsJsonArray("forms")) {
                    forms += form.asString
                }
            }
        }
        return forms
    }

    private fun lockedIdentity(species: String): RecognitionIdentity = RecognitionIdentityFactory.build(
        RecognitionIdentityFactory.Input(
            speciesEvidence = SpeciesEvidence(
                selectedCanonicalSpecies = species,
                authority = SpeciesAuthority.EXACT_CANONICAL,
                profileStatus = SpeciesProfileStatus.COMPATIBLE,
                reasonCodes = listOf(SpeciesEvidenceReason.EXACT),
                observationsAgree = true,
                authorityConflict = false
            ),
            scanAccepted = true,
            lockedSpecies = species,
            classifierSpecies = null,
            fullMatchWinnerSpecies = null,
            formCandidates = emptyList(),
            mergedFeatures = com.pokerarity.scanner.data.model.VisualFeatures(),
            phase2ShinyDemoted = false,
            sizeTag = null
        )
    )

    // ── Real-asset characterization (why the fallback was unsafe) ──────────

    @Test
    fun pikachuRawSnapshotFormsMixEventCostumeIdsWithNormal() {
        val forms = rawFormIds("Pikachu")

        assertTrue("Pikachu must carry multiple raw Game Master form ids", forms.size > 1)
        assertTrue("Pikachu must carry its NORMAL form id", "PIKACHU_NORMAL" in forms)
        val nonNormal = forms - "PIKACHU_NORMAL"
        assertTrue(
            "Pikachu must carry many event/costume raw form ids, found $nonNormal",
            nonNormal.size >= 5
        )
    }

    @Test
    fun charizardRawSnapshotFormsContainCopyNormalAndTempEvolutionMegaIds() {
        val forms = rawFormIds("Charizard")

        assertTrue("CHARIZARD_COPY_2019" in forms)
        assertTrue("CHARIZARD_NORMAL" in forms)
        assertTrue("CHARIZARD/TEMP_EVOLUTION_MEGA_X" in forms)
        assertTrue("CHARIZARD/TEMP_EVOLUTION_MEGA_Y" in forms)
    }

    // ── Raw multiplicity must not reach canonical form state ──────────────

    @Test
    fun pikachuRawFormMultiplicity_staysFormUnknown_withoutTrustedLabel() {
        val identity = lockedIdentity("Pikachu")

        assertEquals(RecognitionSpeciesStatus.KNOWN, identity.speciesStatus)
        assertEquals("Pikachu", identity.canonicalSpecies)
        assertEquals(RecognitionFormStatus.UNKNOWN, identity.formStatus)
        assertNull(identity.knownForm)
        assertTrue(identity.ambiguousForms.isEmpty())
        assertTrue(
            "no raw snapshot form id may leak into form state",
            identity.ambiguousForms.none { it in rawFormIds("Pikachu") }
        )
    }

    @Test
    fun charizardRawFormMultiplicity_staysFormUnknown_withoutTrustedLabel() {
        val identity = lockedIdentity("Charizard")

        assertEquals(RecognitionSpeciesStatus.KNOWN, identity.speciesStatus)
        assertEquals("Charizard", identity.canonicalSpecies)
        assertEquals(RecognitionFormStatus.UNKNOWN, identity.formStatus)
        assertNull(identity.knownForm)
        assertTrue(identity.ambiguousForms.isEmpty())
        assertEquals(0, identity.ambiguousForms.size)
    }

    @Test
    fun rawGameMasterFormIdsCannotLeakThroughTheLabelPathEither() {
        // Worst case: even if raw ids ever arrived disguised as trusted resolver
        // candidates, they are not ordinary form labels and must not establish form.
        val rawIds = rawFormIds("Charizard").map { id ->
            FormCandidateDiagnostic(
                species = "Charizard",
                form = id,
                score = 0.76f,
                source = "authoritative_variant_db",
                reason = "owned_form_label_match"
            )
        }
        val identity = RecognitionIdentityFactory.build(
            RecognitionIdentityFactory.Input(
                speciesEvidence = SpeciesEvidence(
                    selectedCanonicalSpecies = "Charizard",
                    authority = SpeciesAuthority.EXACT_CANONICAL,
                    profileStatus = SpeciesProfileStatus.COMPATIBLE,
                    reasonCodes = listOf(SpeciesEvidenceReason.EXACT),
                    observationsAgree = true,
                    authorityConflict = false
                ),
                scanAccepted = true,
                lockedSpecies = "Charizard",
                classifierSpecies = null,
                fullMatchWinnerSpecies = null,
                formCandidates = rawIds,
                mergedFeatures = com.pokerarity.scanner.data.model.VisualFeatures(),
                phase2ShinyDemoted = false,
                sizeTag = null
            )
        )

        assertEquals(RecognitionFormStatus.UNKNOWN, identity.formStatus)
        assertNull(identity.knownForm)
        assertTrue(identity.ambiguousForms.isEmpty())
    }

    // ── Positive contract still reachable for real snapshot species ───────

    @Test
    fun singleTrustedSameSpeciesLabel_stillEstablishesKnownForm() {
        val identity = RecognitionIdentityFactory.build(
            RecognitionIdentityFactory.Input(
                speciesEvidence = SpeciesEvidence(
                    selectedCanonicalSpecies = "Vulpix",
                    authority = SpeciesAuthority.EXACT_CANONICAL,
                    profileStatus = SpeciesProfileStatus.COMPATIBLE,
                    reasonCodes = listOf(SpeciesEvidenceReason.EXACT),
                    observationsAgree = true,
                    authorityConflict = false
                ),
                scanAccepted = true,
                lockedSpecies = "Vulpix",
                classifierSpecies = null,
                fullMatchWinnerSpecies = null,
                formCandidates = listOf(
                    FormCandidateDiagnostic(
                        species = "Vulpix",
                        form = "Alolan",
                        score = 0.76f,
                        source = "authoritative_variant_db",
                        reason = "owned_form_label_match"
                    )
                ),
                mergedFeatures = com.pokerarity.scanner.data.model.VisualFeatures(),
                phase2ShinyDemoted = false,
                sizeTag = null
            )
        )

        assertEquals(RecognitionFormStatus.KNOWN, identity.formStatus)
        assertEquals("Alolan", identity.knownForm)
    }
}
