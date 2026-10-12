package io.github.halilozel1903.deezer.model

import io.github.halilozel1903.deezer.DeezerFormat
import io.github.halilozel1903.deezer.DeezerImages
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A track. Lists (charts, search, top tracks) carry a short form with [artist] and [album] nested; `GET /track/{id}`
 * adds [isrc], [bpm], [contributors] and more. Missing fields keep their defaults.
 *
 * [preview] is a 30 second MP3 that anyone may stream; full tracks need a Deezer account and SDK.
 */
@Serializable
public data class Track(
    public val id: Long,
    public val readable: Boolean = true,
    public val title: String = "",
    @SerialName("title_short") public val titleShort: String? = null,
    @SerialName("title_version") public val titleVersion: String? = null,
    public val isrc: String? = null,
    public val link: String? = null,
    public val share: String? = null,
    /** Length of the full track in seconds. */
    public val duration: Int = 0,
    @SerialName("track_position") public val trackPosition: Int? = null,
    @SerialName("disk_number") public val diskNumber: Int? = null,
    /** Deezer's popularity score. */
    public val rank: Long? = null,
    @SerialName("release_date") public val releaseDate: String? = null,
    @SerialName("explicit_lyrics") public val explicitLyrics: Boolean = false,
    @SerialName("explicit_content_lyrics") public val explicitContentLyrics: Int? = null,
    @SerialName("explicit_content_cover") public val explicitContentCover: Int? = null,
    /** The url of the 30 second MP3 preview; empty for a few tracks. */
    public val preview: String? = null,
    public val bpm: Double? = null,
    public val gain: Double? = null,
    @SerialName("md5_image") public val md5Image: String? = null,
    /** Chart position, in chart lists. */
    public val position: Int? = null,
    public val artist: Artist? = null,
    public val album: Album? = null,
    public val contributors: List<Artist> = emptyList(),
    public val type: String = "track",
) {
    /** [titleShort] when Deezer sent it, otherwise [title]. */
    public val displayTitle: String get() = titleShort?.takeIf { it.isNotBlank() } ?: title

    /** The 30 second preview url, or `null` when the track has none. */
    public val previewUrl: String? get() = preview?.takeIf { it.isNotBlank() }

    /** `true` when [previewUrl] is set. */
    public val hasPreview: Boolean get() = previewUrl != null

    /** [duration] as `m:ss`. */
    public val durationText: String get() = DeezerFormat.duration(duration)

    /** Cover art for the track: its album's cover, or one built from [md5Image]. */
    public fun coverUrl(sizePx: Int = DeezerImages.MEDIUM): String? =
        album?.pictureFor(sizePx) ?: md5Image?.let { DeezerImages.url(DeezerImages.Type.Cover, it, sizePx) }

    public companion object {
        /** Length of a Deezer preview in seconds. */
        public const val PREVIEW_SECONDS: Int = 30
    }
}
