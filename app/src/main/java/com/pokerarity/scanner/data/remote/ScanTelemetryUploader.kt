package com.pokerarity.scanner.data.remote

import android.util.Log
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
        val error: String? = null
    )

    fun isEnabled(): Boolean = config.enabled

    fun upload(entity: TelemetryUploadEntity): UploadResult {
        if (!isEnabled()) return UploadResult(success = false, error = "Telemetry disabled")

        val endpoint = "${config.baseUrl}/scan-upload.php"
        val boundary = "----PokeRarityBoundary${UUID.randomUUID()}"
        val screenshotFile = entity.screenshotPath?.let(::File)?.takeIf { it.exists() }

        return runCatching {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json")
            }

            conn.outputStream.use { output ->
                val writer = OutputStreamWriter(output, Charsets.UTF_8)
                writeTextPart(writer, boundary, "payload_json", entity.payloadJson)
                if (config.apiKey.isNotBlank()) {
                    writeTextPart(writer, boundary, "api_key", config.apiKey)
                }
                writer.flush()
                if (screenshotFile != null) {
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
            if (code in 200..299) {
                UploadResult(success = true)
            } else {
                Log.w("ScanTelemetryUploader", "Upload failed: code=$code body=$body")
                UploadResult(success = false, error = "HTTP $code")
            }
        }.getOrElse { error ->
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
}
