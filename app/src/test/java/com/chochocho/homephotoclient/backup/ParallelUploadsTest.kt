package com.chochocho.homephotoclient.backup

import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParallelUploadsTest {
    @Test
    fun `three uploads overlap and every item runs exactly once`() = runBlocking {
        withTimeout(5000) {
            val active = AtomicInteger()
            val peak = AtomicInteger()
            val started = AtomicInteger()
            val firstThree = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val seen = Collections.synchronizedList(mutableListOf<Int>())
            val job = launch(Dispatchers.Default) {
                parallelUploads((0 until 100).toList()) { item ->
                    val count = active.incrementAndGet()
                    peak.updateAndGet { maxOf(it, count) }
                    if (started.incrementAndGet() == 3) firstThree.complete(Unit)
                    try {
                        release.await()
                        seen.add(item)
                    } finally {
                        active.decrementAndGet()
                    }
                }
            }
            firstThree.await()
            assertEquals(3, started.get())
            release.complete(Unit)
            job.join()
            assertEquals(3, peak.get())
            assertEquals((0 until 100).toList(), seen.sorted())
        }
    }

    @Test
    fun `cancellation stops in-flight uploads without starting queued files`() = runBlocking {
        withTimeout(5000) {
            val started = AtomicInteger()
            val stopped = AtomicInteger()
            val firstThree = CompletableDeferred<Unit>()
            val job = launch(Dispatchers.Default) {
                parallelUploads((0 until 100).toList()) {
                    try {
                        if (started.incrementAndGet() == 3) firstThree.complete(Unit)
                        awaitCancellation()
                    } finally {
                        stopped.incrementAndGet()
                    }
                }
            }
            firstThree.await()
            job.cancelAndJoin()
            assertEquals(3, started.get())
            assertEquals(3, stopped.get())
            assertTrue(job.isCancelled)
        }
    }

    @Test
    fun `empty queue returns without uploads`() = runBlocking {
        parallelUploads(emptyList<Int>()) { error("unexpected upload") }
    }
}
