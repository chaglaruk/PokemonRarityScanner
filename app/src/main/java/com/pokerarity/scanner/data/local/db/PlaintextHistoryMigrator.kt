package com.pokerarity.scanner.data.local.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pokerarity.scanner.BuildConfig
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherSQLiteDatabase

/** Converts a legacy SQLite History file without replacing its source until the cipher copy opens. */
@Suppress("TooManyFunctions") // Private helpers keep the staged migration transaction and recovery paths together.
internal object PlaintextHistoryMigrator {
    private const val ATTACHED_PLAINTEXT_NAME = "legacy_plaintext"
    private const val ATTACHED_ENCRYPTED_NAME = "encrypted_copy"
    private const val SQLITE_FIRST_SUPPORTED_SCHEMA_VERSION = 3L
    private const val SQLITE_PRAGMA_PREFIX = "PRAGMA "

    fun prepare(context: Context, passphrase: ByteArray): String =
        prepareInternal(context, passphrase, allowNewConversion = false, conversionHooks = ConversionHooks())

    /** Exercise the migration algorithm only from isolated instrumentation fixtures. */
    internal fun prepareForIsolatedValidation(
        context: Context,
        passphrase: ByteArray,
        conversionHooks: ConversionHooks = ConversionHooks()
    ): String {
        check(BuildConfig.DEBUG) { "Plaintext History conversion validation is debug-only" }
        return prepareInternal(context, passphrase, allowNewConversion = true, conversionHooks = conversionHooks)
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
        isValidCipherDatabase(request.encryptedFile, request.passphrase, expectedVersion = null)
    } else {
        isValidCipherDatabase(
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
                plaintextFingerprint(request.sourceFile) != marker.sourceFingerprint
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
        if (!request.allowNewConversion) {
            throw ExistingPlaintextHistoryMigrationRequiredException()
        }
        val sourceFingerprint = validatedPlaintextFingerprint(request.sourceFile)
        PlaintextHistoryMigrationMarkers.writePreparationMarker(
            request.preparationMarkerFile,
            PreparingMarker(sourceFingerprint),
            request.conversionHooks.markerDirectorySync
        )
        convertAndActivate(request, sourceFingerprint)
        return DatabasePassphraseStore.CIPHER_DB_NAME
    }

    private fun resumePreparingConversion(
        request: PlaintextHistoryMigrationRequest,
        discardActivationMarkerArtifacts: Boolean = false
    ): String {
        val marker = PlaintextHistoryMigrationMarkers.readPreparationMarker(request.preparationMarkerFile)
            ?: migrationRequired()
        if (marker.conversionComplete) migrationRequired()
        if (!sourceMatchesPreparationMarker(request.sourceFile, marker)) {
            migrationRequired()
        }
        if (!request.allowNewConversion) migrationRequired()

        if (DatabasePassphraseStore.databaseArtifactsExist(request.encryptedFile)) {
            return activateVerifiedStage(request, marker, discardActivationMarkerArtifacts)
        }

        if (discardActivationMarkerArtifacts) discardMarkerArtifacts(request.markerFile)
        if (marker.encryptedFingerprint != null) {
            PlaintextHistoryMigrationMarkers.writePreparationMarker(
                request.preparationMarkerFile,
                marker.copy(encryptedFingerprint = null),
                request.conversionHooks.markerDirectorySync
            )
        }
        convertAndActivate(request, marker.sourceFingerprint)
        return DatabasePassphraseStore.CIPHER_DB_NAME
    }

    private fun sourceMatchesPreparationMarker(sourceFile: File, marker: PreparingMarker): Boolean =
        sourceFile.isFile &&
            DatabasePassphraseStore.isPlaintextSqliteDatabase(sourceFile) &&
            validatedPlaintextFingerprint(sourceFile) == marker.sourceFingerprint

    private fun activateVerifiedStage(
        request: PlaintextHistoryMigrationRequest,
        marker: PreparingMarker,
        discardActivationMarkerArtifacts: Boolean
    ): String {
        val expectedFingerprint = marker.encryptedFingerprint ?: migrationRequired()
        if (!isValidCipherDatabase(
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
            request.conversionHooks.markerDirectorySync
        )
        return DatabasePassphraseStore.CIPHER_DB_NAME
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
        validatedPlaintextFingerprint(request.sourceFile)
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
        return DatabasePassphraseStore.DB_NAME
    }

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
        PlaintextHistoryMigrationMarkers.writePreparationMarker(
            preparationMarkerPath,
            PreparingMarker(marker.sourceFingerprint, conversionComplete = true),
            PlaintextHistoryMigrationMarkers::syncParentDirectory
        )
        PlaintextHistoryMigrationMarkers.writeActivationMarker(
            markerPath,
            cleanupStartedMarker.copy(conversionComplete = true)
        )
    }

    private fun validateSourceBeforeCleanup(sourceFile: File, marker: ActivationMarker) {
        if (sourceFile.exists()) {
            if (!DatabasePassphraseStore.isPlaintextSqliteDatabase(sourceFile) ||
                plaintextFingerprint(sourceFile) != marker.sourceFingerprint
            ) conversionFailed()
        } else if (!marker.sourceCleanupStarted) {
            conversionFailed()
        }
    }

    private fun convertAndActivate(request: PlaintextHistoryMigrationRequest, expectedSourceFingerprint: String) {
        try {
            request.encryptedFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) error("History staging directory unavailable")
            }
            if (!request.encryptedFile.createNewFile()) error("History staging file already exists")
            val sourceFingerprint = exportAndValidate(
                request.sourceFile,
                request.encryptedFile,
                request.passphrase
            )
            check(sourceFingerprint == expectedSourceFingerprint)
            AppDatabase.migrateStagedDatabase(
                request.context,
                DatabasePassphraseStore.CIPHER_DB_NAME,
                request.passphrase
            )
            val encryptedFingerprint = cipherFingerprint(request.encryptedFile, request.passphrase)
            check(encryptedFingerprint != null)
            PlaintextHistoryMigrationMarkers.writePreparationMarker(
                request.preparationMarkerFile,
                PreparingMarker(sourceFingerprint, encryptedFingerprint = encryptedFingerprint),
                request.conversionHooks.markerDirectorySync
            )
            request.conversionHooks.afterStageReady(request.encryptedFile)

            request.markerFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) error("History migration directory unavailable")
            }
            PlaintextHistoryMigrationMarkers.writeActivationMarker(
                request.markerFile,
                ActivationMarker(sourceFingerprint, encryptedFingerprint),
                request.conversionHooks.markerDirectorySync
            )
        } catch (_: Exception) {
            throw PlaintextHistoryConversionFailedException()
        }
    }

    private fun exportAndValidate(sourceFile: File, encryptedFile: File, passphrase: ByteArray): String {
        require(DatabasePassphraseStore.isPlaintextSqliteDatabase(sourceFile))
        require(sourceFile.isFile)

        val exporter = CipherSQLiteDatabase.create(null)
        var sourceFingerprint: String? = null
        try {
            exporter.execSQL(
                "ATTACH DATABASE ? AS " + ATTACHED_PLAINTEXT_NAME + " KEY ''",
                arrayOf<Any>(sourceFile.absolutePath)
            )
            exporter.execSQL(
                "ATTACH DATABASE ? AS " + ATTACHED_ENCRYPTED_NAME + " KEY ?",
                arrayOf<Any>(encryptedFile.absolutePath, passphrase.clone())
            )

            val sourceVersion = HistoryDatabaseIntegrity.pragmaLong(exporter, ATTACHED_PLAINTEXT_NAME, "user_version")
            if (sourceVersion !in SQLITE_FIRST_SUPPORTED_SCHEMA_VERSION..AppDatabase.CURRENT_SCHEMA_VERSION.toLong()) {
                error("Unsupported plaintext History schema")
            }
            check(HistoryDatabaseIntegrity.integrityIsOk(exporter, ATTACHED_PLAINTEXT_NAME))
            sourceFingerprint = HistoryDatabaseIntegrity.logicalFingerprint(exporter, ATTACHED_PLAINTEXT_NAME)
            exporter.rawQuery(
                "SELECT sqlcipher_export('" + ATTACHED_ENCRYPTED_NAME + "', '" + ATTACHED_PLAINTEXT_NAME + "')",
                null
            ).use { cursor -> check(cursor.moveToFirst()) }

            // sqlcipher_export intentionally does not copy SQLite's user_version.
            exporter.execSQL(
                SQLITE_PRAGMA_PREFIX + quoteIdentifier(ATTACHED_ENCRYPTED_NAME) + ".user_version = " + sourceVersion
            )
            check(
                HistoryDatabaseIntegrity.pragmaLong(exporter, ATTACHED_ENCRYPTED_NAME, "user_version") == sourceVersion
            )
            check(HistoryDatabaseIntegrity.integrityIsOk(exporter, ATTACHED_ENCRYPTED_NAME))
            check(HistoryDatabaseIntegrity.cipherIntegrityIsOk(exporter, ATTACHED_ENCRYPTED_NAME))
            check(
                HistoryDatabaseIntegrity.databaseContentsMatch(
                    exporter,
                    ATTACHED_PLAINTEXT_NAME,
                    ATTACHED_ENCRYPTED_NAME
                )
            )
            check(HistoryDatabaseIntegrity.logicalFingerprint(exporter, ATTACHED_PLAINTEXT_NAME) == sourceFingerprint)
            exporter.execSQL("DETACH DATABASE " + ATTACHED_ENCRYPTED_NAME)
            exporter.execSQL("DETACH DATABASE " + ATTACHED_PLAINTEXT_NAME)
        } finally {
            exporter.close()
        }

        check(!DatabasePassphraseStore.isPlaintextSqliteDatabase(encryptedFile))
        check(isValidCipherDatabase(encryptedFile, passphrase, expectedVersion = null))
        return checkNotNull(sourceFingerprint)
    }

    private fun isValidCipherDatabase(
        file: File,
        passphrase: ByteArray,
        expectedVersion: Int?,
        expectedFingerprint: String? = null
    ): Boolean {
        if (!file.isFile || DatabasePassphraseStore.isPlaintextSqliteDatabase(file)) return false
        return runCatching {
            val database = CipherSQLiteDatabase.openDatabase(
                file.absolutePath,
                passphrase.clone(),
                null,
                CipherSQLiteDatabase.OPEN_READONLY,
                null,
                null
            )
            try {
                HistoryDatabaseIntegrity.integrityIsOk(database, "main") &&
                    (expectedFingerprint == null || HistoryDatabaseIntegrity.cipherIntegrityIsOk(database, "main")) &&
                    (expectedVersion == null ||
                        HistoryDatabaseIntegrity.pragmaLong(database, "main", "user_version") ==
                            expectedVersion.toLong()) &&
                    (expectedFingerprint == null ||
                        HistoryDatabaseIntegrity.logicalFingerprint(database, "main") == expectedFingerprint)
            } finally {
                database.close()
            }
        }.getOrDefault(false)
    }

    private fun cipherFingerprint(file: File, passphrase: ByteArray): String? {
        if (!file.isFile || DatabasePassphraseStore.isPlaintextSqliteDatabase(file)) return null
        return runCatching {
            val database = CipherSQLiteDatabase.openDatabase(
                file.absolutePath,
                passphrase.clone(),
                null,
                CipherSQLiteDatabase.OPEN_READONLY,
                null,
                null
            )
            try {
                check(HistoryDatabaseIntegrity.integrityIsOk(database, "main"))
                check(HistoryDatabaseIntegrity.cipherIntegrityIsOk(database, "main"))
                HistoryDatabaseIntegrity.logicalFingerprint(database, "main")
            } finally {
                database.close()
            }
        }.getOrNull()
    }

    internal data class CleanupHooks(
        val sourceDirectorySync: (File) -> Unit = PlaintextHistoryMigrationMarkers::syncParentDirectory,
        val markerCommitter: ((File, ByteArray) -> Unit)? = null,
        val deleteSourceArtifacts: (File) -> Boolean = DatabasePassphraseStore::deleteDatabaseArtifacts
    )

    internal data class ConversionHooks(
        val markerDirectorySync: (File) -> Unit = PlaintextHistoryMigrationMarkers::syncParentDirectory,
        val afterStageReady: (File) -> Unit = {}
    )

    private fun validatedPlaintextFingerprint(sourceFile: File): String {
        val exporter = CipherSQLiteDatabase.create(null)
        try {
            exporter.execSQL(
                "ATTACH DATABASE ? AS " + ATTACHED_PLAINTEXT_NAME + " KEY ''",
                arrayOf<Any>(sourceFile.absolutePath)
            )
            val sourceVersion = HistoryDatabaseIntegrity.pragmaLong(exporter, ATTACHED_PLAINTEXT_NAME, "user_version")
            if (sourceVersion !in SQLITE_FIRST_SUPPORTED_SCHEMA_VERSION..AppDatabase.CURRENT_SCHEMA_VERSION.toLong()) {
                throw PlaintextHistoryConversionFailedException()
            }
            check(HistoryDatabaseIntegrity.integrityIsOk(exporter, ATTACHED_PLAINTEXT_NAME))
            return HistoryDatabaseIntegrity.logicalFingerprint(exporter, ATTACHED_PLAINTEXT_NAME)
        } finally {
            exporter.close()
        }
    }

    private fun plaintextFingerprint(sourceFile: File): String {
        val exporter = CipherSQLiteDatabase.create(null)
        try {
            exporter.execSQL(
                "ATTACH DATABASE ? AS " + ATTACHED_PLAINTEXT_NAME + " KEY ''",
                arrayOf<Any>(sourceFile.absolutePath)
            )
            check(HistoryDatabaseIntegrity.integrityIsOk(exporter, ATTACHED_PLAINTEXT_NAME))
            return HistoryDatabaseIntegrity.logicalFingerprint(exporter, ATTACHED_PLAINTEXT_NAME)
        } finally {
            exporter.close()
        }
    }

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

    private fun quoteIdentifier(identifier: String): String =
        "\"" + identifier.replace("\"", "\"\"") + "\""

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
