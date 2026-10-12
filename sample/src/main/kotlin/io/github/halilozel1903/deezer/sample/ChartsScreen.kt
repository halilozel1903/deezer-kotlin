package io.github.halilozel1903.deezer.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.deezer.browse.DeezerMediaId

/** A few of Deezer's genre ids, for the chart filter. `0` is the overall chart. */
private val ChartGenres: List<Pair<Long, String>> = listOf(
    0L to "All",
    132L to "Pop",
    116L to "Hip hop",
    152L to "Rock",
    113L to "Dance",
    165L to "R&B",
)

/** `GET /chart/{genre}`: top albums, tracks and artists. */
@Composable
fun ChartsScreen(
    player: PreviewPlayer,
    onArtist: (Long) -> Unit,
    onSearch: () -> Unit,
    contentPadding: PaddingValues,
) {
    var genre by rememberSaveable { mutableLongStateOf(0L) }
    val chart = load(genre) { client.chart(genre) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "title") {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Charts", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Live from the public Deezer API",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onSearch) { Icon(AppIcons.Search, contentDescription = "Search") }
            }
        }
        item(key = "genres") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ChartGenres, key = { it.first }) { (id, name) ->
                    FilterChip(selected = genre == id, onClick = { genre = id }, label = { Text(name) })
                }
            }
        }
        when (val state = chart.state) {
            Load.Loading -> item(key = "loading") { LoadingView() }
            is Load.Failed -> item(key = "error") { ErrorView(state.error, chart.retry) }
            is Load.Ready -> {
                val value = state.value
                val albums = value.albums.data
                val tracks = value.tracks.data
                val artists = value.artists.data
                if (albums.isNotEmpty()) {
                    item(key = "albums-title") { SectionHeader("Top albums") }
                    item(key = "albums") {
                        AlbumShelf(
                            albums = albums,
                            onClick = { album -> player.play(listOf(DeezerMediaId.Album(album.id).value)) },
                            subtitle = { it.artist?.name },
                        )
                    }
                }
                if (tracks.isNotEmpty()) {
                    val ids = tracks.map { DeezerMediaId.Track(it.id).value }
                    item(key = "tracks-title") { SectionHeader("Top tracks") }
                    itemsIndexed(tracks, key = { _, track -> "track-${track.id}" }) { index, track ->
                        TrackRow(
                            track = track,
                            position = index + 1,
                            isCurrent = player.currentId == ids[index],
                            isPlaying = player.isPlaying(ids[index]),
                            onClick = { player.playOrToggle(ids, index) },
                        )
                    }
                }
                if (artists.isNotEmpty()) {
                    item(key = "artists-title") { SectionHeader("Top artists") }
                    item(key = "artists") { ArtistShelf(artists) { onArtist(it.id) } }
                }
            }
        }
    }
}
