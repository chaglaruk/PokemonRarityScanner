package com.pokerarity.scanner.data.repository

import kotlin.math.floor
import kotlin.math.sqrt

object PvPStatEngine {

    data class LeagueRank(
        val league: String,
        val rank: Int,
        val percentile: Float,
        val statProduct: Double,
        val level: Double,
        val ivAtk: Int,
        val ivDef: Int,
        val ivSta: Int
    ) {
        val label: String
            get() = "${league} %${"%.1f".format(percentile)} Rank $rank"
    }

    private val cache = mutableMapOf<Pair<String, Int>, List<LeagueRank>>()

    fun bestRankForSpecies(
        species: String,
        stats: IvCostSolver.BaseStats,
        cpCap: Int
    ): List<LeagueRank> {
        val key = species to cpCap
        return cache.getOrPut(key) {
            val ranks = mutableListOf<LeagueRank>()
            for (ivAtk in 0..15) {
                for (ivDef in 0..15) {
                    for (ivSta in 0..15) {
                        var bestProduct = 0.0
                        var bestLevel = 1.0
                        for (level in IvCostSolver.publicHalfLevels()) {
                            val cp = IvCostSolver.publicCalculateCp(stats, ivAtk, ivDef, ivSta, level)
                            if (cp > cpCap) break
                            val cpm = IvCostSolver.publicCpm(level) ?: continue
                            val atk = (stats.atk + ivAtk) * cpm
                            val def = (stats.def + ivDef) * cpm
                            val hp = floor((stats.sta + ivSta) * cpm).toInt()
                            val product = atk * def * hp
                            if (product > bestProduct) {
                                bestProduct = product
                                bestLevel = level
                            }
                        }
                        if (bestProduct > 0.0) {
                            ranks += LeagueRank(
                                league = if (cpCap == 1500) "GL" else "UL",
                                rank = 0,
                                percentile = 0f,
                                statProduct = bestProduct,
                                level = bestLevel,
                                ivAtk = ivAtk,
                                ivDef = ivDef,
                                ivSta = ivSta
                            )
                        }
                    }
                }
            }
            val sorted = ranks.sortedByDescending { it.statProduct }
            val best = sorted.firstOrNull()?.statProduct ?: 1.0
            sorted.mapIndexed { index, rank ->
                rank.copy(
                    rank = index + 1,
                    percentile = ((rank.statProduct / best) * 100.0).toFloat()
                )
            }
        }
    }

    fun bestObservedCandidate(
        species: String,
        stats: IvCostSolver.BaseStats,
        candidates: List<IvCostSolver.Candidate>
    ): LeagueRank? {
        if (candidates.isEmpty()) return null
        val all = bestRankForSpecies(species, stats, 1500) + bestRankForSpecies(species, stats, 2500)
        return candidates.asSequence()
            .flatMap { candidate ->
                all.asSequence().filter {
                    it.level == candidate.level &&
                        it.ivAtk == candidate.ivAtk &&
                        it.ivDef == candidate.ivDef &&
                        it.ivSta == candidate.ivSta
                }
            }
            .minByOrNull { it.rank }
    }
}
