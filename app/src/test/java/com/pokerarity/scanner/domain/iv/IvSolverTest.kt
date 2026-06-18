package com.pokerarity.scanner.domain.iv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IvSolverTest {
    private val bulbasaur = PokemonBaseStats(attack = 118, defense = 111, stamina = 128)
    private val mewtwo = PokemonBaseStats(attack = 300, defense = 182, stamina = 214)

    @Test
    fun combatMathMatchesKnownFixtures() {
        assertEquals(4178, IvSolver.calculateCp(mewtwo, attackIv = 15, defenseIv = 15, staminaIv = 15, level = 40.0))
        assertEquals(4724, IvSolver.calculateCp(mewtwo, attackIv = 15, defenseIv = 15, staminaIv = 15, level = 50.0))
        assertEquals(180, IvSolver.calculateHp(mewtwo, staminaIv = 15, level = 40.0))

        assertEquals(620, IvSolver.calculateCp(bulbasaur, attackIv = 12, defenseIv = 14, staminaIv = 15, level = 20.0))
        assertEquals(85, IvSolver.calculateHp(bulbasaur, staminaIv = 15, level = 20.0))
    }

    @Test
    fun exactEvidenceReturnsSingleCandidate() {
        val result = IvSolver.solve(
            bulbasaur,
            IvEvidence(
                cp = 620,
                hp = 85,
                level = 20.0,
                appraisalAttack = 12,
                appraisalDefense = 14,
                appraisalStamina = 15
            )
        )

        assertEquals(IvSolveMode.EXACT, result.mode)
        assertEquals(1, result.candidateCount)
        assertEquals(IvCandidate(12, 14, 15, 20.0, 620, 85, 91), result.exactCandidate)
    }

    @Test
    fun cpOnlyReturnsRangeNotExact() {
        val result = IvSolver.solve(bulbasaur, IvEvidence(cp = 620))

        assertEquals(IvSolveMode.RANGE, result.mode)
        assertTrue(result.candidateCount > 1)
        assertNull(result.exactCandidate)
        assertTrue(result.ivPercentMin!! <= result.ivPercentMax!!)
    }

    @Test
    fun regularStardustNarrowsCandidateLevels() {
        val result = IvSolver.solve(bulbasaur, IvEvidence(cp = 620, stardust = 2500))

        assertTrue(result.candidateCount > 0)
        assertTrue(result.candidates.all { it.level in 19.0..20.5 })
        assertTrue(result.levelMin!! >= 19.0)
        assertTrue(result.levelMax!! <= 20.5)
    }

    @Test
    fun conflictingEvidenceReturnsInsufficient() {
        val result = IvSolver.solve(bulbasaur, IvEvidence(cp = 620, hp = 999, level = 20.0))

        assertEquals(IvSolveMode.INSUFFICIENT, result.mode)
        assertEquals(0, result.candidateCount)
        assertTrue(result.warnings.any { it.contains("No IV candidates") })
    }
}
