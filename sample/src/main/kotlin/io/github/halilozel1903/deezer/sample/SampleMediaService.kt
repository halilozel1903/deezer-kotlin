package io.github.halilozel1903.deezer.sample

import io.github.halilozel1903.deezer.DeezerClient
import io.github.halilozel1903.deezer.media.DeezerMediaLibraryService

/**
 * The media service Android Auto (and the app's own [PreviewPlayer]) connects to. Everything comes from
 * [DeezerMediaLibraryService]; this subclass only swaps in the OkHttp engine the rest of the app uses.
 */
class SampleMediaService : DeezerMediaLibraryService() {
    override fun createClient(): DeezerClient = Repository.deezerClient(this)
}
