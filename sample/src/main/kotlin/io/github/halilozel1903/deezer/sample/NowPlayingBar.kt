package io.github.halilozel1903.deezer.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The preview that is playing: cover, title, artist, progress through its 30 seconds and play/pause. */
@Composable
fun NowPlayingBar(player: PreviewPlayer, modifier: Modifier = Modifier, coverSize: Dp = 44.dp) {
    val metadata = player.metadata ?: return
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player.isPlaying, player.currentId) {
        progress = player.progress()
        while (player.isPlaying) {
            delay(250)
            progress = player.progress()
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(metadata.artworkUri, coverSize)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        metadata.title?.toString().orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        player.error ?: listOfNotNull(metadata.artist, "30 s preview").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (player.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = player::toggle) {
                    Icon(
                        if (player.isPlaying) AppIcons.Pause else AppIcons.Play,
                        contentDescription = if (player.isPlaying) "Pause" else "Play",
                    )
                }
            }
        }
    }
}
