package io.github.halilozel1903.deezer

import io.github.halilozel1903.deezer.cache.DeezerCache
import io.github.halilozel1903.deezer.cache.DiskCache
import io.github.halilozel1903.deezer.cache.MemoryCache
import io.github.halilozel1903.deezer.cache.TieredCache
import io.github.halilozel1903.deezer.cache.plus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CacheAndLimiterTest {

    @Test
    fun memoryCacheEvictsLeastRecentlyUsedAndExpires() = runTest {
        var now = 0L
        val cache = MemoryCache(maxEntries = 2, ttl = 10.seconds, clock = { now })
        cache.put("a", "1")
        cache.put("b", "2")
        cache.get("a")
        cache.put("c", "3")
        assertNull(cache.get("b"))
        assertEquals("1", cache.get("a"))
        now = 10_000
        assertNull(cache.get("a"))
        assertEquals(1, cache.size)
        cache.clear()
        assertEquals(0, cache.size)
    }

    @Test
    fun diskCacheStoresExpiresAndTrims() = runTest {
        val dir = Files.createTempDirectory("deezer-cache").toFile()
        var now = 1_000L
        val cache = DiskCache(dir, maxBytes = 400, ttl = 60.seconds, clock = { now })
        cache.put("https://api.deezer.com/chart/0", "{\"tracks\":{}}")
        assertEquals("{\"tracks\":{}}", cache.get("https://api.deezer.com/chart/0"))
        // A new instance on the same folder reads what the first one wrote.
        assertEquals("{\"tracks\":{}}", DiskCache(dir, clock = { now }).get("https://api.deezer.com/chart/0"))
        now += 60_000
        assertNull(cache.get("https://api.deezer.com/chart/0"))
        repeat(10) { cache.put("key-$it", "x".repeat(100)) }
        assertTrue(cache.sizeBytes() <= 400)
        cache.clear()
        assertEquals(0, cache.sizeBytes())
        dir.deleteRecursively()
    }

    @Test
    fun tieredCacheFillsTheUpperLevels() = runTest {
        val memory = DeezerCache.memory()
        val disk = DeezerCache.memory()
        val tiered = memory + disk
        assertIs<TieredCache>(tiered)
        disk.put("k", "v")
        assertEquals("v", tiered.get("k"))
        assertEquals("v", memory.get("k"))
        tiered.remove("k")
        assertNull(disk.get("k"))
        assertEquals(3, (tiered + DeezerCache.memory() as DeezerCache).let { (it as TieredCache).levels.size })
    }

    @Test
    fun limiterAllowsPermitsPerWindow() = runTest {
        val limiter = DeezerRateLimiter(permits = 3, per = 1000.milliseconds, clock = { currentTime })
        repeat(3) { limiter.acquire() }
        assertEquals(0, currentTime)
        limiter.acquire()
        assertEquals(1000, currentTime)
        assertEquals(0, limiter.nextDelayMillis())
    }

    @Test
    fun limiterSpreadsConcurrentRequests() = runTest {
        val limiter = DeezerRateLimiter(permits = 50, per = 5.seconds, clock = { currentTime })
        val times = List(120) { async { limiter.acquire(); currentTime } }.map { it.await() }
        assertEquals(50, times.count { it == 0L })
        assertEquals(50, times.count { it == 5_000L })
        assertEquals(20, times.count { it == 10_000L })
    }

    @Test
    fun pauseHoldsEveryRequest() = runTest {
        val limiter = DeezerRateLimiter(clock = { currentTime })
        limiter.pause(5_000)
        assertEquals(5_000, limiter.nextDelayMillis())
        limiter.acquire()
        assertEquals(5_000, currentTime)
        limiter.pause(1_000)
        limiter.reset()
        assertEquals(0, limiter.nextDelayMillis())
    }

    @Test
    fun invalidSettingsAreRejected() {
        assertFailsWith<IllegalArgumentException> { DeezerRateLimiter(permits = 0) }
        assertFailsWith<IllegalArgumentException> { MemoryCache(maxEntries = 0) }
        assertEquals(50, DeezerRateLimiter.shared.permits)
    }
}
