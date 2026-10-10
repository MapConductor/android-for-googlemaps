package com.mapconductor.googlemaps.raster

import com.mapconductor.core.InternalMapConductorApi
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs

/** Bounds local renders and discards obsolete zooms before they occupy a worker. */
@InternalMapConductorApi
internal class GoogleLocalTileRequestQueue(
    private val width: Int = 4,
    private val render: (String, () -> Boolean) -> ByteArray?,
) {
    @InternalMapConductorApi
    private class Job(val zoom: Int) {
        val cancelled = AtomicBoolean(false)
    }

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val pending = mutableListOf<Job>()
    private val active = mutableSetOf<Job>()
    private val retryAfterZoom = AtomicBoolean(false)
    private var zoom: Double? = null
    private var closed = false

    init {
        require(width > 0)
    }

    fun updateZoom(next: Double) {
        if (!next.isFinite()) return
        lock.withLock {
            zoom = next
            for (job in pending + active) {
                if (abs(job.zoom - next) > 1.5 && job.cancelled.compareAndSet(false, true)) retryAfterZoom.set(true)
            }
            pending.removeAll { it.cancelled.get() }
            changed.signalAll()
        }
    }

    fun consumeRetryAfterZoom(): Boolean = retryAfterZoom.getAndSet(false)

    fun close() {
        lock.withLock {
            closed = true
            for (job in pending + active) job.cancelled.set(true)
            pending.clear()
            changed.signalAll()
        }
    }

    fun renderTile(url: String, tileZoom: Int): ByteArray? {
        val job = Job(tileZoom)
        lock.withLock {
            if (closed || zoom?.let { abs(tileZoom - it) > 1.5 } == true) return null
            pending.add(job)
            try {
                while (!job.cancelled.get()) {
                    val current = zoom
                    val next = pending.minByOrNull { abs(it.zoom - (current ?: it.zoom.toDouble())) }
                    if (active.size < width && next === job) {
                        pending.remove(job)
                        active.add(job)
                        break
                    }
                    if (current != null && next != null) {
                        val closest = abs(next.zoom - current)
                        for (running in active) {
                            if (abs(running.zoom - current) > closest + 0.25 && running.cancelled.compareAndSet(false, true)) {
                                retryAfterZoom.set(true)
                            }
                        }
                    }
                    changed.await()
                }
                if (job.cancelled.get()) return null
            } catch (_: InterruptedException) {
                job.cancelled.set(true)
                Thread.currentThread().interrupt()
                return null
            } finally {
                pending.remove(job)
                changed.signalAll()
            }
        }
        return try {
            val bytes = render(url) { job.cancelled.get() }
            if (job.cancelled.get()) null else bytes
        } finally {
            lock.withLock {
                active.remove(job)
                changed.signalAll()
            }
        }
    }
}
