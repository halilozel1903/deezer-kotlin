package io.github.halilozel1903.deezer.media

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import io.github.halilozel1903.deezer.DeezerClient
import io.github.halilozel1903.deezer.browse.DeezerBrowseTree
import io.github.halilozel1903.deezer.cache.DeezerCache
import io.github.halilozel1903.deezer.cache.plus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import java.io.File

/**
 * A Media3 [MediaLibraryService] for Android Auto (and any other media browser) on the public Deezer API. Android
 * Auto shows four tabs (Charts, Picks, Genres, Radios), lets the driver browse albums, playlists, artists and genres,
 * search by voice or text, and plays 30 second previews with ExoPlayer.
 *
 * Declare it (or a subclass) in the app manifest:
 *
 * ```xml
 * <service
 *     android:name="io.github.halilozel1903.deezer.media.DeezerMediaLibraryService"
 *     android:exported="true"
 *     android:foregroundServiceType="mediaPlayback">
 *     <intent-filter>
 *         <action android:name="androidx.media3.session.MediaLibraryService" />
 *         <action android:name="android.media.browse.MediaBrowserService" />
 *     </intent-filter>
 * </service>
 * <meta-data
 *     android:name="com.google.android.gms.car.application"
 *     android:resource="@xml/deezer_automotive_app_desc" />
 * ```
 *
 * Override the `create` functions to change the client (engine, cache), the tree (titles, group sizes), the player
 * or the callback.
 */
public open class DeezerMediaLibraryService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private var deezer: DeezerClient? = null

    /** Cancelled when the service is destroyed. Runs on the main thread, like the session callbacks. */
    protected val scope: CoroutineScope = MainScope()

    /** The client: Ktor's CIO engine with a memory and disk cache by default. */
    protected open fun createClient(): DeezerClient = DeezerClient {
        cache = DeezerCache.memory() + DeezerCache.disk(File(cacheDir, "deezer"))
    }

    /** The browse tree on [client]. */
    protected open fun createBrowseTree(client: DeezerClient): DeezerBrowseTree = DeezerBrowseTree(client)

    /** ExoPlayer with music audio attributes, audio focus and pausing when headphones are unplugged. */
    protected open fun createPlayer(): Player = ExoPlayer.Builder(this)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    /** The session callback; [DeezerLibraryCallback] by default. */
    protected open fun createCallback(tree: DeezerBrowseTree): MediaLibrarySession.Callback =
        DeezerLibraryCallback(this, tree, scope)

    /** Opened when the user taps the media notification or the app in the car: the app's launcher activity. */
    protected open fun createSessionActivity(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    override fun onCreate() {
        super.onCreate()
        val client = createClient().also { deezer = it }
        val builder = MediaLibrarySession.Builder(this, createPlayer(), createCallback(createBrowseTree(client)))
        createSessionActivity()?.let { builder.setSessionActivity(it) }
        session = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** Stops the service when the app is swiped away while nothing plays. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        scope.cancel()
        deezer?.close()
        deezer = null
        super.onDestroy()
    }
}
