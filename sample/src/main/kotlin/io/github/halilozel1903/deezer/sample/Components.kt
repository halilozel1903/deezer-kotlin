package io.github.halilozel1903.deezer.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.halilozel1903.deezer.DeezerException
import io.github.halilozel1903.deezer.DeezerFormat
import io.github.halilozel1903.deezer.model.Album
import io.github.halilozel1903.deezer.model.Artist
import io.github.halilozel1903.deezer.model.Track

/** A cover or picture loaded by Coil from Deezer's CDN, on a neutral tile until it arrives. */
@Composable
fun Cover(url: Any?, size: Dp, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val tile = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
    AsyncImage(
        model = url,
        contentDescription = null,
        placeholder = tile,
        error = tile,
        fallback = tile,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(shape),
    )
}

/** A section title such as "Top tracks". These fixed strings are what the screenshot script waits for. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A track row: optional position, cover, title, artist, explicit mark and length. Tapping plays the preview. */
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    position: Int? = null,
    showCover: Boolean = true,
    isPlaying: Boolean = isCurrent,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = track.hasPreview, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (position != null) {
            Text(
                "$position",
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(32.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (showCover) {
            Cover(track.album?.coverMedium, 52.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.explicitLyrics) {
                    ExplicitMark()
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    listOfNotNull(track.artist?.name, track.album?.title.takeIf { !showCover }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(track.durationText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Icon(
            if (isCurrent && isPlaying) AppIcons.Pause else AppIcons.Play,
            contentDescription = if (track.hasPreview) "Play preview" else "No preview",
            tint = if (track.hasPreview) accent else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 4.dp).size(22.dp),
        )
    }
}

/** The "E" Deezer shows next to explicit tracks. */
@Composable
fun ExplicitMark() {
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("E", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
    }
}

/** A horizontal shelf of album covers. */
@Composable
fun AlbumShelf(albums: List<Album>, onClick: (Album) -> Unit, subtitle: (Album) -> String?) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(albums, key = { it.id }) { album ->
            Column(Modifier.width(140.dp).clip(RoundedCornerShape(8.dp)).clickable { onClick(album) }) {
                Cover(album.coverMedium, 140.dp)
                Spacer(Modifier.height(8.dp))
                Text(album.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle(album)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A horizontal shelf of round artist pictures. */
@Composable
fun ArtistShelf(artists: List<Artist>, onClick: (Artist) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(artists, key = { it.id }) { artist ->
            Column(
                modifier = Modifier.width(104.dp).clip(RoundedCornerShape(8.dp)).clickable { onClick(artist) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Cover(artist.pictureMedium, 96.dp, shape = CircleShape)
                Spacer(Modifier.height(8.dp))
                Text(artist.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                artist.nbFan?.let {
                    Text(DeezerFormat.fans(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Shown when the API could not be reached after every retry. There is no offline fallback. */
@Composable
fun ErrorView(error: Throwable, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (error is DeezerException.NotFound) "Not found on Deezer" else "Couldn't reach Deezer",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            error.message ?: error::class.simpleName.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Button(onClick = onRetry) { Text("Retry") }
    }
}
