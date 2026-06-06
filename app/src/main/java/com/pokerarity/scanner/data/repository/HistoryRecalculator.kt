package com.pokerarity.scanner.data.repository

import com.google.gson.Gson
import com.pokerarity.scanner.data.local.db.ScanHistoryDao
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.EditedScanDetails
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.model.catalog.CollectionCatalog
import java.util.Date

class HistoryRecalculator(
    private val dao: ScanHistoryDao,
    private val engine: CollectionScoreEngine = CollectionScoreEngine(),
    private val gson: Gson = Gson(),
    private val currentDateProvider: () -> Date = { Date() }
) {
    suspend fun recalculateAll(catalog: CollectionCatalog, batchSize: Int = 50): Int {
        var updated = 0
        var offset = 0
        while (true) {
            val batch = dao.getBatch(batchSize, offset)
            if (batch.isEmpty()) break
            batch.forEach { entity ->
                val edited = parseEditedDetails(entity)
                val result = engine.calculate(
                    pokemonData = entity.toPokemonData(edited),
                    features = entity.toVisualFeatures(),
                    catalog = catalog,
                    editedDetails = edited,
                    currentDate = currentDateProvider()
                )
                dao.updateCollectionScore(
                    id = entity.id,
                    score = result.totalScore,
                    tier = result.tier.name,
                    catalogVersion = catalog.version.version,
                    axisJson = gson.toJson(result.axes)
                )
                updated++
            }
            offset += batchSize
        }
        return updated
    }

    private fun parseEditedDetails(entity: ScanHistoryEntity): EditedScanDetails? =
        entity.editedDetailsJson?.takeIf { it.isNotBlank() }?.let { payload ->
            runCatching { gson.fromJson(payload, EditedScanDetails::class.java) }.getOrNull()
        }

    private fun ScanHistoryEntity.toPokemonData(edited: EditedScanDetails?): PokemonData =
        PokemonData(
            cp = cp,
            hp = hp,
            maxHp = hp,
            name = edited?.species ?: pokemonName,
            realName = edited?.species ?: pokemonName,
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = edited?.caughtDate ?: caughtDate,
            rawOcrText = rawOcrText
        )

    private fun ScanHistoryEntity.toVisualFeatures(): VisualFeatures =
        VisualFeatures(
            isShiny = isShiny,
            isShadow = isShadow,
            isPurified = isPurified,
            isLucky = isLucky,
            hasCostume = hasCostume,
            hasSpecialForm = hasSpecialForm,
            hasLocationCard = hasLocationCard,
            confidence = 1.0f
        )
}
