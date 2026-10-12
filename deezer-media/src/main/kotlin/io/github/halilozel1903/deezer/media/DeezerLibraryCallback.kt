package io.github.halilozel1903.deezer.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.halilozel1903.deezer.DeezerException
import io.github.halilozel1903.deezer.browse.BrowseItem
import io.github.halilozel1903.deezer.browse.DeezerBrowseTree
import io.github.halilozel1903.deezer.browse.DeezerMediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future
import java.util.concurrent.ConcurrentHashMap

/**
 * The [MediaLibrarySession.Callback] behind [DeezerMediaLibraryService]: browsing, item lookup and search answered
 * from a [DeezerBrowseTree], and playback requests resolved to the tracks' 30 second previews.
 *
 * Playing an album, playlist, radio or artist queues its tracks. Preview urls expire, so every played track is
 * loaded again right before playback.
 *
 * @param artworkUri maps an https artwork url to the uri media browsers load. By default it goes through
 * [DeezerArtworkProvider], since Android Auto only loads `content://` artwork.
 */
public open class DeezerLibraryCallback(
    context: Context,
    protected val tree: DeezerBrowseTree,
    protected val scope: CoroutineScope,
    private val artworkUri: (String) -> Uri? = { url -> DeezerArtworkProvider.contentUri(context, url) },
) : MediaLibrarySession.Callback {

    private val searchResults = ConcurrentHashMap<String, List<BrowseItem>>()

    /** [item] as a [MediaItem] with this callback's artwork mapping. */
    protected fun mediaItem(item: BrowseItem): MediaItem = DeezerMediaItems.toMediaItem(item, artworkUri)

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val rootParams = LibraryParams.Builder().setExtras(DeezerMediaItems.rootExtras()).build()
        return Futures.immediateFuture(LibraryResult.ofItem(mediaItem(tree.root), rootParams))
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
        try {
            val item = tree.item(mediaId)
            if (item != null) LibraryResult.ofItem(mediaItem(item), null) else LibraryResult.ofError<MediaItem>(LibraryResult.RESULT_ERROR_BAD_VALUE)
        } catch (e: DeezerException) {
            LibraryResult.ofError<MediaItem>(LibraryResult.RESULT_ERROR_IO)
        }
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        if (DeezerMediaId.parse(parentId) == null) {
            return@future LibraryResult.ofError<ImmutableList<MediaItem>>(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }
        try {
            val children = tree.children(parentId, page.coerceAtLeast(0), pageSize.coerceAtLeast(1))
            LibraryResult.ofItemList(ImmutableList.copyOf(children.map { mediaItem(it) }), params)
        } catch (e: DeezerException.NotFound) {
            LibraryResult.ofError<ImmutableList<MediaItem>>(LibraryResult.RESULT_ERROR_BAD_VALUE)
        } catch (e: DeezerException) {
            LibraryResult.ofError<ImmutableList<MediaItem>>(LibraryResult.RESULT_ERROR_IO)
        }
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = scope.future {
        val results = try {
            tree.search(query)
        } catch (e: DeezerException) {
            emptyList()
        }
        if (searchResults.size > MAX_SEARCHES) searchResults.clear()
        searchResults[query] = results
        session.notifySearchResultChanged(browser, query, results.size, params)
        LibraryResult.ofVoid()
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        try {
            val all = searchResults[query] ?: tree.search(query).also { searchResults[query] = it }
            val size = pageSize.coerceAtLeast(1)
            val from = (page.coerceAtLeast(0) * size).coerceAtMost(all.size)
            val slice = all.subList(from, (from + size).coerceAtMost(all.size))
            LibraryResult.ofItemList(ImmutableList.copyOf(slice.map { mediaItem(it) }), params)
        } catch (e: DeezerException) {
            LibraryResult.ofError<ImmutableList<MediaItem>>(LibraryResult.RESULT_ERROR_IO)
        }
    }

    /** Resolves every requested item to playable tracks with a fresh preview url. */
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = scope.future {
        mediaItems.flatMap { resolve(it) }.toMutableList()
    }

    /** Like [onAddMediaItems]; the start index points at the first track of the requested start item. */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        val resolved = mutableListOf<MediaItem>()
        var newStart = 0
        mediaItems.forEachIndexed { index, item ->
            if (index == startIndex) newStart = resolved.size
            resolved += resolve(item)
        }
        if (resolved.isEmpty()) throw IllegalStateException("Nothing playable in ${mediaItems.map { it.mediaId }}")
        val start = newStart.coerceIn(0, resolved.lastIndex)
        val position = if (startIndex >= 0 && mediaItems.getOrNull(startIndex)?.let { isTrack(it) } == true) startPositionMs else 0L
        MediaSession.MediaItemsWithStartPosition(resolved, start, position)
    }

    /**
     * The playable tracks of [item], loaded again for fresh preview urls. Items with ids this library did not make are
     * kept as they are when they have a playback uri.
     */
    protected open suspend fun resolve(item: MediaItem): List<MediaItem> {
        if (DeezerMediaId.parse(item.mediaId) == null) return listOfNotNull(item.takeIf { it.localConfiguration != null })
        return try {
            tree.playableTracks(item.mediaId).map { mediaItem(it) }
        } catch (e: DeezerException) {
            emptyList()
        }
    }

    private fun isTrack(item: MediaItem): Boolean = DeezerMediaId.parse(item.mediaId) is DeezerMediaId.Track

    private companion object {
        const val MAX_SEARCHES = 20
    }
}
