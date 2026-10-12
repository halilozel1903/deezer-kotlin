package io.github.halilozel1903.deezer

import io.github.halilozel1903.deezer.cache.DeezerCache
import io.github.halilozel1903.deezer.model.Album
import io.github.halilozel1903.deezer.model.Artist
import io.github.halilozel1903.deezer.model.Chart
import io.github.halilozel1903.deezer.model.DeezerError
import io.github.halilozel1903.deezer.model.DeezerErrorCode
import io.github.halilozel1903.deezer.model.DeezerErrorEnvelope
import io.github.halilozel1903.deezer.model.DeezerPage
import io.github.halilozel1903.deezer.model.Editorial
import io.github.halilozel1903.deezer.model.Genre
import io.github.halilozel1903.deezer.model.Playlist
import io.github.halilozel1903.deezer.model.Podcast
import io.github.halilozel1903.deezer.model.Radio
import io.github.halilozel1903.deezer.model.RadioGenre
import io.github.halilozel1903.deezer.model.Track
import io.github.halilozel1903.deezer.transport.DeezerHttpResponse
import io.github.halilozel1903.deezer.transport.DeezerTransport
import io.github.halilozel1903.deezer.transport.KtorTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

/**
 * A coroutine client for the public [Deezer API](https://developers.deezer.com/api): everything that needs no
 * login, from charts and search to artists, albums, playlists, genres, radios and editorial picks.
 *
 * ```kotlin
 * val deezer = DeezerClient()
 * val top = deezer.chartTracks(limit = 10).data
 * val hits = deezer.searchTracks(DeezerQuery(artist = "Daft Punk", track = "One More Time")).data
 * val preview = hits.first().previewUrl          // a 30 second MP3
 * ```
 *
 * Every function is main-safe and throws a [DeezerException] on failure; Deezer's error objects
 * (`{"error":{"type","message","code"}}`, sent with HTTP 200) are mapped to [DeezerException.NotFound],
 * [DeezerException.QuotaExceeded] and [DeezerException.Api]. Requests wait for the shared [DeezerRateLimiter]
 * (50 per 5 seconds), quota errors (code 4) are retried after a pause, responses are cached by url (see
 * [DeezerConfig.cache]) and identical requests running at the same time share one network call.
 * Close the client when you no longer need it.
 */
public class DeezerClient(configure: DeezerConfig.() -> Unit = {}) : AutoCloseable {

    private val config = DeezerConfig().apply(configure)

    /** The API root, always ending with a slash. */
    public val baseUrl: String = config.baseUrl.let { if (it.endsWith('/')) it else "$it/" }

    private val transport: DeezerTransport = config.transport ?: KtorTransport(
        httpClient = config.httpClient,
        engine = config.engine,
        userAgent = config.userAgent,
        requestTimeoutMillis = config.requestTimeoutMillis,
    )
    private val cache: DeezerCache? = config.cache
    private val rateLimiter: DeezerRateLimiter? = config.rateLimiter
    private val inFlightLock = Mutex()
    private val inFlight = HashMap<String, CompletableDeferred<String>>()

    // region Charts

    /** `GET /chart/{genreId}`: top tracks, albums, artists, playlists and podcasts. Genre 0 is all genres. */
    public suspend fun chart(genreId: Long = 0): Chart = fetch(url("chart/${checkGenre(genreId)}"), Chart.serializer())

    /** `GET /chart/{genreId}/tracks`. Each track has its chart [Track.position]. */
    public suspend fun chartTracks(genreId: Long = 0, index: Int = 0, limit: Int = DEFAULT_LIMIT): DeezerPage<Track> =
        page("chart/${checkGenre(genreId)}/tracks", Track.serializer(), paging(index, limit))

    /** `GET /chart/{genreId}/albums`. */
    public suspend fun chartAlbums(genreId: Long = 0, index: Int = 0, limit: Int = DEFAULT_LIMIT): DeezerPage<Album> =
        page("chart/${checkGenre(genreId)}/albums", Album.serializer(), paging(index, limit))

