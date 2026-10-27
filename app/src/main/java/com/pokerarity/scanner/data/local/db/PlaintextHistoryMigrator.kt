package com.pokerarity.scanner.data.local.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pokerarity.scanner.BuildConfig
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherSQLiteDatabase

/** Converts a legacy SQLite History file without replacing its source until the cipher copy opens. */
@Suppress("TooManyFunctions") // Private helpers keep the staged migration transaction and recovery paths together.
internal object PlaintextHistoryMigrator {
    fun prepare(context: Context, passphrase: ByteArray): String =
        prepareInternal(context, passphrase, allowNewConversion = false, conversionHooks = ConversionHooks())

    /** Exercise the migration algorithm only from isolated instrumentation fixtures. */
    internal fun prepareForIsolatedValidation(
        context: Context,
        passphrase: ByteArray,
        conversionHooks: ConversionHooks = ConversionHooks()
    ): String {
        check(BuildConfig.DEBUG) { "Plaintext History conversion validation is debug-only" }
        return HistoryDatabaseInitializationLock.withExclusiveLock(context) {
            prepareInternal(context, passphrase, allowNewConversion = true, conversionHooks = conversionHooks)
        }
    }

    private fun prepareInternal(
        context: Context,
        passphrase: ByteArray,
        allowNewConversion: Boolean,
        conversionHooks: ConversionHooks
    ): String {
        val sourceFile = context.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val encryptedFile = context.getDatabasePath(DatabasePassphraseStore.CIPHER_DB_NAME)
        val request = PlaintextHistoryMigrationRequest(
            context = context,
            sourceFile = sourceFile,
            encryptedFile = encryptedFile,
            markerFile = markerFile(context),
            preparationMarkerFile = preparationMarkerFile(context),
            passphrase = passphrase,
            sourceIsPlaintext = DatabasePassphraseStore.isPlaintextSqliteDatabase(sourceFile),
            encryptedArtifactsExist = DatabasePassphraseStore.databaseArtifactsExist(encryptedFile),
            allowNewConversion = allowNewConversion,
            conversionHooks = conversionHooks
        )
        return when {
            PlaintextHistoryMigrationMarkers.hasMarkerArtifacts(request.markerFile) -> {
                val activationMarker = PlaintextHistoryMigrationMarkers.readActivationMarker(request.markerFile)
                val selectedDatabase = try {
                    resumeMarkedConversion(request)
                } catch (failure: ExistingPlaintextHistoryMigrationRequiredException) {
                    val preparationMarker = PlaintextHistoryMigrationMarkers.readPreparationMarker(
                        request.preparationMarkerFile
                    )
                    if (preparationMarker == null ||
                        preparationMarker.conversionComplete ||
                        activationMarker?.conversionComplete == true
                    ) {
                        throw failure
                    }
                    resumePreparingConversion(request, discardActivationMarkerArtifacts = true)
                }
                val activationComplete = PlaintextHistoryMigrationMarkers
                    .readActivationMarker(request.markerFile)?.conversionComplete == true
                if (activationComplete) {
                    PlaintextHistoryMigrationMarkers.deletePreparationMarkerBestEffort(request.preparationMarkerFile)
                }
                selectedDatabase
            }
            PlaintextHistoryMigrationMarkers.hasMarkerArtifacts(request.preparationMarkerFile) -> {
                if (PlaintextHistoryMigrationMarkers.readPreparationMarker(request.preparationMarkerFile) == null) {
                    recoverUnreadablePreparationMarker(request)
                } else {
                    resumePreparingConversion(request)
                }
            }
            request.sourceIsPlaintext -> convertPlaintextSource(request)
            else -> openExistingDatabase(request)
        }
    }

    private fun resumeMarkedConversion(request: PlaintextHistoryMigrationRequest): String {
        val marker = PlaintextHistoryMigrationMarkers.readActivationMarker(request.markerFile)
            ?: migrationRequired()
        if (!isMarkedCipherDatabaseValid(request, marker)) migrationRequired()
        validateMarkedSourceArtifacts(request, marker)
        return DatabasePassphraseStore.CIPHER_DB_NAME
    }

