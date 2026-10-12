package io.github.halilozel1903.deezer

import io.github.halilozel1903.deezer.cache.DeezerCache
import io.github.halilozel1903.deezer.cache.MemoryCache
import io.github.halilozel1903.deezer.transport.DeezerTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine

/** Settings for [DeezerClient], set in its builder block. */
public class DeezerConfig internal constructor() {
    /** The API root. */
    public var baseUrl: String = DeezerClient.DEFAULT_BASE_URL

    /**
     * The Ktor engine. `null` uses CIO. Pass `OkHttp.create()` (add `io.ktor:ktor-client-okhttp`) to share your
     * app's OkHttp stack, or a `MockEngine` in tests. The client does not close an engine you pass.
     */
    public var engine: HttpClientEngine? = null

    /** A ready Ktor client to use as is, instead of [engine]. [requestTimeoutMillis] then does not apply. */
    public var httpClient: HttpClient? = null

    /** Replaces HTTP entirely, for example with a [io.github.halilozel1903.deezer.transport.FixtureTransport]. */
    public var transport: DeezerTransport? = null

    /**
     * Spaces out requests to stay inside Deezer's quota of 50 requests per 5 seconds. The quota is per IP address,
     * so all clients share [DeezerRateLimiter.shared] by default. `null` turns limiting off.
     */
    public var rateLimiter: DeezerRateLimiter? = DeezerRateLimiter.shared

    /** Where responses are cached. An in-memory LRU cache by default; `null` turns caching off. */
    public var cache: DeezerCache? = MemoryCache()

    /** Sent with every request. */
    public var userAgent: String = "deezer-kotlin/1.0.0 (+https://github.com/halilozel1903/deezer-kotlin)"

    /** Timeout of one request, in milliseconds. */
    public var requestTimeoutMillis: Long = 30_000

    /** Retries on network errors, 5xx responses and error code 700 (service busy), with exponential backoff. */
    public var retries: Int = 2

    /** Retries on error code 4 (quota exceeded). Each one waits [quotaRetryDelayMillis]. */
    public var quotaRetries: Int = 3

    /** How long to wait after a quota error: one quota window (5 seconds) by default. */
    public var quotaRetryDelayMillis: Long = 5_000

    /** The first backoff delay in milliseconds; it doubles with each retry, up to [maxRetryDelayMillis]. */
    public var retryDelayMillis: Long = 1_000

    /** The longest backoff delay in milliseconds. */
    public var maxRetryDelayMillis: Long = 16_000
}
