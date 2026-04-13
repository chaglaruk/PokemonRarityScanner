package com.pokerarity.scanner.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pokerarity.scanner.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class RemoteMetadataSyncManager(
    private val context: Context,
    private val gson: Gson = Gson(),
) {
    suspend fun refresh() = withContext(Dispatchers.IO) {
        runCatching {
            val manifestJson = fetchText(Constants.GITHUB_METADATA_MANIFEST_URL)
            val manifest = parseManifest(manifestJson)
            if (manifest.files.isEmpty()) return@runCatching

            val root = RemoteMetadataStore.root(context).apply { mkdirs() }
            manifest.files.forEach { (name, url) ->
                val target = File(root, name)
                fetchText(url).let { target.writeText(it) }
            }
            RemoteMetadataStore.versionFile(context).writeText(manifest.version)
            Log.d("RemoteMetadataSync", "Metadata synced: version=${manifest.version} files=${manifest.files.keys}")
        }.onFailure { error ->
            Log.w("RemoteMetadataSync", "Remote metadata sync skipped: ${error.message}")
        }
    }

    private fun fetchText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            doInput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "${context.packageName}/remote-metadata")
        }
        val code = connection.responseCode
        if (code !in 200..299) throw IllegalStateException("HTTP $code")
        return connection.inputStream.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        }
    }

    companion object {
        data class Manifest(
            val version: String,
            val files: Map<String, String>
        )

        fun parseManifest(payload: String): Manifest {
            val root = JsonParser.parseString(payload).asJsonObject
            val version = root.get("version")?.asString ?: "unknown"
            val filesObject = root.getAsJsonObject("files")
            val files = buildMap {
                filesObject?.entrySet()?.forEach { (key, value) ->
                    if (value.isJsonPrimitive) put(key, value.asString)
                }
            }
            return Manifest(version = version, files = files)
        }

        fun currentVersion(context: Context): String? =
            RemoteMetadataStore.versionFile(context).takeIf(File::exists)?.readText()?.trim()?.ifBlank { null }
    }
}

object RemoteMetadataStore {
    fun root(context: Context): File = File(context.filesDir, "remote_metadata")

    fun versionFile(context: Context): File = File(root(context), "version.txt")

    fun fileFor(context: Context, name: String): File = File(root(context), name)

    fun readTextIfExists(context: Context, name: String): String? =
        fileFor(context, name).takeIf(File::exists)?.readText()
}