    private fun isMarkedCipherDatabaseValid(
        request: PlaintextHistoryMigrationRequest,
        marker: ActivationMarker
    ): Boolean = if (marker.conversionComplete) {
        PlaintextHistoryCipherAccess.isValidCipherDatabase(
            request.encryptedFile,
            request.passphrase,
            expectedVersion = null
        )
    } else {
        PlaintextHistoryCipherAccess.isValidCipherDatabase(
            request.encryptedFile,
            request.passphrase,
            expectedVersion = AppDatabase.CURRENT_SCHEMA_VERSION,
            expectedFingerprint = marker.encryptedFingerprint
        )
    }

    private fun validateMarkedSourceArtifacts(request: PlaintextHistoryMigrationRequest, marker: ActivationMarker) {
        val sourceArtifactsExist = DatabasePassphraseStore.databaseArtifactsExist(request.sourceFile)
        val preparationIsComplete = PlaintextHistoryMigrationMarkers
            .readPreparationMarker(request.preparationMarkerFile)?.conversionComplete == true
        if (preparationIsComplete && sourceArtifactsExist) {
            migrationRequired()
        }
        if (marker.conversionComplete && sourceArtifactsExist) migrationRequired()
        if (marker.conversionComplete) return

        if (request.sourceFile.exists()) {
            if (!DatabasePassphraseStore.isPlaintextSqliteDatabase(request.sourceFile) ||
                PlaintextHistoryCipherAccess.plaintextFingerprint(request.sourceFile) != marker.sourceFingerprint
            ) migrationRequired()
        } else if (sourceArtifactsExist && !marker.sourceCleanupStarted) {
            migrationRequired()
        }
    }

    private fun convertPlaintextSource(request: PlaintextHistoryMigrationRequest): String {
        // Only a valid activation marker authorizes cleanup of a previous encrypted stage.
        // An unmarked destination may be an older encrypted History with newer rows.
        if (DatabasePassphraseStore.databaseArtifactsExist(request.encryptedFile)) {
            throw ExistingPlaintextHistoryMigrationRequiredException()
        }
        if (hasUnownedStageArtifacts(request.encryptedFile.parentFile)) migrationRequired()
        if (!request.allowNewConversion) {
            throw ExistingPlaintextHistoryMigrationRequiredException()
        }
        val sourceFingerprint = PlaintextHistoryCipherAccess.validatedPlaintextFingerprint(request.sourceFile)
        val marker = PreparingMarker(
            sourceFingerprint = sourceFingerprint,
            stageDatabaseName = newStageDatabaseName()
        )
        try {
            PlaintextHistoryMigrationMarkers.writePreparationMarker(
                request.preparationMarkerFile,
                marker,
                request.conversionHooks.markerDirectorySync,
                request.conversionHooks.markerPayloadWriter
            )
        } catch (_: Exception) {
            conversionFailed()
        }
        convertAndActivate(request, marker)
        return DatabasePassphraseStore.CIPHER_DB_NAME
    }

    private fun resumePreparingConversion(
        request: PlaintextHistoryMigrationRequest,
        discardActivationMarkerArtifacts: Boolean = false
    ): String {
        val marker = PlaintextHistoryMigrationMarkers.readPreparationMarker(request.preparationMarkerFile)
            ?: migrationRequired()
        return when {
            marker.conversionComplete -> migrationRequired()
            !sourceMatchesPreparationMarker(request.sourceFile, marker) -> migrationRequired()
            !request.allowNewConversion -> migrationRequired()
            marker.stageDatabaseName == null -> resumeLegacyPreparation(request, discardActivationMarkerArtifacts)
            else -> resumeOwnedStage(request, marker, discardActivationMarkerArtifacts)
        }
    }

