package io.github.halilozel1903.deezer

import io.github.halilozel1903.deezer.model.DeezerErrorCode
import io.github.halilozel1903.deezer.transport.DeezerHttpResponse
import io.github.halilozel1903.deezer.transport.FixtureTransport
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ErrorsAndRetriesTest {

    @Test
    fun quotaErrorWaitsAndRetries() = runTest {
        val calls = AtomicInteger()
        MockDeezer(override = { if (calls.getAndIncrement() < 2) respond(quota(), HttpStatusCode.OK, jsonHeaders) else null }).use { api ->
            val start = currentTime
            val genres = api.client.genres()
            assertEquals("All", genres.first().name)
            assertEquals(3, api.requests.size)
            // Two quota errors: two pauses of one quota window each.
            assertEquals(10_000, currentTime - start)
        }
    }

    @Test
    fun quotaErrorPausesTheSharedLimiter() = runTest {
        val limiter = DeezerRateLimiter(clock = { currentTime })
        val calls = AtomicInteger()
        MockDeezer(rateLimiter = limiter, override = { if (calls.getAndIncrement() == 0) respond(quota(), HttpStatusCode.OK, jsonHeaders) else null }).use { api ->
            val start = currentTime
            api.client.genres()
            assertEquals(5_000, currentTime - start)
            assertEquals(2, api.requests.size)
        }
    }

    @Test
    fun quotaErrorAfterEveryRetryIsQuotaExceeded() = runTest {
        MockDeezer(quotaRetries = 2, override = { respond(quota(), HttpStatusCode.OK, jsonHeaders) }).use { api ->
            val error = assertFailsWith<DeezerException.QuotaExceeded> { api.client.chart() }
            assertEquals(DeezerErrorCode.QUOTA, error.error?.code)
            assertEquals("Quota limit exceeded", error.error?.message)
            assertEquals(3, api.requests.size)
        }
    }

    @Test
    fun http429CountsAsQuota() = runTest {
        val calls = AtomicInteger()
        MockDeezer(override = { if (calls.getAndIncrement() == 0) respond("Too many requests", HttpStatusCode.TooManyRequests) else null }).use { api ->
            assertEquals("All", api.client.genres().first().name)
            assertEquals(2, api.requests.size)
        }
    }

    @Test
    fun serviceBusyAndServerErrorsAreRetried() = runTest {
        val calls = AtomicInteger()
        MockDeezer(retries = 2, override = {
            when (calls.getAndIncrement()) {
                0 -> respond("""{"error":{"type":"Exception","message":"Service busy","code":700}}""", HttpStatusCode.OK, jsonHeaders)
                1 -> respond("oops", HttpStatusCode.BadGateway)
                else -> null
            }
        }).use { api ->
            assertEquals(6, api.client.topRadios().data.size)
            assertEquals(3, api.requests.size)
        }
    }

    @Test
    fun otherErrorObjectsAreApiErrors() = runTest {
        MockDeezer(override = {
            respond("""{"error":{"type":"InvalidQueryException","message":"Invalid query","code":600}}""", HttpStatusCode.OK, jsonHeaders)
        }).use { api ->
            val error = assertFailsWith<DeezerException.Api> { api.client.searchTracks("x") }
            assertEquals(DeezerErrorCode.QUERY_INVALID, error.code)
            assertTrue(error.message!!.contains("Invalid query"))
        }
    }

    @Test
    fun httpErrorsWithoutErrorObject() = runTest {
        MockDeezer(override = { respond("gone", HttpStatusCode.Forbidden) }).use { api ->
            assertEquals(403, assertFailsWith<DeezerException.Http> { api.client.chart() }.status)
        }
        MockDeezer(override = { respond("", HttpStatusCode.NotFound) }).use { api ->
            assertFailsWith<DeezerException.NotFound> { api.client.chart() }
        }
    }

    @Test
    fun networkErrorsAreRetriedThenWrapped() = runTest {
        MockDeezer(retries = 1, override = { throw IOException("offline") }).use { api ->
            val error = assertFailsWith<DeezerException.Network> { api.client.chart() }
            assertIs<IOException>(error.cause)
            assertEquals(2, api.requests.size)
        }
    }

    @Test
    fun malformedJsonIsADecodingErrorAndNotCached() = runTest {
        val calls = AtomicInteger()
        MockDeezer(override = { if (calls.getAndIncrement() == 0) respond("""{"data": 42}""", HttpStatusCode.OK, jsonHeaders) else null }).use { api ->
            assertFailsWith<DeezerException.Decoding> { api.client.genres() }
            assertEquals("All", api.client.genres().first().name)
            assertEquals(2, api.requests.size)
        }
    }

    @Test
    fun errorObjectsAreNotCached() = runTest {
        val calls = AtomicInteger()
        MockDeezer(override = { if (calls.getAndIncrement() == 0) respond(FixtureTransport.NO_DATA, HttpStatusCode.OK, jsonHeaders) else null }).use { api ->
            assertFailsWith<DeezerException.NotFound> { api.client.genres() }
            api.client.genres()
            api.client.genres()
            assertEquals(2, api.requests.size)
        }
    }

    @Test
    fun responsesAreCachedByUrl() = runTest {
        MockDeezer().use { api ->
            api.client.chart()
            api.client.chart()
            api.client.chartTracks()
            assertEquals(2, api.requests.size)
            api.client.clearCache()
            api.client.chart()
            assertEquals(3, api.requests.size)
        }
        MockDeezer(cache = null).use { api ->
            api.client.chart()
            api.client.chart()
            assertEquals(2, api.requests.size)
        }
    }

    @Test
    fun identicalRequestsShareOneCall() = runTest {
        val transportCalls = AtomicInteger()
        val fixtures = FixtureTransport { fixture(it) }
        val client = DeezerClient {
            rateLimiter = null
            cache = null
            transport = io.github.halilozel1903.deezer.transport.DeezerTransport { url ->
                transportCalls.incrementAndGet()
                delay(100)
                fixtures.get(url)
            }
        }
        val charts = List(5) { async { client.chart() } }.awaitAll()
        assertEquals(1, transportCalls.get())
        assertTrue(charts.all { it == charts.first() })
        client.close()
    }

    @Test
    fun transportAnswersAreUsedAsTheyAre() = runTest {
        val client = DeezerClient {
            rateLimiter = null
            transport = io.github.halilozel1903.deezer.transport.DeezerTransport { DeezerHttpResponse(200, fixture("genre.json")!!) }
        }
        assertEquals(9, client.genres().size)
        client.close()
    }
}
