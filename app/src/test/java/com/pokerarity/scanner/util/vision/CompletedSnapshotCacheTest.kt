package com.pokerarity.scanner.util.vision

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletedSnapshotCacheTest {

    @Test
    fun concurrentReaderWaitsForCompletePublishedSnapshot() {
        val cache = CompletedSnapshotCache<List<String>>()
        val loadEntered = CountDownLatch(1)
        val releaseLoader = CountDownLatch(1)
        val readerStarted = CountDownLatch(1)
        val loads = AtomicInteger()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<List<String>> {
                cache.getOrLoad(
                    load = {
                        loads.incrementAndGet()
                        loadEntered.countDown()
                        check(releaseLoader.await(10, TimeUnit.SECONDS)) { "Loader timed out" }
                        listOf("Cacnea", "Farfetch'd")
                    },
                    onFailure = { emptyList() }
                )
            }
            assertTrue("First loader never started", loadEntered.await(10, TimeUnit.SECONDS))
            val second = executor.submit<List<String>> {
                readerStarted.countDown()
                cache.getOrLoad(
                    load = { error("Second reader must never run a competing loader") },
                    onFailure = { emptyList() }
                )
            }
            assertTrue("Second reader never started", readerStarted.await(10, TimeUnit.SECONDS))
            val returnedPrematurely = try {
                second.get(150, TimeUnit.MILLISECONDS)
                true
            } catch (_: TimeoutException) {
                false
            }
            assertFalse("Reader returned before completed publication", returnedPrematurely)
            releaseLoader.countDown()
            assertEquals(listOf("Cacnea", "Farfetch'd"), first.get(10, TimeUnit.SECONDS))
            assertEquals(listOf("Cacnea", "Farfetch'd"), second.get(10, TimeUnit.SECONDS))
            assertEquals(1, loads.get())
        } finally {
            releaseLoader.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun transientFailureReturnsSafeFallbackButAllowsRetry() {
        val cache = CompletedSnapshotCache<List<String>>()
        val attempts = AtomicInteger()
        assertEquals(
            emptyList<String>(),
            cache.getOrLoad(load = {
                attempts.incrementAndGet()
                throw IOException("Simulated failed model read")
            }, onFailure = { emptyList() })
        )
        assertEquals(
            listOf("Cacnea"),
            cache.getOrLoad(load = {
                attempts.incrementAndGet()
                listOf("Cacnea")
            }, onFailure = { emptyList() })
        )
        assertEquals(
            listOf("Cacnea"),
            cache.getOrLoad(load = { error("Should be cached") }, onFailure = { emptyList() })
        )
        assertEquals(2, attempts.get())
    }

    @Test
    fun validEmptySnapshotIsCachedRatherThanRetried() {
        val cache = CompletedSnapshotCache<List<String>>()
        val loads = AtomicInteger()
        assertEquals(
            emptyList<String>(),
            cache.getOrLoad(
                load = {
                    loads.incrementAndGet()
                    emptyList()
                },
                onFailure = { listOf("unwanted fallback") }
            )
        )
        assertEquals(
            emptyList<String>(),
            cache.getOrLoad(
                load = { error("Valid empty snapshot must not reload") },
                onFailure = { listOf("unwanted fallback") }
            )
        )
        assertEquals(1, loads.get())
    }
}
