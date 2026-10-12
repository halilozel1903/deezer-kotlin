package io.github.halilozel1903.deezer.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.deezer.DeezerFormat
import io.github.halilozel1903.deezer.browse.DeezerMediaId
import io.github.halilozel1903.deezer.model.Album
import io.github.halilozel1903.deezer.model.Artist
import io.github.halilozel1903.deezer.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Everything the artist screen shows, from four requests made at once. */
private class ArtistPage(
    val artist: Artist,
    val top: List<Track>,
    val albums: List<Album>,
    val related: List<Artist>,
)

/** `GET /artist/{id}`, `/top`, `/albums` and `/related`. */
@Composable
fun ArtistScreen(
    id: Long,
    player: PreviewPlayer,
    onArtist: (Long) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
) {
    val page = load(id) {
        coroutineScope {
            val artist = async { client.artist(id) }
            val top = async { client.artistTopTracks(id, limit = 5) }
            val albums = async { client.artistAlbums(id, limit = 12) }
            val related = async { client.relatedArtists(id, limit = 12) }
            ArtistPage(artist.await(), top.await().data, albums.await().data, related.await().data)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val state = page.state) {
            Load.Loading -> LoadingView(Modifier.statusBarsPadding().padding(top = 64.dp))
            is Load.Failed -> ErrorView(state.error, page.retry, Modifier.statusBarsPadding().padding(top = 64.dp))
            is Load.Ready -> ArtistContent(state.value, player, onArtist, contentPadding)
        }
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
            modifier = Modifier.statusBarsPadding().padding(8.dp),
        ) {
            Icon(AppIcons.Back, contentDescription = "Back")
        }
    }
}

@Composable
private fun ArtistContent(page: ArtistPage, player: PreviewPlayer, onArtist: (Long) -> Unit, contentPadding: PaddingValues) {
    val artist = page.artist
    val ids = page.top.map { DeezerMediaId.Track(it.id).value }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
    ) {
        item(key = "hero") {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                Cover(artist.pictureXl ?: artist.pictureBig, 300.dp, Modifier.fillMaxWidth(), shape = RectangleShape)
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.background,
                            ),
                        ),
                )
                Text(
                    artist.name,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        item(key = "facts") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    listOfNotNull(
                        artist.nbFan?.let { DeezerFormat.fans(it) },
                        artist.nbAlbum?.let { if (it == 1) "1 album" else "$it albums" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { player.play(ids) }, enabled = ids.isNotEmpty()) {
                    Icon(AppIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play top tracks")
                }
            }
        }
        if (page.top.isNotEmpty()) {
            item(key = "top-title") { SectionHeader("Popular") }
            itemsIndexed(page.top, key = { _, track -> "top-${track.id}" }) { index, track ->
                TrackRow(
                    track = track,
                    position = index + 1,
                    isCurrent = player.currentId == ids[index],
                    isPlaying = player.isPlaying(ids[index]),
                    onClick = { player.playOrToggle(ids, index) },
                )
            }
        }
        if (page.albums.isNotEmpty()) {
            item(key = "albums-title") { SectionHeader("Albums") }
            item(key = "albums") {
                AlbumShelf(
                    albums = page.albums,
                    onClick = { album -> player.play(listOf(DeezerMediaId.Album(album.id).value)) },
                    subtitle = { album -> listOfNotNull(album.releaseYear?.toString(), album.recordType?.replaceFirstChar { it.uppercase() }).joinToString(" · ").ifEmpty { null } },
                )
            }
        }
        if (page.related.isNotEmpty()) {
            item(key = "related-title") { SectionHeader("Fans also like") }
            item(key = "related") { ArtistShelf(page.related) { onArtist(it.id) } }
        }
        item(key = "end") { Spacer(Modifier.height(16.dp)) }
    }
}
