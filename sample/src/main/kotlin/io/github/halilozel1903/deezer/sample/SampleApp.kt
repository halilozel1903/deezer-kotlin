package io.github.halilozel1903.deezer.sample

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** The bottom navigation destinations. */
private enum class Tab(val label: String, val icon: ImageVector, val root: Screen) {
    Charts("Charts", AppIcons.Charts, Screen.Charts),
    Search("Search", AppIcons.Search, Screen.Search()),
    Car("Car", AppIcons.Car, Screen.Car),
}

private fun Screen.tab(): Tab = when (this) {
    Screen.Charts, is Screen.Artist -> Tab.Charts
    is Screen.Search -> Tab.Search
    Screen.Car -> Tab.Car
}

/** A [Screen] as a string, so the back stack survives rotation and process death. */
private fun Screen.encode(): String = when (this) {
    Screen.Charts -> "charts"
    is Screen.Artist -> "artist/$id"
    is Screen.Search -> "search/$query"
    Screen.Car -> "car"
}

private fun decodeScreen(value: String): Screen? = when {
    value == "charts" -> Screen.Charts
    value == "car" -> Screen.Car
    value.startsWith("artist/") -> value.removePrefix("artist/").toLongOrNull()?.let { Screen.Artist(it) }
    value.startsWith("search/") -> Screen.Search(value.removePrefix("search/"))
    else -> null
}

@Composable
fun SampleApp(scene: Scene?, repository: Repository) {
    val stack = rememberSaveable(
        saver = listSaver<SnapshotStateList<Screen>, String>(
            save = { list -> list.map { it.encode() } },
            restore = { saved -> saved.mapNotNull { decodeScreen(it) }.ifEmpty { listOf(Screen.Charts) }.toMutableStateList() },
        ),
    ) { (scene?.backStack() ?: listOf(Screen.Charts)).toMutableStateList() }
    val player = rememberPreviewPlayer(connectNow = scene == null)
    val current = stack.last()

    fun open(screen: Screen) {
        stack.add(screen)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun select(tab: Tab) {
        if (current.tab() == tab && stack.size == 1) return
        stack.clear()
        stack.add(tab.root)
    }

    BackHandler(enabled = stack.size > 1) { back() }

    CompositionLocalProvider(
        LocalRepository provides repository,
        LocalLoadAttempts provides if (scene != null) 4 else 2,
    ) {
        if (current == Screen.Car) {
            // Full screen, like the car display.
            CarScreen(player = player, onExit = { select(Tab.Charts) })
        } else {
            PhoneScaffold(current, player, scene, ::open, ::back, ::select)
        }
    }
}

@Composable
private fun PhoneScaffold(
    current: Screen,
    player: PreviewPlayer,
    scene: Scene?,
    open: (Screen) -> Unit,
    back: () -> Unit,
    select: (Tab) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            Column {
                NowPlayingBar(player)
                NavigationBar {
                    for (tab in Tab.entries) {
                        NavigationBarItem(
                            selected = current.tab() == tab,
                            onClick = { select(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Each screen handles the status bar itself (the artist picture runs under it); lists end above the bottom bar.
        val contentPadding = PaddingValues(top = 0.dp, bottom = padding.calculateBottomPadding())
        val modifier = Modifier.fillMaxSize()
        when (current) {
            Screen.Charts -> Column(modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                ChartsScreen(player, onArtist = { open(Screen.Artist(it)) }, onSearch = { select(Tab.Search) }, contentPadding = contentPadding)
            }
            is Screen.Artist -> Column(modifier) {
                // A new artist gets a fresh list, scrolled to the top.
                key(current.id) {
                    ArtistScreen(current.id, player, onArtist = { open(Screen.Artist(it)) }, onBack = back, contentPadding = contentPadding)
                }
            }
            is Screen.Search -> Column(modifier) {
                SearchScreen(
                    initialQuery = current.query,
                    initialFilter = if (scene == Scene.Search) LengthFilter.UnderFour else LengthFilter.AnyLength,
                    player = player,
                    onArtist = { open(Screen.Artist(it)) },
                    contentPadding = contentPadding,
                )
            }
            Screen.Car -> Unit
        }
    }
}