    private fun resumeLegacyPreparation(
        request: PlaintextHistoryMigrationRequest,
        discardActivationMarkerArtifacts: Boolean
    ): String {
        // V1 markers did not name their output. Never claim the old fixed-name output as owned.
        if (DatabasePassphraseStore.databaseArtifactsExist(request.encryptedFile) ||
            hasUnownedStageArtifacts(request.encryptedFile.parentFile)
        ) migrationRequired()
        if (discardActivationMarkerArtifacts) discardMarkerArtifacts(request.markerFile)
        PlaintextHistoryMigrationMarkers.deleteOwnedMarkerArtifacts(request.preparationMarkerFile)
        return convertPlaintextSource(request)
    }

    private fun resumeOwnedStage(
        request: PlaintextHistoryMigrationRequest,
        marker: PreparingMarker,
        discardActivationMarkerArtifacts: Boolean
    ): String {
        val stageName = checkNotNull(marker.stageDatabaseName)
        val stageFile = File(request.encryptedFile.parentFile ?: migrationRequired(), stageName)
        val stageExists = DatabasePassphraseStore.databaseArtifactsExist(stageFile)
        val finalExists = DatabasePassphraseStore.databaseArtifactsExist(request.encryptedFile)
        return when {
            stageExists && finalExists -> migrationRequired()
            stageExists && marker.encryptedFingerprint == null -> retryIncompleteStage(
                request,
                stageFile,
                marker,
                discardActivationMarkerArtifacts
            )
            stageExists -> activateVerifiedStage(request, stageFile, marker, discardActivationMarkerArtifacts)
            marker.encryptedFingerprint != null && finalExists ->
                activatePromotedStage(request, marker, discardActivationMarkerArtifacts)
            finalExists -> migrationRequired()
            else -> retryMissingStage(request, marker, discardActivationMarkerArtifacts)
        }
    }

    private fun retryIncompleteStage(
        request: PlaintextHistoryMigrationRequest,
        stageFile: File,
        marker: PreparingMarker,
        discardActivationMarkerArtifacts: Boolean
    ): String {
        discardOwnedIncompleteStage(stageFile)
        if (discardActivationMarkerArtifacts) discardMarkerArtifacts(request.markerFile)
        convertAndActivate(request, marker)
        return DatabasePassphraseStore.CIPHER_DB_NAME
    }

    private fun retryMissingStage(
        request: PlaintextHistoryMigrationRequest,
        marker: PreparingMarker,
        discardActivationMarkerArtifacts: Boolean
    ): String {
        // A missing stage can be rebuilt from the validated source. The promotion may not have
        // reached storage before interruption, so clear only the missing stage's stale hash.
        if (discardActivationMarkerArtifacts) discardMarkerArtifacts(request.markerFile)
        val retryMarker = marker.copy(encryptedFingerprint = null)
        if (retryMarker != marker) {
            PlaintextHistoryMigrationMarkers.writePreparationMarker(
                request.preparationMarkerFile,
                retryMarker,
                request.conversionHooks.markerDirectorySync,
                request.conversionHooks.markerPayloadWriter
            )
        }
        convertAndActivate(request, retryMarker)
        return DatabasePassphraseStore.CIPHER_DB_NAME
    }

    private fun sourceMatchesPreparationMarker(sourceFile: File, marker: PreparingMarker): Boolean =
        sourceFile.isFile &&
            DatabasePassphraseStore.isPlaintextSqliteDatabase(sourceFile) &&
            PlaintextHistoryCipherAccess.validatedPlaintextFingerprint(sourceFile) == marker.sourceFingerprint

    private fun activateVerifiedStage(
        request: PlaintextHistoryMigrationRequest,
        stageFile: File,
        marker: PreparingMarker,
        discardActivationMarkerArtifacts: Boolean
    ): String {
        val expectedFingerprint = marker.encryptedFingerprint ?: migrationRequired()
        if (!PlaintextHistoryCipherAccess.isValidCipherDatabase(
                stageFile,
                request.passphrase,
                expectedVersion = AppDatabase.CURRENT_SCHEMA_VERSION,
                expectedFingerprint = expectedFingerprint
            )
        ) migrationRequired()

        promoteValidatedStage(request, stageFile, expectedFingerprint)
        return activatePromotedStage(request, marker, discardActivationMarkerArtifacts)
    }

