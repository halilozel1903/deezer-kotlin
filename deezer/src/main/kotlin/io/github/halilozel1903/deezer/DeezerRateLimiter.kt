package io.github.halilozel1903.deezer

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A sliding window rate limiter for coroutines: at most [permits] requests in any window of [per].
 * [acquire] suspends until one more request fits.
 *
 * Deezer allows 50 requests per 5 seconds per IP address, so every [DeezerClient] uses [shared] by default. After a
 * quota error the client calls [pause], which holds back every request that goes through this limiter.
 *
 * @param clock milliseconds from a monotonic clock; tests pass a virtual clock.
 */
public class DeezerRateLimiter(
    public val permits: Int = DEEZER_PERMITS,
    public val per: Duration = DEEZER_WINDOW,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    init {
        require(permits > 0) { "permits must be positive, was $permits" }
        require(per.isPositive() && per.isFinite()) { "per must be a positive, finite duration, was $per" }
    }

    private val mutex = Mutex()
    private val granted = ArrayDeque<Long>()
    private val window = per.inWholeMilliseconds
    private var pausedUntil = Long.MIN_VALUE

    /** Suspends until one more request is allowed, and counts it. */
    public suspend fun acquire() {
        while (true) {
            val wait = mutex.withLock {
                val now = clock()
                val wait = waitMillis(now)
                if (wait <= 0) granted.addLast(now)
                wait
            }
            if (wait <= 0) return
            delay(wait)
        }
    }

    /** Milliseconds until the next request would be allowed; 0 when it is allowed now. Does not count a request. */
    public suspend fun nextDelayMillis(): Long = mutex.withLock { waitMillis(clock()).coerceAtLeast(0) }

    /** Holds back every request for [millis], for example after a quota error. */
    public suspend fun pause(millis: Long) {
        if (millis <= 0) return
        mutex.withLock { pausedUntil = maxOf(pausedUntil, clock() + millis) }
    }

    /** Forgets every counted request and pause. */
    public suspend fun reset() {
        mutex.withLock {
            granted.clear()
            pausedUntil = Long.MIN_VALUE
        }
    }

    private fun waitMillis(now: Long): Long {
        while (granted.isNotEmpty() && now - granted.first() >= window) granted.removeFirst()
        var wait = if (pausedUntil > now) pausedUntil - now else 0L
        if (granted.size >= permits) {
            // The request that has to leave the window before another one fits.
            val blocker = granted[granted.size - permits]
            wait = maxOf(wait, blocker + window - now)
        }
        return wait
    }

    public companion object {
        /** Deezer's quota: 50 requests ... */
        public const val DEEZER_PERMITS: Int = 50

        /** ... per 5 seconds. */
        public val DEEZER_WINDOW: Duration = 5.seconds

        /** The limiter every client uses unless configured otherwise, so all clients in a process share the quota. */
        public val shared: DeezerRateLimiter = DeezerRateLimiter()
    }
}
