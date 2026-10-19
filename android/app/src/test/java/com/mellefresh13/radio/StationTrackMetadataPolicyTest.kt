package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationTrackMetadataPolicyTest {

    @Test
    fun retainsLocalTrackMetadataWhenCatalogOmitsIt() {
        val existing = listOf(
            Station(
                id = "station-1",
                name = "Example FM",
                country = "Germany",
                countryCode = "DE",
                city = "Berlin",
                genre = "Rock",
                language = "de",
                streams = listOf("https://radio.example/live"),
                songTitle = "Current Track",
                artist = "Current Artist"
            )
        )
        val incoming = listOf(
            ApiStation(
                id = "station-1",
                name = "Example FM",
                country = "DE",
                city = "Berlin",
                languages = listOf("de"),
                genres = listOf("Rock"),
                homepage = null,
                logo = null,
                songTitle = null,
                artist = null,
                streams = listOf(ApiStream("https://radio.example/live", null, null, false, "online"))
            )
        )

        val preserved = StationTrackMetadataPolicy.preserve(existing, incoming)

        assertEquals("Current Track", preserved.single().songTitle)
        assertEquals("Current Artist", preserved.single().artist)
    }

    @Test
    fun nonblankIncomingMetadataWinsAndBlankIncomingValuesFallBack() {
        val existing = listOf(
            Station(
                id = "station-1",
                name = "Example FM",
                country = "Germany",
                countryCode = "DE",
                city = "",
                genre = "Other",
                language = "",
                streams = listOf("https://radio.example/live"),
                songTitle = "Old Track",
                artist = "Old Artist"
            )
        )
        val incoming = listOf(
            ApiStation(
                id = "station-1",
                name = "Example FM",
                country = "DE",
                city = null,
                languages = emptyList(),
                genres = emptyList(),
                homepage = null,
                logo = null,
                songTitle = "  ",
                artist = "New Artist",
                streams = emptyList()
            )
        )

        val preserved = StationTrackMetadataPolicy.preserve(existing, incoming).single()

        assertEquals("Old Track", preserved.songTitle)
        assertEquals("New Artist", preserved.artist)
    }

    @Test
    fun doesNotCopyMetadataBetweenDifferentStationIds() {
        val existing = listOf(
            Station(
                id = "old-station",
                name = "Old FM",
                country = "Germany",
                countryCode = "DE",
                city = "",
                genre = "Other",
                language = "",
                streams = listOf("https://radio.example/live"),
                songTitle = "Old Track",
                artist = "Old Artist"
            )
        )
        val incoming = listOf(
            ApiStation(
                id = "new-station",
                name = "New FM",
                country = "DE",
                city = null,
                languages = emptyList(),
                genres = emptyList(),
                homepage = null,
                logo = null,
                songTitle = null,
                artist = null,
                streams = emptyList()
            )
        )

        val mapped = StationTrackMetadataPolicy.preserve(existing, incoming).single()

        assertNull(mapped.songTitle)
        assertNull(mapped.artist)
    }
}