    private fun activatePromotedStage(
        request: PlaintextHistoryMigrationRequest,
        marker: PreparingMarker,
        discardActivationMarkerArtifacts: Boolean
    ): String {
        val expectedFingerprint = marker.encryptedFingerprint ?: migrationRequired()
        if (!PlaintextHistoryCipherAccess.isValidCipherDatabase(
                request.encryptedFile,
                request.passphrase,
                expectedVersion = AppDatabase.CURRENT_SCHEMA_VERSION,
                expectedFingerprint = expectedFingerprint
            )
        ) migrationRequired()

        if (discardActivationMarkerArtifacts) discardMarkerArtifacts(request.markerFile)
        PlaintextHistoryMigrationMarkers.writeActivationMarker(
            request.markerFile,
            ActivationMarker(marker.sourceFingerprint, expectedFingerprint),
            request.conversionHooks.markerDirectorySync,
            request.conversionHooks.markerPayloadWriter
        )
        return DatabasePassphraseStore.CIPHER_DB_NAME
    }

    private fun discardOwnedIncompleteStage(stageFile: File) {
        if (!DatabasePassphraseStore.deleteDatabaseArtifacts(stageFile)) conversionFailed()
        PlaintextHistoryMigrationMarkers.syncParentDirectory(stageFile)
        if (DatabasePassphraseStore.databaseArtifactsExist(stageFile)) conversionFailed()
    }

    private fun discardMarkerArtifacts(markerFile: File) {
        if (PlaintextHistoryMigrationMarkers.hasMarkerArtifacts(markerFile)) {
            PlaintextHistoryMigrationMarkers.deleteOwnedMarkerArtifacts(markerFile)
        }
    }

    private fun recoverUnreadablePreparationMarker(request: PlaintextHistoryMigrationRequest): String {
        if (PlaintextHistoryMigrationMarkers.hasMarkerArtifacts(request.markerFile)) migrationRequired()
        if (!request.allowNewConversion || !request.sourceFile.isFile) migrationRequired()
        if (!DatabasePassphraseStore.isPlaintextSqliteDatabase(request.sourceFile)) migrationRequired()
        if (DatabasePassphraseStore.databaseArtifactsExist(request.encryptedFile)) migrationRequired()
        if (hasUnownedStageArtifacts(request.encryptedFile.parentFile)) migrationRequired()
        PlaintextHistoryCipherAccess.validatedPlaintextFingerprint(request.sourceFile)
        PlaintextHistoryMigrationMarkers.deleteOwnedMarkerArtifacts(request.preparationMarkerFile)
        return convertPlaintextSource(request)
    }

    private fun openExistingDatabase(request: PlaintextHistoryMigrationRequest): String {
        if (request.encryptedArtifactsExist) {
            // A destination without the marker and without its plaintext source is ambiguous.
            throw ExistingPlaintextHistoryMigrationRequiredException()
        }
        if (!request.sourceFile.exists() && DatabasePassphraseStore.databaseArtifactsExist(request.sourceFile)) {
            throw ExistingPlaintextHistoryMigrationRequiredException()
        }
        if (hasUnownedStageArtifacts(request.encryptedFile.parentFile)) migrationRequired()
        return DatabasePassphraseStore.DB_NAME
    }

    private fun hasUnownedStageArtifacts(databaseDirectory: File?): Boolean =
        databaseDirectory?.listFiles()?.any { file ->
            file.name.matches(Regex("${Regex.escape(DatabasePassphraseStore.CIPHER_DB_NAME)}\\.stage-[0-9a-f]{32}")) &&
                DatabasePassphraseStore.databaseArtifactsExist(file)
        } == true

    /** Remove plaintext only after the promoted encrypted database has opened through production Room. */
    fun complete(
        context: Context,
        databaseName: String,
        openedDatabase: SupportSQLiteDatabase,
        cleanupHooks: CleanupHooks = CleanupHooks()
    ) {
        if (databaseName == DatabasePassphraseStore.CIPHER_DB_NAME) {
            completeCipherDatabase(context, openedDatabase, cleanupHooks)
        }
    }

