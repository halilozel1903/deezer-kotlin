package io.github.halilozel1903.deezer.browse

import io.github.halilozel1903.deezer.DeezerClient
import io.github.halilozel1903.deezer.DeezerException
import io.github.halilozel1903.deezer.DeezerFormat
import io.github.halilozel1903.deezer.DeezerImages
import io.github.halilozel1903.deezer.model.Album
import io.github.halilozel1903.deezer.model.Artist
import io.github.halilozel1903.deezer.model.Genre
import io.github.halilozel1903.deezer.model.Playlist
import io.github.halilozel1903.deezer.model.Radio
import io.github.halilozel1903.deezer.model.Track

/** What a [BrowseItem] stands for. */
public enum class BrowseKind { Root, Folder, Track, Album, Artist, Playlist, Radio, Genre }

/** How a media browser should show the children of a [BrowseItem]. */
public enum class BrowseStyle {
    /** Rows with a small image, for tracks. */
    List,

    /** Tiles with a large image, for albums, artists, playlists, radios and genres. */
    Grid,
}

/**
 * One node of the browse tree, independent of any media framework. `deezer-media` turns it into a Media3
 * `MediaItem` for Android Auto; a phone or car preview UI can draw it directly.
 *
 * @property groupTitle the section the item belongs to inside a mixed list ("Top tracks", "Top albums"); Android
 * Auto shows a header above each run of items with the same title.
 * @property previewUrl the 30 second MP3 of a track. Preview urls expire, so resolve playable tracks again with
 * [DeezerBrowseTree.playableTracks] right before playback.
 */
public data class BrowseItem(
    public val mediaId: String,
    public val title: String,
    public val kind: BrowseKind,
    public val isBrowsable: Boolean,
    public val isPlayable: Boolean,
    public val subtitle: String? = null,
    public val artworkUrl: String? = null,
    public val childStyle: BrowseStyle = BrowseStyle.List,
    public val groupTitle: String? = null,
    public val previewUrl: String? = null,
    public val artist: String? = null,
    public val album: String? = null,
    public val durationSeconds: Int? = null,
    public val isExplicit: Boolean = false,
) {
    /** [mediaId] parsed, or `null` for an id this library did not make. */
    public val id: DeezerMediaId? get() = DeezerMediaId.parse(mediaId)
}

/** Titles of the browse tree. Override them to translate. */
public class BrowseStrings(
    public val root: String = "Deezer",
    public val charts: String = "Charts",
    public val picks: String = "Picks",
    public val genres: String = "Genres",
    public val radios: String = "Radios",
    public val topTracks: String = "Top tracks",
    public val topAlbums: String = "Top albums",
    public val topPlaylists: String = "Top playlists",
    public val topArtists: String = "Top artists",
    public val allTopTracks: String = "All top tracks",
    public val editorsPicks: String = "Editor's picks",
    public val newReleases: String = "New releases",
    public val popular: String = "Popular",
    public val albums: String = "Albums",
    public val tracks: String = "Tracks",
    public val artists: String = "Artists",
    public val radio: String = "Radio",
    public val fans: (Long) -> String = { DeezerFormat.fans(it) },
    public val trackCount: (Int) -> String = { if (it == 1) "1 track" else "$it tracks" },
)

/**
 * The browse tree of a music app on the public Deezer API: four tabs (charts, picks, genres, radios) and
 * everything they lead to. Every node is rebuilt from its media id, so it works with any media browser.
 *
 * | Media id | Children |
 * | --- | --- |
 * | `root` | `charts`, `picks`, `genres`, `radios` |
 * | `charts`, `genre/{id}` | top tracks, albums, playlists and artists of the chart (groups) |
 * | `charts/tracks` | every top track, paged |
 * | `picks` | editor's picks and new releases (groups) |
 * | `genres` | every genre |
 * | `radios` | the top radios (playable) |
 * | `album/{id}`, `playlist/{id}`, `radio/{id}` | tracks |
 * | `artist/{id}` | popular tracks and albums (groups) |
 *
 * Tracks without a preview are left out, since they can't be played.
 */
