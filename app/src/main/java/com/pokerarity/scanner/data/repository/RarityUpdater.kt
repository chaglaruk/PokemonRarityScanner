package com.pokerarity.scanner.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.pokerarity.scanner.Constants
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.local.db.EventPokemonEntity
import com.pokerarity.scanner.data.model.EventPokemonSeed
import com.pokerarity.scanner.data.model.EventPokemonSeedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RarityUpdater private constructor(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val gson: Gson = Gson(),
) {
    private val eventDao = database.eventDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun syncAsync() {
        scope.launch {
            seedLocalEvents()
            refreshFromRemote()
        }
    }

    suspend fun seedLocalEvents() {
        val seeded = loadAssetFile("data/events_seed.json")
        if (seeded.events.isNotEmpty()) {
            eventDao.upsertEventPokemonAll(seeded.events.map { it.toEntity("asset_seed", isoDate) })
        }
    }

    suspend fun refreshFromRemote() {
        runCatching {
            val url = URL(Constants.GITHUB_UPDATE_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 20000
                doInput = true
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            val payload = connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            }
            val updateFile = gson.fromJson(payload, EventPokemonSeedFile::class.java)
            if (updateFile.events.isNotEmpty()) {
                eventDao.upsertEventPokemonAll(updateFile.events.map { it.toEntity("github_updates", isoDate) })
            }
        }.onFailure { error ->
            Log.w("RarityUpdater", "Remote update skipped: ${error.message}")
        }
    }

    private fun loadAssetFile(path: String): EventPokemonSeedFile {
        return runCatching {
            context.assets.open(path).bufferedReader().use { reader ->
                gson.fromJson(reader, EventPokemonSeedFile::class.java)
            }
        }.getOrElse {
            EventPokemonSeedFile()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: RarityUpdater? = null

        fun getInstance(context: Context): RarityUpdater {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RarityUpdater(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

private fun EventPokemonSeed.toEntity(sourceName: String, isoDate: SimpleDateFormat): EventPokemonEntity {
    fun parseDate(value: String?): Date? = value?.let {
        runCatching { isoDate.parse(it) }.getOrNull()
    }

    val normalizedSource = source?.takeIf { it.isNotBlank() } ?: sourceName
    val normalizedVariantToken = variantToken?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedSpriteKey = spriteKey?.trim()?.takeIf { it.isNotEmpty() }
    val keyParts = listOf(
        baseName.trim(),
        eventName.trim(),
        normalizedSpriteKey.orEmpty(),
        normalizedVariantToken.orEmpty(),
    )
    val entityId = keyParts.joinToString("|")

    return EventPokemonEntity(
        id = entityId,
        baseName = baseName.trim(),
        eventName = eventName.trim(),
        eventBonusScore = eventBonusScore.coerceIn(50, 500),
        spriteKey = normalizedSpriteKey,
        variantToken = normalizedVariantToken,
        eventStart = parseDate(eventStart),
        eventEnd = parseDate(eventEnd),
        source = normalizedSource,
        updatedAt = Date(),
    )
}
