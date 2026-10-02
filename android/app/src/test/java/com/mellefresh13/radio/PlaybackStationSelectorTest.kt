package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStationSelectorTest {

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
    fun nextStation_skipsFailedStationsAndWrapsAround() {
        val catalog = listOf(station("a"), station("b"), station("c"), station("d"))

        val result = PlaybackStationSelector.nextPlayableStation(
            catalog = catalog,
            currentStationId = "c",
            failedStationIds = setOf("d")
        )

        assertEquals("a", result?.id)
    }

    @Test
    fun nextStation_doesNotUseEmptyStreamStations() {
        val catalog = listOf(
            station("a"),
            station("b", emptyList()),
            station("c")
        )

        val result = PlaybackStationSelector.nextPlayableStation(
            catalog = catalog,
            currentStationId = "a",
            failedStationIds = emptySet()
        )

        assertEquals("c", result?.id)
    }

    @Test
    fun nextStation_returnsNullWhenNoCandidateExists() {
        val catalog = listOf(station("a"), station("b"))

        val result = PlaybackStationSelector.nextPlayableStation(
            catalog = catalog,
            currentStationId = "a",
            failedStationIds = setOf("b")
        )

        assertNull(result)
    }

    @Test
    fun nextStation_usesFirstPlayableWhenCurrentIsMissing() {
        val catalog = listOf(station("a"), station("b"), station("c"))

        val result = PlaybackStationSelector.nextPlayableStation(
            catalog = catalog,
            currentStationId = "missing",
            failedStationIds = emptySet()
        )

        assertEquals("a", result?.id)
    }

    @Test
    fun nextStation_returnsNullWhenAllPlayableStationsAreFailed() {
        val catalog = listOf(station("a"), station("b"), station("c"))

        val result = PlaybackStationSelector.nextPlayableStation(
            catalog = catalog,
            currentStationId = "a",
            failedStationIds = setOf("a", "b", "c")
        )

        assertNull(result)
    }

    @Test
    fun nextStation_deduplicatesByStationId() {
        val catalog = listOf(station("a"), station("b"), station("b"))

        val result = PlaybackStationSelector.nextPlayableStation(
            catalog = catalog,
            currentStationId = "a",
            failedStationIds = emptySet()
        )

        assertTrue(result != null)
        assertEquals("b", result?.id)
    }
}
