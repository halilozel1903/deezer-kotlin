package io.github.halilozel1903.deezer

import io.github.halilozel1903.deezer.browse.BrowseKind
import io.github.halilozel1903.deezer.browse.BrowseStrings
import io.github.halilozel1903.deezer.browse.BrowseStyle
import io.github.halilozel1903.deezer.browse.DeezerBrowseTree
import io.github.halilozel1903.deezer.browse.DeezerMediaId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowseTreeTest {

    @Test
    fun mediaIdsRoundTrip() {
        val ids = listOf(
            DeezerMediaId.Root, DeezerMediaId.Charts, DeezerMediaId.ChartTracks, DeezerMediaId.Picks, DeezerMediaId.Genres,
            DeezerMediaId.Radios, DeezerMediaId.Genre(0), DeezerMediaId.Track(3135556), DeezerMediaId.Album(302127),
            DeezerMediaId.Artist(27), DeezerMediaId.Playlist(908622995), DeezerMediaId.Radio(37151),
        )
        for (id in ids) assertEquals(id, DeezerMediaId.parse(id.value))
        assertEquals("album/302127", DeezerMediaId.Album(302127).toString())
        for (bad in listOf("", "album", "album/", "album/x", "track/0", "genre/-1", "podcast/1", "charts/")) {
            assertNull(DeezerMediaId.parse(bad), bad)
        }
    }

    @Test
    fun rootHasFourTabs() = runTest {
        MockDeezer().use { api ->
            val tree = DeezerBrowseTree(api.client)
            assertEquals(listOf("Charts", "Picks", "Genres", "Radios"), tree.children("root").map { it.title })
            assertTrue(tree.children("root", page = 1).isEmpty())
            assertTrue(api.requests.isEmpty())
            assertEquals(BrowseKind.Root, tree.root.kind)
        }
    }

    @Test
    fun chartsAreGroups() = runTest {
        MockDeezer().use { api ->
            val tree = DeezerBrowseTree(api.client, groupSize = 4)
            val items = tree.children("charts")
            assertEquals(
                listOf("Top tracks", "Top albums", "Top playlists", "Top artists"),
                items.mapNotNull { it.groupTitle }.distinct(),
            )
            val tracks = items.filter { it.kind == BrowseKind.Track }
            assertEquals(4, tracks.size)
            val daylight = tracks.first()
            assertEquals("track/${Fixtures.DAYLIGHT}", daylight.mediaId)
            assertEquals("Mira Vale · Daylight Hours", daylight.subtitle)
            assertTrue(daylight.isPlayable && !daylight.isBrowsable)
            assertNotNull(daylight.previewUrl)
            assertTrue(daylight.artworkUrl!!.contains("/500x500-"))
            // "All top tracks" leads to the paged list.
            val all = items.single { it.mediaId == "charts/tracks" }
            assertTrue(all.isBrowsable)
            assertEquals(25, tree.children(all.mediaId, page = 0, pageSize = 25).size)
            assertEquals(15, tree.children(all.mediaId, page = 1, pageSize = 25).size)
            assertTrue(tree.children("charts", page = 1).isEmpty())
            val album = items.first { it.kind == BrowseKind.Album }
            assertEquals("album/${Fixtures.DAYLIGHT_HOURS}", album.mediaId)
            assertEquals(BrowseStyle.List, album.childStyle)
            assertEquals("5 tracks", BrowseStrings().trackCount(5))
        }
    }

    @Test
    fun genresPicksAndRadios() = runTest {
        MockDeezer().use { api ->
            val tree = DeezerBrowseTree(api.client)
            val genres = tree.children("genres")
            assertEquals(8, genres.size)
            assertFalse(genres.any { it.title == "All" })
            val pop = genres.first { it.title == "Pop" }
            assertEquals("genre/132", pop.mediaId)
            val popChart = tree.children(pop.mediaId)
            assertTrue(popChart.isNotEmpty())
            assertTrue(popChart.none { it.mediaId == "charts/tracks" })

            val picks = tree.children("picks")
            assertEquals(listOf("Editor's picks", "New releases"), picks.mapNotNull { it.groupTitle }.distinct())
            assertEquals(picks.size, picks.map { it.mediaId }.toSet().size)

            val radios = tree.children("radios")
            assertEquals(6, radios.size)
            assertTrue(radios.all { it.kind == BrowseKind.Radio && it.isPlayable && !it.isBrowsable })
        }
    }

    @Test
    fun albumsArtistsAndPlaylists() = runTest {
        MockDeezer().use { api ->
            val tree = DeezerBrowseTree(api.client)
            val albumTracks = tree.children("album/${Fixtures.DAYLIGHT_HOURS}")
            assertEquals(10, albumTracks.size)
            // Album track lists have no album object; the album's cover is used.
            assertTrue(albumTracks.all { it.artworkUrl != null && it.album == "Daylight Hours" })

            val artist = tree.children("artist/${Fixtures.MIRA_VALE}")
            assertEquals(listOf("Popular", "Albums"), artist.mapNotNull { it.groupTitle }.distinct())
            assertEquals(5, artist.count { it.groupTitle == "Popular" })

            assertEquals(10, tree.children("playlist/${Fixtures.LATE_NIGHT_DRIVE}").size)
            assertEquals(6, tree.children("radio/${Fixtures.NIGHT_DRIVE_RADIO}").size)
            assertTrue(tree.children("track/${Fixtures.DAYLIGHT}").isEmpty())
            assertTrue(tree.children("nonsense").isEmpty())
        }
    }

    @Test
    fun itemsAreRebuiltFromTheirIds() = runTest {
        MockDeezer().use { api ->
            val tree = DeezerBrowseTree(api.client)
            assertEquals("Daylight", tree.item("track/${Fixtures.DAYLIGHT}")?.title)
            assertEquals("Daylight Hours", tree.item("album/${Fixtures.DAYLIGHT_HOURS}")?.title)
            assertEquals("1.3M fans", tree.item("artist/${Fixtures.MIRA_VALE}")?.subtitle)
            assertEquals("10 tracks", tree.item("playlist/${Fixtures.LATE_NIGHT_DRIVE}")?.subtitle)
            assertEquals("Night Drive", tree.item("radio/${Fixtures.NIGHT_DRIVE_RADIO}")?.title)
            assertEquals("Pop", tree.item("genre/132")?.title)
            assertEquals("Charts", tree.item("charts")?.title)
            assertNull(tree.item("album/1"))
            assertNull(tree.item("bogus"))
        }
    }

    @Test
    fun searchAndPlayback() = runTest {
        MockDeezer().use { api ->
            val tree = DeezerBrowseTree(api.client)
            val results = tree.search("night drive")
            assertEquals(listOf("Tracks", "Albums", "Artists"), results.mapNotNull { it.groupTitle }.distinct())
            assertEquals("Night Drive", results.first().title)
            assertTrue(tree.search("  ").isEmpty())

            val single = tree.playableTracks("track/${Fixtures.DAYLIGHT}")
            assertEquals(1, single.size)
            assertTrue(single.single().previewUrl!!.endsWith(".mp3").not())
            assertTrue(single.single().previewUrl!!.contains(".mp3?hdnea="))
            assertEquals(6, tree.playableTracks("radio/${Fixtures.NIGHT_DRIVE_RADIO}").size)
            assertEquals(10, tree.playableTracks("album/${Fixtures.DAYLIGHT_HOURS}").size)
            assertEquals(5, tree.playableTracks("artist/${Fixtures.MIRA_VALE}").size)
            assertTrue(tree.playableTracks("genres").isEmpty())
        }
    }
}
