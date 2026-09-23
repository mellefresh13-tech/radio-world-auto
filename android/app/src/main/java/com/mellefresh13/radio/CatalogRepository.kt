package com.mellefresh13.radio

interface CatalogRepository {
    fun loadStations(
        query: String? = null,
        country: String? = null,
        genre: String? = null,
        limit: Int = 50,
        callback: (Result<List<Station>>) -> Unit
    )

    fun close()
}
