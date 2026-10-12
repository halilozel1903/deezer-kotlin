package io.github.halilozel1903.deezer

import io.github.halilozel1903.deezer.model.Album
import io.github.halilozel1903.deezer.transport.FixtureTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HelpersTest {

    @Test
    fun queryBuildsDeezerSyntax() {
        assertEquals("""artist:"aloe blacc" track:"i need a dollar"""", DeezerQuery(artist = "aloe blacc", track = "i need a dollar").toString())
        assertEquals(
            """night drive album:"Night Drive" label:"Low Orbit" dur_min:120 dur_max:240 bpm_min:90 bpm_max:120""",
            DeezerQuery("  night drive ", album = "Night Drive", label = "Low Orbit", durationMin = 120, durationMax = 240, bpmMin = 90, bpmMax = 120).toString(),
        )
        // Quotes inside values would end the field early, so they are dropped.
        assertEquals("""track:"say hello"""", DeezerQuery(track = "say \"hello\"").toString())
        assertTrue(DeezerQuery().isEmpty)
        assertTrue(DeezerQuery(text = "  ", artist = "\"\"").isEmpty)
        assertFalse(DeezerQuery(bpmMin = 100).isEmpty)
    }

    @Test
    fun formatsDurationsAndCounts() {
        assertEquals("0:00", DeezerFormat.duration(-5))
        assertEquals("3:05", DeezerFormat.duration(185))
        assertEquals("1:01:01", DeezerFormat.duration(3661))
        assertEquals("950", DeezerFormat.compact(950))
        assertEquals("12.4K", DeezerFormat.compact(12_400))
        assertEquals("1K", DeezerFormat.compact(1_000))
        assertEquals("250K", DeezerFormat.compact(250_400))
        assertEquals("3.1M", DeezerFormat.compact(3_100_000))
        assertEquals("2B", DeezerFormat.compact(2_000_000_000))
        assertEquals("1 fan", DeezerFormat.fans(1))
        assertEquals("98.4K fans", DeezerFormat.fans(98_417))
    }

    @Test
    fun imageUrlsFromMd5() {
        val md5 = "2e018122cb56986277102d2041a592c8"
        assertEquals("https://cdn-images.dzcdn.net/images/cover/$md5/500x500-000000-80-0-0.jpg", DeezerImages.url(DeezerImages.Type.Cover, md5, 500))
        assertEquals("https://cdn-images.dzcdn.net/images/artist/$md5/1900x1900-000000-80-0-0.jpg", DeezerImages.url(DeezerImages.Type.Artist, md5, 5000))
        assertEquals(md5, DeezerImages.md5From("https://cdn-images.dzcdn.net/images/artist/$md5/56x56-000000-80-0-0.jpg"))
        assertNull(DeezerImages.md5From("https://api.deezer.com/artist/27/image"))
        assertNull(DeezerImages.md5From(null))
        assertFailsWith<IllegalArgumentException> { DeezerImages.url(DeezerImages.Type.Cover, " ") }
    }

    @Test
    fun pictureForPicksTheClosestSize() {
        val album = Album(id = 1, coverSmall = "s", coverMedium = "m", coverBig = "b", coverXl = null)
        assertEquals("s", album.pictureFor(40))
        assertEquals("m", album.pictureFor(200))
        assertEquals("b", album.pictureFor(500))
        // No XL: the next best size.
        assertEquals("b", album.pictureFor(1000))
        assertNull(Album(id = 2).pictureFor(100))
    }

    @Test
    fun fixturePaths() {
        assertEquals("chart/0.json", FixtureTransport.pathFor("https://api.deezer.com/chart/0"))
        assertEquals("chart/0/tracks.json", FixtureTransport.pathFor("https://api.deezer.com/chart/0/tracks?index=0&limit=10"))
        assertEquals("chart/0/tracks-index-10.json", FixtureTransport.pathFor("https://api.deezer.com/chart/0/tracks?index=10&limit=10"))
        assertEquals("artist/27/top.json", FixtureTransport.pathFor("https://api.deezer.com/artist/27/top?limit=5"))
        assertEquals(
            "search/track/artist-mira-vale-daylight.json",
            FixtureTransport.pathFor("https://api.deezer.com/search/track?q=artist%3A%22Mira%20Vale%22%20Daylight&order=RANKING"),
        )
        assertEquals("genre.json", FixtureTransport.pathFor("https://mirror.example.com/genre", baseUrl = "https://mirror.example.com"))
        assertNull(FixtureTransport.pathFor("https://example.com/chart/0"))
        assertNull(FixtureTransport.pathFor("https://api.deezer.com/"))
    }
}