public class DeezerBrowseTree(
    private val client: DeezerClient,
    private val strings: BrowseStrings = BrowseStrings(),
    /** How many items of each group the mixed lists (charts, picks, artist) show. */
    private val groupSize: Int = 10,
    /** The size images are requested at, in pixels. */
    private val artworkSizePx: Int = DeezerImages.BIG,
) {
    init {
        require(groupSize > 0) { "groupSize must be positive, was $groupSize" }
    }

    /** The root node. */
    public val root: BrowseItem
        get() = folder(DeezerMediaId.Root, strings.root, BrowseStyle.Grid)

    /** The top-level tabs, in order. Android Auto shows at most four. */
    public val tabs: List<BrowseItem>
        get() = listOf(
            folder(DeezerMediaId.Charts, strings.charts, BrowseStyle.List),
            folder(DeezerMediaId.Picks, strings.picks, BrowseStyle.Grid),
            folder(DeezerMediaId.Genres, strings.genres, BrowseStyle.Grid),
            folder(DeezerMediaId.Radios, strings.radios, BrowseStyle.Grid),
        )

    /**
     * The children of [parentId], or an empty list for an unknown id or a page past the end. Lists backed by a paged
     * endpoint honour [page] and [pageSize]; mixed lists (groups) are one page.
     *
     * @throws DeezerException when Deezer can't be reached or answers with an error.
     */
    public suspend fun children(parentId: String, page: Int = 0, pageSize: Int = DEFAULT_PAGE_SIZE): List<BrowseItem> {
        require(page >= 0) { "page must not be negative, was $page" }
        require(pageSize > 0) { "pageSize must be positive, was $pageSize" }
        val id = DeezerMediaId.parse(parentId) ?: return emptyList()
        val index = page * pageSize
        if (id.isMixed && page > 0) return emptyList()
        return when (id) {
            DeezerMediaId.Root -> if (page == 0) tabs else emptyList()
            DeezerMediaId.Charts -> chartGroups(genreId = 0, withAllTracks = true)
            is DeezerMediaId.Genre -> chartGroups(genreId = id.id, withAllTracks = false)
            DeezerMediaId.ChartTracks -> client.chartTracks(index = index, limit = pageSize).data.mapNotNull { trackItem(it) }
            DeezerMediaId.Picks -> picks()
            DeezerMediaId.Genres -> if (page == 0) client.genres().filter { it.id != 0L }.map { genreItem(it) } else emptyList()
            DeezerMediaId.Radios -> client.topRadios(index = index, limit = pageSize).data.map { radioItem(it) }
            is DeezerMediaId.Album -> {
                val album = client.album(id.id)
                client.albumTracks(id.id, index = index, limit = pageSize).data
                    .mapNotNull { trackItem(it, albumOverride = album) }
            }
            is DeezerMediaId.Playlist -> client.playlistTracks(id.id, index = index, limit = pageSize).data.mapNotNull { trackItem(it) }
            is DeezerMediaId.Radio -> if (page == 0) client.radioTracks(id.id).data.mapNotNull { trackItem(it) } else emptyList()
            is DeezerMediaId.Artist -> artist(id.id)
            is DeezerMediaId.Track -> emptyList()
        }
    }

    /**
     * The node of [mediaId] itself, for example when a media browser asks for an item it saw earlier.
     * Returns `null` for an unknown id or an id Deezer does not know.
     */
    public suspend fun item(mediaId: String): BrowseItem? {
        val id = DeezerMediaId.parse(mediaId) ?: return null
        return try {
            when (id) {
                DeezerMediaId.Root -> root
                DeezerMediaId.Charts, DeezerMediaId.Picks, DeezerMediaId.Genres, DeezerMediaId.Radios ->
                    tabs.first { it.mediaId == id.value }
                DeezerMediaId.ChartTracks -> folder(id, strings.allTopTracks, BrowseStyle.List)
                is DeezerMediaId.Genre -> genreItem(client.genre(id.id))
                is DeezerMediaId.Track -> trackItem(client.track(id.id))
                is DeezerMediaId.Album -> albumItem(client.album(id.id))
                is DeezerMediaId.Artist -> artistItem(client.artist(id.id))
                is DeezerMediaId.Playlist -> playlistItem(client.playlist(id.id))
                is DeezerMediaId.Radio -> radioItem(client.radio(id.id))
            }
        } catch (e: DeezerException.NotFound) {
            null
        }
    }

    /**
     * Tracks matching [query] (and, after them, albums and artists as groups), for voice and text search in the car.
     * A blank query has no results.
     */
    public suspend fun search(query: String, limit: Int = 25): List<BrowseItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val tracks = client.searchTracks(q, limit = limit).data.mapNotNull { trackItem(it, group = strings.tracks) }
        val albums = optional { client.searchAlbums(q, limit = groupSize).data.map { albumItem(it, group = strings.albums) } }
        val artists = optional { client.searchArtists(q, limit = groupSize).data.map { artistItem(it, group = strings.artists) } }
        return tracks + albums + artists
    }

    /**
     * The tracks to queue when [mediaId] is played: the track itself (loaded again for a fresh preview url), or the
     * tracks of an album, playlist or radio, or an artist's top tracks. Only tracks with a preview are returned.
     */
    public suspend fun playableTracks(mediaId: String, limit: Int = 50): List<BrowseItem> {
        val id = DeezerMediaId.parse(mediaId) ?: return emptyList()
        val tracks: List<Track> = when (id) {
            is DeezerMediaId.Track -> listOf(client.track(id.id))
            is DeezerMediaId.Album -> {
                val album = client.album(id.id)
                return client.albumTracks(id.id, limit = limit).data.mapNotNull { trackItem(it, albumOverride = album) }
            }
            is DeezerMediaId.Playlist -> client.playlistTracks(id.id, limit = limit).data
            is DeezerMediaId.Radio -> client.radioTracks(id.id, limit = limit).data
            is DeezerMediaId.Artist -> client.artistTopTracks(id.id, limit = limit).data
            DeezerMediaId.ChartTracks, DeezerMediaId.Charts -> client.chartTracks(limit = limit).data
            is DeezerMediaId.Genre -> client.chartTracks(genreId = id.id, limit = limit).data
            DeezerMediaId.Root, DeezerMediaId.Picks, DeezerMediaId.Genres, DeezerMediaId.Radios -> emptyList()
        }
        return tracks.mapNotNull { trackItem(it) }
    }

    // region Node builders

    /** [track] as a playable node, or `null` when it has no preview. */
    public fun trackItem(track: Track, group: String? = null, albumOverride: Album? = null): BrowseItem? {
        val preview = track.previewUrl ?: return null
        val album = track.album ?: albumOverride
        return BrowseItem(
            mediaId = DeezerMediaId.Track(track.id).value,
            title = track.displayTitle,
            kind = BrowseKind.Track,
            isBrowsable = false,
            isPlayable = true,
            subtitle = listOfNotNull(track.artist?.name, album?.title).joinToString(" · ").ifEmpty { null },
            artworkUrl = album?.pictureFor(artworkSizePx)
                ?: track.md5Image?.let { DeezerImages.url(DeezerImages.Type.Cover, it, artworkSizePx) },
            groupTitle = group,
            previewUrl = preview,
            artist = track.artist?.name,
            album = album?.title,
            durationSeconds = track.duration.takeIf { it > 0 },
            isExplicit = track.explicitLyrics,
        )
    }

    /** [album] as a browsable node. */
    public fun albumItem(album: Album, group: String? = null): BrowseItem = BrowseItem(
        mediaId = DeezerMediaId.Album(album.id).value,
        title = album.title,
        kind = BrowseKind.Album,
        isBrowsable = true,
        isPlayable = true,
        subtitle = listOfNotNull(album.artist?.name, album.releaseYear?.toString()).joinToString(" · ").ifEmpty { null },
        artworkUrl = album.pictureFor(artworkSizePx),
        childStyle = BrowseStyle.List,
        groupTitle = group,
        artist = album.artist?.name,
        isExplicit = album.explicitLyrics,
    )

    /** [artist] as a browsable node. */
    public fun artistItem(artist: Artist, group: String? = null): BrowseItem = BrowseItem(
        mediaId = DeezerMediaId.Artist(artist.id).value,
        title = artist.name,
        kind = BrowseKind.Artist,
        isBrowsable = true,
        isPlayable = true,
        subtitle = artist.nbFan?.let(strings.fans),
        artworkUrl = artist.pictureFor(artworkSizePx),
        childStyle = BrowseStyle.List,
        groupTitle = group,
    )

    /** [playlist] as a browsable node. */
    public fun playlistItem(playlist: Playlist, group: String? = null): BrowseItem = BrowseItem(
        mediaId = DeezerMediaId.Playlist(playlist.id).value,
        title = playlist.title,
        kind = BrowseKind.Playlist,
        isBrowsable = true,
        isPlayable = true,
        subtitle = playlist.nbTracks?.let(strings.trackCount) ?: playlist.owner?.name,
        artworkUrl = playlist.pictureFor(artworkSizePx),
        childStyle = BrowseStyle.List,
        groupTitle = group,
    )

    /** [radio] as a playable node; browsing it lists its current tracks. */
    public fun radioItem(radio: Radio, group: String? = null): BrowseItem = BrowseItem(
        mediaId = DeezerMediaId.Radio(radio.id).value,
        title = radio.title,
        kind = BrowseKind.Radio,
        isBrowsable = false,
        isPlayable = true,
        subtitle = strings.radio,
        artworkUrl = radio.pictureFor(artworkSizePx),
        groupTitle = group,
    )

    /** [genre] as a browsable node that leads to the genre's charts. */
    public fun genreItem(genre: Genre): BrowseItem = BrowseItem(
        mediaId = DeezerMediaId.Genre(genre.id).value,
        title = genre.name,
        kind = BrowseKind.Genre,
        isBrowsable = true,
        isPlayable = false,
        artworkUrl = genre.pictureFor(artworkSizePx),
        childStyle = BrowseStyle.List,
    )

    // endregion

    private suspend fun chartGroups(genreId: Long, withAllTracks: Boolean): List<BrowseItem> {
        val chart = client.chart(genreId)
        return buildList<BrowseItem> {
            addAll(chart.tracks.data.take(groupSize).mapNotNull { trackItem(it, group = strings.topTracks) })
            if (withAllTracks) add(folder(DeezerMediaId.ChartTracks, strings.allTopTracks, BrowseStyle.List, strings.topTracks))
            addAll(chart.albums.data.take(groupSize).map { albumItem(it, group = strings.topAlbums) })
            addAll(chart.playlists.data.take(groupSize).map { playlistItem(it, group = strings.topPlaylists) })
            addAll(chart.artists.data.take(groupSize).map { artistItem(it, group = strings.topArtists) })
        }
    }

    private suspend fun picks(): List<BrowseItem> {
        val selection = client.editorialSelection(0).take(groupSize).map { albumItem(it, group = strings.editorsPicks) }
        val releases = optional {
            client.editorialReleases(0, limit = groupSize).data.map { albumItem(it, group = strings.newReleases) }
        }
        return (selection + releases).distinctBy { it.mediaId }
    }

    private suspend fun artist(id: Long): List<BrowseItem> {
        val top = client.artistTopTracks(id, limit = groupSize).data.mapNotNull { trackItem(it, group = strings.popular) }
        val albums = optional { client.artistAlbums(id, limit = groupSize).data.map { albumItem(it, group = strings.albums) } }
        return top + albums
    }

    private fun folder(id: DeezerMediaId, title: String, style: BrowseStyle, group: String? = null): BrowseItem = BrowseItem(
        mediaId = id.value,
        title = title,
        kind = if (id == DeezerMediaId.Root) BrowseKind.Root else BrowseKind.Folder,
        isBrowsable = true,
        isPlayable = false,
        childStyle = style,
        groupTitle = group,
    )

    /** Extra sections should not fail a whole list. */
    private suspend fun <T> optional(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (e: DeezerException) {
        emptyList()
    }

    private val DeezerMediaId.isMixed: Boolean
        get() = this == DeezerMediaId.Charts || this == DeezerMediaId.Picks || this is DeezerMediaId.Genre ||
            this is DeezerMediaId.Artist

    public companion object {
        /** The page size used when a media browser does not ask for one. */
        public const val DEFAULT_PAGE_SIZE: Int = 50
    }
}
