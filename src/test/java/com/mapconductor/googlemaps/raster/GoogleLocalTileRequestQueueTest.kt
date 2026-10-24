package com.mapconductor.googlemaps.raster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class GoogleLocalTileRequestQueueTest {
    @Test
    fun obsoletePendingRequestsReturnBeforeTheActiveRenderFinishes() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val queue = GoogleLocalTileRequestQueue(width = 1) { _, _ -> entered.countDown(); release.await(); byteArrayOf(1) }
        val workers = Executors.newFixedThreadPool(2)
        try {
            queue.updateZoom(12.0)
            val owner = workers.submit<ByteArray?> { queue.renderTile("owner", 12) }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val pending = workers.submit<ByteArray?> { queue.renderTile("pending", 12) }
            queue.updateZoom(16.0)
            assertNull(pending.get(1, TimeUnit.SECONDS))
            assertFalse(owner.isDone)
            assertTrue(queue.consumeRetryAfterZoom())
            assertFalse(queue.consumeRetryAfterZoom())
            assertNull(queue.renderTile("obsolete-arrival", 12))
            release.countDown()
            assertNull(owner.get(1, TimeUnit.SECONDS))
        } finally {
            release.countDown(); queue.close(); workers.shutdownNow()
        }
    }

    @Test
    fun currentZoomPreemptsAnAdjacentFallbackWhenEverySlotIsOccupied() {
        val entered = CountDownLatch(1)
        val queue = GoogleLocalTileRequestQueue(width = 1) { url, cancelled ->
            if (url == "fallback") {
                entered.countDown()
                while (!cancelled()) Thread.sleep(1)
            }
            url.toByteArray()
        }
        val workers = Executors.newFixedThreadPool(2)
        try {
            queue.updateZoom(12.0)
            val fallback = workers.submit<ByteArray?> { queue.renderTile("fallback", 11) }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val current = workers.submit<ByteArray?> { queue.renderTile("current", 12) }
            assertEquals("current", current.get(1, TimeUnit.SECONDS)!!.decodeToString())
            assertNull(fallback.get(1, TimeUnit.SECONDS))
        } finally {
            queue.close(); workers.shutdownNow()
        }
    }

    @Test
    fun concurrentSdkCallbacksRemainBoundedAndKeepTheirCoordinates() {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val queue = GoogleLocalTileRequestQueue(width = 3) { url, _ ->
            peak.accumulateAndGet(active.incrementAndGet(), ::maxOf)
            try { Thread.sleep(2); url.toByteArray() } finally { active.decrementAndGet() }
        }
        val workers = Executors.newFixedThreadPool(16)
        try {
            queue.updateZoom(12.0)
            val results = (0 until 200).map { id -> workers.submit<ByteArray?> { queue.renderTile("12/$id/1612", 12) } }
            results.forEachIndexed { id, result -> assertEquals("12/$id/1612", result.get(5, TimeUnit.SECONDS)!!.decodeToString()) }
            assertTrue(peak.get() in 1..3)
            assertEquals(0, active.get())
        } finally {
            queue.close(); workers.shutdownNow()
        }
    }

    @Test
    fun removingTheLayerRejectsNewRequestsAndCancelsTheActiveOne() {
        val entered = CountDownLatch(1)
        val queue = GoogleLocalTileRequestQueue { _, cancelled ->
            entered.countDown()
            while (!cancelled()) Thread.sleep(1)
            byteArrayOf(1)
        }
        val worker = Executors.newSingleThreadExecutor()
        try {
            val running = worker.submit<ByteArray?> { queue.renderTile("active", 12) }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            queue.close()
            assertNull(running.get(1, TimeUnit.SECONDS))
            assertNull(queue.renderTile("after-close", 12))
        } finally {
            queue.close(); worker.shutdownNow()
        }
    }
}
