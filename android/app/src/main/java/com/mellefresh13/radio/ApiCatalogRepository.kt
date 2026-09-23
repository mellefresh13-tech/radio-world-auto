package com.mellefresh13.radio

class ApiCatalogRepository(
    private val client: RadioApiClient = RadioApiClient()
) : CatalogRepository {

    override fun loadStations(
        query: String?,
        country: String?,
        genre: String?,
        limit: Int,
        callback: (Result<List<Station>>) -> Unit
    ) {
        client.loadStations(query, country, genre, limit) { result ->
            callback(
                result.map { stations ->
                    stations.map { api ->
                        Station(
                            id = api.id,
                            name = api.name,
                            country = api.country,
                            countryCode = api.country,
                            city = api.city ?: "",
                            genre = api.genres.firstOrNull() ?: "Other",
                            language = api.languages.firstOrNull() ?: "",
                            streams = api.streams.map { it.url },
                            website = api.homepage
                        )
                    }
                }
            )
        }
    }

    override fun close() {
        client.close()
    }
}
