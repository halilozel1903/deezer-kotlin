package io.github.halilozel1903.deezer

import io.github.halilozel1903.deezer.model.DeezerPage
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PagingTest {

    @Test
    fun pagesFollowNextLinksAndStopAtTheEnd() = runTest {
        MockDeezer().use { api ->
            val pages = api.client.chartTrackPages().toList()
            assertEquals(listOf(25, 15), pages.map { it.data.size })
            assertEquals(
                listOf(
                    "https://api.deezer.com/chart/0/tracks?index=0&limit=25",
                    "https://api.deezer.com/chart/0/tracks?index=25&limit=25",
                ),
                api.urls,
            )
        }
    }

    @Test
    fun itemsAreRequestedLazily() = runTest {
        MockDeezer().use { api ->
            val first = api.client.chartTrackPages().items().take(10).toList()
            assertEquals(10, first.size)
            assertEquals(1, api.requests.size)
            val all = api.client.chartTrackPages().items().toList()
            assertEquals((1..40).toList(), all.map { it.position })
        }
    }

    @Test
    fun maxPagesAndEmptyPagesStop() = runTest {
        MockDeezer().use { api ->
            assertEquals(1, api.client.chartTrackPages(maxPages = 1).toList().size)
            // Without next links the flow follows total.
            val synthetic = api.client.pages(pageSize = 2) { index, _ ->
                DeezerPage(data = listOf(index, index + 1).filter { it < 5 }, total = 5)
            }.toList()
            assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), synthetic.map { it.data })
            val empty = api.client.pages<Int> { _, _ -> DeezerPage(next = "https://api.deezer.com/x?index=99") }.toList()
            assertEquals(1, empty.size)
        }
    }

    @Test
    fun nextIndexIsReadFromTheLink() {
        assertEquals(50, DeezerPage<Int>(next = "https://api.deezer.com/search/track?q=a&index=50").nextIndex)
        assertEquals(0, DeezerPage<Int>(prev = "https://api.deezer.com/chart/0/tracks?limit=10").prevIndex)
        assertEquals(null, DeezerPage<Int>().nextIndex)
    }
}