    private fun completeCipherDatabase(
        context: Context,
        openedDatabase: SupportSQLiteDatabase,
        cleanupHooks: CleanupHooks
    ) {
        val sourceFile = context.getDatabasePath(DatabasePassphraseStore.DB_NAME)
        val markerPath = markerFile(context)
        val preparationMarkerPath = preparationMarkerFile(context)
        val marker = PlaintextHistoryMigrationMarkers.readActivationMarker(markerPath) ?: return
        validateOpenedCipherDatabase(openedDatabase, marker)
        val preparationMarker = PlaintextHistoryMigrationMarkers.readPreparationMarker(preparationMarkerPath)
        if (preparationMarker?.conversionComplete == true &&
            DatabasePassphraseStore.databaseArtifactsExist(sourceFile)
        ) conversionFailed()
        if (marker.conversionComplete) {
            if (DatabasePassphraseStore.databaseArtifactsExist(sourceFile)) conversionFailed()
            PlaintextHistoryMigrationMarkers.deletePreparationMarkerBestEffort(preparationMarkerPath)
        } else {
            cleanupValidatedSource(sourceFile, markerPath, preparationMarkerPath, marker, cleanupHooks)
            PlaintextHistoryMigrationMarkers.deletePreparationMarkerBestEffort(preparationMarkerPath)
        }
    }

    private fun validateOpenedCipherDatabase(openedDatabase: SupportSQLiteDatabase, marker: ActivationMarker) {
        val hasCurrentSchema =
            HistoryDatabaseIntegrity.pragmaLong(openedDatabase, "main", "user_version") ==
                AppDatabase.CURRENT_SCHEMA_VERSION.toLong()
        val hasValidIntegrity = HistoryDatabaseIntegrity.integrityIsOk(openedDatabase, "main") &&
            (marker.conversionComplete || HistoryDatabaseIntegrity.cipherIntegrityIsOk(openedDatabase, "main"))
        val fingerprintMatches = marker.conversionComplete ||
            HistoryDatabaseIntegrity.logicalFingerprint(openedDatabase, "main") == marker.encryptedFingerprint
        if (!hasCurrentSchema || !hasValidIntegrity || !fingerprintMatches) conversionFailed()
    }

    private fun cleanupValidatedSource(
        sourceFile: File,
        markerPath: File,
        preparationMarkerPath: File,
        marker: ActivationMarker,
        cleanupHooks: CleanupHooks
    ) {
        val preparationMarker = PlaintextHistoryMigrationMarkers.readPreparationMarker(preparationMarkerPath)
        val sourceArtifactsExist = DatabasePassphraseStore.databaseArtifactsExist(sourceFile)
        if (sourceArtifactsExist) validateSourceBeforeCleanup(sourceFile, marker)
        val cleanupStartedMarker = marker.copy(sourceCleanupStarted = true)
        val markerCommitter = cleanupHooks.markerCommitter
        if (markerCommitter == null) {
            PlaintextHistoryMigrationMarkers.writeActivationMarker(markerPath, cleanupStartedMarker)
        } else {
            markerCommitter(
                markerPath,
                PlaintextHistoryMigrationMarkers.activationMarkerContents(cleanupStartedMarker)
            )
        }
        if (PlaintextHistoryMigrationMarkers.readActivationMarker(markerPath) != cleanupStartedMarker) {
            conversionFailed()
        }

        if (sourceArtifactsExist) {
            val sourceArtifactsRemoved = cleanupHooks.deleteSourceArtifacts(sourceFile)
            if (!sourceArtifactsRemoved || DatabasePassphraseStore.databaseArtifactsExist(sourceFile)) {
                conversionFailed()
            }
        }
        if (DatabasePassphraseStore.databaseArtifactsExist(sourceFile)) conversionFailed()
        cleanupHooks.sourceDirectorySync(sourceFile)
        if (DatabasePassphraseStore.databaseArtifactsExist(sourceFile)) conversionFailed()
        preparationMarker?.stageDatabaseName?.let { stageDatabaseName ->
            PlaintextHistoryMigrationMarkers.writePreparationMarker(
                preparationMarkerPath,
                PreparingMarker(
                    sourceFingerprint = marker.sourceFingerprint,
                    stageDatabaseName = stageDatabaseName,
                    encryptedFingerprint = marker.encryptedFingerprint,
                    conversionComplete = true
                ),
                PlaintextHistoryMigrationMarkers::syncParentDirectory
            )
        }
        PlaintextHistoryMigrationMarkers.writeActivationMarker(
            markerPath,
            cleanupStartedMarker.copy(conversionComplete = true)
        )
    }