    /** `GET /chart/{genreId}/artists`. */
    public suspend fun chartArtists(genreId: Long = 0, index: Int = 0, limit: Int = DEFAULT_LIMIT): DeezerPage<Artist> =
        page("chart/${checkGenre(genreId)}/artists", Artist.serializer(), paging(index, limit))

    /** `GET /chart/{genreId}/playlists`. */
    public suspend fun chartPlaylists(genreId: Long = 0, index: Int = 0, limit: Int = DEFAULT_LIMIT): DeezerPage<Playlist> =
        page("chart/${checkGenre(genreId)}/playlists", Playlist.serializer(), paging(index, limit))

    /** `GET /chart/{genreId}/podcasts`. */
    public suspend fun chartPodcasts(genreId: Long = 0, index: Int = 0, limit: Int = DEFAULT_LIMIT): DeezerPage<Podcast> =
        page("chart/${checkGenre(genreId)}/podcasts", Podcast.serializer(), paging(index, limit))

    /** Every page of `GET /chart/{genreId}/tracks`, requested as the flow is collected. */
    public fun chartTrackPages(genreId: Long = 0, pageSize: Int = DEFAULT_LIMIT, maxPages: Int = Int.MAX_VALUE): Flow<DeezerPage<Track>> =
        pages(pageSize, maxPages = maxPages) { index, limit -> chartTracks(genreId, index, limit) }

    // endregion

    // region Search

    /** `GET /search/track?q=`: tracks matching [query], such as `daft punk` or `artist:"daft punk" track:"one more time"`. */
    public suspend fun searchTracks(
        query: String,
        order: SearchOrder? = null,
        strict: Boolean = false,
        index: Int = 0,
        limit: Int = DEFAULT_LIMIT,
    ): DeezerPage<Track> = page("search/track", Track.serializer(), searchParameters(query, order, strict, index, limit))

    /** `GET /search/track` with an advanced [DeezerQuery]. */
    public suspend fun searchTracks(
        query: DeezerQuery,
        order: SearchOrder? = null,
        strict: Boolean = false,
        index: Int = 0,
        limit: Int = DEFAULT_LIMIT,
    ): DeezerPage<Track> = searchTracks(query.toString(), order, strict, index, limit)

    /** `GET /search/album?q=`. */
    public suspend fun searchAlbums(
        query: String,
        order: SearchOrder? = null,
        strict: Boolean = false,
        index: Int = 0,
        limit: Int = DEFAULT_LIMIT,
    ): DeezerPage<Album> = page("search/album", Album.serializer(), searchParameters(query, order, strict, index, limit))

    /** `GET /search/album` with an advanced [DeezerQuery]. */
    public suspend fun searchAlbums(
        query: DeezerQuery,
        order: SearchOrder? = null,
        strict: Boolean = false,
        index: Int = 0,
        limit: Int = DEFAULT_LIMIT,
    ): DeezerPage<Album> = searchAlbums(query.toString(), order, strict, index, limit)

    /** `GET /search/artist?q=`. */
    public suspend fun searchArtists(
        query: String,
        order: SearchOrder? = null,
        strict: Boolean = false,
        index: Int = 0,
        limit: Int = DEFAULT_LIMIT,
    ): DeezerPage<Artist> = page("search/artist", Artist.serializer(), searchParameters(query, order, strict, index, limit))

    /** `GET /search/artist` with an advanced [DeezerQuery]. */
    public suspend fun searchArtists(
        query: DeezerQuery,
        order: SearchOrder? = null,
        strict: Boolean = false,
        index: Int = 0,
        limit: Int = DEFAULT_LIMIT,
    ): DeezerPage<Artist> = searchArtists(query.toString(), order, strict, index, limit)

