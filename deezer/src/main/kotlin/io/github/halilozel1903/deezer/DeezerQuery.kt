package io.github.halilozel1903.deezer

/**
 * A Deezer search query with advanced fields. Deezer's syntax is `artist:"aloe blacc" track:"i need a dollar"`;
 * this builds it for you, quoting values and dropping quotes inside them.
 *
 * ```kotlin
 * client.searchTracks(DeezerQuery(artist = "Daft Punk", track = "One More Time"))
 * client.searchTracks(DeezerQuery("night drive", durationMax = 240), order = SearchOrder.RatingDesc)
 * ```
 *
 * @param text free text, matched against everything.
 * @param durationMin shortest track length in seconds.
 * @param durationMax longest track length in seconds.
 */
public data class DeezerQuery(
    public val text: String? = null,
    public val artist: String? = null,
    public val album: String? = null,
    public val track: String? = null,
    public val label: String? = null,
    public val durationMin: Int? = null,
    public val durationMax: Int? = null,
    public val bpmMin: Int? = null,
    public val bpmMax: Int? = null,
) {
    /** `true` when no field is set. */
    public val isEmpty: Boolean get() = toString().isEmpty()

    /** The `q` parameter: free text first, then the fields in Deezer's syntax. */
    override fun toString(): String = buildList<String> {
        text?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        artist?.let { quoted("artist", it)?.let { q -> add(q) } }
        album?.let { quoted("album", it)?.let { q -> add(q) } }
        track?.let { quoted("track", it)?.let { q -> add(q) } }
        label?.let { quoted("label", it)?.let { q -> add(q) } }
        durationMin?.let { add("dur_min:$it") }
        durationMax?.let { add("dur_max:$it") }
        bpmMin?.let { add("bpm_min:$it") }
        bpmMax?.let { add("bpm_max:$it") }
    }.joinToString(" ")

    private fun quoted(field: String, value: String): String? {
        val clean = value.replace("\"", "").trim()
        return if (clean.isEmpty()) null else "$field:\"$clean\""
    }
}

/** The `order` of search results. */
public enum class SearchOrder(public val apiValue: String) {
    Ranking("RANKING"),
    TrackAsc("TRACK_ASC"),
    TrackDesc("TRACK_DESC"),
    ArtistAsc("ARTIST_ASC"),
    ArtistDesc("ARTIST_DESC"),
    AlbumAsc("ALBUM_ASC"),
    AlbumDesc("ALBUM_DESC"),
    RatingAsc("RATING_ASC"),
    RatingDesc("RATING_DESC"),
    DurationAsc("DURATION_ASC"),
    DurationDesc("DURATION_DESC"),
}
