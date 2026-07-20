package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFrameFusionDetailedPassTest {

    @Test
    fun detailedPassRequiredWhenCpIsMissing() {
        val shouldRun = ScanFrameFusion.shouldRunDetailedPass(
            pokemon = pokemon(cp = null),
            cpQuality = 0.90,
            speciesEvidence = evidence()
        )

        assertTrue(shouldRun)
    }

    @Test
    fun detailedPassRequiredWhenSpeciesIsUnknown() {
        val shouldRun = ScanFrameFusion.shouldRunDetailedPass(
            pokemon = pokemon(name = "Unknown", realName = "Unknown"),
            cpQuality = 0.90,
            speciesEvidence = SpeciesEvidence.failClosed(SpeciesProfileStatus.COMPATIBLE)
        )

        assertTrue(shouldRun)
    }

    @Test
    fun detailedPassSkippedWhenCpNameDateAndHpAreReliable() {
        val shouldRun = ScanFrameFusion.shouldRunDetailedPass(
            pokemon = pokemon(cp = 621, name = "Pikachu", realName = "Pikachu", caughtDate = defaultCaughtDate),
            cpQuality = 0.90,
            speciesEvidence = evidence()
        )

        assertFalse(shouldRun)
    }

    @Test
    fun detailedPassRequiredWhenCpQualityBelowMinimum() {
        val shouldRun = ScanFrameFusion.shouldRunDetailedPass(
            pokemon = pokemon(
                cp = 621, name = "Pikachu", realName = "Pikachu",
                hp = 84, caughtDate = defaultCaughtDate
            ),
            cpQuality = 0.50,
            speciesEvidence = evidence()
        )

        assertTrue(shouldRun)
    }

    @Test
    fun detailedPassRequiredWhenTextConfidenceBelowThreshold() {
        // Legacy raw-confidence overload: independent 0.86 threshold test.
        val shouldRun = ScanFrameFusion.shouldRunDetailedPass(
            pokemon = pokemon(
                cp = 621, name = "Pikachu", realName = "Pikachu",
                hp = 84, caughtDate = defaultCaughtDate
            ),
            cpQuality = 0.90,
            topTextConfidence = 0.85
        )

        assertTrue(shouldRun)
    }

    @Test
    fun detailedPassSkippedWhenAllSignalsAboveThresholds() {
        // Legacy raw-confidence overload: 0.86 boundary still applies.
        val shouldRun = ScanFrameFusion.shouldRunDetailedPass(
            pokemon = pokemon(
                cp = 621,
                name = "Pikachu",
                realName = "Pikachu",
                hp = 84,
                caughtDate = defaultCaughtDate
            ),
            cpQuality = ScanFrameFusion.CP_QUALITY_MIN,
            topTextConfidence = 0.86
        )

        assertFalse(shouldRun)
    }

    @Test
    fun structuredSpeciesUncertaintyRequestsDetailedPass() {
        val blockedEvidence = listOf(
            evidence(authority = SpeciesAuthority.SAFE_FUZZY),
            evidence(authority = SpeciesAuthority.UNCERTAIN),
            evidence(authority = SpeciesAuthority.NO_MATCH),
            evidence(
                authority = SpeciesAuthority.CONFLICT,
                tuning = EvidenceTuning(conflict = true, observationsAgree = false)
            ),
            evidence(tuning = EvidenceTuning(candidatesClose = true)),
            evidence(tuning = EvidenceTuning(profileStatus = SpeciesProfileStatus.MISSING)),
            evidence(tuning = EvidenceTuning(profileStatus = SpeciesProfileStatus.CONTRADICTORY))
        )

        blockedEvidence.forEach { speciesEvidence ->
            assertTrue(
                speciesEvidence.toString(),
                ScanFrameFusion.shouldRunDetailedPass(
                    pokemon = pokemon(),
                    cpQuality = 0.90,
                    speciesEvidence = speciesEvidence
                )
            )
        }
    }

    @Test
    fun reviewedAliasWithCompatibleProfileDoesNotForceDetailedPass() {
        assertFalse(
            ScanFrameFusion.shouldRunDetailedPass(
                pokemon = pokemon(),
                cpQuality = 0.90,
                speciesEvidence = evidence(authority = SpeciesAuthority.REVIEWED_ALIAS)
            )
        )
    }

    @Test
    fun reviewedAliasWithAuthorityBandedScoreSkipsDetailedPass() {
        // topCandidateScore is authority-banded (0.75-0.80) and must NOT be
        // consumed as legacy topTextConfidence under the structured path.
        listOf(0.75f, 0.80f).forEach { banded ->
            val speciesEvidence = evidence(authority = SpeciesAuthority.REVIEWED_ALIAS)
                .copy(topCandidateScore = banded)
            assertFalse(
                "REVIEWED_ALIAS with topCandidateScore=$banded must skip detailed pass",
                ScanFrameFusion.shouldRunDetailedPass(
                    pokemon = pokemon(),
                    cpQuality = 0.90,
                    speciesEvidence = speciesEvidence
                )
            )
        }
    }

    @Test
    fun exactCanonicalWithAuthorityBandedScoreSkipsDetailedPass() {
        val speciesEvidence = evidence(authority = SpeciesAuthority.EXACT_CANONICAL)
            .copy(topCandidateScore = 0.95f)
        assertFalse(
            "EXACT_CANONICAL with complete evidence must skip detailed pass",
            ScanFrameFusion.shouldRunDetailedPass(
                pokemon = pokemon(),
                cpQuality = 0.90,
                speciesEvidence = speciesEvidence
            )
        )
    }

    @Test
    fun safeFuzzyAuthorityRequestsDetailedPass() {
        assertTrue(
            ScanFrameFusion.shouldRunDetailedPass(
                pokemon = pokemon(),
                cpQuality = 0.90,
                speciesEvidence = evidence(authority = SpeciesAuthority.SAFE_FUZZY)
            )
        )
    }

    @Test
    fun weakAuthoritiesRequestDetailedPass() {
        listOf(
            SpeciesAuthority.UNCERTAIN,
            SpeciesAuthority.NO_MATCH,
            SpeciesAuthority.CONFLICT
        ).forEach { authority ->
            val conflict = authority == SpeciesAuthority.CONFLICT
            val speciesEvidence = evidence(
                authority = authority,
                tuning = EvidenceTuning(conflict = conflict, observationsAgree = !conflict)
            )
            assertTrue(
                "$authority must request detailed pass",
                ScanFrameFusion.shouldRunDetailedPass(
                    pokemon = pokemon(),
                    cpQuality = 0.90,
                    speciesEvidence = speciesEvidence
                )
            )
        }
    }

    @Test
    fun closeCandidateMarginRequestsDetailedPass() {
        listOf(SpeciesAuthority.EXACT_CANONICAL, SpeciesAuthority.REVIEWED_ALIAS).forEach { authority ->
            val speciesEvidence = evidence(
                authority = authority,
                tuning = EvidenceTuning(candidatesClose = true)
            )
            assertTrue(
                "$authority with close candidates must request detailed pass",
                ScanFrameFusion.shouldRunDetailedPass(
                    pokemon = pokemon(),
                    cpQuality = 0.90,
                    speciesEvidence = speciesEvidence
                )
            )
        }
    }

    @Test
    fun badProfileStatusRequestsDetailedPass() {
        listOf(
            SpeciesProfileStatus.MISSING,
            SpeciesProfileStatus.CONTRADICTORY,
            SpeciesProfileStatus.IMPOSSIBLE,
            SpeciesProfileStatus.INDETERMINATE
        ).forEach { status ->
            val speciesEvidence = evidence(tuning = EvidenceTuning(profileStatus = status))
            assertTrue(
                "$status profile must request detailed pass",
                ScanFrameFusion.shouldRunDetailedPass(
                    pokemon = pokemon(),
                    cpQuality = 0.90,
                    speciesEvidence = speciesEvidence
                )
            )
        }
    }

    @Test
    fun missingRequiredFieldsRequestDetailedPass() {
        assertTrue(ScanFrameFusion.shouldRunDetailedPass(pokemon(cp = null), 0.90, evidence()))
        assertTrue(ScanFrameFusion.shouldRunDetailedPass(pokemon(hp = null, maxHp = null), 0.90, evidence()))
        assertTrue(ScanFrameFusion.shouldRunDetailedPass(pokemon(caughtDate = null), 0.90, evidence()))
    }

    @Test
    fun lowCpQualityRequestsDetailedPass() {
        assertTrue(
            ScanFrameFusion.shouldRunDetailedPass(
                pokemon = pokemon(),
                cpQuality = ScanFrameFusion.CP_QUALITY_MIN - 0.01,
                speciesEvidence = evidence()
            )
        )
    }
}
