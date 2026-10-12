package io.github.halilozel1903.deezer.model

import io.github.halilozel1903.deezer.DeezerFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An album; `GET /album/{id}` adds [genres], [label], [tracks] and more to the short form in lists. */
@Serializable
public data class Album(
    public val id: Long,
    public val title: String = "",
    public val upc: String? = null,
    public val link: String? = null,
    public val share: String? = null,
    public val cover: String? = null,
    @SerialName("cover_small") public val coverSmall: String? = null,
    @SerialName("cover_medium") public val coverMedium: String? = null,
    @SerialName("cover_big") public val coverBig: String? = null,
    @SerialName("cover_xl") public val coverXl: String? = null,
    @SerialName("md5_image") public val md5Image: String? = null,
    @SerialName("genre_id") public val genreId: Long? = null,
    public val genres: DeezerPage<Genre>? = null,
    public val label: String? = null,
    @SerialName("nb_tracks") public val nbTracks: Int? = null,
    /** Total length in seconds. */
    public val duration: Int? = null,
    public val fans: Long? = null,
    @SerialName("release_date") public val releaseDate: String? = null,
    /** `album`, `single`, `ep` or `compile`. */
    @SerialName("record_type") public val recordType: String? = null,
    public val available: Boolean? = null,
    public val tracklist: String? = null,
    @SerialName("explicit_lyrics") public val explicitLyrics: Boolean = false,
    /** Chart position, in chart lists. */
    public val position: Int? = null,
    public val artist: Artist? = null,
    public val contributors: List<Artist> = emptyList(),
    public val tracks: DeezerPage<Track>? = null,
    public val type: String = "album",
) : HasPicture {
    override val pictureSmall: String? get() = coverSmall
    override val pictureMedium: String? get() = coverMedium
    override val pictureBig: String? get() = coverBig
    override val pictureXl: String? get() = coverXl

    /** The release year, from [releaseDate]. */
    public val releaseYear: Int? get() = releaseDate?.take(4)?.toIntOrNull()

    /** [duration] as `m:ss` or `h:mm:ss`. */
    public val durationText: String? get() = duration?.let { DeezerFormat.duration(it) }
}
