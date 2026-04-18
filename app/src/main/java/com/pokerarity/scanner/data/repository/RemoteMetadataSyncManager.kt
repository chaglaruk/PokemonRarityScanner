package com.pokerarity.scanner.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.pokerarity.scanner.Constants
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
import java.security.MessageDigest
import java.util.Locale

class RemoteMetadataSyncManager(
    private val context: Context,
    private val gson: Gson = Gson(),
) {
    suspend fun refresh() = withContext(Dispatchers.IO) {
        runCatching {
            val manifestJson = fetchTrustedText(Constants.GITHUB_METADATA_MANIFEST_URL)
            val manifest = parseManifest(manifestJson)
            if (manifest.files.isEmpty()) return@runCatching

            val root = RemoteMetadataStore.root(context).apply { mkdirs() }
            manifest.files.forEach { (name, fileSpec) ->
                validateTargetName(name)
                val payload = fetchTrustedText(fileSpec.url)
                fileSpec.sha256?.let { expectedHash ->
                    validateSha256(payload, expectedHash, name)
                }
                RemoteMetadataStore.writeTextAtomically(context, name, payload)
            }
            RemoteMetadataStore.writeVersionAtomically(context, manifest.version)
            Log.d("RemoteMetadataSync", "Metadata synced: version=${manifest.version} files=${manifest.files.keys}")
        }.onFailure { error ->
            Log.w("RemoteMetadataSync", "Remote metadata sync skipped: ${error.message}")
        }
    }

    internal fun fetchTrustedText(url: String): String {
        validateTrustedRepoUrl(url)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            doInput = true
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "${context.packageName}/remote-metadata")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val payload = connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            }
            if (payload.length > MAX_REMOTE_TEXT_LENGTH) {
                throw IllegalStateException("Remote payload too large: ${payload.length}")
            }
            payload
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MAX_REMOTE_TEXT_LENGTH = 20 * 1024 * 1024

        data class ManifestFile(
            val url: String,
            val sha256: String? = null
        )

        data class Manifest(
            val version: String,
            val files: Map<String, ManifestFile>
        )

        fun parseManifest(payload: String): Manifest {
            val root = JsonParser.parseString(payload).asJsonObject
            val version = root.get("version")?.asString ?: "unknown"
            val filesObject = root.getAsJsonObject("files")
            val files = mutableMapOf<String, ManifestFile>()
            filesObject?.entrySet()?.forEach { (key, value) ->
                when {
                    value.isJsonPrimitive -> {
                        files[key] = ManifestFile(url = value.asString)
                    }
                    value.isJsonObject -> {
                        val obj = value.asJsonObject
                        val url = obj.get("url")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                        if (url.isNotBlank()) {
                            val sha256 = obj.get("sha256")
                                ?.takeIf { !it.isJsonNull }
                                ?.asString
                                ?.trim()
                                ?.ifBlank { null }
                            files[key] = ManifestFile(url = url, sha256 = sha256)
                        }
                    }
                }
            }
            return Manifest(version = version, files = files)
        }

        internal fun validateTrustedRepoUrl(url: String) {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase(Locale.US)
            require(scheme == "https") { "Only HTTPS metadata URLs are allowed" }
            require(uri.host.equals(Constants.GITHUB_RAW_HOST, ignoreCase = true)) {
                "Untrusted metadata host: ${uri.host}"
            }
            require(uri.path.startsWith(Constants.GITHUB_REPO_PATH_PREFIX)) {
                "Untrusted metadata path: ${uri.path}"
            }
        }

        internal fun validateSha256(payload: String, expectedHash: String, name: String) {
            val actualHash = sha256(payload)
            require(actualHash.equals(expectedHash, ignoreCase = true)) {
                "Checksum mismatch for $name"
            }
        }

        internal fun sha256(payload: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        internal fun validateTargetName(name: String) {
            require(name.isNotBlank()) { "Metadata file name cannot be blank" }
            require(!name.contains("/") && !name.contains("\\") && !name.contains("..")) {
                "Invalid metadata file name: $name"
            }
        }

        fun currentVersion(context: Context): String? =
            RemoteMetadataStore.versionFile(context).takeIf(File::exists)?.readText()?.trim()?.ifBlank { null }
    }
}

object RemoteMetadataStore {
    fun root(context: Context): File = File(context.filesDir, "remote_metadata")

    fun versionFile(context: Context): File = File(root(context), "version.txt")

    fun fileFor(context: Context, name: String): File = File(root(context), name)

    fun writeVersionAtomically(context: Context, version: String) {
        writeTextAtomically(context, "version.txt", version)
    }

    fun writeTextAtomically(context: Context, name: String, payload: String) {
        val root = root(context).apply { mkdirs() }
        val target = File(root, name)
        val temp = File(root, "$name.tmp")
        temp.writeText(payload)
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    fun readTextIfExists(context: Context, name: String): String? =
        fileFor(context, name).takeIf(File::exists)?.readText()
}
