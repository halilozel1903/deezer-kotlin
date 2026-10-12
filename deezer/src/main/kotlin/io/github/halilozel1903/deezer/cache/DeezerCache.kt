package io.github.halilozel1903.deezer.cache

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Stores response bodies by request url. Charts change during the day and preview urls expire, so the default
 * [memory] cache keeps responses for 10 minutes.
 *
 * ```kotlin
 * cache = DeezerCache.memory() + DeezerCache.disk(File(context.cacheDir, "deezer"))
 * ```
 *
 * Implement it to store responses elsewhere (a database, a key value store). Error objects are never cached.
 */
public interface DeezerCache {
    /** The body stored for [key], or `null` when there is none or it expired. */
    public suspend fun get(key: String): String?

    /** Stores [value] for [key]. */
    public suspend fun put(key: String, value: String)

    /** Removes the entry for [key]. */
    public suspend fun remove(key: String)

    /** Removes every entry. */
    public suspend fun clear()

    public companion object {
        /** An in-memory LRU cache of up to [maxEntries] responses that live for [ttl]. */
        public fun memory(
            maxEntries: Int = MemoryCache.DEFAULT_MAX_ENTRIES,
            ttl: Duration = MemoryCache.DEFAULT_TTL,
            clock: () -> Long = System::currentTimeMillis,
        ): MemoryCache = MemoryCache(maxEntries, ttl, clock)

        /** A cache of files in [directory], trimmed to [maxBytes], with entries that live for [ttl]. */
        public fun disk(
            directory: File,
            maxBytes: Long = DiskCache.DEFAULT_MAX_BYTES,
            ttl: Duration = 1.days,
            clock: () -> Long = System::currentTimeMillis,
        ): DiskCache = DiskCache(directory, maxBytes, ttl, clock)
    }
}

/** A thread safe, least recently used, in-memory [DeezerCache] whose entries live for [ttl]. */
public class MemoryCache(
    public val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    public val ttl: Duration = DEFAULT_TTL,
    private val clock: () -> Long = System::currentTimeMillis,
) : DeezerCache {

    init {
        require(maxEntries > 0) { "maxEntries must be positive, was $maxEntries" }
        require(ttl.isPositive()) { "ttl must be positive, was $ttl" }
    }

    private class Entry(val value: String, val storedAt: Long)

    private val lock = Any()
    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > maxEntries
    }

    /** The number of stored entries, expired ones included until they are read. */
    public val size: Int get() = synchronized(lock) { entries.size }

    override suspend fun get(key: String): String? = synchronized(lock) {
        val entry = entries[key] ?: return@synchronized null
        if (isExpired(entry.storedAt)) {
            entries.remove(key)
            null
        } else {
            entry.value
        }
    }

    override suspend fun put(key: String, value: String) {
        synchronized(lock) { entries[key] = Entry(value, clock()) }
    }

    override suspend fun remove(key: String) {
        synchronized(lock) { entries.remove(key) }
    }

    override suspend fun clear() {
        synchronized(lock) { entries.clear() }
    }

    private fun isExpired(storedAt: Long): Boolean =
        !ttl.isInfinite() && clock() - storedAt >= ttl.inWholeMilliseconds

    public companion object {
        /** Responses kept at most. */
        public const val DEFAULT_MAX_ENTRIES: Int = 256

        /** How long a response is reused: 10 minutes. */
        public val DEFAULT_TTL: Duration = 10.minutes
    }
}

/**
 * Looks in this cache first and then in [other]; a hit in [other] is copied into this one. Writes go to both.
 * `memory + disk` keeps recent responses in memory and the rest on disk.
 */
public operator fun DeezerCache.plus(other: DeezerCache): DeezerCache {
    val levels = buildList<DeezerCache> {
        if (this@plus is TieredCache) addAll(this@plus.levels) else add(this@plus)
        if (other is TieredCache) addAll(other.levels) else add(other)
    }
    return TieredCache(levels)
}

/** Several caches checked in order. Create one with `memory + disk`. */
public class TieredCache(public val levels: List<DeezerCache>) : DeezerCache {
    override suspend fun get(key: String): String? {
        for ((index, level) in levels.withIndex()) {
            val value = level.get(key) ?: continue
            for (upper in 0 until index) levels[upper].put(key, value)
            return value
        }
        return null
    }

    override suspend fun put(key: String, value: String) {
        for (level in levels) level.put(key, value)
    }

    override suspend fun remove(key: String) {
        for (level in levels) level.remove(key)
    }

    override suspend fun clear() {
        for (level in levels) level.clear()
    }
}
