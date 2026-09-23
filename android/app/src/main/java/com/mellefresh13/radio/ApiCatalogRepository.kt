package com.mellefresh13.radio

class ApiCatalogRepository(
    private val client: RadioApiClient = RadioApiClient()
) : CatalogRepository {

    override fun loadStations(
        query: String?,
        country: String?,
        genre: String?,
        limit: Int,
        offset: Int,
        callback: (Result<List<Station>>) -> Unit
    ) {
        client.loadStations(query, country, genre, limit, offset) { result ->
            callback(
                result.map { stations ->
                    stations.map { api ->
                        Station(
                            id = api.id,
                            name = api.name,
                            country = countryName(api.country),
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

    override fun loadCountries(callback: (Result<List<CountryItem>>) -> Unit) {
        client.loadCountries { result ->
            callback(
                result.map { countries ->
                    countries.map { country ->
                        CountryItem(
                            name = countryName(country.code),
                            code = country.code,
                            flag = flagFor(country.code),
                            stationCount = country.stationCount
                        )
                    }
                }
            )
        }
    }

    override fun loadGenres(callback: (Result<List<GenreItem>>) -> Unit) {
        client.loadGenres { result ->
            callback(
                result.map { genres ->
                    genres.map { genre ->
                        GenreItem(genre.name, genre.stationCount)
                    }
                }
            )
        }
    }

    private fun countryName(code: String): String {
        if (code == "ZZ") return "Unknown"
        return java.util.Locale("", code).getDisplayCountry(
            java.util.Locale.getDefault()
        ).ifBlank { code }
    }

    private fun flagFor(code: String): String {
        if (code.length != 2) return "🌐"
        val upper = code.uppercase()
        val first = upper[0] - 'A'
        val second = upper[1] - 'A'
        return String(Character.toChars(0x1F1E6 + first)) +
            String(Character.toChars(0x1F1E6 + second))
    }

    override fun close() {
        client.close()
    }
}
