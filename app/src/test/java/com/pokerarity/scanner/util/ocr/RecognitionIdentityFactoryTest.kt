// Purpose: Phase 2E contract tests — species lock, form states, variant tri-state semantics.
package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.RecognitionFormStatus
import com.pokerarity.scanner.data.model.RecognitionIdentity
import com.pokerarity.scanner.data.model.RecognitionSpeciesStatus
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.data.model.VisualFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RecognitionIdentityFactory is the pure builder of the explicit recognition contract.
 * These tests pin the species-lock invariant, the form KNOWN/AMBIGUOUS/UNKNOWN states
 * and the TRUE/FALSE/UNKNOWN semantics of every variant flag.
 */
class RecognitionIdentityFactoryTest {

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun hardEvidence(
        species: String,
        authority: SpeciesAuthority = SpeciesAuthority.EXACT_CANONICAL
    ): SpeciesEvidence = SpeciesEvidence(
        selectedCanonicalSpecies = species,
        authority = authority,
        profileStatus = SpeciesProfileStatus.COMPATIBLE,
        reasonCodes = listOf(SpeciesEvidenceReason.EXACT),
        observationsAgree = true,
        authorityConflict = false
    )

    private fun softEvidence(species: String): SpeciesEvidence = SpeciesEvidence(
        selectedCanonicalSpecies = species,
        authority = SpeciesAuthority.SAFE_FUZZY,
        profileStatus = SpeciesProfileStatus.INDETERMINATE,
        reasonCodes = listOf(SpeciesEvidenceReason.SAFE_FUZZY),
        observationsAgree = true,
        authorityConflict = false
    )

    private fun formCandidate(
        form: String,
        source: String = "authoritative_variant_db"
    ): FormCandidateDiagnostic = FormCandidateDiagnostic(
        species = "Vulpix",
        form = form,
        score = 0.76f,
        source = source,
        reason = "owned_form_label_match"
    )

    private fun baseInput(): RecognitionIdentityFactory.Input = RecognitionIdentityFactory.Input(
        speciesEvidence = hardEvidence("Vulpix"),
        scanAccepted = true,
        lockedSpecies = "Vulpix",
        classifierSpecies = null,
        fullMatchWinnerSpecies = null,
        formCandidates = emptyList(),
        supportedFormIds = setOf("VULPIX_NORMAL"),
        mergedFeatures = VisualFeatures(),
        phase2ShinyDemoted = false,
        sizeTag = null
    )

    // ── Species KNOWN / UNKNOWN ───────────────────────────────────────────

