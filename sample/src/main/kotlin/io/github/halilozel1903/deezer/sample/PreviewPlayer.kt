package io.github.halilozel1903.deezer.sample

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Plays 30 second previews through [SampleMediaService], the same session Android Auto uses. The app only sends
 * media ids such as `track/3135556` or `album/302127`; the service resolves them to fresh preview urls.
 *
 * It connects on the first [play], or at once when [connectNow] is set, so a screenshot run never starts the service.
 */
@Stable
class PreviewPlayer(private val context: Context, private val scope: CoroutineScope) {

    /** The media id of the item that is playing or paused, `null` when nothing is queued. */
    var currentId: String? by mutableStateOf(null)
        private set

    var metadata: MediaMetadata? by mutableStateOf(null)
        private set

    var isPlaying: Boolean by mutableStateOf(false)
        private set

    /** The last playback error, cleared by the next [play]. */
    var error: String? by mutableStateOf(null)
        private set

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val connectLock = Mutex()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)

        override fun onPlayerError(e: PlaybackException) {
            error = e.message ?: "Playback failed"
        }
    }

    private suspend fun controller(): MediaController = connectLock.withLock {
        controller?.let { return@withLock it }
        val token = SessionToken(context, ComponentName(context, SampleMediaService::class.java))
        val pending = MediaController.Builder(context, token).buildAsync().also { future = it }
        try {
            pending.await().also {
                it.addListener(listener)
                controller = it
                sync(it)
            }
        } catch (e: Exception) {
            if (future === pending) future = null
            throw e
        }
    }

    /** Connects to the service now, to show what is already playing. */
    fun connectNow() {
        scope.launch { runCatching { controller() } }
    }

    /** Queues [mediaIds] and plays from [index]. Albums, playlists, radios and artists expand to their tracks. */
    fun play(mediaIds: List<String>, index: Int = 0) {
        if (mediaIds.isEmpty()) return
        error = null
        scope.launch {
            val player = try {
                controller()
            } catch (e: Exception) {
                error = "Could not reach the player"
                return@launch
            }
            player.setMediaItems(mediaIds.map { MediaItem.Builder().setMediaId(it).build() }, index.coerceIn(0, mediaIds.lastIndex), 0L)
            player.prepare()
            player.play()
        }
    }

    /** Like [play], but pauses or resumes when the item at [index] is already the current one. */
    fun playOrToggle(mediaIds: List<String>, index: Int) {
        if (controller != null && currentId != null && mediaIds.getOrNull(index) == currentId) toggle() else play(mediaIds, index)
    }

    /** `true` while the item with [mediaId] is current and playing. */
    fun isPlaying(mediaId: String): Boolean = isPlaying && currentId == mediaId

    fun toggle() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    /** How far the current preview is, from 0 to 1. */
    fun progress(): Float {
        val player = controller ?: return 0f
        val duration = player.duration.takeIf { it > 0 } ?: return 0f
        return (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    }

    private fun sync(player: Player) {
        val item = player.currentMediaItem
        currentId = item?.mediaId
        metadata = item?.mediaMetadata
        isPlaying = player.isPlaying
    }

    /** Disconnects. Playback goes on in the service. */
    fun release() {
        // Releases the controller, or the connection still being made.
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }
}

@Composable
fun rememberPreviewPlayer(connectNow: Boolean): PreviewPlayer {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val player = remember { PreviewPlayer(context, scope) }
    DisposableEffect(player) {
        if (connectNow) player.connectNow()
        onDispose { player.release() }
    }
    return player
}