    /** `GET /search/playlist?q=`. */
    public suspend fun searchPlaylists(
        query: String,
        order: SearchOrder? = null,
        strict: Boolean = false,
        index: Int = 0,
        limit: Int = DEFAULT_LIMIT,
    ): DeezerPage<Playlist> = page("search/playlist", Playlist.serializer(), searchParameters(query, order, strict, index, limit))

    /** `GET /search/playlist` with an advanced [DeezerQuery]. */
    public suspend fun searchPlaylists(
        query: DeezerQuery,
        order: SearchOrder? = null,
        strict: Boolean = false,
        index: Int = 0,
        limit: Int = DEFAULT_LIMIT,
    ): DeezerPage<Playlist> = searchPlaylists(query.toString(), order, strict, index, limit)

    /** Every page of track results for [query], requested as the flow is collected. */
    public fun searchTrackPages(
        query: DeezerQuery,
        order: SearchOrder? = null,
        pageSize: Int = DEFAULT_LIMIT,
        maxPages: Int = Int.MAX_VALUE,
    ): Flow<DeezerPage<Track>> = pages(pageSize, maxPages = maxPages) { index, limit ->
        searchTracks(query, order, index = index, limit = limit)
    }

    // endregion

    // region Artists, albums, tracks, playlists

    /** `GET /artist/{id}`. */
    public suspend fun artist(id: Long): Artist = fetch(url("artist/${checkId(id)}"), Artist.serializer())

    /** `GET /artist/{id}/top`: the artist's most popular tracks. */
    public suspend fun artistTopTracks(id: Long, index: Int = 0, limit: Int = 5): DeezerPage<Track> =
        page("artist/${checkId(id)}/top", Track.serializer(), paging(index, limit))

    /** `GET /artist/{id}/albums`, newest first. */
    public suspend fun artistAlbums(id: Long, index: Int = 0, limit: Int = DEFAULT_LIMIT): DeezerPage<Album> =
        page("artist/${checkId(id)}/albums", Album.serializer(), paging(index, limit))

    /** Every page of `GET /artist/{id}/albums`. */
    public fun artistAlbumPages(id: Long, pageSize: Int = DEFAULT_LIMIT, maxPages: Int = Int.MAX_VALUE): Flow<DeezerPage<Album>> =
        pages(pageSize, maxPages = maxPages) { index, limit -> artistAlbums(id, index, limit) }

    /** `GET /artist/{id}/related`: similar artists. */
    public suspend fun relatedArtists(id: Long, index: Int = 0, limit: Int = 20): DeezerPage<Artist> =
        page("artist/${checkId(id)}/related", Artist.serializer(), paging(index, limit))

    /** `GET /artist/{id}/radio`: a mix of tracks by the artist and similar artists. */
    public suspend fun artistRadio(id: Long, index: Int = 0, limit: Int = DEFAULT_LIMIT): DeezerPage<Track> =
        page("artist/${checkId(id)}/radio", Track.serializer(), paging(index, limit))

    /** `GET /album/{id}`, with its first tracks in [Album.tracks]. */
    public suspend fun album(id: Long): Album = fetch(url("album/${checkId(id)}"), Album.serializer())

    /** `GET /album/{id}/tracks`. */
    public suspend fun albumTracks(id: Long, index: Int = 0, limit: Int = DEFAULT_LIMIT): DeezerPage<Track> =
        page("album/${checkId(id)}/tracks", Track.serializer(), paging(index, limit))

    /** Every page of `GET /album/{id}/tracks`. */
    public fun albumTrackPages(id: Long, pageSize: Int = DEFAULT_LIMIT, maxPages: Int = Int.MAX_VALUE): Flow<DeezerPage<Track>> =
        pages(pageSize, maxPages = maxPages) { index, limit -> albumTracks(id, index, limit) }

