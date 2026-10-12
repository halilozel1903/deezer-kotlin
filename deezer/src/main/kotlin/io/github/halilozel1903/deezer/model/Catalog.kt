package io.github.halilozel1903.deezer.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A music genre. Id 0 is "All", the genre of the overall charts. */
@Serializable
public data class Genre(
    public val id: Long,
    public val name: String = "",
    public val picture: String? = null,
    @SerialName("picture_small") override val pictureSmall: String? = null,
    @SerialName("picture_medium") override val pictureMedium: String? = null,
    @SerialName("picture_big") override val pictureBig: String? = null,
    @SerialName("picture_xl") override val pictureXl: String? = null,
    public val type: String = "genre",
) : HasPicture

/** A Deezer radio (a mix that plays endlessly), from `GET /radio` or `GET /genre/{id}/radios`. */
@Serializable
public data class Radio(
    public val id: Long,
    public val title: String = "",
    public val description: String? = null,
    public val share: String? = null,
    public val picture: String? = null,
    @SerialName("picture_small") override val pictureSmall: String? = null,
    @SerialName("picture_medium") override val pictureMedium: String? = null,
    @SerialName("picture_big") override val pictureBig: String? = null,
    @SerialName("picture_xl") override val pictureXl: String? = null,
    public val tracklist: String? = null,
    @SerialName("md5_image") public val md5Image: String? = null,
    public val type: String = "radio",
) : HasPicture

/** A genre with its radios, from `GET /radio/genres`. */
@Serializable
public data class RadioGenre(
    public val id: Long,
    public val title: String = "",
    public val radios: List<Radio> = emptyList(),
)

/** A podcast, as listed in the charts. */
@Serializable
public data class Podcast(
    public val id: Long,
    public val title: String = "",
    public val description: String? = null,
    public val available: Boolean = true,
    public val fans: Long? = null,
    public val link: String? = null,
    public val share: String? = null,
    public val picture: String? = null,
    @SerialName("picture_small") override val pictureSmall: String? = null,
    @SerialName("picture_medium") override val pictureMedium: String? = null,
    @SerialName("picture_big") override val pictureBig: String? = null,
    @SerialName("picture_xl") override val pictureXl: String? = null,
    /** Chart position, in chart lists. */
    public val position: Int? = null,
    public val type: String = "podcast",
) : HasPicture

/** An editorial page: Deezer's editors' picks per genre. Id 0 is the main one. */
@Serializable
public data class Editorial(
    public val id: Long,
    public val name: String = "",
    public val picture: String? = null,
    @SerialName("picture_small") override val pictureSmall: String? = null,
    @SerialName("picture_medium") override val pictureMedium: String? = null,
    @SerialName("picture_big") override val pictureBig: String? = null,
    @SerialName("picture_xl") override val pictureXl: String? = null,
    public val type: String = "editorial",
) : HasPicture

/** `GET /chart/{genreId}`: the top tracks, albums, artists, playlists and podcasts of one genre (0 for all). */
@Serializable
public data class Chart(
    public val tracks: DeezerPage<Track> = DeezerPage(),
    public val albums: DeezerPage<Album> = DeezerPage(),
    public val artists: DeezerPage<Artist> = DeezerPage(),
    public val playlists: DeezerPage<Playlist> = DeezerPage(),
    public val podcasts: DeezerPage<Podcast> = DeezerPage(),
)
