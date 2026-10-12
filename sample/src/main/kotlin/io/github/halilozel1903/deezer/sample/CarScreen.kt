package io.github.halilozel1903.deezer.sample

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.deezer.browse.BrowseItem
import io.github.halilozel1903.deezer.browse.BrowseKind
import io.github.halilozel1903.deezer.browse.BrowseStyle
import io.github.halilozel1903.deezer.browse.DeezerMediaId
import kotlinx.coroutines.delay
import java.util.Date

/**
 * A preview of what Android Auto shows for this app: the same [io.github.halilozel1903.deezer.browse.DeezerBrowseTree]
 * that [SampleMediaService] serves to the car, drawn here the way the car lays it out (tabs, group headers, list
 * rows and grid tiles). It is a mock of the car screen, not Android Auto itself; use the Desktop Head Unit to try
 * the real thing.
 */
@Composable
fun CarScreen(player: PreviewPlayer, onExit: () -> Unit) {
    val tree = LocalRepository.current.tree
    val tabs = remember(tree) { tree.tabs }
    var tab by rememberSaveable { mutableStateOf(tabs.first().mediaId) }
    val stack = remember { mutableStateListOf<BrowseItem>() }
    val folder = stack.lastOrNull() ?: tabs.first { it.mediaId == tab }
    val children = load(folder.mediaId) { tree.children(folder.mediaId, page = 0, pageSize = 40) }

    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    // The car screen is always dark.
    SampleTheme(dark = true) {
        Surface(color = CarBackground, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                CarRail(
                    tabs = tabs,
                    selected = tab,
                    onSelect = {
                        tab = it
                        stack.clear()
                    },
                    onExit = onExit,
                )
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    CarTopBar(
                        title = folder.title,
                        onBack = if (stack.isNotEmpty()) ({ stack.removeAt(stack.lastIndex) }) else null,
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (val state = children.state) {
                            Load.Loading -> LoadingView()
                            is Load.Failed -> ErrorView(state.error, children.retry)
                            is Load.Ready -> {
                                val items = state.value
                                val onClick: (BrowseItem) -> Unit = { item ->
                                    if (item.isBrowsable) {
                                        stack.add(item)
                                    } else if (item.isPlayable) {
                                        val playable = items.filter { it.isPlayable && !it.isBrowsable }
                                        player.playOrToggle(playable.map { it.mediaId }, playable.indexOf(item))
                                    }
                                }
                                when {
                                    items.isEmpty() -> Text(
                                        "Nothing here right now",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.align(Alignment.Center),
                                    )
                                    folder.childStyle == BrowseStyle.Grid -> CarGrid(items, onClick)
                                    else -> CarList(items, player, onClick)
                                }
                            }
                        }
                    }
                    NowPlayingBar(player, coverSize = 48.dp)
                }
            }
        }
    }
}

private val CarBackground = Color(0xFF101114)
private val CarRailColor = Color(0xFF1B1C20)

private fun tabIcon(mediaId: String): ImageVector = when (DeezerMediaId.parse(mediaId)) {
    DeezerMediaId.Charts -> AppIcons.Charts
    DeezerMediaId.Picks -> AppIcons.Star
    DeezerMediaId.Genres -> AppIcons.Grid
    else -> AppIcons.Radio
}

@Composable
private fun CarRail(tabs: List<BrowseItem>, selected: String, onSelect: (String) -> Unit, onExit: () -> Unit) {
    Column(
        modifier = Modifier.width(96.dp).fillMaxHeight().background(CarRailColor).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (tab in tabs) {
            val isSelected = tab.mediaId == selected
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { onSelect(tab.mediaId) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    tabIcon(tab.mediaId),
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                )
                Text(
                    tab.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onExit) {
            Icon(AppIcons.Close, contentDescription = "Leave car preview", tint = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun CarTopBar(title: String, onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val clock by produceState(DateFormat.getTimeFormat(context).format(Date())) {
        while (true) {
            delay(15_000)
            value = DateFormat.getTimeFormat(context).format(Date())
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 8.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Back") }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Car browse preview",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1,
        )
        Spacer(Modifier.width(16.dp))
        Text(clock, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}

/** Rows under group headers, like Android Auto's list style. */
@Composable
private fun CarList(items: List<BrowseItem>, player: PreviewPlayer, onClick: (BrowseItem) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        itemsIndexed(items, key = { _, item -> item.mediaId }) { index, item ->
            val group = item.groupTitle
            if (group != null && group != items.getOrNull(index - 1)?.groupTitle) {
                Text(
                    group,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 12.dp, top = if (index == 0) 4.dp else 16.dp, bottom = 4.dp),
                )
            }
            CarRow(item, isPlaying = player.isPlaying(item.mediaId), onClick = { onClick(item) })
        }
    }
}

@Composable
private fun CarRow(item: BrowseItem, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.artworkUrl != null) {
            Cover(item.artworkUrl, 52.dp, shape = if (item.kind == BrowseKind.Artist) CircleShape else RoundedCornerShape(6.dp))
        } else {
            Box(
                Modifier.size(52.dp).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Charts, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.subtitle?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isExplicit) {
                        ExplicitMark()
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (item.isBrowsable) {
            Icon(AppIcons.Chevron, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
        } else if (isPlaying) {
            Icon(AppIcons.Pause, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Tiles, like Android Auto's grid style. */
@Composable
private fun CarGrid(items: List<BrowseItem>, onClick: (BrowseItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(132.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.mediaId }) { _, item ->
            Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClick(item) }) {
                Cover(
                    item.artworkUrl,
                    132.dp,
                    Modifier.fillMaxWidth().aspectRatio(1f),
                    shape = if (item.kind == BrowseKind.Artist) CircleShape else RoundedCornerShape(10.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
