package io.github.halilozel1903.deezer

/**
 * Builds Deezer image urls from an md5 hash (`md5_image` on tracks, albums, playlists and radios), so a list can
 * show artwork in any size without another request.
 *
 * ```kotlin
 * DeezerImages.url(DeezerImages.Type.Cover, track.md5Image!!, 500)
 * // https://cdn-images.dzcdn.net/images/cover/<md5>/500x500-000000-80-0-0.jpg
 * ```
 */
public object DeezerImages {
    /** 56 px, the size of `*_small` urls. */
    public const val SMALL: Int = 56

    /** 250 px, the size of `*_medium` urls. */
    public const val MEDIUM: Int = 250

    /** 500 px, the size of `*_big` urls. */
    public const val BIG: Int = 500

    /** 1000 px, the size of `*_xl` urls. */
    public const val XL: Int = 1000

    /** The image host. */
    public const val HOST: String = "https://cdn-images.dzcdn.net/images/"

    /** The kind of image, which is a folder in the url. */
    public enum class Type(public val path: String) {
        Cover("cover"),
        Artist("artist"),
        Playlist("playlist"),
        User("user"),
        Misc("misc"),
    }

    /** The url of the [type] image with [md5] at [sizePx] x [sizePx] pixels (1 to 1900). */
    public fun url(type: Type, md5: String, sizePx: Int = MEDIUM): String {
        require(md5.isNotBlank()) { "md5 is blank" }
        val size = sizePx.coerceIn(1, 1900)
        return "$HOST${type.path}/$md5/${size}x$size-000000-80-0-0.jpg"
    }

    /** The md5 hash inside a Deezer image url, or `null` when [url] is not one. */
    public fun md5From(url: String?): String? {
        if (url == null) return null
        val match = MD5_IN_URL.find(url) ?: return null
        return match.groupValues[1]
    }

    private val MD5_IN_URL = Regex("/images/[a-z]+/([0-9a-f]{32})/")
}
