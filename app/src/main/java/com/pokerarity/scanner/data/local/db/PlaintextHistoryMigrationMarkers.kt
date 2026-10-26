package com.pokerarity.scanner.data.local.db

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

internal data class PreparingMarker(
    val sourceFingerprint: String,
    val encryptedFingerprint: String? = null,
    val conversionComplete: Boolean = false
)

internal data class ActivationMarker(
    val sourceFingerprint: String,
    val encryptedFingerprint: String,
    val sourceCleanupStarted: Boolean = false,
    val conversionComplete: Boolean = false
)

/** Atomic durable markers for staged plaintext-to-SQLCipher History conversion. */
@Suppress("TooManyFunctions") // Marker parsing, atomic writes, and directory sync form one recovery protocol.
internal object PlaintextHistoryMigrationMarkers {
    private const val ACTIVATION_MARKER_PREFIX = "PRS_SQLCIPHER_HISTORY_V2"
    private const val PREPARATION_MARKER_PREFIX = "PRS_SQLCIPHER_HISTORY_PREPARING_V1"
    private const val ACTIVATION_MARKER_MIN_LINE_COUNT = 3
    private const val ACTIVATION_MARKER_MAX_LINE_COUNT = 4
    private const val PREPARATION_MARKER_MIN_LINE_COUNT = 2
    private const val PREPARATION_MARKER_MAX_LINE_COUNT = 4
    private const val SOURCE_FINGERPRINT_LINE_INDEX = 1
    private const val ENCRYPTED_FINGERPRINT_LINE_INDEX = 2
    private const val PREPARATION_STAGE_FINGERPRINT_LINE_INDEX = 2
    private const val PREPARATION_STATE_LINE_INDEX = 3
    private const val CLEANUP_STATE_LINE_INDEX = 3
    private const val SOURCE_CLEANUP_STARTED_MARKER = "SOURCE_CLEANUP_STARTED"
    private const val CONVERSION_COMPLETE_MARKER = "CONVERSION_COMPLETE"
    private const val PREPARATION_COMPLETE_MARKER = "PREPARATION_COMPLETE"
    private const val FINGERPRINT_PATTERN = "[0-9a-f]{64}"

    private enum class MarkerState {
        STAGED,
        CLEANUP_STARTED,
        COMPLETE
    }

    internal fun readActivationMarker(markerFile: File): ActivationMarker? = runCatching {
        val lines = AtomicFile(markerFile).readFully().toString(Charsets.US_ASCII).lines()
        if (lines.size !in ACTIVATION_MARKER_MIN_LINE_COUNT..ACTIVATION_MARKER_MAX_LINE_COUNT ||
            lines[0] != ACTIVATION_MARKER_PREFIX
        ) return null
        val source = lines[SOURCE_FINGERPRINT_LINE_INDEX]
        val encrypted = lines[ENCRYPTED_FINGERPRINT_LINE_INDEX]
        if (!source.matches(Regex(FINGERPRINT_PATTERN)) || !encrypted.matches(Regex(FINGERPRINT_PATTERN))) return null
        val state = when (lines.getOrNull(CLEANUP_STATE_LINE_INDEX)) {
            null -> MarkerState.STAGED
            SOURCE_CLEANUP_STARTED_MARKER -> MarkerState.CLEANUP_STARTED
            CONVERSION_COMPLETE_MARKER -> MarkerState.COMPLETE
            else -> return null
        }
        ActivationMarker(
            sourceFingerprint = source,
            encryptedFingerprint = encrypted,
            sourceCleanupStarted = state != MarkerState.STAGED,
            conversionComplete = state == MarkerState.COMPLETE
        )
    }.getOrNull()

