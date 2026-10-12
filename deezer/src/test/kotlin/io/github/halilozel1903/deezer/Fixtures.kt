package io.github.halilozel1903.deezer

import io.github.halilozel1903.deezer.cache.DeezerCache
import io.github.halilozel1903.deezer.cache.MemoryCache
import io.github.halilozel1903.deezer.transport.FixtureTransport
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.concurrent.CopyOnWriteArrayList

/** Reads `src/test/resources/fixtures/<path>`. */
internal fun fixture(path: String): String? =
    Fixtures::class.java.getResource("/fixtures/$path")?.readText()

internal object Fixtures {
    const val MIRA_VALE = 5021347L
    const val MIDNIGHT_FERRY = 9002317L
    const val DAYLIGHT_HOURS = 612834751L
    const val NIGHT_DRIVE_ALBUM = 619004311L
    const val DAYLIGHT = 3401220310L
    const val LATE_NIGHT_DRIVE = 1479458365L
    const val TOP_WORLDWIDE = 1313621735L
    const val NIGHT_DRIVE_RADIO = 38305L
    const val POP = 132L
}

internal val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8")

internal fun quota(): String = """{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}"""

/**
 * A client on a Ktor [MockEngine] that serves the JSON fixtures like Deezer: HTTP 200 with the data, or HTTP 200 with
 * Deezer's "no data" error object for anything else. [override] answers a request itself when it returns non-null.
 */
internal class MockDeezer(
    cache: DeezerCache? = MemoryCache(),
    rateLimiter: DeezerRateLimiter? = null,
    retries: Int = 0,
    quotaRetries: Int = 3,
    private val override: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?)? = null,
) : AutoCloseable {
    val requests: MutableList<HttpRequestData> = CopyOnWriteArrayList()

    val engine = MockEngine { request ->
        requests += request
        override?.invoke(this, request) ?: run {
            val body = FixtureTransport.pathFor(request.url.toString())?.let { fixture(it) } ?: FixtureTransport.NO_DATA
            respond(body, HttpStatusCode.OK, jsonHeaders)
        }
    }

    val client = DeezerClient {
        engine = this@MockDeezer.engine
        this.cache = cache
        this.rateLimiter = rateLimiter
        this.retries = retries
        this.quotaRetries = quotaRetries
        retryDelayMillis = 10
        quotaRetryDelayMillis = 5_000
    }

    val urls: List<String> get() = requests.map { it.url.toString() }

    override fun close() {
        client.close()
        engine.close()
    }
}
