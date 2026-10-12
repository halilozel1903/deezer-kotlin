package io.github.halilozel1903.deezer

import io.github.halilozel1903.deezer.model.DeezerError
import io.github.halilozel1903.deezer.model.DeezerErrorCode

/** Every failure of [DeezerClient]. Catch the subclasses you care about, or all of them. */
public sealed class DeezerException(
    /** The requested url. */
    public val url: String,
    /** The error object Deezer sent, when it sent one. */
    public val error: DeezerError?,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Deezer has nothing with that id: error code 800 (`DataException`, "no data") or HTTP 404. */
    public class NotFound(url: String, error: DeezerError? = null) :
        DeezerException(url, error, "Not found: $url" + (error?.message?.let { " ($it)" } ?: ""))

    /**
     * Deezer still answered with error code 4 ("Quota limit exceeded", 50 requests per 5 seconds) after every
     * retry. Slow down; see [DeezerRateLimiter].
     */
    public class QuotaExceeded(url: String, error: DeezerError?) :
        DeezerException(url, error, "Deezer quota exceeded: $url")

    /** Any other Deezer error object, such as code 600 for an invalid search query. */
    public class Api(url: String, error: DeezerError) :
        DeezerException(url, error, "Deezer error ${error.code} (${error.type}): ${error.message}") {
        /** [DeezerError.code]; compare it with [DeezerErrorCode]. */
        public val code: Int get() = error?.code ?: 0
    }

    /** Deezer answered with an unsuccessful HTTP [status] and no error object. */
    public class Http(url: String, public val status: Int) :
        DeezerException(url, null, "HTTP $status for $url")

    /** The request failed before a response arrived (offline, timeout, DNS). */
    public class Network(url: String, cause: Throwable) :
        DeezerException(url, null, "Request failed for $url: ${cause.message ?: cause::class.simpleName}", cause)

    /** The response was not the JSON this library expects. */
    public class Decoding(url: String, cause: Throwable) :
        DeezerException(url, null, "Could not decode the response of $url: ${cause.message}", cause)
}
