package io.github.halilozel1903.deezer.transport

/** A raw HTTP answer: the status code and the body. */
public data class DeezerHttpResponse(
    public val status: Int,
    public val body: String,
)

/**
 * Fetches a Deezer url. The client uses a Ktor based transport by default; supply your own through
 * `DeezerConfig.transport` to serve responses from files ([FixtureTransport]) or another HTTP stack.
 *
 * Return every answer as it is (the client reads Deezer's error objects and retries); throw only when no answer
 * arrived.
 */
public fun interface DeezerTransport {
    public suspend fun get(url: String): DeezerHttpResponse
}
