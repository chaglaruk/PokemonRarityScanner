package com.pokerarity.scanner.data.local

import android.content.Context
import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TelemetryPreferencesMigrationConcurrencyTest {
    private lateinit var context: Context
    private lateinit var legacyPrefs: SharedPreferences
    private lateinit var securePrefs: SharedPreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        securePrefs = context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
        legacyPrefs.edit().clear().commit()
        securePrefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        legacyPrefs.edit().clear().commit()
        securePrefs.edit().clear().commit()
    }

    @Test
    @Suppress("LongMethod") // Keep the deterministic two-thread race and its assertions together.
    fun concurrentMigrationCannotOverwriteConsentAfterLegacyPreferencesAreCleared() {
        val expectedTimestamp = 1_798_000_123L
        legacyPrefs.edit()
            .putBoolean(TelemetryPreferences.KEY_USER_CONSENT, true)
            .putLong(TelemetryPreferences.KEY_CONSENT_TIMESTAMP, expectedTimestamp)
            .putBoolean(TelemetryPreferences.KEY_HAS_SEEN_ONBOARDING, true)
            .commit()

        val legacyConsentReads = AtomicInteger()
        val firstMigrationReachedReadWindow = CountDownLatch(1)
        val releaseFirstMigration = CountDownLatch(1)
        val observedLegacyPrefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, arguments ->
            if (method.name == "getBoolean" &&
                arguments?.getOrNull(0) == TelemetryPreferences.KEY_USER_CONSENT &&
                legacyConsentReads.incrementAndGet() == 2
            ) {
                firstMigrationReachedReadWindow.countDown()
                check(releaseFirstMigration.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    "Test did not release the first migration's legacy read"
                }
            }
            method.invoke(legacyPrefs, *(arguments ?: emptyArray()))
        } as SharedPreferences

        val firstFinished = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val first = migrationThread("telemetry-migration-first", firstFinished, failure) {
            TelemetryPreferencesLegacyMigration.migrate(observedLegacyPrefs, securePrefs) { }
        }
        val second = migrationThread(
            "telemetry-migration-second",
            secondFinished,
            failure,
            beforeMigration = { secondStarted.countDown() }
        ) {
            TelemetryPreferencesLegacyMigration.migrate(observedLegacyPrefs, securePrefs) { }
        }

        first.start()
        assertTrue(firstMigrationReachedReadWindow.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        second.start()
        try {
            assertTrue(secondStarted.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertTrue(
                "The second constructor must wait for the process-wide migration lock",
                awaitBlocked(second)
            )
        } finally {
            releaseFirstMigration.countDown()
        }

        assertTrue(firstFinished.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertTrue(secondFinished.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        first.join(TEST_TIMEOUT_MILLIS)
        second.join(TEST_TIMEOUT_MILLIS)
        failure.get()?.let { throw AssertionError("Legacy preference migration failed", it) }

        assertTrue(securePrefs.getBoolean(TelemetryPreferences.KEY_USER_CONSENT, false))
        assertEquals(expectedTimestamp, securePrefs.getLong(TelemetryPreferences.KEY_CONSENT_TIMESTAMP, 0L))
        assertTrue(securePrefs.getBoolean(TelemetryPreferences.KEY_HAS_SEEN_ONBOARDING, false))
        assertTrue(securePrefs.getBoolean(TelemetryPreferences.KEY_LEGACY_MIGRATED, false))
        assertFalse(legacyPrefs.contains(TelemetryPreferences.KEY_USER_CONSENT))
    }

    private fun migrationThread(
        name: String,
        finished: CountDownLatch,
        failure: AtomicReference<Throwable?>,
        beforeMigration: () -> Unit = {},
        migration: () -> Unit
    ) = Thread({
        try {
            beforeMigration()
            migration()
        } catch (error: Throwable) {
            failure.compareAndSet(null, error)
        } finally {
            finished.countDown()
        }
    }, name)

    private fun awaitBlocked(thread: Thread): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TEST_TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            when (thread.state) {
                Thread.State.BLOCKED -> return true
                Thread.State.TERMINATED -> break
                else -> Thread.yield()
            }
        }
        return thread.state == Thread.State.BLOCKED
    }

    private companion object {
        const val LEGACY_PREFS_NAME = "telemetry_prefs_migration_test"
        const val SECURE_PREFS_NAME = "telemetry_prefs_secure_migration_test"
        const val TEST_TIMEOUT_SECONDS = 5L
        const val TEST_TIMEOUT_MILLIS = 5_000L
    }
}