    private fun validateSourceBeforeCleanup(sourceFile: File, marker: ActivationMarker) {
        if (sourceFile.exists()) {
            if (!DatabasePassphraseStore.isPlaintextSqliteDatabase(sourceFile) ||
                PlaintextHistoryCipherAccess.plaintextFingerprint(sourceFile) != marker.sourceFingerprint
            ) conversionFailed()
        } else if (!marker.sourceCleanupStarted) {
            conversionFailed()
        }
    }

    private fun convertAndActivate(request: PlaintextHistoryMigrationRequest, marker: PreparingMarker) {
        try {
            request.encryptedFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) error("History staging directory unavailable")
            }
            if (DatabasePassphraseStore.databaseArtifactsExist(request.encryptedFile)) {
                throw ExistingPlaintextHistoryMigrationRequiredException()
            }
            val stageName = checkNotNull(marker.stageDatabaseName)
            val stageFile = File(checkNotNull(request.encryptedFile.parentFile), stageName)
            if (DatabasePassphraseStore.databaseArtifactsExist(stageFile) || !stageFile.createNewFile()) {
                error("History staging file already exists")
            }
            val sourceFingerprint = PlaintextHistoryCipherAccess.exportAndValidate(
                request.sourceFile,
                stageFile,
                request.passphrase,
                request.conversionHooks.beforeCipherExport,
                request.conversionHooks.afterCipherExport
            )
            check(sourceFingerprint == marker.sourceFingerprint)
            AppDatabase.migrateStagedDatabase(
                request.context,
                stageName,
                request.passphrase
            )
            ensureNoPendingStageSidecars(stageFile)
            request.conversionHooks.stageFileSync(stageFile)
            val encryptedFingerprint = PlaintextHistoryCipherAccess.cipherFingerprint(stageFile, request.passphrase)
            check(encryptedFingerprint != null)
            val validatedMarker = marker.copy(encryptedFingerprint = encryptedFingerprint)
            PlaintextHistoryMigrationMarkers.writePreparationMarker(
                request.preparationMarkerFile,
                validatedMarker,
                request.conversionHooks.markerDirectorySync,
                request.conversionHooks.markerPayloadWriter
            )
            request.conversionHooks.afterStageReady(stageFile)

            promoteValidatedStage(request, stageFile, encryptedFingerprint)
            request.conversionHooks.afterStagePromotion(stageFile)