    /** `GET /track/{id}`. Load a track again for a fresh [Track.preview] url: preview urls expire. */
    public suspend fun track(id: Long): Track = fetch(url("track/${checkId(id)}"), Track.serializer())

    /** `GET /playlist/{id}`, with its first tracks in [Playlist.tracks]. */
    public suspend fun playlist(id: Long): Playlist = fetch(url("playlist/${checkId(id)}"), Playlist.serializer())

    /** `GET /playlist/{id}/tracks`. */
    public suspend fun playlistTracks(id: Long, index: Int = 0, limit: Int = DEFAULT_LIMIT): DeezerPage<Track> =
        page("playlist/${checkId(id)}/tracks", Track.serializer(), paging(index, limit))

    /** Every page of `GET /playlist/{id}/tracks`. */
    public fun playlistTrackPages(id: Long, pageSize: Int = DEFAULT_LIMIT, maxPages: Int = Int.MAX_VALUE): Flow<DeezerPage<Track>> =
        pages(pageSize, maxPages = maxPages) { index, limit -> playlistTracks(id, index, limit) }

    // endregion

    // region Genres, radios, editorial

    /** `GET /genre`: every genre, "All" (id 0) first. */
    public suspend fun genres(): List<Genre> = page("genre", Genre.serializer(), emptyList()).data

    /** `GET /genre/{id}`. */
    public suspend fun genre(id: Long): Genre = fetch(url("genre/${checkGenre(id)}"), Genre.serializer())

    /** `GET /genre/{id}/artists`: the genre's popular artists. */
    public suspend fun genreArtists(id: Long): List<Artist> =
        page("genre/${checkGenre(id)}/artists", Artist.serializer(), emptyList()).data

    /** `GET /genre/{id}/radios`. */
    public suspend fun genreRadios(id: Long): List<Radio> =
        page("genre/${checkGenre(id)}/radios", Radio.serializer(), emptyList()).data

    /** `GET /radio`: every radio. */
    public suspend fun radios(): List<Radio> = page("radio", Radio.serializer(), emptyList()).data

    /** `GET /radio/{id}`. */
    public suspend fun radio(id: Long): Radio = fetch(url("radio/${checkId(id)}"), Radio.serializer())

    /** `GET /radio/{id}/tracks`: the radio's current tracks. */
    public suspend fun radioTracks(id: Long, index: Int = 0, limit: Int = DEFAULT_LIMIT): DeezerPage<Track> =
        page("radio/${checkId(id)}/tracks", Track.serializer(), paging(index, limit))

    /** `GET /radio/genres`: radios grouped by genre. */
    public suspend fun radioGenres(): List<RadioGenre> = page("radio/genres", RadioGenre.serializer(), emptyList()).data

    /** `GET /radio/top`: the most popular radios. */
    public suspend fun topRadios(index: Int = 0, limit: Int = DEFAULT_LIMIT): DeezerPage<Radio> =
        page("radio/top", Radio.serializer(), paging(index, limit))

    /** `GET /editorial`: every editorial page. */
    public suspend fun editorials(): List<Editorial> = page("editorial", Editorial.serializer(), emptyList()).data

    /** `GET /editorial/{id}`. */
    public suspend fun editorial(id: Long): Editorial = fetch(url("editorial/${checkGenre(id)}"), Editorial.serializer())

    /** `GET /editorial/{id}/charts`: the charts of an editorial page. */
    public suspend fun editorialCharts(id: Long): Chart = fetch(url("editorial/${checkGenre(id)}/charts"), Chart.serializer())

    /** `GET /editorial/{id}/releases`: new releases. */
    public suspend fun editorialReleases(id: Long, index: Int = 0, limit: Int = DEFAULT_LIMIT): DeezerPage<Album> =
        page("editorial/${checkGenre(id)}/releases", Album.serializer(), paging(index, limit))

    /** `GET /editorial/{id}/selection`: albums picked by Deezer's editors. */
    public suspend fun editorialSelection(id: Long): List<Album> =
        page("editorial/${checkGenre(id)}/selection", Album.serializer(), emptyList()).data

