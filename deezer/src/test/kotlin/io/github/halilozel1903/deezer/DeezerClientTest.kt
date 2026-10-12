package io.github.halilozel1903.deezer

import io.github.halilozel1903.deezer.Fixtures.DAYLIGHT
import io.github.halilozel1903.deezer.Fixtures.DAYLIGHT_HOURS
import io.github.halilozel1903.deezer.Fixtures.LATE_NIGHT_DRIVE
import io.github.halilozel1903.deezer.Fixtures.MIRA_VALE
import io.github.halilozel1903.deezer.Fixtures.NIGHT_DRIVE_RADIO
import io.github.halilozel1903.deezer.Fixtures.POP
import io.github.halilozel1903.deezer.model.Track
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeezerClientTest {

    @Test
    fun chartHasEverySection() = runTest {
        MockDeezer().use { api ->
            val chart = api.client.chart()
            assertEquals(10, chart.tracks.data.size)
            assertEquals(10, chart.albums.data.size)
            assertEquals(10, chart.artists.data.size)
            assertEquals(5, chart.playlists.data.size)
            assertEquals(2, chart.podcasts.data.size)
            val first = chart.tracks.data.first()
            assertEquals("Daylight", first.title)
            assertEquals(1, first.position)
            assertEquals("Mira Vale", first.artist?.name)
            assertEquals("Daylight Hours", first.album?.title)
            assertTrue(first.hasPreview)
            assertTrue(first.previewUrl!!.startsWith("https://cdnt-preview.dzcdn.net/"))
            assertEquals("Charts Team", chart.playlists.data.first().owner?.name)
            assertEquals("https://api.deezer.com/chart/0", api.urls.single())
        }
    }

    @Test
    fun chartTracksAreNumberedAndPaged() = runTest {
        MockDeezer().use { api ->
            val page = api.client.chartTracks()
            assertEquals(25, page.data.size)
            assertEquals(40, page.total)
            assertEquals(25, page.nextIndex)
            assertEquals((1..25).toList(), page.data.map { it.position })
            assertEquals("https://api.deezer.com/chart/0/tracks?index=0&limit=25", api.urls.single())
            val second = api.client.nextPage(page, Track.serializer())
            assertNotNull(second)
            assertEquals(15, second.data.size)
            assertEquals(0, second.prevIndex)
            assertNull(second.next)
            assertNull(api.client.nextPage(second, Track.serializer()))
        }
    }

    @Test
    fun artistTopAlbumsAndRelated() = runTest {
        MockDeezer().use { api ->
            val artist = api.client.artist(MIRA_VALE)
            assertEquals("Mira Vale", artist.name)
            assertEquals(1_284_311L, artist.nbFan)
            assertEquals(4, artist.nbAlbum)
            assertTrue(artist.radio)
            assertEquals("1.3M fans", DeezerFormat.fans(artist.nbFan!!))
            assertNotNull(artist.md5Image)

            val top = api.client.artistTopTracks(MIRA_VALE)
            assertEquals(5, top.data.size)
            assertEquals("Daylight", top.data.first().title)
            assertEquals("Main", top.data.first().contributors.single().role)
            assertEquals(5, top.nextIndex)

            val albums = api.client.artistAlbums(MIRA_VALE).data
            assertEquals(listOf("Daylight Hours", "Northern Lights", "Glass City", "First Bloom"), albums.map { it.title })
            assertEquals(listOf("album", "single", "album", "ep"), albums.map { it.recordType })
            assertEquals(2026, albums.first().releaseYear)

            val related = api.client.relatedArtists(MIRA_VALE).data
            assertEquals(6, related.size)
            assertEquals("Harbor Lights", related.first().name)

            val radio = api.client.artistRadio(MIRA_VALE).data
            assertEquals(8, radio.size)
            assertEquals(
                listOf(
                    "https://api.deezer.com/artist/5021347",
                    "https://api.deezer.com/artist/5021347/top?index=0&limit=5",
                    "https://api.deezer.com/artist/5021347/albums?index=0&limit=25",
                    "https://api.deezer.com/artist/5021347/related?index=0&limit=20",
                    "https://api.deezer.com/artist/5021347/radio?index=0&limit=25",
                ),
                api.urls,
            )
        }
    }

    @Test
    fun albumWithGenresAndTracks() = runTest {
        MockDeezer().use { api ->
            val album = api.client.album(DAYLIGHT_HOURS)
            assertEquals("Daylight Hours", album.title)
            assertEquals("Bright Room Records", album.label)
            assertEquals(10, album.nbTracks)
            assertEquals("Pop", album.genres?.data?.single()?.name)
            assertEquals(POP, album.genreId)
            assertEquals(10, album.tracks?.data?.size)
            assertEquals("2026-09-18", album.releaseDate)
            assertEquals(album.duration, album.tracks!!.data.sumOf { it.duration })
            assertNotNull(album.durationText)

            val tracks = api.client.albumTracks(DAYLIGHT_HOURS).data
            assertEquals((1..10).toList(), tracks.map { it.trackPosition })
            assertNull(tracks.first().album)
            assertEquals("Mira Vale", tracks.first().artist?.name)
        }
    }

    @Test
    fun fullTrack() = runTest {
        MockDeezer().use { api ->
            val track = api.client.track(DAYLIGHT)
            assertEquals("Daylight", track.displayTitle)
            assertNotNull(track.isrc)
            assertNotNull(track.bpm)
            assertNotNull(track.gain)
            assertEquals(1, track.trackPosition)
            assertEquals(1, track.diskNumber)
            assertEquals("2026-09-18", track.releaseDate)
            assertEquals("Mira Vale", track.contributors.single().name)
            assertEquals("2:33", track.durationText)
            assertEquals(
                "https://cdn-images.dzcdn.net/images/cover/${track.md5Image}/250x250-000000-80-0-0.jpg",
                track.coverUrl(),
            )
        }
    }

    @Test
    fun reprisesUseTheShortTitle() = runTest {
        MockDeezer().use { api ->
            val reprise = api.client.searchTracks("night drive").data.single { it.titleVersion == "(Reprise)" }
            assertEquals("Night Drive (Reprise)", reprise.title)
            assertEquals("Night Drive", reprise.displayTitle)
        }
    }

    @Test
    fun playlistAndItsTracks() = runTest {
        MockDeezer().use { api ->
            val playlist = api.client.playlist(LATE_NIGHT_DRIVE)
            assertEquals("Late Night Drive", playlist.title)
            assertEquals("Charts Team", playlist.owner?.name)
            assertEquals(playlist.creator, playlist.owner)
            assertEquals(10, playlist.nbTracks)
            assertTrue(playlist.isPublic)
            assertEquals(10, playlist.tracks?.data?.size)
            assertEquals(playlist.duration, playlist.tracks!!.data.sumOf { it.duration })
            assertEquals(10, api.client.playlistTracks(LATE_NIGHT_DRIVE).data.size)
        }
    }

    @Test
    fun genresRadiosAndEditorial() = runTest {
        MockDeezer().use { api ->
            val genres = api.client.genres()
            assertEquals(0L, genres.first().id)
            assertEquals("All", genres.first().name)
            assertEquals("Pop", api.client.genre(POP).name)
            assertEquals(3, api.client.genreArtists(POP).size)
            assertEquals(listOf("Pop Hits"), api.client.genreRadios(POP).map { it.title })

            val radios = api.client.topRadios().data
            assertEquals(6, radios.size)
            assertEquals(6, api.client.radios().size)
            assertEquals("Night Drive", api.client.radio(NIGHT_DRIVE_RADIO).title)
            assertEquals(6, api.client.radioTracks(NIGHT_DRIVE_RADIO).data.size)
            assertTrue(api.client.radioGenres().all { it.radios.isNotEmpty() })

            assertEquals("All", api.client.editorials().first().name)
            assertEquals("All", api.client.editorial(0).name)
            assertEquals(6, api.client.editorialSelection(0).size)
            val releases = api.client.editorialReleases(0)
            assertEquals(8, releases.data.size)
            assertEquals(128, releases.total)
            val pop = api.client.chart(POP)
            assertEquals("Daylight", pop.tracks.data.first().title)
            assertTrue(pop.podcasts.data.isEmpty())
        }
    }

    @Test
    fun advancedSearchIsEncoded() = runTest {
        MockDeezer().use { api ->
            val result = api.client.searchTracks(DeezerQuery("night drive", durationMax = 240), order = SearchOrder.Ranking)
            assertTrue(result.data.isNotEmpty())
            assertTrue(result.data.all { it.duration <= 240 })
            assertEquals(
                "https://api.deezer.com/search/track?q=night%20drive%20dur_max%3A240&order=RANKING&index=0&limit=25",
                api.urls.single(),
            )
            val exact = api.client.searchTracks(DeezerQuery(artist = "Mira Vale", track = "Daylight"), strict = true)
            assertEquals("Daylight", exact.data.single().title)
            assertTrue(api.urls.last().contains("q=artist%3A%22Mira%20Vale%22%20track%3A%22Daylight%22&strict=on"))
            assertEquals(2, api.client.searchArtists("night drive").data.size)
            assertEquals(2, api.client.searchAlbums("night drive").data.size)
            assertFailsWith<IllegalArgumentException> { api.client.searchTracks("   ") }
        }
    }

    @Test
    fun sendsUserAgentAndAcceptsJson() = runTest {
        MockDeezer().use { api ->
            api.client.genres()
            val request = api.requests.single()
            assertTrue(request.headers[HttpHeaders.UserAgent].orEmpty().startsWith("deezer-kotlin/"))
            assertEquals("application/json", request.headers[HttpHeaders.Accept])
        }
    }

    @Test
    fun argumentsAreChecked() = runTest {
        MockDeezer().use { api ->
            assertFailsWith<IllegalArgumentException> { api.client.artist(0) }
            assertFailsWith<IllegalArgumentException> { api.client.genre(-1) }
            assertFailsWith<IllegalArgumentException> { api.client.chartTracks(index = -1) }
            assertFailsWith<IllegalArgumentException> { api.client.chartTracks(limit = 0) }
            assertTrue(api.requests.isEmpty())
        }
    }

    @Test
    fun baseUrlGetsATrailingSlash() {
        DeezerClient { baseUrl = "https://deezer.example.com/api"; rateLimiter = null }.use {
            assertEquals("https://deezer.example.com/api/", it.baseUrl)
        }
    }

    @Test
    fun unknownIdsAreNotFound() = runTest {
        MockDeezer().use { api ->
            val error = assertFailsWith<DeezerException.NotFound> { api.client.album(1) }
            assertEquals(800, error.error?.code)
            assertEquals("DataException", error.error?.type)
            assertEquals("https://api.deezer.com/album/1", error.url)
            assertFalse(api.client.chart().tracks.data.isEmpty())
        }
    }
}
