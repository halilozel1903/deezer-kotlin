package io.github.halilozel1903.deezer.sample

/**
 * Screenshot scenes, picked with the `scene` intent extra. They load the live Deezer API like a normal launch, but
 * retry a few times before showing an error, so a slow network on CI does not end in an empty screen.
 */
enum class Scene(val key: String) {
    Charts("charts"),
    Artist("artist"),
    Search("search"),
    Car("car"),
    ;

    /** The screens to open, bottom of the back stack first. */
    fun backStack(): List<Screen> = when (this) {
        Charts -> listOf(Screen.Charts)
        Artist -> listOf(Screen.Charts, Screen.Artist(ARTIST_ID))
        Search -> listOf(Screen.Search(SEARCH_QUERY))
        Car -> listOf(Screen.Car)
    }

    companion object {
        /** Daft Punk, an artist with a long, stable catalogue on Deezer. */
        const val ARTIST_ID = 27L

        /** The query of the search scene. */
        const val SEARCH_QUERY = "midnight city"

        fun from(key: String?): Scene? = entries.firstOrNull { it.key == key }
    }
}

/** One screen of the app's back stack. */
sealed interface Screen {
    data object Charts : Screen

    data class Artist(val id: Long) : Screen

    data class Search(val query: String = "") : Screen

    data object Car : Screen
}
