package com.pokerarity.scanner.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.local.db.EventPokemonEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventContextManager(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val gson: Gson = Gson(),
) {
    private val eventDao = database.eventDao()
    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun refreshLiveEvents() = withContext(Dispatchers.IO) {
        runCatching {
            val payload = fetchJson("https://pogoapi.net/api/v1/community_days.json")
            val entries = parseCommunityDaysPayload(payload, gson)
            if (entries.isNotEmpty()) {
                eventDao.upsertEventPokemonAll(entries)
            }
        }.onFailure { error ->
            Log.w("EventContextManager", "Live event refresh skipped: ${error.message}")
        }
    }

    private fun fetchJson(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            doInput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "${context.packageName}/event-context")
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code")
        }
        return connection.inputStream.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        }
    }

    private fun parseDate(value: String?): Date? = value?.let {
        runCatching { isoDate.parse(it) }.getOrNull()
    }

    companion object {
        internal fun parseCommunityDaysPayload(
            payload: String,
            gson: Gson = Gson()
        ): List<EventPokemonEntity> {
            val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val type = object : TypeToken<List<CommunityDayPayload>>() {}.type
            val parsed: List<CommunityDayPayload> = gson.fromJson(payload, type) ?: emptyList()
            return parsed.flatMap { event ->
                val start = event.startDate?.let { runCatching { isoDate.parse(it) }.getOrNull() }
                val end = event.endDate?.let { runCatching { isoDate.parse(it) }.getOrNull() }
                val eventName = buildCommunityDayName(event)
                event.boostedPokemon
                    .mapNotNull { species -> species?.trim()?.takeIf(String::isNotBlank) }
                    .distinct()
                    .map { species ->
                        EventPokemonEntity(
                            id = listOf(species, eventName, "pogoapi_community_days").joinToString("|"),
                            baseName = species,
                            eventName = eventName,
                            eventBonusScore = 140,
                            eventStart = start,
                            eventEnd = end,
                            source = "pogoapi_community_days",
                        )
                    }
            }
        }

        private fun buildCommunityDayName(event: CommunityDayPayload): String {
            val number = event.communityDayNumber?.takeIf { it > 0 }?.let { "#$it" }
            val leadSpecies = event.boostedPokemon
                .firstOrNull { !it.isNullOrBlank() }
                ?.trim()
                ?.takeIf(String::isNotBlank)
            return listOfNotNull("Community Day", number, leadSpecies).joinToString(" ")
        }
    }

    private data class CommunityDayPayload(
        @SerializedName("community_day_number")
        val communityDayNumber: Int? = null,
        @SerializedName("start_date")
        val startDate: String? = null,
        @SerializedName("end_date")
        val endDate: String? = null,
        @SerializedName("boosted_pokemon")
        val boostedPokemon: List<String?> = emptyList(),
    )
}
