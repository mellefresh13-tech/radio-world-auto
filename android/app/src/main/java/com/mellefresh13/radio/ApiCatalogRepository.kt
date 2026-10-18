package com.mellefresh13.radio

import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class ApiCatalogRepository(
    context: android.content.Context,
    onProgress: ((Long, Long) -> Unit)? = null,
    private val client: RadioApiClient = RadioApiClient(onProgress = onProgress),
    private val cacheStore: CatalogCacheStore = CatalogCacheStore(context)
) : CatalogRepository {

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
        val stationIndex: Map<String, Station>,
        val version: String
    )

    private data class StationFilterRequest(
        val source: List<Station>,
        val query: String?,
        val country: String?,
        val genre: String?,
        val limit: Int,
        val offset: Int
    )

    private val syncProcessor = BackgroundResultProcessor<
        RadioApiClient.CatalogSyncResult,
        MappedCatalog
    >(processingExecutor, mainExecutor, ::mapAndCacheCatalog)
    private val cacheLoadProcessor = BackgroundResultProcessor<Unit, CatalogCacheStore.Snapshot?>(
        processingExecutor,
        mainExecutor
    ) {
        cacheStore.load()?.also { it.stationsById }
    }
    private val filterProcessor = BackgroundResultProcessor<StationFilterRequest, List<Station>>(
        processingExecutor,
        mainExecutor
    ) { request ->
        StationCatalogFilter.filter(
            request.source,
            request.query,
            request.country,
            request.genre,
            request.limit,
            request.offset
        )
    }

    private val syncCallbacks = SingleFlightCallbacks<Result<MappedCatalog>>()

    // Repository entry points and this cold-start gate are used from the main thread.
    private val firstLoadRequests = CatalogLoadRequestGate<Station>()

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
            result.fold(
                onSuccess = { source ->
                    filterProcessor.submit(
                        StationFilterRequest(source, query, country, genre, limit, offset)
                    ) { filtered ->
                        if (!closed) callback(filtered)
                    }
                },
                onFailure = { error ->
                    if (!closed) callback(Result.failure(error))
                }
            )
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
        stations?.let { localStations ->
            if (allowSync) {
                syncFromGitHub(localStations, catalogVersion, callback)
            } else {
                callback(Result.success(localStations))
            }
            return
        }

        when (firstLoadRequests.request(allowSync, callback)) {
            CatalogLoadRequestGate.Action.JOIN_LOAD -> return
            CatalogLoadRequestGate.Action.SKIP_CACHE_LOAD -> {
                // A failed/missing cache was already checked once. Retry the network
                // path directly instead of repeatedly decompressing/parsing it.
                syncFromGitHub(emptyList(), null, callback)
                return
            }
            CatalogLoadRequestGate.Action.START_LOAD -> Unit
        }

        cacheLoadProcessor.submit(Unit) { loadResult ->
            if (closed) {
                firstLoadRequests.clear()
                return@submit
            }

            val cached = loadResult.getOrNull()?.takeIf { it.stations.isNotEmpty() }
            if (stations == null && cached != null) {
                stations = cached.stations
                stationIndex = cached.stationsById
                countries = cached.countries
                genres = cached.genres
                catalogVersion = cached.catalogVersion
            }

            firstLoadRequests.completeLoad().forEach { request ->
                val localStations = stations
                if (localStations != null) {
                    if (request.allowSync) {
                        syncFromGitHub(localStations, catalogVersion, request.callback)
                    } else {
                        request.callback(Result.success(localStations))
                    }
                } else {
                    syncFromGitHub(emptyList(), null, request.callback)
                }
            }
        }
    }

    private fun syncFromGitHub(
        localStations: List<Station>,
        localVersion: String?,
        callback: (Result<List<Station>>) -> Unit
    ) {
        if (closed) return

        // Multiple screens/requests can arrive together after cold-start cache
        // loading. Share one network sync and catalog mapping across all waiters.
        val shouldStart = syncCallbacks.enqueue { processed ->
            processed.onSuccess { mapped ->
                callback(Result.success(mapped.stations))
            }.onFailure { error ->
                if (localStations.isNotEmpty()) {
                    callback(Result.success(localStations))
                } else {
                    callback(Result.failure(error))
                }
            }
        }
        if (!shouldStart) return

        runCatching {
            client.syncCatalog(localStations, localVersion) { result ->
                if (closed) {
                    syncCallbacks.clear()
                    return@syncCatalog
                }
                result.onSuccess { synced ->
                    syncProcessor.submit(synced) { processed ->
                        if (closed) {
                            syncCallbacks.clear()
                            return@submit
                        }
                        processed.onSuccess { mapped ->
                            stations = mapped.stations
                            stationIndex = mapped.stationIndex
                            countries = mapped.countries
                            genres = mapped.genres
                            catalogVersion = mapped.version
                        }
                        syncCallbacks.complete(processed)
                    }
                }.onFailure { error ->
                    syncCallbacks.complete(Result.failure(error))
                }
            }
        }.onFailure { error ->
            if (!closed) syncCallbacks.complete(Result.failure(error))
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
        val mappedStationIndex = mappedStations.associateBy { it.id }
        return MappedCatalog(
            stations = mappedStations,
            countries = mappedCountries,
            genres = mappedGenres,
            stationIndex = mappedStationIndex,
            version = synced.version
        )
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
        firstLoadRequests.clear()
        syncCallbacks.clear()
        client.close()
        processingExecutor.shutdownNow()
    }
}
