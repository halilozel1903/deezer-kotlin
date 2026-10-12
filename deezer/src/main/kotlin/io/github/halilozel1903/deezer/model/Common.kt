package io.github.halilozel1903.deezer.model

import io.github.halilozel1903.deezer.DeezerImages
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One page of a Deezer list: `{"data": [...], "total": 120, "next": "https://api.deezer.com/...&index=25"}`.
 *
 * Deezer pages with `index` (the offset of the first item) and `limit`. [next] and [prev] are full urls, present
 * only when there is a page after or before this one. [total] is missing on a few endpoints.
 */
@Serializable
public data class DeezerPage<T>(
    public val data: List<T> = emptyList(),
    public val total: Int? = null,
    public val next: String? = null,
    public val prev: String? = null,
) {
    /** `true` when Deezer announced another page. */
    public val hasNext: Boolean get() = next != null

    /** The `index` of the next page, read from [next]; `null` on the last page. */
    public val nextIndex: Int? get() = next?.let { queryParameter(it, "index")?.toIntOrNull() ?: 0 }

    /** The `index` of the previous page, read from [prev]; `null` on the first page. */
    public val prevIndex: Int? get() = prev?.let { queryParameter(it, "index")?.toIntOrNull() ?: 0 }
}

internal fun queryParameter(url: String, name: String): String? =
    url.substringAfter('?', missingDelimiterValue = "")
        .split('&')
        .firstOrNull { it.substringBefore('=') == name }
        ?.substringAfter('=', missingDelimiterValue = "")

/** A Deezer user, as the creator of a playlist (`creator` or `user`). */
@Serializable
public data class User(
    public val id: Long,
    public val name: String = "",
    public val link: String? = null,
    public val picture: String? = null,
    @SerialName("picture_small") public val pictureSmall: String? = null,
    @SerialName("picture_medium") public val pictureMedium: String? = null,
    @SerialName("picture_big") public val pictureBig: String? = null,
    @SerialName("picture_xl") public val pictureXl: String? = null,
    public val tracklist: String? = null,
    public val type: String = "user",
)

/**
 * The error object Deezer sends instead of data, usually with HTTP status 200:
 * `{"error": {"type": "DataException", "message": "no data", "code": 800}}`. See [DeezerErrorCode].
 */
@Serializable
public data class DeezerError(
    public val type: String = "",
    public val message: String = "",
    public val code: Int = 0,
)

@Serializable
internal class DeezerErrorEnvelope(val error: DeezerError? = null)

/** The `code` values of [DeezerError], from the API documentation. */
public object DeezerErrorCode {
    /** Too many requests: Deezer allows 50 requests per 5 seconds. The client waits and retries. */
    public const val QUOTA: Int = 4

    /** `limit` is too high. */
    public const val ITEMS_LIMIT_EXCEEDED: Int = 100

    /** The request needs a permission the client does not have. */
    public const val PERMISSION: Int = 200

    /** The access token is invalid or expired. */
    public const val TOKEN_INVALID: Int = 300

    /** A parameter is wrong. */
    public const val PARAMETER: Int = 500

    /** A required parameter is missing. */
    public const val PARAMETER_MISSING: Int = 501

    /** The search query is not valid. */
    public const val QUERY_INVALID: Int = 600

    /** Deezer is busy; the client retries. */
    public const val SERVICE_BUSY: Int = 700

    /** There is nothing with that id. */
    public const val DATA_NOT_FOUND: Int = 800

    /** Individual accounts may not use this. */
    public const val INDIVIDUAL_ACCOUNT_NOT_ALLOWED: Int = 901
}

/** Something with artwork urls in four sizes and, usually, the md5 hash those urls are built from. */
public interface HasPicture {
    public val pictureSmall: String?
    public val pictureMedium: String?
    public val pictureBig: String?
    public val pictureXl: String?

    /** The best url for an image shown [sizePx] pixels wide, falling back to any size there is. */
    public fun pictureFor(sizePx: Int): String? {
        val ordered = when {
            sizePx <= DeezerImages.SMALL -> listOf(pictureSmall, pictureMedium, pictureBig, pictureXl)
            sizePx <= DeezerImages.MEDIUM -> listOf(pictureMedium, pictureBig, pictureXl, pictureSmall)
            sizePx <= DeezerImages.BIG -> listOf(pictureBig, pictureXl, pictureMedium, pictureSmall)
            else -> listOf(pictureXl, pictureBig, pictureMedium, pictureSmall)
        }
        return ordered.firstOrNull { !it.isNullOrBlank() }
    }
}
