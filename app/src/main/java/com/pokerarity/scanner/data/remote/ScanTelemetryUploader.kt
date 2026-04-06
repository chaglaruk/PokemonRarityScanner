package com.pokerarity.scanner.data.remote

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
    private val config: ScanTelemetryConfig = ScanTelemetryConfig.fromBuildConfig()
) {
    data class UploadResult(
        val success: Boolean,
        val error: String? = null,
        val screenshotUrl: String? = null
    )

    fun isEnabled(): Boolean = config.enabled

    fun upload(entity: TelemetryUploadEntity): UploadResult {
        if (!isEnabled()) return UploadResult(success = false, error = "Telemetry disabled")

        val logTag = "ScanTelemetryUploader"
        val endpoint = "${config.baseUrl}/scan-upload.php"
        val boundary = "----PokeRarityBoundary${UUID.randomUUID()}"
        val screenshotPath = entity.screenshotPath
        val screenshotFile = screenshotPath?.let(::File)
        val screenshotExists = screenshotFile?.exists() == true
        val screenshotSize = screenshotFile?.takeIf(File::exists)?.length() ?: 0L

        Log.d(
            logTag,
            "Preparing upload: uploadId=${entity.uploadId} screenshotPath=$screenshotPath exists=$screenshotExists size=$screenshotSize attempts=${entity.attempts}"
        )
        if (screenshotPath.isNullOrBlank()) {
            Log.w(logTag, "Upload blocked: uploadId=${entity.uploadId} missing screenshot path")
            return UploadResult(success = false, error = "Screenshot path missing")
        }
        if (screenshotFile == null || !screenshotFile.exists() || !screenshotFile.isFile) {
            Log.w(logTag, "Upload blocked: uploadId=${entity.uploadId} screenshot file missing at $screenshotPath")
            return UploadResult(success = false, error = "Screenshot file missing")
        }
        if (screenshotSize <= 0L) {
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
                if (config.apiKey.isNotBlank()) {
                    writeTextPart(writer, boundary, "api_key", config.apiKey)
                }
                writer.flush()
                writeFilePart(output, writer, boundary, "screenshot", screenshotFile)
                writer.append("--").append(boundary).append("--\r\n")
                writer.flush()
            }

            val code = conn.responseCode
            val body = runCatching {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
            }.getOrNull()
            val result = parseScanUploadResponse(code, body)
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
        internal fun parseScanUploadResponse(code: Int, body: String?): UploadResult {
            if (code !in 200..299) {
                return UploadResult(success = false, error = "HTTP $code")
            }
            if (body.isNullOrBlank()) {
                return UploadResult(success = false, error = "Empty response body")
            }
            return runCatching {
                val json = JsonParser.parseString(body).asJsonObject
                val ok = json.get("ok")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                val screenshotUrl = json.get("screenshot_url")
                    ?.takeIf { !it.isJsonNull }
                    ?.asString
                    ?.trim()
                    ?.ifBlank { null }
                val error = json.get("error")
                    ?.takeIf { !it.isJsonNull }
                    ?.asString
                    ?.trim()
                    ?.ifBlank { null }
                when {
                    !ok -> UploadResult(success = false, error = error ?: "Server returned ok=false")
                    screenshotUrl == null -> UploadResult(success = false, error = "Missing screenshot_url")
                    else -> UploadResult(success = true, screenshotUrl = screenshotUrl)
                }
            }.getOrElse {
                UploadResult(success = false, error = "Invalid JSON response")
            }
        }
    }
}