    // endregion

    // region Paging and raw access

    /**
     * Pages of any list, requested lazily as the flow is collected. [load] is called with each page's `index` and
     * `limit`; the flow follows Deezer's `next` links (and `total` when `next` is missing) and stops at the last
     * page, at an empty page or after [maxPages].
     *
     * ```kotlin
     * deezer.pages(pageSize = 50) { index, limit -> playlistTracks(playlistId, index, limit) }.items().toList()
     * ```
     */
    public fun <T> pages(
        pageSize: Int = DEFAULT_LIMIT,
        startIndex: Int = 0,
        maxPages: Int = Int.MAX_VALUE,
        load: suspend DeezerClient.(index: Int, limit: Int) -> DeezerPage<T>,
    ): Flow<DeezerPage<T>> {
        checkLimit(pageSize)
        checkIndex(startIndex)
        require(maxPages > 0) { "maxPages must be positive, was $maxPages" }
        return flow {
            var index = startIndex
            var loaded = 0
            while (loaded < maxPages) {
                val page = load(this@DeezerClient, index, pageSize)
                emit(page)
                loaded++
                if (page.data.isEmpty()) break
                val total = page.total
                val following = page.nextIndex
                    ?: (index + page.data.size).takeIf { total != null && it < total }
                    ?: break
                index = if (following > index) following else index + page.data.size
            }
        }
    }

    /** Loads any Deezer API url (such as a `next` or `tracklist` link) and decodes it with [deserializer]. */
    public suspend fun <T> fetch(url: String, deserializer: DeserializationStrategy<T>): T {
        val body = load(url)
        return try {
            json.decodeFromString(deserializer, body)
        } catch (e: SerializationException) {
            cache?.remove(url)
            throw DeezerException.Decoding(url, e)
        } catch (e: IllegalArgumentException) {
            cache?.remove(url)
            throw DeezerException.Decoding(url, e)
        }
    }

    /** Loads the page a [DeezerPage.next] link points to, or `null` on the last page. */
    public suspend fun <T> nextPage(page: DeezerPage<T>, itemSerializer: KSerializer<T>): DeezerPage<T>? =
        page.next?.let { fetch(it, DeezerPage.serializer(itemSerializer)) }

    /** Removes every cached response. */
    public suspend fun clearCache() {
        cache?.clear()
    }

    /** Releases the HTTP client this instance created. */
    override fun close() {
        (transport as? AutoCloseable)?.close()
    }

    // endregion

    private suspend fun <T> page(path: String, serializer: KSerializer<T>, parameters: List<Pair<String, String>>): DeezerPage<T> =
        fetch(url(path, parameters), DeezerPage.serializer(serializer))

    private fun url(path: String, parameters: List<Pair<String, String>> = emptyList()): String =
        if (parameters.isEmpty()) {
            "$baseUrl$path"
        } else {
            "$baseUrl$path?" + parameters.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
        }

    private fun searchParameters(query: String, order: SearchOrder?, strict: Boolean, index: Int, limit: Int) =
        buildList<Pair<String, String>> {
            val q = query.trim()
            require(q.isNotEmpty()) { "The search query is empty" }
            add("q" to q)
            order?.let { add("order" to it.apiValue) }
            if (strict) add("strict" to "on")
            addAll(paging(index, limit))
        }

    private fun paging(index: Int, limit: Int): List<Pair<String, String>> =
        listOf("index" to checkIndex(index).toString(), "limit" to checkLimit(limit).toString())

    private suspend fun load(url: String): String {
        cache?.get(url)?.let { return it }
        return loadShared(url)
    }

