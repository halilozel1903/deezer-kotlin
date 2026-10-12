package io.github.halilozel1903.deezer.browse

/**
 * The media ids of the browse tree, such as `charts`, `album/302127` or `track/3135556`. Media browsers (Android
 * Auto, a phone app, a watch) only hand ids back, so every node must be rebuilt from its id alone.
 *
 * ```kotlin
 * DeezerMediaId.parse("album/302127")            // DeezerMediaId.Album(302127)
 * DeezerMediaId.Track(3135556).value             // "track/3135556"
 * ```
 */
public sealed class DeezerMediaId(public val value: String) {

    /** The root; its children are the top-level tabs. */
    public data object Root : DeezerMediaId("root")

    /** The overall charts: top tracks, albums, playlists and artists. */
    public data object Charts : DeezerMediaId("charts")

    /** Every page of the top tracks. */
    public data object ChartTracks : DeezerMediaId("charts/tracks")

    /** Albums picked by Deezer's editors and new releases. */
    public data object Picks : DeezerMediaId("picks")

    /** Every genre. */
    public data object Genres : DeezerMediaId("genres")

    /** The most popular radios (endless mixes). */
    public data object Radios : DeezerMediaId("radios")

    /** The charts of one genre. */
    public data class Genre(val id: Long) : DeezerMediaId("genre/$id")

    /** One track, playable. */
    public data class Track(val id: Long) : DeezerMediaId("track/$id")

    /** An album and its tracks. */
    public data class Album(val id: Long) : DeezerMediaId("album/$id")

    /** An artist's top tracks and albums. */
    public data class Artist(val id: Long) : DeezerMediaId("artist/$id")

    /** A playlist and its tracks. */
    public data class Playlist(val id: Long) : DeezerMediaId("playlist/$id")

    /** A radio: playing it queues its current tracks. */
    public data class Radio(val id: Long) : DeezerMediaId("radio/$id")

    final override fun toString(): String = value

    public companion object {
        /** The id [value] stands for, or `null` when it is not one of these ids. */
        public fun parse(value: String): DeezerMediaId? {
            when (value) {
                Root.value -> return Root
                Charts.value -> return Charts
                ChartTracks.value -> return ChartTracks
                Picks.value -> return Picks
                Genres.value -> return Genres
                Radios.value -> return Radios
            }
            val kind = value.substringBefore('/', missingDelimiterValue = "")
            val id = value.substringAfter('/', missingDelimiterValue = "").toLongOrNull() ?: return null
            return when (kind) {
                "genre" -> if (id >= 0) Genre(id) else null
                "track" -> if (id > 0) Track(id) else null
                "album" -> if (id > 0) Album(id) else null
                "artist" -> if (id > 0) Artist(id) else null
                "playlist" -> if (id > 0) Playlist(id) else null
                "radio" -> if (id > 0) Radio(id) else null
                else -> null
            }
        }
    }
}
