package io.github.halilozel1903.deezer.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.deezer.DeezerQuery
import io.github.halilozel1903.deezer.browse.DeezerMediaId
import io.github.halilozel1903.deezer.model.Artist
import io.github.halilozel1903.deezer.model.DeezerPage
import io.github.halilozel1903.deezer.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Track length filters, sent as Deezer's advanced search fields `dur_min` and `dur_max`. */
enum class LengthFilter(val label: String, val min: Int?, val max: Int?) {
    AnyLength("Any length", null, null),
    UnderFour("Under 4 min", null, 240),
    OverSix("Over 6 min", 360, null),
}

private class SearchResults(val query: DeezerQuery, val artists: List<Artist>, val tracks: DeezerPage<Track>)

/** `GET /search/artist` and `GET /search/track` with a [DeezerQuery]. */
@Composable
fun SearchScreen(
    initialQuery: String,
    initialFilter: LengthFilter,
    player: PreviewPlayer,
    onArtist: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    var text by rememberSaveable { mutableStateOf(initialQuery) }
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    val query = text.trim()
    val results = load(query, filter, debounceMillis = 300) {
        if (query.isEmpty()) {
            null
        } else {
            coroutineScope {
                val tracksQuery = DeezerQuery(text = query, durationMin = filter.min, durationMax = filter.max)
                val artists = async { client.searchArtists(query, limit = 8) }
                val tracks = async { client.searchTracks(tracksQuery, limit = 25) }
                SearchResults(tracksQuery, artists.await().data, tracks.await())
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "field") {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Artists, tracks, albums") },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { text = "" }) { Icon(AppIcons.Close, contentDescription = "Clear") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
        }
        item(key = "filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(LengthFilter.entries, key = { it.name }) { entry ->
                    FilterChip(selected = filter == entry, onClick = { filter = entry }, label = { Text(entry.label) })
                }
            }
        }
        when (val state = results.state) {
            Load.Loading -> if (query.isNotEmpty()) item(key = "loading") { LoadingView() }
            is Load.Failed -> item(key = "error") { ErrorView(state.error, results.retry) }
            is Load.Ready -> state.value?.let { found -> searchResults(found, player, onArtist) }
        }
    }
}

private fun LazyListScope.searchResults(
    found: SearchResults,
    player: PreviewPlayer,
    onArtist: (Long) -> Unit,
) {
    item(key = "q") {
        // The `q` parameter the client built, advanced fields included.
        Text(
            "q = ${found.query}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    if (found.artists.isNotEmpty()) {
        item(key = "artists-title") { SectionHeader("Artists") }
        item(key = "artists") { ArtistShelf(found.artists) { onArtist(it.id) } }
    }
    val tracks = found.tracks.data
    if (tracks.isEmpty()) {
        item(key = "empty") {
            Text(
                "No tracks match",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    } else {
        val ids = tracks.map { DeezerMediaId.Track(it.id).value }
        item(key = "tracks-title") {
            SectionHeader("Matching tracks", trailing = found.tracks.total?.let { if (it == 1) "1 result" else "$it results" })
        }
        itemsIndexed(tracks, key = { _, track -> "result-${track.id}" }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = player.currentId == ids[index],
                isPlaying = player.isPlaying(ids[index]),
                onClick = { player.playOrToggle(ids, index) },
            )
        }
    }
}