            request.markerFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) error("History migration directory unavailable")
            }
            PlaintextHistoryMigrationMarkers.writeActivationMarker(
                request.markerFile,
                ActivationMarker(sourceFingerprint, encryptedFingerprint),
                request.conversionHooks.markerDirectorySync,
                request.conversionHooks.markerPayloadWriter
            )
            request.conversionHooks.afterActivationMarker(stageFile)
        } catch (_: Exception) {
            throw PlaintextHistoryConversionFailedException()
        }
    }

    private fun promoteValidatedStage(
        request: PlaintextHistoryMigrationRequest,
        stageFile: File,
        expectedFingerprint: String
    ) {
        if (!PlaintextHistoryCipherAccess.isValidCipherDatabase(
                stageFile,
                request.passphrase,
                expectedVersion = AppDatabase.CURRENT_SCHEMA_VERSION,
                expectedFingerprint = expectedFingerprint
            )
        ) conversionFailed()
        ensureNoPendingStageSidecars(stageFile)
        if (DatabasePassphraseStore.databaseArtifactsExist(request.encryptedFile)) migrationRequired()
        Files.move(stageFile.toPath(), request.encryptedFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
        request.conversionHooks.markerDirectorySync(stageFile)
        if (DatabasePassphraseStore.databaseArtifactsExist(stageFile) ||
            !PlaintextHistoryCipherAccess.isValidCipherDatabase(
                request.encryptedFile,
                request.passphrase,
                expectedVersion = AppDatabase.CURRENT_SCHEMA_VERSION,
                expectedFingerprint = expectedFingerprint
            )
        ) conversionFailed()
    }

    private fun ensureNoPendingStageSidecars(stageFile: File) {
        val parent = stageFile.parentFile ?: conversionFailed()
        val wal = File(stageFile.path + "-wal")
        val journal = File(stageFile.path + "-journal")
        val shm = File(stageFile.path + "-shm")
        val masterJournals = parent.listFiles()
            ?.filter { it.name.startsWith(stageFile.name + "-mj") }
            .orEmpty()
        if (masterJournals.isNotEmpty() || wal.length() > 0L || journal.length() > 0L) conversionFailed()
        listOf(wal, journal, shm).forEach { sidecar ->
            if (sidecar.exists() && !sidecar.delete() && sidecar.exists()) conversionFailed()
        }
        PlaintextHistoryMigrationMarkers.syncParentDirectory(stageFile)
        if (wal.exists() || journal.exists() || shm.exists()) conversionFailed()
    }

    private fun newStageDatabaseName(): String =
        DatabasePassphraseStore.CIPHER_DB_NAME + ".stage-" + UUID.randomUUID().toString().replace("-", "")

    internal data class CleanupHooks(
        val sourceDirectorySync: (File) -> Unit = PlaintextHistoryMigrationMarkers::syncParentDirectory,
        val markerCommitter: ((File, ByteArray) -> Unit)? = null,
        val deleteSourceArtifacts: (File) -> Boolean = DatabasePassphraseStore::deleteDatabaseArtifacts
    )

    internal data class ConversionHooks(
        val markerDirectorySync: (File) -> Unit = PlaintextHistoryMigrationMarkers::syncParentDirectory,
        val markerPayloadWriter: (java.io.FileOutputStream, ByteArray) -> Unit =
            PlaintextHistoryMigrationMarkers::writePayload,
        val stageFileSync: (File) -> Unit = { file ->
            FileChannel.open(file.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
        },
        val beforeCipherExport: (CipherSQLiteDatabase) -> Unit = {},
        val afterCipherExport: (File) -> Unit = {},
        val afterStageReady: (File) -> Unit = {},
        val afterStagePromotion: (File) -> Unit = {},
        val afterActivationMarker: (File) -> Unit = {}
    )

    private fun migrationRequired(): Nothing = throw ExistingPlaintextHistoryMigrationRequiredException()

    private fun conversionFailed(): Nothing = throw PlaintextHistoryConversionFailedException()

    private fun markerFile(context: Context): File =
        File(
            context.getDatabasePath(DatabasePassphraseStore.DB_NAME).parentFile,
            DatabasePassphraseStore.CIPHER_MIGRATION_MARKER_NAME
        )

    private fun preparationMarkerFile(context: Context): File =
        File(
            context.getDatabasePath(DatabasePassphraseStore.DB_NAME).parentFile,
            DatabasePassphraseStore.CIPHER_MIGRATION_PREPARATION_NAME
        )

}

private data class PlaintextHistoryMigrationRequest(
    val context: Context,
    val sourceFile: File,
    val encryptedFile: File,
    val markerFile: File,
    val preparationMarkerFile: File,
    val passphrase: ByteArray,
    val sourceIsPlaintext: Boolean,
    val encryptedArtifactsExist: Boolean,
    val allowNewConversion: Boolean,
    val conversionHooks: PlaintextHistoryMigrator.ConversionHooks
)

internal class PlaintextHistoryConversionFailedException : IllegalStateException(
    "The legacy History database could not be converted safely. The original files were preserved."
)
