package io.github.halilozel1903.deezer.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** The state of data loaded from the Deezer API. There is no offline fallback: it either loads or fails. */
@Immutable
sealed interface Load<out T> {
    data object Loading : Load<Nothing>

    data class Ready<T>(val value: T) : Load<T>

    data class Failed(val error: Throwable) : Load<Nothing>
}

/** A [Load] and the function that loads it again. */
class Loaded<T>(val state: Load<T>, val retry: () -> Unit)

/** The repository of the running app. */
val LocalRepository = compositionLocalOf<Repository> { error("No Repository provided") }

/** How many times a load is tried before it fails: more in screenshot scenes, which must not end on an error. */
val LocalLoadAttempts = compositionLocalOf { 2 }

/**
 * Loads [block] whenever [keys] change, retrying with [retrying]. [debounceMillis] waits before the first request,
 * for loads that follow typing.
 */
@Composable
fun <T> load(vararg keys: Any?, debounceMillis: Long = 0, block: suspend Repository.() -> T): Loaded<T> {
    val repository = LocalRepository.current
    val attempts = LocalLoadAttempts.current
    var reloads by remember { mutableIntStateOf(0) }
    val state by produceState<Load<T>>(Load.Loading, *keys, reloads) {
        value = Load.Loading
        if (debounceMillis > 0) delay(debounceMillis)
        value = try {
            Load.Ready(retrying(attempts) { repository.block() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Failed(e)
        }
    }
    return Loaded(state) { reloads++ }
}
