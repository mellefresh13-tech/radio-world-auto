package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackAdjacentStationPolicyTest {
    private fun station(id: String, streams: List<String> = listOf("https://stream/$id")) =
        Station(
            id = id,
            name = id,
            country = "Test",
            countryCode = "ZZ",
            city = "",
            genre = "Test",
            language = "en",
            streams = streams
        )

    @Test
    fun nextAndPreviousFollowCatalogOrder() {
        val catalog = listOf(station("a"), station("b"), station("c"))
        assertEquals("c", PlaybackAdjacentStationPolicy.resolve(catalog, "a", -1)?.id)
        assertEquals("c", PlaybackAdjacentStationPolicy.resolve(catalog, "b", 1)?.id)
        assertEquals("a", PlaybackAdjacentStationPolicy.resolve(catalog, "b", -1)?.id)
    }

    @Test
    fun skipsStationsWithoutStreamsAndDeduplicatesIds() {
        val catalog = listOf(
            station("a"),
            station("b", emptyList()),
            station("c"),
            station("c"),
            station("d")
        )
        assertEquals("c", PlaybackAdjacentStationPolicy.resolve(catalog, "a", 1)?.id)
        assertEquals("d", PlaybackAdjacentStationPolicy.resolve(catalog, "c", 1)?.id)
    }

    @Test
    fun returnsNullForEmptyCatalogOrSingleCurrentStation() {
        assertNull(PlaybackAdjacentStationPolicy.resolve(emptyList(), "a", 1))
        assertNull(PlaybackAdjacentStationPolicy.resolve(listOf(station("a")), "a", 1))
    }
}
