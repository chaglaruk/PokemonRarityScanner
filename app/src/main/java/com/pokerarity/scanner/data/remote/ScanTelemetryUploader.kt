package com.pokerarity.scanner.data.remote

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import com.pokerarity.scanner.data.local.db.TelemetryUploadEntity
import com.pokerarity.scanner.data.model.ScanFeedbackPayload
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ScanTelemetryUploader(
    context: Context
) {
    private val config = ScanTelemetryConfig.fromContext(context.applicationContext)

    data class UploadResult(
        val success: Boolean,
        val error: String? = null,
        val screenshotUrl: String? = null
    )

    data class ProbeResult(
        val url: String,
        val method: String,
        val statusCode: Int? = null,
        val error: String? = null
    )

    fun isEnabled(): Boolean = config.enabled

    fun hasConfiguredApiKey(): Boolean = config.apiKey.isNotBlank()

    fun upload(entity: TelemetryUploadEntity): UploadResult {
        if (!isEnabled()) return UploadResult(success = false, error = "Telemetry disabled")

        val logTag = "ScanTelemetryUploader"
        val endpoint = "${config.baseUrl}/scan-upload.php"
        val boundary = "----PokeRarityBoundary${UUID.randomUUID()}"
        val screenshotPath = entity.screenshotPath
        val screenshotFile = screenshotPath?.let(::File)
        val screenshotExists = screenshotFile?.exists() == true
        val expectScreenshotUrl = screenshotExists
        val screenshotSize = screenshotFile?.takeIf(File::exists)?.length() ?: 0L

        Log.d(
            logTag,
            "Preparing upload: uploadId=${entity.uploadId} screenshotPath=$screenshotPath exists=$screenshotExists size=$screenshotSize attempts=${entity.attempts} metadataOnly=${!expectScreenshotUrl}"
        )
        if (!screenshotPath.isNullOrBlank() && (screenshotFile == null || !screenshotFile.exists() || !screenshotFile.isFile)) {
            Log.w(logTag, "Upload blocked: uploadId=${entity.uploadId} screenshot file missing at $screenshotPath")
            return UploadResult(success = false, error = "Screenshot file missing")
        }
        if (screenshotExists && screenshotSize <= 0L) {
            Log.w(logTag, "Upload blocked: uploadId=${entity.uploadId} screenshot file empty at $screenshotPath")
            return UploadResult(success = false, error = "Screenshot file empty")
        }

        return runCatching {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json")
            }
            Log.d(logTag, "Creating multipart upload: uploadId=${entity.uploadId} endpoint=$endpoint")

            conn.outputStream.use { output ->
                val writer = OutputStreamWriter(output, Charsets.UTF_8)
                writeTextPart(writer, boundary, "payload_json", entity.payloadJson)
                writeTextPart(writer, boundary, "metadata_only", (!expectScreenshotUrl).toString())
                if (config.apiKey.isNotBlank()) {
                    writeTextPart(writer, boundary, "api_key", config.apiKey)
                }
                writer.flush()
                if (expectScreenshotUrl && screenshotFile != null) {
                    writeFilePart(output, writer, boundary, "screenshot", screenshotFile)
                }
                writer.append("--").append(boundary).append("--\r\n")
                writer.flush()
            }

            val code = conn.responseCode
            val body = runCatching {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
            }.getOrNull()
            val result = parseScanUploadResponse(code, body, expectScreenshotUrl = expectScreenshotUrl)
            Log.d(
                logTag,
                "Upload response: uploadId=${entity.uploadId} code=$code screenshotUrlPresent=${!result.screenshotUrl.isNullOrBlank()} body=$body"
            )
            if (!result.success) {
                Log.w(logTag, "Upload failed: uploadId=${entity.uploadId} code=$code error=${result.error}")
            }
            result
        }.getOrElse { error ->
            Log.w(logTag, "Upload exception: uploadId=${entity.uploadId} error=${error.message}", error)
            UploadResult(success = false, error = error.message ?: error.javaClass.simpleName)
        }
    }

    fun probeLegacyTelemetryEndpoint(): ProbeResult {
        return probeAbsoluteUrl("https://caglardinc.com/api/telemetry.php")
    }

    fun probePrimaryTelemetryEndpoint(): ProbeResult {
        return probeAbsoluteUrl("${config.baseUrl}/scan-upload.php")
    }

    private fun probeAbsoluteUrl(url: String): ProbeResult {
        return runCatching {
            val head = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
            }
            val headCode = runCatching { head.responseCode }.getOrNull()
            if (headCode != null && headCode !in listOf(HttpURLConnection.HTTP_BAD_METHOD, HttpURLConnection.HTTP_NOT_IMPLEMENTED)) {
                ProbeResult(url = url, method = "HEAD", statusCode = headCode)
            } else {
                val get = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = false
                    setRequestProperty("Accept", "application/json")
                }
                ProbeResult(url = url, method = "GET", statusCode = get.responseCode)
            }
        }.getOrElse { error ->
            ProbeResult(url = url, method = "HEAD", error = error.message ?: error.javaClass.simpleName)
        }
    }

    fun uploadFeedback(payload: ScanFeedbackPayload): UploadResult {
        if (!isEnabled()) return UploadResult(success = false, error = "Telemetry disabled")

        val endpoint = "${config.baseUrl}/scan-feedback.php"
        return runCatching {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }
            val form = buildMap {
                put("upload_id", payload.uploadId)
                put("category", payload.category)
                if (!payload.notes.isNullOrBlank()) put("notes", payload.notes)
                if (config.apiKey.isNotBlank()) put("api_key", config.apiKey)
            }.entries.joinToString("&") { (key, value) ->
                "${java.net.URLEncoder.encode(key, Charsets.UTF_8.name())}=" +
                    java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
            }
            conn.outputStream.use { output ->
                output.write(form.toByteArray(Charsets.UTF_8))
                output.flush()
            }
            val code = conn.responseCode
            val body = runCatching {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
            }.getOrNull()
            if (code in 200..299) {
                UploadResult(success = true)
            } else {
                Log.w("ScanTelemetryUploader", "Feedback upload failed: code=$code body=$body")
                UploadResult(success = false, error = "HTTP $code")
            }
        }.getOrElse { error ->
            UploadResult(success = false, error = error.message ?: error.javaClass.simpleName)
        }
    }

    private fun writeTextPart(
        writer: OutputStreamWriter,
        boundary: String,
        name: String,
        value: String
    ) {
        writer.append("--").append(boundary).append("\r\n")
        writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n")
        writer.append("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
        writer.append(value).append("\r\n")
    }

    private fun writeFilePart(
        output: java.io.OutputStream,
        writer: OutputStreamWriter,
        boundary: String,
        name: String,
        file: File
    ) {
        writer.append("--").append(boundary).append("\r\n")
        writer.append("Content-Disposition: form-data; name=\"").append(name)
            .append("\"; filename=\"").append(file.name).append("\"\r\n")
        writer.append("Content-Type: image/png\r\n\r\n")
        writer.flush()
        file.inputStream().use { input -> input.copyTo(output) }
        output.flush()
        writer.append("\r\n")
        writer.flush()
    }

    companion object {
        private const val MAX_RESPONSE_SIZE = 1024 * 100  // 100 KB max
        private const val MAX_URL_LENGTH = 2048

        internal fun shouldStageOfflineTelemetryForStatus(statusCode: Int?): Boolean {
            return statusCode == 404 || statusCode == 503
        }
        
        internal fun parseScanUploadResponse(
            code: Int,
            body: String?,
            expectScreenshotUrl: Boolean = true
        ): UploadResult {
            if (code !in 200..299) {
                return UploadResult(success = false, error = "HTTP $code")
            }
            if (body.isNullOrBlank()) {
                return UploadResult(success = false, error = "Empty response body")
            }
            
            // 🟠 SECURITY: Validate response size to prevent DOS
            if (body.length > MAX_RESPONSE_SIZE) {
                return UploadResult(success = false, error = "Response too large (${body.length} bytes)")
            }
            
            return runCatching {
                val json = JsonParser.parseString(body).asJsonObject
                val ok = json.get("ok")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                
                val screenshotUrl = json.get("screenshot_url")
                    ?.takeIf { !it.isJsonNull }
                    ?.asString
                    ?.trim()
                    ?.ifBlank { null }
                    ?.takeIf { isValidUrl(it) }  // 🟠 SECURITY: Validate URL format
                
                val error = json.get("error")
                    ?.takeIf { !it.isJsonNull }
                    ?.asString
                    ?.trim()
                    ?.ifBlank { null }
                
                when {
                    !ok -> UploadResult(success = false, error = error ?: "Server returned ok=false")
                    expectScreenshotUrl && screenshotUrl == null -> UploadResult(success = false, error = "Missing or invalid screenshot_url")
                    else -> UploadResult(success = true, screenshotUrl = screenshotUrl)
                }
            }.getOrElse { throwable ->
                UploadResult(success = false, error = "Invalid JSON response: ${throwable.message?.take(50)}")
            }
        }

        internal fun isRetryableFailure(error: String?): Boolean {
            if (error.isNullOrBlank()) return true
            val normalized = error.lowercase()
            if (normalized.startsWith("http 401") || normalized.startsWith("http 403") || normalized.startsWith("http 404") || normalized.startsWith("http 422")) {
                return false
            }
            return normalized !in setOf(
                "screenshot path missing",
                "screenshot file missing",
                "screenshot file empty",
                "missing or invalid screenshot_url",
                "telemetry disabled"
            )
        }
        
        /**
         * Validates URL format to prevent injection attacks.
         * 🟠 SECURITY: Only allows HTTPS URLs with normal schemes.
         */
        private fun isValidUrl(url: String): Boolean {
            return runCatching {
                if (url.length > MAX_URL_LENGTH) return false
                if (url.contains("javascript:")) return false   // No JS URLs
                if (url.contains("..")) return false            // No path traversal
                if (url.contains("\n") || url.contains("\r")) return false  // No newlines
                
                val uri = java.net.URI(url)
                val scheme = uri.scheme?.lowercase()
                
                // Only allow HTTPS (or HTTP for testing, but log warning)
                if (scheme !in listOf("https", "http")) return false
                if (scheme == "http") {
                    Log.w("ScanTelemetryUploader", "WARNING: Received HTTP URL instead of HTTPS")
                }
                
                uri.host?.isNotBlank() == true && uri.host?.contains(".") == true
            }.getOrDefault(false)
        }
    }
}