    /** Joins a request for the same url that is already running, or starts one. */
    private suspend fun loadShared(url: String): String {
        var owner = false
        val deferred = inFlightLock.withLock {
            inFlight[url] ?: CompletableDeferred<String>().also {
                inFlight[url] = it
                owner = true
            }
        }
        if (!owner) {
            return try {
                deferred.await()
            } catch (e: CancellationException) {
                // The coroutine that made the request was cancelled, not this one: try again.
                currentCoroutineContext().ensureActive()
                loadShared(url)
            }
        }
        val result = runCatching { request(url) }
        withContext(NonCancellable) { inFlightLock.withLock { inFlight.remove(url) } }
        result.fold(
            onSuccess = { deferred.complete(it) },
            onFailure = { deferred.completeExceptionally(it) },
        )
        return result.getOrThrow()
    }

    /**
     * One request with rate limiting and retries. Deezer reports most errors as an error object with HTTP 200:
     * code 4 (quota) waits a quota window and retries, code 700 (busy), network errors and 5xx back off and retry,
     * code 800 is [DeezerException.NotFound].
     */
    private suspend fun request(url: String): String {
        var failures = 0
        var quotaHits = 0
        while (true) {
            rateLimiter?.acquire()
            val response: DeezerHttpResponse = try {
                transport.get(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DeezerException) {
                throw e
            } catch (e: Exception) {
                if (failures < config.retries) {
                    delay(backoff(failures++))
                    continue
                }
                throw DeezerException.Network(url, e)
            }
            val error = errorOf(response.body)
            val status = response.status
            when {
                error != null && error.code == DeezerErrorCode.QUOTA || error == null && status == 429 -> {
                    if (quotaHits >= config.quotaRetries) throw DeezerException.QuotaExceeded(url, error)
                    quotaHits++
                    val wait = config.quotaRetryDelayMillis
                    if (rateLimiter != null) rateLimiter.pause(wait) else delay(wait)
                }
                error != null && error.code == DeezerErrorCode.SERVICE_BUSY -> {
                    if (failures >= config.retries) throw DeezerException.Api(url, error)
                    delay(backoff(failures++))
                }
                error != null && error.code == DeezerErrorCode.DATA_NOT_FOUND -> throw DeezerException.NotFound(url, error)
                error != null -> throw DeezerException.Api(url, error)
                status == 404 -> throw DeezerException.NotFound(url)
                status in 200..299 -> {
                    cache?.put(url, response.body)
                    return response.body
                }
                status >= 500 && failures < config.retries -> delay(backoff(failures++))
                else -> throw DeezerException.Http(url, status)
            }
        }
    }

    private fun backoff(attempt: Int): Long {
        val delay = config.retryDelayMillis shl attempt.coerceIn(0, 20)
        return delay.coerceIn(0, config.maxRetryDelayMillis)
    }

    public companion object {
        /** `https://api.deezer.com/`. */
        public const val DEFAULT_BASE_URL: String = "https://api.deezer.com/"

        /** The page size Deezer uses when none is given. */
        public const val DEFAULT_LIMIT: Int = 25

        internal val json: Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
            isLenient = true
        }

        private val ERROR_START = Regex("""^\s*\{\s*"error"\s*:\s*\{""")

        /** The error object in [body], or `null` when the body is data. */
        internal fun errorOf(body: String): DeezerError? {
            if (!ERROR_START.containsMatchIn(body.take(64))) return null
            return try {
                json.decodeFromString(DeezerErrorEnvelope.serializer(), body).error
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

private fun checkId(id: Long): Long {
    require(id > 0) { "Deezer ids start at 1, was $id" }
    return id
}

/** Genre and editorial ids start at 0 ("All"). */
private fun checkGenre(id: Long): Long {
    require(id >= 0) { "Genre ids start at 0, was $id" }
    return id
}

private fun checkIndex(index: Int): Int {
    require(index >= 0) { "index must not be negative, was $index" }
    return index
}

private fun checkLimit(limit: Int): Int {
    require(limit > 0) { "limit must be positive, was $limit" }
    return limit
}

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
