package com.mellefresh13.radio

import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class ApiCatalogRepository(
    context: android.content.Context,
    onProgress: ((Long, Long) -> Unit)? = null,
    private val client: RadioApiClient = RadioApiClient(onProgress = onProgress)
) : CatalogRepository {

    private val cacheStore = CatalogCacheStore(context)
    private val processingExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command ->
        mainHandler.post(command)
        Unit
    }
    @Volatile private var closed = false

    private data class MappedCatalog(
        val stations: List<Station>,
        val countries: List<CountryItem>,
        val genres: List<GenreItem>,
        val version: String
    )

    private val syncProcessor = BackgroundResultProcessor<
        RadioApiClient.CatalogSyncResult,
        MappedCatalog
    >(processingExecutor, mainExecutor, ::mapAndCacheCatalog)

    private var stations: List<Station>? = null
    private var stationIndex: Map<String, Station> = emptyMap()
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
            result.onSuccess { list ->
                val station = stationIndex[stationId] ?: list.firstOrNull { it.id == stationId }
                if (station != null) {
                    callback(Result.success(station))
                } else {
                    callback(Result.failure(IllegalArgumentException("Station not found: $stationId")))
                }
            }.onFailure { callback(Result.failure(it)) }
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
            stationIndex = cached.stations.associateBy { it.id }
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
            if (closed) return@syncCatalog
            result.onSuccess { synced ->
                syncProcessor.submit(synced) { processed ->
                    if (closed) return@submit
                    processed.onSuccess { mapped ->
                        stations = mapped.stations
                        stationIndex = mapped.stations.associateBy { it.id }
                        countries = mapped.countries
                        genres = mapped.genres
                        catalogVersion = mapped.version
                        callback(Result.success(mapped.stations))
                    }.onFailure { error ->
                        if (localStations.isNotEmpty()) {
                            callback(Result.success(localStations))
                        } else {
                            callback(Result.failure(error))
                        }
                    }
                }
            }.onFailure { error ->
                if (localStations.isNotEmpty()) {
                    callback(Result.success(localStations))
                } else {
                    callback(Result.failure(error))
                }
            }
        }
    }

    private fun mapAndCacheCatalog(synced: RadioApiClient.CatalogSyncResult): MappedCatalog {
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

        // Keep the live catalog usable if cache persistence fails. save() is
        // atomic and returns false without replacing the previous good snapshot.
        cacheStore.save(mappedStations, mappedCountries, mappedGenres, synced.version)
        return MappedCatalog(mappedStations, mappedCountries, mappedGenres, synced.version)
    }

    private fun filterStations(
        source: List<Station>,
        query: String?,
        country: String?,
        genre: String?,
        limit: Int,
        offset: Int
    ): List<Station> {
        val normalizedQuery = query?.trim()?.lowercase(Locale.ROOT) ?: ""
        val normalizedCountry = country?.trim()?.lowercase(Locale.ROOT) ?: ""
        val normalizedGenre = genre?.trim()?.lowercase(Locale.ROOT) ?: ""

        return source.asSequence()
            .filter { normalizedCountry.isBlank() || it.countryCode.lowercase(Locale.ROOT) == normalizedCountry }
            .filter { normalizedGenre.isBlank() || it.genre.lowercase(Locale.ROOT) == normalizedGenre }
            .filter {
                normalizedQuery.isBlank() || listOf(
                    it.name,
                    it.country,
                    it.countryCode,
                    it.city,
                    it.genre,
                    it.language,
                    it.website.orEmpty()
                ).any { value -> value.lowercase(Locale.ROOT).contains(normalizedQuery) }
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
        val upper = code.uppercase(Locale.ROOT)
        val first = upper[0] - 'A'
        val second = upper[1] - 'A'
        return String(Character.toChars(0x1F1E6 + first)) +
            String(Character.toChars(0x1F1E6 + second))
    }

    override fun close() {
        closed = true
        client.close()
        processingExecutor.shutdownNow()
    }
}
