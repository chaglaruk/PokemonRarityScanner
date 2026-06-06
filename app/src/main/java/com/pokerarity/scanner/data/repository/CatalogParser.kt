package com.pokerarity.scanner.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.pokerarity.scanner.data.model.catalog.CatalogVersion
import com.pokerarity.scanner.data.model.catalog.CollectionCatalog
import com.pokerarity.scanner.data.model.catalog.CostumeRecord
import com.pokerarity.scanner.data.model.catalog.CurrentAvailabilityRecord
import com.pokerarity.scanner.data.model.catalog.EventRecord
import com.pokerarity.scanner.data.model.catalog.MetaDemandRecord
import com.pokerarity.scanner.data.model.catalog.RegionalRecord
import com.pokerarity.scanner.data.model.catalog.SpecialSpeciesRecord

class CatalogParser(private val gson: Gson = Gson()) {
    fun parse(payload: String): CollectionCatalog {
        val root = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull()
            ?: return CollectionCatalog.EMPTY
        return CollectionCatalog(
            version = parseVersion(root),
            costumes = parseArray<CostumeRecord>(root, "costumes"),
            events = parseArray<EventRecord>(root, "events"),
            regionals = parseArray<RegionalRecord>(root, "regionals"),
            specialSpecies = parseArray<SpecialSpeciesRecord>(root, "specialSpecies"),
            currentAvailability = parseArray<CurrentAvailabilityRecord>(root, "currentAvailability"),
            metaDemand = parseArray<MetaDemandRecord>(root, "metaDemand")
        )
    }

    fun parseVersion(payload: String): CatalogVersion? =
        runCatching { gson.fromJson(payload, CatalogVersion::class.java) }.getOrNull()

    private fun parseVersion(root: JsonObject): CatalogVersion {
        val versionObj = root.getAsJsonObject("version")
        return runCatching { gson.fromJson(versionObj, CatalogVersion::class.java) }.getOrNull()
            ?: CollectionCatalog.EMPTY.version
    }

    private inline fun <reified T> parseArray(root: JsonObject, key: String): List<T> {
        val array = root.getAsJsonArray(key) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<T>>(array, object : TypeToken<List<T>>() {}.type)
        }.getOrDefault(emptyList())
    }
}