    internal fun readPreparationMarker(markerFile: File): PreparingMarker? = runCatching {
        val lines = AtomicFile(markerFile).readFully().toString(Charsets.US_ASCII).lines()
        if (lines.size !in PREPARATION_MARKER_MIN_LINE_COUNT..PREPARATION_MARKER_MAX_LINE_COUNT ||
            lines[0] != PREPARATION_MARKER_PREFIX
        ) return null
        val source = lines[SOURCE_FINGERPRINT_LINE_INDEX]
        if (!source.matches(Regex(FINGERPRINT_PATTERN))) return null
        val stage = lines.getOrNull(PREPARATION_STAGE_FINGERPRINT_LINE_INDEX)
        val encryptedFingerprint: String?
        val complete: Boolean
        when {
            stage == null -> {
                encryptedFingerprint = null
                complete = false
            }
            stage == PREPARATION_COMPLETE_MARKER && lines.size == PREPARATION_MARKER_MIN_LINE_COUNT + 1 -> {
                encryptedFingerprint = null
                complete = true
            }
            stage.matches(Regex(FINGERPRINT_PATTERN)) -> {
                encryptedFingerprint = stage
                complete = lines.getOrNull(PREPARATION_STATE_LINE_INDEX) == PREPARATION_COMPLETE_MARKER
                if (lines.size == PREPARATION_MARKER_MAX_LINE_COUNT && !complete) return null
            }
            else -> return null
        }
        PreparingMarker(source, encryptedFingerprint, conversionComplete = complete)
    }.getOrNull()

    internal fun writePreparationMarker(
        markerFile: File,
        marker: PreparingMarker,
        directorySync: (File) -> Unit
    ) {
        val lines = buildList {
            add(PREPARATION_MARKER_PREFIX)
            add(marker.sourceFingerprint)
            marker.encryptedFingerprint?.let(::add)
            if (marker.conversionComplete) add(PREPARATION_COMPLETE_MARKER)
        }
        writeAtomic(markerFile, lines.joinToString("\n").toByteArray(Charsets.US_ASCII))
        if (readPreparationMarker(markerFile) != marker) conversionFailed()
        directorySync(markerFile)
        if (readPreparationMarker(markerFile) != marker) conversionFailed()
    }

    internal fun writeActivationMarker(
        markerFile: File,
        marker: ActivationMarker,
        directorySync: (File) -> Unit = ::syncParentDirectory
    ) {
        writeAtomic(markerFile, activationMarkerContents(marker))
        if (readActivationMarker(markerFile) != marker) conversionFailed()
        directorySync(markerFile)
        if (readActivationMarker(markerFile) != marker) conversionFailed()
    }

    internal fun activationMarkerContents(marker: ActivationMarker): ByteArray {
        val lines = buildList {
            add(ACTIVATION_MARKER_PREFIX)
            add(marker.sourceFingerprint)
            add(marker.encryptedFingerprint)
            when {
                marker.conversionComplete -> add(CONVERSION_COMPLETE_MARKER)
                marker.sourceCleanupStarted -> add(SOURCE_CLEANUP_STARTED_MARKER)
            }
        }
        return lines.joinToString("\n").toByteArray(Charsets.US_ASCII)
    }

    internal fun hasMarkerArtifacts(markerFile: File): Boolean =
        markerFile.exists() || File(markerFile.path + ".bak").exists() || File(markerFile.path + ".new").exists()

    internal fun deletePreparationMarkerBestEffort(markerFile: File) {
        runCatching {
            AtomicFile(markerFile).delete()
            if (!hasMarkerArtifacts(markerFile)) syncParentDirectory(markerFile)
        }
    }

    internal fun deleteOwnedMarkerArtifacts(markerFile: File) {
        AtomicFile(markerFile).delete()
        if (hasMarkerArtifacts(markerFile)) conversionFailed()
        syncParentDirectory(markerFile)
        if (hasMarkerArtifacts(markerFile)) conversionFailed()
    }

    internal fun syncParentDirectory(file: File) {
        val parent = file.parentFile ?: conversionFailed()
        val parentSynced = runCatching {
            FileChannel.open(parent.toPath(), StandardOpenOption.READ).use { channel -> channel.force(true) }
        }.isSuccess
        if (!parentSynced) conversionFailed()
    }

    private fun writeAtomic(markerFile: File, contents: ByteArray) {
        val atomicFile = AtomicFile(markerFile)
        val stream = atomicFile.startWrite()
        try {
            stream.write(contents)
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (failure: IOException) {
            atomicFile.failWrite(stream)
            throw failure
        }
    }

    private fun conversionFailed(): Nothing = throw PlaintextHistoryConversionFailedException()
}
