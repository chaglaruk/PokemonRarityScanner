package com.pokerarity.scanner.data.local.db

import android.net.Uri
import android.os.Bundle
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDatabaseInitializationLockTest {
    @Test
    fun serializesDatabaseInitializationAcrossProcesses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val authority = "${context.packageName}.history_lock_probe"
        val probeUri = Uri.Builder().scheme("content").authority(authority).build()
        val attemptId = UUID.randomUUID().toString()
        val executor = Executors.newSingleThreadExecutor()
        var acquisition: java.util.concurrent.Future<Bundle>? = null

        try {
            HistoryDatabaseInitializationLock.withExclusiveLock(context) {
                val nonblockingResult = callProbe(
                    context,
                    probeUri,
                    HistoryDatabaseInitializationLockProbeProvider.METHOD_TRY_LOCK
                )
                assertTrue(
                    "the try-lock result is reported",
                    nonblockingResult.containsKey(HistoryDatabaseInitializationLockProbeProvider.RESULT_ACQUIRED)
                )
                assertFalse(
                    "second process cannot take the held OS file lock",
                    nonblockingResult.getBoolean(HistoryDatabaseInitializationLockProbeProvider.RESULT_ACQUIRED)
                )
                val nonblockingProcessId = nonblockingResult.getInt(
                    HistoryDatabaseInitializationLockProbeProvider.RESULT_PROCESS_ID
                )
                assertTrue("probe reports its process ID", nonblockingProcessId > 0)
                assertFalse(
                    "probe ran in another process",
                    Process.myPid() == nonblockingProcessId
                )
                assertFalse(
                    "the probe process skips scanner and database startup",
                    nonblockingResult.getBoolean(
                        HistoryDatabaseInitializationLockProbeProvider.RESULT_SCANNER_RUNTIME_STARTED
                    )
                )
                val unrelatedAttemptState = callProbe(
                    context,
                    probeUri,
                    HistoryDatabaseInitializationLockProbeProvider.METHOD_STATE,
                    UUID.randomUUID().toString()
                )
                assertFalse(
                    "a prior attempt cannot satisfy this attempt's start check",
                    unrelatedAttemptState.getBoolean(
                        HistoryDatabaseInitializationLockProbeProvider.RESULT_ATTEMPT_STARTED
                    )
                )

                acquisition = executor.submit<Bundle> {
                    callProbe(
                        context,
                        probeUri,
                        HistoryDatabaseInitializationLockProbeProvider.METHOD_ACQUIRE_LOCK,
                        attemptId
                    )
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LOCK_TIMEOUT_SECONDS)
                var attemptStarted = false
                while (!attemptStarted && System.nanoTime() < deadline) {
                    val state = callProbe(
                        context,
                        probeUri,
                        HistoryDatabaseInitializationLockProbeProvider.METHOD_STATE,
                        attemptId
                    )
                    val stateProcessId = state.getInt(HistoryDatabaseInitializationLockProbeProvider.RESULT_PROCESS_ID)
                    assertTrue("probe reports its process ID", stateProcessId > 0)
                    assertFalse("probe ran in another process", Process.myPid() == stateProcessId)
                    attemptStarted = state.getBoolean(HistoryDatabaseInitializationLockProbeProvider.RESULT_ATTEMPT_STARTED)
                    if (!attemptStarted) Thread.sleep(10)
                }
                assertTrue("second process entered the blocking acquisition attempt", attemptStarted)
                assertFalse("blocking acquisition remains pending while the lock is held", checkNotNull(acquisition).isDone)
            }

            val acquired = checkNotNull(acquisition).get(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue("second process acquires the released lock", acquired.getBoolean(
                HistoryDatabaseInitializationLockProbeProvider.RESULT_ACQUIRED
            ))
        } finally {
            executor.shutdownNow()
        }
    }

    private fun callProbe(context: android.content.Context, uri: Uri, method: String, arg: String? = null): Bundle =
        checkNotNull(context.contentResolver.call(uri, method, arg, null))

    companion object {
        private const val LOCK_TIMEOUT_SECONDS = 10L
    }
}
