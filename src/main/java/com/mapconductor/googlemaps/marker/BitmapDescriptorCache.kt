package com.mapconductor.googlemaps.marker

import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import android.graphics.Bitmap
import android.util.LruCache

/**
 * Cache wrapper for BitmapDescriptor to avoid recreating instances for identical bitmaps.
 * Uses bitmap hashCode as the cache key for efficient lookup.
 */
object BitmapDescriptorCache {
    // Bounded LRU (count-based, max 512 entries) rather than an unbounded map:
    // this is a process-global singleton whose clearCache() is never called, so an
    // unbounded map would grow forever with icon churn. The core BitmapIconCache
    // regenerates bitmaps on LRU eviction, minting new identity-hash keys, so the
    // key space is effectively unbounded. LruCache is synchronized internally.
    private val cache = object : LruCache<Int, BitmapDescriptor>(512) {
        override fun sizeOf(key: Int, value: BitmapDescriptor): Int = 1
    }

    /**
     * Gets a cached BitmapDescriptor for the given bitmap, or creates and caches a new one.
     *
     * @param bitmap The bitmap to convert to BitmapDescriptor
     * @return Cached or newly created BitmapDescriptor
     */
    fun fromBitmap(bitmap: Bitmap): BitmapDescriptor {
        val key = bitmap.hashCode()
        return cache.get(key) ?: BitmapDescriptorFactory.fromBitmap(bitmap).also { cache.put(key, it) }
    }

    /**
     * Clears the entire cache. Use this when memory pressure is detected
     * or when you want to force recreation of all descriptors.
     */
    fun clearCache() {
        cache.evictAll()
    }

    /**
     * Gets the current cache size for debugging/monitoring purposes.
     */
    fun getCacheSize(): Int = cache.size()
}
