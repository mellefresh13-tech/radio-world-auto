package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationApiMapperTest {

    @Test
    fun preservesTrackMetadataWhenMappingCachedStationForSameVersionSync() {
        val station = Station(
            id = "station-1",
            name = "Example FM",
            country = "Germany",
            countryCode = "DE",
            city = "Berlin",
            genre = "Rock",
            language = "de",
            streams = listOf("https://radio.example/live", "https://radio.example/backup"),
            songTitle = "Current Track",
            artist = "Current Artist",
            logo = "https://radio.example/logo.png",
            website = "https://radio.example"
        )

        val mapped = StationApiMapper.fromStation(station)

        assertEquals(station.id, mapped.id)
        assertEquals(station.name, mapped.name)
        assertEquals(station.countryCode, mapped.country)
        assertEquals(station.city, mapped.city)
        assertEquals(listOf(station.language), mapped.languages)
        assertEquals(listOf(station.genre), mapped.genres)
        assertEquals(station.website, mapped.homepage)
        assertEquals(station.logo, mapped.logo)
        assertEquals(station.songTitle, mapped.songTitle)
        assertEquals(station.artist, mapped.artist)
        assertEquals(station.streams, mapped.streams.map { it.url })
        assertEquals(listOf("", ""), mapped.streams.map { it.status })
    }

    @Test
    fun blankOptionalFieldsRemainAbsentWhenMappingCachedStation() {
        val station = Station(
            id = "station-2",
            name = "No Metadata FM",
            country = "Unknown",
            countryCode = "ZZ",
            city = "",
            genre = "",
            language = "",
            streams = listOf("https://radio.example/live"),
            songTitle = null,
            artist = null,
            logo = null,
            website = null
        )

        val mapped = StationApiMapper.fromStation(station)

        assertNull(mapped.city)
        assertEquals(emptyList<String>(), mapped.languages)
        assertEquals(emptyList<String>(), mapped.genres)
        assertNull(mapped.homepage)
        assertNull(mapped.logo)
        assertNull(mapped.songTitle)
        assertNull(mapped.artist)
    }
}
