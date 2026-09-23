package com.mellefresh13.radio

interface CatalogRepository {
    fun loadStations(
        query: String? = null,
        country: String? = null,
        genre: String? = null,
        limit: Int = 50,
        offset: Int = 0,
        callback: (Result<List<Station>>) -> Unit
    )

    fun loadCountries(callback: (Result<List<CountryItem>>) -> Unit)

    fun loadGenres(callback: (Result<List<GenreItem>>) -> Unit)

    fun loadStation(
        stationId: String,
        callback: (Result<Station>) -> Unit
    )

    fun close()
}
