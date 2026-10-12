package io.github.halilozel1903.deezer.transport

import io.github.halilozel1903.deezer.DeezerClient
import java.net.URLDecoder

/**
 * Serves Deezer responses from local JSON files, for unit tests and offline development. [read] gets a relative
 * path and returns the file's text, or `null` when there is no such file; a missing file is answered like Deezer
 * answers an unknown id (`{"error":{"type":"DataException","message":"no data","code":800}}`).
 *
 * Urls map to paths like this (`limit` and `order` are ignored):
 *
 * | Url | Path |
 * | --- | --- |
 * | `/chart/0` | `chart/0.json` |
 * | `/chart/0/tracks?index=0&limit=10` | `chart/0/tracks.json` |
 * | `/chart/0/tracks?index=10&limit=10` | `chart/0/tracks-index-10.json` |
 * | `/artist/27/top?limit=5` | `artist/27/top.json` |
 * | `/search/track?q=artist:"Daft Punk"` | `search/track/artist-daft-punk.json` |
 *
 * ```kotlin
 * val client = DeezerClient {
 *     transport = FixtureTransport { path -> javaClass.getResource("/fixtures/$path")?.readText() }
 * }
 * ```
 */
public class FixtureTransport(
    private val baseUrl: String = DeezerClient.DEFAULT_BASE_URL,
    private val read: (path: String) -> String?,
) : DeezerTransport {

    private val lock = Any()
    private val recorded = mutableListOf<String>()

    /** Every url requested so far, in order. */
    public val requests: List<String> get() = synchronized(lock) { recorded.toList() }

    override suspend fun get(url: String): DeezerHttpResponse {
        synchronized(lock) { recorded += url }
        val body = pathFor(url, baseUrl)?.let(read) ?: NO_DATA
        return DeezerHttpResponse(200, body)
    }

    public companion object {
        /** What Deezer answers for an id that does not exist. */
        public const val NO_DATA: String = """{"error":{"type":"DataException","message":"no data","code":800}}"""

        /** The fixture path for [url], or `null` when the url is not a Deezer API url. */
        public fun pathFor(url: String, baseUrl: String = DeezerClient.DEFAULT_BASE_URL): String? {
            val base = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"
            val relative = when {
                url.startsWith(base) -> url.removePrefix(base)
                "api.deezer.com/" in url -> url.substringAfter("api.deezer.com/")
                else -> return null
            }
            val path = relative.substringBefore('?').trim('/')
            if (path.isEmpty()) return null
            val params = relative.substringAfter('?', missingDelimiterValue = "")
                .split('&')
                .filter { it.isNotEmpty() }
                .associate { decode(it.substringBefore('=')) to decode(it.substringAfter('=', "")) }
            return buildString {
                append(path)
                params["q"]?.let { slug(it) }?.takeIf { it.isNotEmpty() }?.let { append('/').append(it) }
                val index = params["index"]?.toIntOrNull() ?: 0
                if (index > 0) append("-index-").append(index)
                append(".json")
            }
        }

        /** `artist:"Daft Punk" Around` becomes `artist-daft-punk-around`. */
        public fun slug(text: String): String =
            text.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

        private fun decode(value: String): String = try {
            URLDecoder.decode(value, "UTF-8")
        } catch (e: IllegalArgumentException) {
            value
        }
    }
}