    @Test
    fun knownSpecies_withIndependentProfileAuthority_keepsDisplayCasing() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(
                speciesEvidence = hardEvidence("Mr. Mime", SpeciesAuthority.INDEPENDENT_PROFILE),
                lockedSpecies = "Mr. Mime"
            )
        )

        assertEquals(RecognitionSpeciesStatus.KNOWN, identity.speciesStatus)
        assertEquals("Mr. Mime", identity.canonicalSpecies)
        assertEquals(SpeciesEvidenceReason.INDEPENDENT_PROFILE, identity.speciesAuthority)
    }

    @Test
    fun knownSpecies_withReviewedAliasAuthority_isKnown() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(
                speciesEvidence = hardEvidence("Farfetch'd", SpeciesAuthority.REVIEWED_ALIAS),
                lockedSpecies = "Farfetch'd"
            )
        )

        assertEquals(RecognitionSpeciesStatus.KNOWN, identity.speciesStatus)
        assertEquals("Farfetch'd", identity.canonicalSpecies)
        assertEquals(SpeciesEvidenceReason.REVIEWED_ALIAS, identity.speciesAuthority)
    }

    @Test
    fun softFuzzyAuthority_isNeverSpeciesAuthority() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(speciesEvidence = softEvidence("Vulpix"))
        )

        assertEquals(RecognitionSpeciesStatus.UNKNOWN, identity.speciesStatus)
        assertNull(identity.canonicalSpecies)
        assertNull(identity.speciesAuthority)
    }

    @Test
    fun failClosedEvidence_isUnknownSpecies() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(speciesEvidence = SpeciesEvidence.failClosed())
        )

        assertEquals(RecognitionSpeciesStatus.UNKNOWN, identity.speciesStatus)
        assertNull(identity.canonicalSpecies)
    }

    @Test
    fun rejectedScan_isUnknownSpeciesEvenWithHardEvidence() {
        val identity = RecognitionIdentityFactory.build(baseInput().copy(scanAccepted = false))

        assertEquals(RecognitionSpeciesStatus.UNKNOWN, identity.speciesStatus)
        assertNull(identity.canonicalSpecies)
        assertTrue(identity.speciesReasonCodes.contains("scan_not_accepted"))
    }

    @Test
    fun unknownMarkerIsNeverStoredAsAuthoritativeSpecies() {
        val evidence = hardEvidence("Unknown").copy(selectedCanonicalSpecies = "Unknown")
        val identity = RecognitionIdentityFactory.build(baseInput().copy(speciesEvidence = evidence))

        assertEquals(RecognitionSpeciesStatus.UNKNOWN, identity.speciesStatus)
        assertNull(identity.canonicalSpecies)
    }

    @Test
    fun conflictingAuthority_isUnknownSpecies() {
        val evidence = hardEvidence("Vulpix").copy(
            authority = SpeciesAuthority.CONFLICT,
            authorityConflict = true,
            observationsAgree = false
        )
        val identity = RecognitionIdentityFactory.build(baseInput().copy(speciesEvidence = evidence))

        assertEquals(RecognitionSpeciesStatus.UNKNOWN, identity.speciesStatus)
    }

    // ── Species lock invariant (weak classifier safety matrix) ────────────

    @Test
    fun lockMatrix_hardSpeciesA_withClassifierSpeciesA_staysKnownWithoutMismatch() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(classifierSpecies = "Vulpix")
        )

        assertEquals(RecognitionSpeciesStatus.KNOWN, identity.speciesStatus)
        assertEquals("Vulpix", identity.canonicalSpecies)
        assertNull(identity.classifierMismatch)
    }

    @Test
    fun lockMatrix_hardSpeciesA_withSameFamilyClassifierB_keepsSpeciesA() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(classifierSpecies = "Ninetales")
        )

        assertEquals(RecognitionSpeciesStatus.KNOWN, identity.speciesStatus)
        assertEquals("Vulpix", identity.canonicalSpecies)
        assertEquals("Ninetales", identity.classifierMismatch?.suggestedSpecies)
        assertEquals("Vulpix", identity.classifierMismatch?.lockedSpecies)
        assertEquals(RecognitionIdentity.MISMATCH_REASON_CLASSIFIER, identity.classifierMismatch?.reason)
        assertEquals("variant_classifier", identity.classifierMismatch?.source)
    }

    @Test
    fun lockMatrix_hardSpeciesA_withCrossFamilyClassifierB_keepsSpeciesA() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(classifierSpecies = "Charizard")
        )

        assertEquals(RecognitionSpeciesStatus.KNOWN, identity.speciesStatus)
        assertEquals("Vulpix", identity.canonicalSpecies)
        assertEquals("Charizard", identity.classifierMismatch?.suggestedSpecies)
    }

    @Test
    fun lockMatrix_hardSpeciesA_withWeakGlobalClassifierB_keepsSpeciesA() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(
                classifierSpecies = "Skwovet",
                mergedFeatures = VisualFeatures(hasCostume = true)
            )
        )

        // Weak classifier species stays a recorded suggestion; the costume positive it
        // carried still may enrich variants, but species identity is untouched.
        assertEquals("Vulpix", identity.canonicalSpecies)
        assertEquals(RecognitionTruth.TRUE, identity.costume)
        assertEquals("Skwovet", identity.classifierMismatch?.suggestedSpecies)
    }

    @Test
    fun lockMatrix_hardSpeciesA_withFullVariantMatchSpeciesB_keepsSpeciesAAndRecordsMismatch() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(fullMatchWinnerSpecies = "Ninetales")
        )

        assertEquals(RecognitionSpeciesStatus.KNOWN, identity.speciesStatus)
        assertEquals("Vulpix", identity.canonicalSpecies)
        assertEquals(RecognitionIdentity.MISMATCH_REASON_FULL_VARIANT, identity.classifierMismatch?.reason)
        assertEquals("full_variant_match", identity.classifierMismatch?.source)
        assertEquals("Ninetales", identity.classifierMismatch?.suggestedSpecies)
    }

    @Test
    fun lockMatrix_fullVariantMismatchTakesPriorityOverClassifierMismatch() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(
                classifierSpecies = "Charizard",
                fullMatchWinnerSpecies = "Ninetales"
            )
        )

        assertEquals("full_variant_match", identity.classifierMismatch?.source)
        assertEquals("Ninetales", identity.classifierMismatch?.suggestedSpecies)
    }

    @Test
    fun lockMatrix_unknownSpeciesWithClassifierSuggestion_doesNotInventKnownSpecies() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(
                speciesEvidence = SpeciesEvidence.failClosed(),
                lockedSpecies = null,
                classifierSpecies = "Charizard"
            )
        )

        assertEquals(RecognitionSpeciesStatus.UNKNOWN, identity.speciesStatus)
        assertNull(identity.canonicalSpecies)
        assertNull(identity.classifierMismatch)
    }

    @Test
    fun lockMatrix_noClassifierEvidence_leavesIdentityUnchanged() {
        val identity = RecognitionIdentityFactory.build(baseInput())

        assertEquals(RecognitionSpeciesStatus.KNOWN, identity.speciesStatus)
        assertEquals("Vulpix", identity.canonicalSpecies)
        assertNull(identity.classifierMismatch)
    }

    @Test
    fun lockedSpeciesFromGateDisagreeingWithEvidence_isUnknownSpecies() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(lockedSpecies = "Ninetales")
        )

        assertEquals(RecognitionSpeciesStatus.UNKNOWN, identity.speciesStatus)
        assertTrue(identity.speciesReasonCodes.contains("gate_species_disagreement"))
    }

    // ── Form KNOWN / AMBIGUOUS / UNKNOWN ──────────────────────────────────

    @Test
    fun formKnown_singleTrustedRegionalLabel() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(
                supportedFormIds = setOf("VULPIX_NORMAL", "VULPIX_ALOLA"),
                formCandidates = listOf(formCandidate("Alolan"))
            )
        )

        assertEquals(RecognitionFormStatus.KNOWN, identity.formStatus)
        assertEquals("Alolan", identity.knownForm)
        assertEquals(RecognitionIdentityFactory.FORM_PROVENANCE_OWNED_LABEL, identity.formProvenance)
        assertTrue(identity.ambiguousForms.isEmpty())
    }

    @Test
    fun formAmbiguous_multipleSurvivingTrustedLabels() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(
                formCandidates = listOf(formCandidate("Origin"), formCandidate("Altered"))
            )
        )

        assertEquals(RecognitionFormStatus.AMBIGUOUS, identity.formStatus)
        assertNull(identity.knownForm)
        assertEquals(listOf("Origin", "Altered"), identity.ambiguousForms)
        assertEquals(RecognitionIdentityFactory.FORM_PROVENANCE_OWNED_LABELS, identity.formProvenance)
    }

    @Test
    fun formAmbiguous_multiFormSnapshotRowsWithoutLabel() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(
                supportedFormIds = setOf("DEOXYS_NORMAL", "DEOXYS_ATTACK", "DEOXYS_DEFENSE", "DEOXYS_SPEED")
            )
        )

        assertEquals(RecognitionFormStatus.AMBIGUOUS, identity.formStatus)
        assertNull(identity.knownForm)
        assertEquals(4, identity.ambiguousForms.size)
        assertEquals(RecognitionIdentityFactory.FORM_PROVENANCE_SNAPSHOT_ROWS, identity.formProvenance)
    }

    @Test
    fun formUnknown_singleSupportedFormWithoutLabel_baseFormIsNotInferred() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(supportedFormIds = setOf("PURRLOIN_NORMAL"))
        )

        assertEquals(RecognitionFormStatus.UNKNOWN, identity.formStatus)
        assertNull(identity.knownForm)
        assertTrue(identity.ambiguousForms.isEmpty())
    }

    @Test
    fun formUnknown_whenSnapshotMetadataUnavailable() {
        val identity = RecognitionIdentityFactory.build(baseInput().copy(supportedFormIds = null))

        assertEquals(RecognitionFormStatus.UNKNOWN, identity.formStatus)
        assertTrue(identity.formReasonCodes.contains(RecognitionIdentityFactory.FORM_REASON_METADATA_UNAVAILABLE))
    }

    @Test
    fun formUnknown_whenSpeciesUnknown() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(
                speciesEvidence = SpeciesEvidence.failClosed(),
                lockedSpecies = null,
                formCandidates = listOf(formCandidate("Alolan"))
            )
        )

        assertEquals(RecognitionFormStatus.UNKNOWN, identity.formStatus)
        assertNull(identity.knownForm)
    }

    @Test
    fun formAlternatives_areBoundedToFive() {
        val labels = listOf("Alolan", "Galarian", "Hisuian", "Paldean", "Origin", "Altered")
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(formCandidates = labels.map(::formCandidate))
        )

        assertEquals(RecognitionFormStatus.AMBIGUOUS, identity.formStatus)
        assertEquals(RecognitionIdentity.MAX_FORM_ALTERNATIVES, identity.ambiguousForms.size)
    }

    @Test
    fun untrustedFormSources_doNotEstablishForm() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(formCandidates = listOf(formCandidate("Alolan", source = "display_text")))
        )

        assertEquals(RecognitionFormStatus.UNKNOWN, identity.formStatus)
    }

    @Test
    fun eventCostumeLabelsAreNotCanonicalFormIdentity() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(formCandidates = listOf(formCandidate("Holiday 2016")))
        )

        // A costume/event label arriving as a candidate must not by itself establish the
        // canonical form; the resolver's label filter normally excludes these outright.
        assertEquals(RecognitionFormStatus.UNKNOWN, identity.formStatus)
    }

    // ── Variant tri-state semantics ───────────────────────────────────────

    @Test
    fun shinyTrue_onPositiveMergedEvidence() {
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(mergedFeatures = VisualFeatures(isShiny = true))
        )
        assertEquals(RecognitionTruth.TRUE, identity.shiny)
    }

    @Test
    fun shinyFalse_onlyThroughPhase2ExplicitDemotion() {
        // Pipeline reality: the demotion overrides the merged boolean to false while the
        // explicit-negative signal records WHY it is false.
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(mergedFeatures = VisualFeatures(isShiny = false), phase2ShinyDemoted = true)
        )
        assertEquals(RecognitionTruth.FALSE, identity.shiny)
    }

    @Test
    fun shinyUnknown_whenDetectorDidNotFire() {
        val identity = RecognitionIdentityFactory.build(baseInput())
        assertEquals(RecognitionTruth.UNKNOWN, identity.shiny)
    }

    @Test
    fun presenceOnlyDetectors_stayUnknownWithoutPositiveEvidence() {
        val identity = RecognitionIdentityFactory.build(baseInput())

        assertEquals(RecognitionTruth.UNKNOWN, identity.shadow)
        assertEquals(RecognitionTruth.UNKNOWN, identity.purified)
        assertEquals(RecognitionTruth.UNKNOWN, identity.lucky)
        assertEquals(RecognitionTruth.UNKNOWN, identity.costume)
        assertEquals(RecognitionTruth.UNKNOWN, identity.specialForm)
        assertEquals(RecognitionTruth.UNKNOWN, identity.locationCard)
    }

    @Test
    fun purified_hasNoDetectorAndStaysUnknownEvenWithOtherPositives() {
        // No code path in the pipeline ever sets purified; a merged feature blob that
        // carries other positives must still report purified UNKNOWN.
        val identity = RecognitionIdentityFactory.build(
            baseInput().copy(mergedFeatures = VisualFeatures(isShadow = true, isLucky = true))
        )
        assertEquals(RecognitionTruth.UNKNOWN, identity.purified)
        assertEquals(RecognitionTruth.TRUE, identity.shadow)
        assertEquals(RecognitionTruth.TRUE, identity.lucky)
    }

    @Test
    fun sizeTagTrue_falseAndUnknownSemantics() {
        val xxs = RecognitionIdentityFactory.build(baseInput().copy(sizeTag = "XXS"))
        assertEquals(RecognitionTruth.TRUE, xxs.xxs)
        assertEquals(RecognitionTruth.FALSE, xxs.xxl)

        val xl = RecognitionIdentityFactory.build(baseInput().copy(sizeTag = "XL"))
        assertEquals(RecognitionTruth.FALSE, xl.xxs)
        assertEquals(RecognitionTruth.FALSE, xl.xxl)

        val unread = RecognitionIdentityFactory.build(baseInput().copy(sizeTag = null))
        assertEquals(RecognitionTruth.UNKNOWN, unread.xxs)
        assertEquals(RecognitionTruth.UNKNOWN, unread.xxl)
    }

    // ── SpeciesFormResolver integration ───────────────────────────────────

    @Test
    fun distinctTrustedFormLabels_dedupesCaseAndSeparators() {
        val labels = RecognitionIdentityFactory.distinctTrustedFormLabels(
            listOf(
                formCandidate("Alolan"),
                formCandidate("ALOLAN"),
                formCandidate("Alolan", source = "display_text"),
                formCandidate("Galarian")
            )
        )

        assertEquals(listOf("Alolan", "Galarian"), labels)
    }

    @Test
    fun distinctTrustedFormLabels_rejectsEventAndCostumeLabels() {
        val labels = RecognitionIdentityFactory.distinctTrustedFormLabels(
            listOf(
                formCandidate("Holiday 2016"),
                formCandidate("Costume Party"),
                formCandidate("Alolan")
            )
        )

        assertEquals(listOf("Alolan"), labels)
    }

    @Test
    fun resolverFormAlternativesCommunicateSurvivingAmbiguity() {
        // SpeciesFormResolution.formAlternatives exposes the surviving trusted labels so
        // the threshold-selected winningForm can never erase remaining alternatives.
        val resolution = SpeciesFormResolution(
            species = "Vulpix",
            form = "Alolan",
            confidence = 0.9f,
            reasons = listOf("exact_name_match:Name"),
            alternatives = emptyList(),
            trace = SpeciesResolverTrace(
                formCandidates = listOf(formCandidate("Alolan"), formCandidate("Galarian"))
            )
        )

        assertEquals(listOf("Alolan", "Galarian"), resolution.formAlternatives)
    }

    @Test
    fun resolverFormAlternativesEmptyWithoutTrustedLabels() {
        val resolution = SpeciesFormResolution(
            species = "Purrloin",
            form = null,
            confidence = 0.9f,
            reasons = listOf("exact_name_match:Name"),
            alternatives = emptyList(),
            trace = SpeciesResolverTrace()
        )

        assertTrue(resolution.formAlternatives.isEmpty())
    }
}
