package io.github.halilozel1903.deezer.sample

import android.content.Context
import io.github.halilozel1903.deezer.DeezerClient
import io.github.halilozel1903.deezer.DeezerException
import io.github.halilozel1903.deezer.browse.DeezerBrowseTree
import io.github.halilozel1903.deezer.cache.DeezerCache
import io.github.halilozel1903.deezer.cache.plus
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.delay
import java.io.File

/**
 * The app's single [DeezerClient] on the live public API, with OkHttp (the HTTP stack Coil uses too) and a memory
 * plus disk cache, and the browse tree that the car preview renders.
 */
class Repository private constructor(context: Context) {

    val client: DeezerClient = deezerClient(context)

    val tree: DeezerBrowseTree = DeezerBrowseTree(client, groupSize = 8)

    companion object {
        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context.applicationContext).also { instance = it } }

        /** The client setup shared by the app and [SampleMediaService]. */
        fun deezerClient(context: Context): DeezerClient = DeezerClient {
            engine = OkHttp.create()
            cache = DeezerCache.memory() + DeezerCache.disk(File(context.cacheDir, "deezer"))
        }
    }
}

/**
 * Runs [block] up to [attempts] times, waiting 2, 4, 8... seconds between tries. Only failures that may go away
 * (network, quota, server errors) are retried; a missing id fails at once. The client already retries each request
 * a couple of times; this adds a slower outer loop for a flaky network, which screenshot runs on CI rely on.
 */
suspend fun <T> retrying(attempts: Int, block: suspend () -> T): T {
    var wait = 2_000L
    repeat(attempts - 1) {
        try {
            return block()
        } catch (e: DeezerException.NotFound) {
            throw e
        } catch (e: DeezerException) {
            delay(wait)
            wait *= 2
        }
    }
    return block()
}
