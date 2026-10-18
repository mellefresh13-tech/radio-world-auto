package com.mellefresh13.radio

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class StationCatalogFilterTest {

    @Test
    fun querySearchesEverySupportedMetadataFieldCaseInsensitively() {
        val station = station(
            id = "de-rock",
            name = "Radio Alpin",
            country = "Germany",
            countryCode = "DE",
            city = "Berlin",
            genre = "Rock",
            language = "German",
            website = "https://alpin.example"
        )
        val queries = listOf(
            "ALPIN" to station.id,
            "germany" to station.id,
            " de " to station.id,
            "BERLIN" to station.id,
            "rock" to station.id,
            "GERMAN" to station.id,
            "ALPIN.EXAMPLE" to station.id
        )

        queries.forEach { (query, expectedId) ->
            assertEquals(
                "query=$query",
                listOf(expectedId),
                StationCatalogFilter.filter(
                    source = listOf(station),
                    query = query,
                    country = null,
                    genre = null,
                    limit = 20,
                    offset = 0
                ).map { it.id }
            )
        }
    }

    @Test
    fun countryAndGenreFiltersRunBeforePagingAndPreserveSourceOrder() {
        val source = listOf(
            station(id = "rock-de-a", countryCode = "DE", genre = "Rock"),
            station(id = "jazz-de", countryCode = "DE", genre = "Jazz"),
            station(id = "rock-de-b", countryCode = "DE", genre = "Rock"),
            station(id = "rock-us", countryCode = "US", genre = "Rock"),
            station(id = "rock-de-c", countryCode = "DE", genre = "Rock")
        )

        val filtered = StationCatalogFilter.filter(
            source = source,
            query = null,
            country = " DE ",
            genre = " rOcK ",
            limit = 2,
            offset = 1
        )

        assertEquals(listOf("rock-de-b", "rock-de-c"), filtered.map { it.id })
    }

    @Test
    fun blankQueryCountryAndGenreAreIgnored() {
        val source = listOf(station(id = "one"), station(id = "two"))

        val filtered = StationCatalogFilter.filter(
            source = source,
            query = "   ",
            country = " ",
            genre = "\t",
            limit = 50,
            offset = 0
        )

        assertEquals(listOf("one", "two"), filtered.map { it.id })
    }

    @Test
    fun searchCaseFoldingDoesNotDependOnDeviceLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            val station = station(id = "indie", name = "INDIE Radio")

            val filtered = StationCatalogFilter.filter(
                source = listOf(station),
                query = "indie",
                country = null,
                genre = null,
                limit = 20,
                offset = 0
            )

            assertEquals(listOf("indie"), filtered.map { it.id })
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    private fun station(
        id: String,
        name: String = "Station $id",
        country: String = "Germany",
        countryCode: String = "DE",
        city: String = "Berlin",
        genre: String = "Other",
        language: String = "German",
        website: String? = null
    ) = Station(
        id = id,
        name = name,
        country = country,
        countryCode = countryCode,
        city = city,
        genre = genre,
        language = language,
        streams = listOf("https://stream.example/$id"),
        website = website
    )
}
