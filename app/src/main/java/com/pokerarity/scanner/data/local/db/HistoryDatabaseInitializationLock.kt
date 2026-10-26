package com.pokerarity.scanner.data.local.db

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException

/** Serializes key selection, migration, Room open, and migration completion across processes. */
internal object HistoryDatabaseInitializationLock {
    private const val LOCK_FILE_NAME = "pokerarity_db.initialization.lock"
    private val processMonitor = Any()

    fun <T> withExclusiveLock(context: Context, operation: () -> T): T = synchronized(processMonitor) {
        val lockFile = lockFile(context)
        RandomAccessFile(lockFile, "rw").use { lockHandle ->
            lockHandle.channel.lock().use { operation() }
        }
    }

    /** Returns false immediately when another process owns the lock. */
    fun tryWithExclusiveLock(context: Context, operation: () -> Unit): Boolean = synchronized(processMonitor) {
        val lockFile = lockFile(context)
        RandomAccessFile(lockFile, "rw").use { lockHandle ->
            val fileLock = try {
                lockHandle.channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (fileLock == null) {
                false
            } else {
                fileLock.use { operation() }
                true
            }
        }
    }

    private fun lockFile(context: Context): File {
        val databaseDirectory = context.applicationContext
            .getDatabasePath(DatabasePassphraseStore.DB_NAME)
            .parentFile ?: error("History database directory is unavailable")
        if (!databaseDirectory.exists() && !databaseDirectory.mkdirs() && !databaseDirectory.exists()) {
            error("History database directory is unavailable")
        }
        return File(databaseDirectory, LOCK_FILE_NAME)
    }
}
