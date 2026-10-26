package com.pokerarity.scanner.data.local.db

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

internal data class PreparingMarker(
    val sourceFingerprint: String,
    val stageDatabaseName: String? = null,
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
    private const val PREPARATION_MARKER_PREFIX_V1 = "PRS_SQLCIPHER_HISTORY_PREPARING_V1"
    private const val PREPARATION_MARKER_PREFIX = "PRS_SQLCIPHER_HISTORY_PREPARING_V2"
    private const val ACTIVATION_MARKER_MIN_LINE_COUNT = 3
    private const val ACTIVATION_MARKER_MAX_LINE_COUNT = 4
    private const val PREPARATION_MARKER_MIN_LINE_COUNT = 2
    private const val PREPARATION_MARKER_MAX_LINE_COUNT = 4
    private const val PREPARATION_V2_MIN_LINE_COUNT = 3
    private const val PREPARATION_V2_MAX_LINE_COUNT = 5
    private const val PREPARATION_V2_UNVALIDATED_LINE_COUNT = 3
    private const val PREPARATION_V2_VALIDATED_LINE_COUNT = 4
    private const val PREPARATION_V2_COMPLETE_LINE_COUNT = 5
    private const val SOURCE_FINGERPRINT_LINE_INDEX = 1
    private const val ENCRYPTED_FINGERPRINT_LINE_INDEX = 2
    private const val PREPARATION_STAGE_FINGERPRINT_LINE_INDEX = 2
    private const val PREPARATION_STATE_LINE_INDEX = 3
    private const val PREPARATION_STAGE_NAME_LINE_INDEX = 2
    private const val PREPARATION_ENCRYPTED_FINGERPRINT_LINE_INDEX = 3
    private const val PREPARATION_V2_STATE_LINE_INDEX = 4
    private const val CLEANUP_STATE_LINE_INDEX = 3
    private const val SOURCE_CLEANUP_STARTED_MARKER = "SOURCE_CLEANUP_STARTED"
    private const val CONVERSION_COMPLETE_MARKER = "CONVERSION_COMPLETE"
    private const val PREPARATION_COMPLETE_MARKER = "PREPARATION_COMPLETE"
    private const val FINGERPRINT_PATTERN = "[0-9a-f]{64}"
    private const val STAGE_DATABASE_NAME_PATTERN = "pokerarity_db_cipher\\.stage-[0-9a-f]{32}"

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
        when (lines.firstOrNull()) {
            PREPARATION_MARKER_PREFIX_V1 -> return readLegacyPreparationMarker(lines)
            PREPARATION_MARKER_PREFIX -> Unit
            else -> return null
        }
        if (lines.size !in PREPARATION_V2_MIN_LINE_COUNT..PREPARATION_V2_MAX_LINE_COUNT) return null
        val source = lines[SOURCE_FINGERPRINT_LINE_INDEX]
        if (!source.matches(Regex(FINGERPRINT_PATTERN))) return null
        val stageDatabaseName = lines[PREPARATION_STAGE_NAME_LINE_INDEX]
        if (!stageDatabaseName.matches(Regex(STAGE_DATABASE_NAME_PATTERN))) return null
        val encryptedFingerprint = lines.getOrNull(PREPARATION_ENCRYPTED_FINGERPRINT_LINE_INDEX)
        val complete = lines.getOrNull(PREPARATION_V2_STATE_LINE_INDEX) == PREPARATION_COMPLETE_MARKER
        when {
            encryptedFingerprint == null -> if (lines.size != PREPARATION_V2_UNVALIDATED_LINE_COUNT) return null
            !encryptedFingerprint.matches(Regex(FINGERPRINT_PATTERN)) -> return null
            complete -> if (lines.size != PREPARATION_V2_COMPLETE_LINE_COUNT) return null
            lines.size != PREPARATION_V2_VALIDATED_LINE_COUNT -> return null
        }
        PreparingMarker(source, stageDatabaseName, encryptedFingerprint, conversionComplete = complete)
    }.getOrNull()

    private fun readLegacyPreparationMarker(lines: List<String>): PreparingMarker? = run {
        val source = lines.getOrNull(SOURCE_FINGERPRINT_LINE_INDEX)
        if (lines.size !in PREPARATION_MARKER_MIN_LINE_COUNT..PREPARATION_MARKER_MAX_LINE_COUNT ||
            source?.matches(Regex(FINGERPRINT_PATTERN)) != true
        ) {
            return@run null
        }
        parseLegacyPreparationState(lines)?.let { state ->
            PreparingMarker(
                sourceFingerprint = source,
                stageDatabaseName = null,
                encryptedFingerprint = state.encryptedFingerprint,
                conversionComplete = state.conversionComplete
            )
        }
    }

    private data class LegacyPreparationState(
        val encryptedFingerprint: String?,
        val conversionComplete: Boolean
    )

    private fun parseLegacyPreparationState(lines: List<String>): LegacyPreparationState? {
        val stage = lines.getOrNull(PREPARATION_STAGE_FINGERPRINT_LINE_INDEX)
        return when {
            stage == null && lines.size == PREPARATION_MARKER_MIN_LINE_COUNT ->
                LegacyPreparationState(encryptedFingerprint = null, conversionComplete = false)
            stage == PREPARATION_COMPLETE_MARKER &&
                lines.size == PREPARATION_MARKER_MIN_LINE_COUNT + 1 ->
                LegacyPreparationState(encryptedFingerprint = null, conversionComplete = true)
            stage != null && stage.matches(Regex(FINGERPRINT_PATTERN)) -> {
                val complete = lines.getOrNull(PREPARATION_STATE_LINE_INDEX) == PREPARATION_COMPLETE_MARKER
                if (lines.size == PREPARATION_MARKER_MAX_LINE_COUNT && !complete) {
                    null
                } else {
                    LegacyPreparationState(encryptedFingerprint = stage, conversionComplete = complete)
                }
            }
            else -> null
        }
    }

    internal fun writePreparationMarker(
        markerFile: File,
        marker: PreparingMarker,
        directorySync: (File) -> Unit
    ) {
        val stageDatabaseName = marker.stageDatabaseName ?: conversionFailed()
        if (!stageDatabaseName.matches(Regex(STAGE_DATABASE_NAME_PATTERN)) ||
            !marker.sourceFingerprint.matches(Regex(FINGERPRINT_PATTERN))
        ) conversionFailed()
        if (marker.encryptedFingerprint?.matches(Regex(FINGERPRINT_PATTERN)) == false) conversionFailed()
        if (marker.conversionComplete && marker.encryptedFingerprint == null) conversionFailed()
        val lines = buildList {
            add(PREPARATION_MARKER_PREFIX)
            add(marker.sourceFingerprint)
            add(stageDatabaseName)
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
