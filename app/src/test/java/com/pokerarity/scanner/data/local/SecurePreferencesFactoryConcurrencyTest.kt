package com.pokerarity.scanner.data.local

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurePreferencesFactoryConcurrencyTest {
    @Test
    fun concurrentFirstUseSerializesMasterKeyCreation() {
        val masterKeyExists = AtomicBoolean(false)
        val creationCount = AtomicInteger()
        val firstEnteredCreation = CountDownLatch(1)
        val releaseFirstCreation = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        val first = Thread({
            runLockedCreation(failure) {
                if (!masterKeyExists.get()) {
                    creationCount.incrementAndGet()
                    firstEnteredCreation.countDown()
                    check(releaseFirstCreation.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    masterKeyExists.set(true)
                }
            }
        }, "secure-preferences-first-use-one")
        val second = Thread({
            secondStarted.countDown()
            runLockedCreation(failure) {
                if (!masterKeyExists.get()) {
                    creationCount.incrementAndGet()
                    masterKeyExists.set(true)
                }
            }
        }, "secure-preferences-first-use-two")

        first.start()
        assertTrue(firstEnteredCreation.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        second.start()
        try {
            assertTrue(secondStarted.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertTrue(awaitBlocked(second))
        } finally {
            releaseFirstCreation.countDown()
        }

        first.join(TEST_TIMEOUT_MILLIS)
        second.join(TEST_TIMEOUT_MILLIS)
        assertTrue(!first.isAlive && !second.isAlive)
        failure.get()?.let { throw AssertionError("Secure preference initialization failed", it) }
        assertEquals(1, creationCount.get())
    }

    private fun runLockedCreation(failure: AtomicReference<Throwable?>, create: () -> Unit) {
        try {
            SecurePreferencesFactory.withCreationLock(create)
        } catch (error: Throwable) {
            failure.compareAndSet(null, error)
        }
    }

    private fun awaitBlocked(thread: Thread): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TEST_TIMEOUT_SECONDS)
        while (thread.state != Thread.State.BLOCKED &&
            thread.state != Thread.State.TERMINATED && System.nanoTime() < deadline
        ) {
            Thread.yield()
        }
        return thread.state == Thread.State.BLOCKED
    }

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 5L
        const val TEST_TIMEOUT_MILLIS = 5_000L
    }
}
