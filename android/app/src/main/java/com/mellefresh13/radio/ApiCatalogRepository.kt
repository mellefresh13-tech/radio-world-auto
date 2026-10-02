package com.mellefresh13.radio

class ApiCatalogRepository(
    context: android.content.Context,
    onProgress: ((Long, Long) -> Unit)? = null,
    private val client: RadioApiClient = RadioApiClient(onProgress = onProgress)
) : CatalogRepository {

    private val cacheStore = CatalogCacheStore(context)
    private var stations: List<Station>? = null
    private var countries: List<CountryItem> = emptyList()
    private var genres: List<GenreItem> = emptyList()
    private var catalogVersion: String? = null

    override fun loadStations(
        query: String?,
        country: String?,
        genre: String?,
        limit: Int,
        offset: Int,
        callback: (Result<List<Station>>) -> Unit
    ) {
        val shouldSync = query == null && country == null && genre == null && offset == 0 && limit >= 50_000
        ensureLocalCatalog(allowSync = shouldSync) { result ->
            callback(result.map { filterStations(it, query, country, genre, limit, offset) })
        }
    }

    override fun loadStation(
        stationId: String,
        callback: (Result<Station>) -> Unit
    ) {
        ensureLocalCatalog(allowSync = false) { result ->
            callback(
                result.flatMap { list ->
                    list.firstOrNull { it.id == stationId }
                        ?.let { Result.success(it) }
                        ?: Result.failure(IllegalArgumentException("Station not found: $stationId"))
                }
            )
        }
    }

    override fun loadCountries(callback: (Result<List<CountryItem>>) -> Unit) {
        ensureLocalCatalog(allowSync = false) {
            callback(it.map { countries })
        }
    }

    override fun loadGenres(callback: (Result<List<GenreItem>>) -> Unit) {
        ensureLocalCatalog(allowSync = false) {
            callback(it.map { genres })
        }
    }

    private fun ensureLocalCatalog(
        allowSync: Boolean,
        callback: (Result<List<Station>>) -> Unit
    ) {
        stations?.let {
            if (allowSync) {
                syncFromGitHub(it, catalogVersion, callback)
            } else {
                callback(Result.success(it))
            }
            return
        }

        val cached = cacheStore.load()
        if (cached != null && cached.stations.isNotEmpty()) {
            stations = cached.stations
            countries = cached.countries
            genres = cached.genres
            catalogVersion = cached.catalogVersion
            if (allowSync) {
                syncFromGitHub(cached.stations, cached.catalogVersion, callback)
            } else {
                callback(Result.success(cached.stations))
            }
            return
        }

        syncFromGitHub(emptyList(), null, callback)
    }

    private fun syncFromGitHub(
        localStations: List<Station>,
        localVersion: String?,
        callback: (Result<List<Station>>) -> Unit
    ) {
        client.syncCatalog(localStations, localVersion) { result ->
            result.onSuccess { synced ->
                val mappedStations = synced.stations.map(::mapStation).filter { it.streams.isNotEmpty() }
                val mappedCountries = synced.countries.map { country ->
                    CountryItem(
                        name = countryName(country.code),
                        code = country.code,
                        flag = flagFor(country.code),
                        stationCount = country.stationCount
                    )
                }
                val mappedGenres = synced.genres.map { genre ->
                    GenreItem(genre.name, genre.stationCount)
                }

                stations = mappedStations
                countries = mappedCountries
                genres = mappedGenres
                catalogVersion = synced.version
                cacheStore.save(mappedStations, mappedCountries, mappedGenres, synced.version)
                callback(Result.success(mappedStations))
            }.onFailure { error ->
                if (localStations.isNotEmpty()) {
                    callback(Result.success(localStations))
                } else {
                    callback(Result.failure(error))
                }
            }
        }
    }

    private fun filterStations(
        source: List<Station>,
        query: String?,
        country: String?,
        genre: String?,
        limit: Int,
        offset: Int
    ): List<Station> {
        val normalizedQuery = query?.trim()?.lowercase() ?: ""
        val normalizedCountry = country?.trim()?.lowercase() ?: ""
        val normalizedGenre = genre?.trim()?.lowercase() ?: ""

        return source.asSequence()
            .filter { normalizedCountry.isBlank() || it.countryCode.lowercase() == normalizedCountry }
            .filter { normalizedGenre.isBlank() || it.genre.lowercase() == normalizedGenre }
            .filter {
                normalizedQuery.isBlank() || listOf(
                    it.name,
                    it.country,
                    it.countryCode,
                    it.city,
                    it.genre,
                    it.language,
                    it.website.orEmpty()
                ).any { value -> value.lowercase().contains(normalizedQuery) }
            }
            .drop(offset)
            .take(limit)
            .toList()
    }

    private fun mapStation(api: ApiStation): Station =
        Station(
            id = api.id,
            name = api.name,
            country = countryName(api.country),
            countryCode = api.country,
            city = api.city ?: "",
            genre = api.genres.firstOrNull() ?: "Other",
            language = api.languages.firstOrNull() ?: "",
            streams = api.streams.map { it.url },
            songTitle = api.songTitle,
            artist = api.artist,
            website = api.homepage,
            logo = api.logo
        )

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
