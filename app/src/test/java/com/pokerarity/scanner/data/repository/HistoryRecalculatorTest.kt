package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.local.db.ScanHistoryDao
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.EditedScanDetails
import com.pokerarity.scanner.data.model.catalog.CatalogVersion
import com.pokerarity.scanner.data.model.catalog.CollectionCatalog
import com.pokerarity.scanner.data.model.catalog.SpecialSpeciesRecord
import com.pokerarity.scanner.data.model.catalog.VerificationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRecalculatorTest {

    @Test
    fun recalculatesRowsAndMirrorsLegacyFields() = runBlocking {
        val dao = FakeScanHistoryDao(
            listOf(
                scan(
                    id = 1,
                    pokemonName = "Mewtwo",
                    caughtDate = date("2017-07-22"),
                    isShadow = true
                ),
                scan(
                    id = 2,
                    pokemonName = "Unknown",
                    editedDetailsJson = com.google.gson.Gson().toJson(
                        EditedScanDetails(species = "Mewtwo", isShadow = true)
                    )
                )
            )
        )
        val recalculator = HistoryRecalculator(
            dao = dao,
            engine = CollectionScoreEngine(
                speciesRarityLookup = { if (it == "Mewtwo") 25 else 0 },
                currentDateProvider = { date("2026-06-05") }
            ),
            currentDateProvider = { date("2026-06-05") }
        )

        val updated = recalculator.recalculateAll(catalog(), batchSize = 1)

        assertEquals(2, updated)
        assertEquals(2, dao.updates.size)
        assertTrue(dao.updates.all { it.score > 0 })
        assertTrue(dao.updates.all { it.tier.isNotBlank() })
        assertTrue(dao.updates.all { it.catalogVersion == "test-catalog" })
        assertTrue(dao.updates.all { it.axisJson.contains("BASE_SPECIES") })
    }

    private fun catalog() = CollectionCatalog(
        version = CatalogVersion("test-catalog", "2026-06-05T00:00:00Z", 1),
        costumes = emptyList(),
        events = emptyList(),
        regionals = emptyList(),
        specialSpecies = listOf(
            SpecialSpeciesRecord(
                species = "Mewtwo",
                category = "legendary",
                baseSpeciesScore = 18,
                isTradable = true,
                sourceLinks = listOf("https://example.com/source"),
                verificationStatus = VerificationStatus.VERIFIED_COMMUNITY
            )
        ),
        currentAvailability = emptyList(),
        metaDemand = emptyList()
    )

    private fun scan(
        id: Long,
        pokemonName: String?,
        caughtDate: Date? = null,
        isShadow: Boolean = false,
        editedDetailsJson: String? = null
    ) = ScanHistoryEntity(
        id = id,
        pokemonName = pokemonName,
        cp = 1500,
        hp = 120,
        caughtDate = caughtDate,
        rawOcrText = "",
        isShadow = isShadow,
        editedDetailsJson = editedDetailsJson,
        isEdited = editedDetailsJson != null
    )

    private fun date(value: String): Date =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.parse(value)!!

    private class FakeScanHistoryDao(
        private val rows: List<ScanHistoryEntity>
    ) : ScanHistoryDao {
        val updates = mutableListOf<Update>()

        override suspend fun insert(scan: ScanHistoryEntity): Long = scan.id
        override fun getAll(): Flow<List<ScanHistoryEntity>> = flowOf(rows)
        override fun getRecent(limit: Int): Flow<List<ScanHistoryEntity>> = flowOf(rows.take(limit))
        override suspend fun getById(id: Long): ScanHistoryEntity? = rows.firstOrNull { it.id == id }
        override suspend fun getByPokemonName(name: String): List<ScanHistoryEntity> =
            rows.filter { it.pokemonName == name }

        override suspend fun deleteById(id: Long) = Unit
        override suspend fun count(): Int = rows.size
        override fun getByMinRarity(minScore: Int): Flow<List<ScanHistoryEntity>> =
            flowOf(rows.filter { it.rarityScore >= minScore })

        override suspend fun getBatch(batchSize: Int, offset: Int): List<ScanHistoryEntity> =
            rows.drop(offset).take(batchSize)

        override suspend fun updateCollectionScore(
            id: Long,
            score: Int,
            tier: String,
            catalogVersion: String,
            axisJson: String
        ) {
            updates += Update(id, score, tier, catalogVersion, axisJson)
        }

        override suspend fun updateEditedCollectionScore(
            id: Long,
            pokemonName: String?,
            caughtDate: Date?,
            isShiny: Boolean,
            isShadow: Boolean,
            isLucky: Boolean,
            hasCostume: Boolean,
            isPurified: Boolean,
            hasLocationCard: Boolean,
            hasSpecialForm: Boolean,
            score: Int,
            tier: String,
            catalogVersion: String?,
            editedDetailsJson: String,
            axisJson: String
        ) = Unit

        override suspend fun deleteOlderThan(beforeEpochMs: Long): Int = 0
    }

    private data class Update(
        val id: Long,
        val score: Int,
        val tier: String,
        val catalogVersion: String,
        val axisJson: String
    )
}
