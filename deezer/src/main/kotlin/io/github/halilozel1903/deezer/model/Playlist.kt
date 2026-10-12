package io.github.halilozel1903.deezer.model

import io.github.halilozel1903.deezer.DeezerFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A public playlist. Chart lists name the owner `user`, `GET /playlist/{id}` names it `creator`. */
@Serializable
public data class Playlist(
    public val id: Long,
    public val title: String = "",
    public val description: String? = null,
    /** Total length in seconds. */
    public val duration: Int? = null,
    @SerialName("public") public val isPublic: Boolean = true,
    @SerialName("is_loved_track") public val isLovedTrack: Boolean = false,
    public val collaborative: Boolean = false,
    @SerialName("nb_tracks") public val nbTracks: Int? = null,
    public val fans: Long? = null,
    public val link: String? = null,
    public val share: String? = null,
    public val picture: String? = null,
    @SerialName("picture_small") override val pictureSmall: String? = null,
    @SerialName("picture_medium") override val pictureMedium: String? = null,
    @SerialName("picture_big") override val pictureBig: String? = null,
    @SerialName("picture_xl") override val pictureXl: String? = null,
    @SerialName("picture_type") public val pictureType: String? = null,
    public val checksum: String? = null,
    public val tracklist: String? = null,
    @SerialName("creation_date") public val creationDate: String? = null,
    @SerialName("md5_image") public val md5Image: String? = null,
    /** Chart position, in chart lists. */
    public val position: Int? = null,
    public val creator: User? = null,
    public val user: User? = null,
    public val tracks: DeezerPage<Track>? = null,
    public val type: String = "playlist",
) : HasPicture {
    /** Whoever made the playlist: [creator] or [user]. */
    public val owner: User? get() = creator ?: user

    /** [duration] as `m:ss` or `h:mm:ss`. */
    public val durationText: String? get() = duration?.let { DeezerFormat.duration(it) }
}
