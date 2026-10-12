package io.github.halilozel1903.deezer.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

/**
 * Pocket Charts, a phone app on deezer-kotlin: today's charts, artists, search with advanced filters and 30 second
 * previews, plus an in-app preview of the Android Auto browse tree that `SampleMediaService` serves to the car.
 * Everything is loaded from the live public Deezer API, with cover images through Coil.
 *
 * `scripts/screenshots.sh` starts it with `--es scene <scene>` to open a fixed screen:
 *
 * - `charts`: the overall charts
 * - `artist`: Daft Punk's top tracks, albums and related artists
 * - `search`: tracks and artists for "night drive"
 * - `car`: the car browse preview (landscape)
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val scene = Scene.from(intent.getStringExtra(EXTRA_SCENE))
        setContent {
            SampleTheme(dark = isSystemInDarkTheme()) {
                // The Surface makes text default to onBackground, so it stays readable in dark mode.
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    SampleApp(scene = scene, repository = Repository.get(applicationContext))
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCENE = "scene"
    }
}
