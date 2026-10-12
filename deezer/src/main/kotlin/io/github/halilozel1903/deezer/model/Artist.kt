package io.github.halilozel1903.deezer.model

import io.github.halilozel1903.deezer.DeezerImages
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An artist; nested in tracks and albums with fewer fields than from `GET /artist/{id}`. */
@Serializable
public data class Artist(
    public val id: Long,
    public val name: String = "",
    public val link: String? = null,
    public val share: String? = null,
    public val picture: String? = null,
    @SerialName("picture_small") override val pictureSmall: String? = null,
    @SerialName("picture_medium") override val pictureMedium: String? = null,
    @SerialName("picture_big") override val pictureBig: String? = null,
    @SerialName("picture_xl") override val pictureXl: String? = null,
    @SerialName("nb_album") public val nbAlbum: Int? = null,
    @SerialName("nb_fan") public val nbFan: Long? = null,
    /** `true` when Deezer has an artist radio (see `DeezerClient.artistRadio`). */
    public val radio: Boolean = false,
    public val tracklist: String? = null,
    /** Chart position, in chart lists. */
    public val position: Int? = null,
    /** The artist's role on a track, in `contributors`. */
    public val role: String? = null,
    public val type: String = "artist",
) : HasPicture {
    /** The md5 hash of the artist picture, read from its url. */
    public val md5Image: String? get() = DeezerImages.md5From(pictureMedium ?: pictureSmall ?: pictureBig ?: pictureXl)
}
