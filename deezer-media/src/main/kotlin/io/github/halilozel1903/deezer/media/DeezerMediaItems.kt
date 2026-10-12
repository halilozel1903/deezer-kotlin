package io.github.halilozel1903.deezer.media

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import io.github.halilozel1903.deezer.browse.BrowseItem
import io.github.halilozel1903.deezer.browse.BrowseKind
import io.github.halilozel1903.deezer.browse.BrowseStyle

/**
 * Turns [BrowseItem]s into Media3 [MediaItem]s with the extras Android Auto reads: content style hints (list or
 * grid), group titles and the explicit flag.
 */
public object DeezerMediaItems {

    /** How the browsable children of an item are shown. */
    public const val EXTRA_CONTENT_STYLE_BROWSABLE: String = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"

    /** How the playable children of an item are shown. */
    public const val EXTRA_CONTENT_STYLE_PLAYABLE: String = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"

    /** The section header above an item in a mixed list. */
    public const val EXTRA_CONTENT_STYLE_GROUP_TITLE: String = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"

    /** Root hint: the service supports search. */
    public const val EXTRA_SEARCH_SUPPORTED: String = "android.media.browse.SEARCH_SUPPORTED"

    /** Marks explicit content (value `1L`). */
    public const val EXTRA_IS_EXPLICIT: String = "android.media.IS_EXPLICIT"

    /** Content style value for rows. */
    public const val STYLE_LIST_ITEM: Int = 1

    /** Content style value for tiles. */
    public const val STYLE_GRID_ITEM: Int = 2

    /** The extras of the library root: grids for folders, lists for tracks, search supported. */
    public fun rootExtras(): Bundle = Bundle().apply {
        putBoolean(EXTRA_SEARCH_SUPPORTED, true)
        putInt(EXTRA_CONTENT_STYLE_BROWSABLE, STYLE_GRID_ITEM)
        putInt(EXTRA_CONTENT_STYLE_PLAYABLE, STYLE_LIST_ITEM)
    }

    /**
     * [item] as a [MediaItem]. [artworkUri] maps an https artwork url to the uri the browser loads; Android Auto needs
     * a content uri, see [DeezerArtworkProvider.contentUri]. A track with a preview gets its MP3 as the playback uri.
     */
    public fun toMediaItem(item: BrowseItem, artworkUri: (String) -> Uri? = { Uri.parse(it) }): MediaItem {
        val extras = Bundle()
        val style = if (item.childStyle == BrowseStyle.Grid) STYLE_GRID_ITEM else STYLE_LIST_ITEM
        if (item.isBrowsable) {
            extras.putInt(EXTRA_CONTENT_STYLE_BROWSABLE, style)
            extras.putInt(EXTRA_CONTENT_STYLE_PLAYABLE, style)
        }
        item.groupTitle?.let { extras.putString(EXTRA_CONTENT_STYLE_GROUP_TITLE, it) }
        if (item.isExplicit) extras.putLong(EXTRA_IS_EXPLICIT, 1L)

        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setDisplayTitle(item.title)
            .setSubtitle(item.subtitle)
            .setArtist(item.artist)
            .setAlbumTitle(item.album)
            .setArtworkUri(item.artworkUrl?.let(artworkUri))
            .setIsBrowsable(item.isBrowsable)
            .setIsPlayable(item.isPlayable)
            .setMediaType(mediaType(item.kind))
            .setExtras(extras)
            .build()
        val builder = MediaItem.Builder()
            .setMediaId(item.mediaId)
            .setMediaMetadata(metadata)
        item.previewUrl?.let { builder.setUri(it).setMimeType(MimeTypes.AUDIO_MPEG) }
        return builder.build()
    }

    private fun mediaType(kind: BrowseKind): Int = when (kind) {
        BrowseKind.Root, BrowseKind.Folder -> MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        BrowseKind.Track -> MediaMetadata.MEDIA_TYPE_MUSIC
        BrowseKind.Album -> MediaMetadata.MEDIA_TYPE_ALBUM
        BrowseKind.Artist -> MediaMetadata.MEDIA_TYPE_ARTIST
        BrowseKind.Playlist -> MediaMetadata.MEDIA_TYPE_PLAYLIST
        BrowseKind.Radio -> MediaMetadata.MEDIA_TYPE_RADIO_STATION
        BrowseKind.Genre -> MediaMetadata.MEDIA_TYPE_GENRE
    }
}
