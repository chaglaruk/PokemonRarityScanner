package com.pokerarity.scanner.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.pokerarity.scanner.Constants
import com.pokerarity.scanner.data.model.catalog.CollectionCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

class CatalogProvider(
    private val context: Context,
    gson: Gson = Gson()
) {
    private val parser = CatalogParser(gson)

    fun loadCatalog(): CollectionCatalog {
        readCachedCatalog()?.let { return it }
        return readBundledCatalog()
    }

    fun currentVersion(): String? =
        readCachedVersion()?.version ?: loadCatalog().version.version.takeIf { it.isNotBlank() }

    fun isOutdated(nowMs: Long = System.currentTimeMillis()): Boolean {
        val lastCheck = lastCheckFile().takeIf(File::exists)?.readText()?.trim()?.toLongOrNull() ?: 0L
        if (lastCheck <= 0L) return true
        return nowMs - lastCheck >= OUTDATED_DAYS * ONE_DAY_MS
    }

    suspend fun checkForUpdate(
        nowMs: Long = System.currentTimeMillis(),
        onCatalogUpdated: suspend (CollectionCatalog) -> Unit = {}
    ): UpdateResult = withContext(Dispatchers.IO) {
        val lastCheck = lastCheckFile().takeIf(File::exists)?.readText()?.trim()?.toLongOrNull() ?: 0L
        if (nowMs - lastCheck < ONE_DAY_MS) return@withContext UpdateResult.UP_TO_DATE
        forceUpdate(nowMs, onCatalogUpdated)
    }

    suspend fun forceUpdate(
        nowMs: Long = System.currentTimeMillis(),
        onCatalogUpdated: suspend (CollectionCatalog) -> Unit = {}
    ): UpdateResult = withContext(Dispatchers.IO) {
        runCatching {
            val remoteVersionPayload = fetchText(Constants.COLLECTION_CATALOG_VERSION_URL)
            val remoteVersion = parser.parseVersion(remoteVersionPayload) ?: return@withContext UpdateResult.FAILED
            val localVersion = readCachedVersion()?.version ?: readBundledCatalog().version.version
            if (remoteVersion.version == localVersion) {
                writeLastCheck(nowMs)
                return@withContext UpdateResult.UP_TO_DATE
            }
            val catalogPayload = fetchText(Constants.COLLECTION_CATALOG_URL)
            val parsed = parser.parse(catalogPayload)
            if (parsed.version.schemaVersion < 1 || parsed.version.version.isBlank()) {
                return@withContext UpdateResult.FAILED
            }
            writeAtomically(catalogFile(), catalogPayload)
            writeAtomically(versionFile(), remoteVersionPayload)
            writeLastCheck(nowMs)
            onCatalogUpdated(parsed)
            UpdateResult.UPDATED
        }.onFailure { error ->
            Log.w(TAG, "Collection catalog update skipped: ${error.message}")
        }.getOrDefault(UpdateResult.FAILED)
    }

    private fun readCachedCatalog(): CollectionCatalog? =
        catalogFile().takeIf(File::exists)?.let { file ->
            runCatching { parser.parse(file.readText()) }.getOrNull()
        }

    private fun readCachedVersion() =
        versionFile().takeIf(File::exists)?.let { file ->
            runCatching { parser.parseVersion(file.readText()) }.getOrNull()
        }

    private fun readBundledCatalog(): CollectionCatalog =
        runCatching {
            context.assets.open(BUNDLED_ASSET).bufferedReader().use { parser.parse(it.readText()) }
        }.onFailure { error ->
            Log.w(TAG, "Bundled collection catalog unavailable: ${error.message}")
        }.getOrDefault(CollectionCatalog.EMPTY)

    private fun fetchText(url: String): String {
        validateTrustedCatalogUrl(url)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            doInput = true
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "${context.packageName}/collection-catalog")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val payload = connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            }
            require(payload.length <= MAX_REMOTE_TEXT_LENGTH) { "Remote catalog payload too large" }
            payload
        } finally {
            connection.disconnect()
        }
    }

    private fun rootDir(): File = File(context.filesDir, CATALOG_DIR).apply { mkdirs() }
    private fun catalogFile(): File = File(rootDir(), CATALOG_FILE)
    private fun versionFile(): File = File(rootDir(), VERSION_FILE)
    private fun lastCheckFile(): File = File(rootDir(), LAST_CHECK_FILE)

    private fun writeLastCheck(nowMs: Long) {
        writeAtomically(lastCheckFile(), nowMs.toString())
    }

    private fun writeAtomically(target: File, payload: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(payload)
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    internal fun validateTrustedCatalogUrl(url: String) {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase(Locale.US)
        require(scheme == "https") { "Only HTTPS catalog URLs are allowed" }
        require(uri.host.equals(Constants.GITHUB_PAGES_HOST, ignoreCase = true)) {
            "Untrusted catalog host: ${uri.host}"
        }
        require(uri.path.startsWith(Constants.GITHUB_PAGES_CATALOG_PATH_PREFIX)) {
            "Untrusted catalog path: ${uri.path}"
        }
        require(uri.path.endsWith(".json")) {
            "Collection catalog URL must point to JSON"
        }
    }

    enum class UpdateResult {
        UPDATED,
        UP_TO_DATE,
        FAILED
    }

    companion object {
        private const val TAG = "CatalogProvider"
        private const val CATALOG_DIR = "collection_catalog"
        private const val CATALOG_FILE = "catalog.json"
        private const val VERSION_FILE = "version.json"
        private const val LAST_CHECK_FILE = "last_check.txt"
        private const val BUNDLED_ASSET = "data/collection_catalog.json"
        private const val ONE_DAY_MS = 86_400_000L
        private const val OUTDATED_DAYS = 7L
        private const val MAX_REMOTE_TEXT_LENGTH = 4 * 1024 * 1024
    }
}
