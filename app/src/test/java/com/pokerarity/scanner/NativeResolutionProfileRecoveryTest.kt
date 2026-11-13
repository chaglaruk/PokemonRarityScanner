package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.util.ocr.RecognitionObservation
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeResolutionProfileRecoveryTest {

    @Test
    fun independentNativeProfileRecoversCpContradictionAcrossStableFastBurst() {
        val fast = frame(
            FrameSpec(path = "fast-0", frameIndex = 0, cp = 735, width = 900),
            profileStatus = SpeciesProfileStatus.CONTRADICTORY
        )
        val otherFast = frame(
            FrameSpec(path = "fast-1", frameIndex = 1, cp = 735, width = 900),
            profileStatus = SpeciesProfileStatus.CONTRADICTORY
        )
        val native = frame(
            FrameSpec(path = fast.path, frameIndex = 0, cp = 734, width = 1080),
            authority = SpeciesAuthority.INDEPENDENT_PROFILE
        )

        val selection = checkNotNull(ScanFrameFusion.resolveAnchoredFrames(
            frames = listOf(fast, otherFast),
            authoritative = fast,
            detailed = native
        ) { candidate, _, _ -> candidate.speciesEvidence })

        assertEquals("Skwovet", selection.speciesEvidence.selectedCanonicalSpecies)
        assertEquals(SpeciesAuthority.INDEPENDENT_PROFILE, selection.speciesEvidence.authority)
        assertFalse(selection.speciesEvidence.authorityConflict)
        assertTrue(selection.speciesEvidence.reasonCodes.contains("native_resolution_recovered_profile"))
        assertEquals(native, selection.frame)
        assertEquals(1080, selection.frame.recognitionWidth)
    }

    @Test
    fun recoveryRejectsTextOnlyStaleConflictedAndNonmatchingEvidence() {
        val fast = frame(
            FrameSpec(path = "fast-0", frameIndex = 0, cp = 735, width = 900),
            profileStatus = SpeciesProfileStatus.CONTRADICTORY
        )
        val textOnlyNative = frame(
            FrameSpec(path = fast.path, frameIndex = 0, cp = 734, width = 1080),
            profileStatus = SpeciesProfileStatus.COMPATIBLE
        )
        assertConflict(resolve(fast, textOnlyNative))

        val conflictedFast = fast.copy(
            speciesEvidence = fast.speciesEvidence.copy(authorityConflict = true)
        )
        val independentNative = frame(
            FrameSpec(path = conflictedFast.path, frameIndex = 0, cp = 734, width = 1080),
            authority = SpeciesAuthority.INDEPENDENT_PROFILE
        )
        assertConflict(resolve(conflictedFast, independentNative))

        val staleNative = independentNative.copy(path = "different-capture")
        assertConflict(resolve(fast, staleNative))

        val mismatchedNative = independentNative.copy(
            data = independentNative.data.copy(
                recognitionObservation = checkNotNull(independentNative.data.recognitionObservation)
                    .copy(types = setOf("dark"))
            )
        )
        assertConflict(resolve(fast, mismatchedNative))
    }

    private fun resolve(
        fast: ScanFrameCandidate,
        detailed: ScanFrameCandidate
    ) = checkNotNull(ScanFrameFusion.resolveAnchoredFrames(
        frames = listOf(fast),
        authoritative = fast,
        detailed = detailed
    ) { candidate, _, _ -> candidate.speciesEvidence })

    private fun frame(
        spec: FrameSpec,
        authority: SpeciesAuthority = SpeciesAuthority.EXACT_CANONICAL,
        profileStatus: SpeciesProfileStatus = SpeciesProfileStatus.COMPATIBLE,
        authorityConflict: Boolean = false,
        types: Set<String> = setOf("normal")
    ): ScanFrameCandidate {
        val observation = RecognitionObservation(
            candySpecies = "Skwovet",
            powerUpStardust = null,
            types = types,
            detailScreen = true,
            frameIndex = spec.frameIndex
        )
        val pokemon = PokemonData(
            cp = spec.cp,
            hp = 133,
            maxHp = 133,
            name = "Skwovet",
            realName = "Skwovet",
            candyName = "Skwovet",
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = null,
            recognitionObservation = observation
        )
        val evidence = SpeciesEvidence(
            selectedCanonicalSpecies = "Skwovet",
            authority = authority,
            profileStatus = profileStatus,
            reasonCodes = if (authority == SpeciesAuthority.INDEPENDENT_PROFILE) {
                listOf(SpeciesEvidenceReason.INDEPENDENT_PROFILE)
            } else {
                listOf(SpeciesEvidenceReason.EXACT, SpeciesEvidenceReason.PROFILE_CONTRADICTORY)
            },
            observationsAgree = !authorityConflict,
            authorityConflict = authorityConflict
        )
        return ScanFrameCandidate(
            path = spec.path,
            data = pokemon,
            cpQuality = 0.9,
            speciesEvidence = evidence,
            recognitionWidth = spec.width
        )
    }

    private fun assertConflict(selection: com.pokerarity.scanner.service.AnchoredFrameSelection) {
        assertEquals(SpeciesAuthority.CONFLICT, selection.speciesEvidence.authority)
        assertEquals(SpeciesProfileStatus.CONTRADICTORY, selection.speciesEvidence.profileStatus)
        assertTrue(selection.speciesEvidence.authorityConflict)
    }

    private data class FrameSpec(
        val path: String,
        val frameIndex: Int,
        val cp: Int,
        val width: Int
    )
}
