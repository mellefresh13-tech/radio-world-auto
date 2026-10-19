package com.mellefresh13.radio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal object CatalogCacheLimits {
    const val MAX_STATIONS = 50_000
    const val MAX_DECOMPRESSED_BYTES = 64L * 1024L * 1024L

    fun validateStationCount(count: Int) {
        require(count <= MAX_STATIONS) {
            "Cached catalog station count exceeds the $MAX_STATIONS-station limit"
        }
    }
}

class CatalogCacheStore(context: Context) {

    private val file = File(context.filesDir, "radio_catalog_cache.json.gz")
    private val maxStations = CatalogCacheLimits.MAX_STATIONS
    @Volatile private var memorySnapshot: Snapshot? = null
    private var loadCompleted = false

    data class Snapshot(
        val savedAt: Long,
        val catalogVersion: String?,
        val stations: List<Station>,
        val countries: List<CountryItem>,
        val genres: List<GenreItem>
    ) {
        val stationsById: Map<String, Station> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            stations.associateBy { it.id }
        }
    }

    @Synchronized
    fun load(): Snapshot? {
        memorySnapshot?.let { return it }
        if (loadCompleted) return null

        if (!file.exists()) {
            loadCompleted = true
            return null
        }

        val loaded = runCatching {
            GZIPInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                // GZIP expands data before JSON parsing; cap the decompressed byte
                // stream even if the compressed cache file itself is tiny.
                val json = ProgressReportingUtf8Reader.read(
                    input = input,
                    totalBytes = -1L,
                    maxBytes = CatalogCacheLimits.MAX_DECOMPRESSED_BYTES
                )
                val root = JSONObject(json)
                val savedAt = root.optLong("saved_at", 0L)
                val catalogVersion = root.optString("catalog_version").takeIf { it.isNotBlank() }
                val stationArray = root.optJSONArray("stations")
                stationArray?.let { CatalogCacheLimits.validateStationCount(it.length()) }
                val stations = stationArray?.toStations().orEmpty()
                val countries = root.optJSONArray("countries")?.toCountries().orEmpty()
                val genres = root.optJSONArray("genres")?.toGenres().orEmpty()

                if (stations.isEmpty() && countries.isEmpty() && genres.isEmpty()) {
                    null
                } else {
                    Snapshot(savedAt, catalogVersion, stations, countries, genres)
                }
            }
        }.getOrNull()
        memorySnapshot = loaded
        loadCompleted = true
        return loaded
    }

    @Synchronized
    fun save(
        stations: Collection<Station>,
        countries: Collection<CountryItem>,
        genres: Collection<GenreItem>,
        catalogVersion: String? = null
    ): Boolean = runCatching {
        val existingVersion = memorySnapshot?.catalogVersion ?: load()?.catalogVersion
        val uniqueStations = stations
            .filter { it.streams.isNotEmpty() }
            .distinctBy { it.id }
            .take(maxStations)

        val root = JSONObject()
            .put("saved_at", System.currentTimeMillis())
            .putOpt("catalog_version", catalogVersion ?: existingVersion)
            .put("stations", JSONArray().apply {
                uniqueStations.forEach { put(it.toJson()) }
            })
            .put("countries", JSONArray().apply {
                countries.forEach {
                    put(
                        JSONObject()
                            .put("name", it.name)
                            .put("code", it.code)
                            .put("flag", it.flag)
                            .put("station_count", it.stationCount)
                    )
                }
            })
            .put("genres", JSONArray().apply {
                genres.forEach {
                    put(
                        JSONObject()
                            .put("name", it.name)
                            .put("station_count", it.stationCount)
                    )
                }
            })

        AtomicFileWriter.writeAtomically(file) { temp ->
            GZIPOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { output ->
                output.write(root.toString().toByteArray(Charsets.UTF_8))
            }
        }

        memorySnapshot = Snapshot(
            savedAt = root.optLong("saved_at"),
            catalogVersion = root.optString("catalog_version").takeIf { it.isNotBlank() },
            stations = uniqueStations.toList(),
            countries = countries.toList(),
            genres = genres.toList()
        )
        loadCompleted = true
        true
    }.getOrDefault(false)

    fun fileStamp(): Long = file.lastModified()

    fun findStation(stationId: String): Station? {
        // Most lookups happen on the UI thread. Once a snapshot has been
        // published, read its volatile reference without waiting for a full
        // catalog save/load that holds this store's monitor.
        val snapshot = memorySnapshot ?: load() ?: return null
        return snapshot.stationsById[stationId]
    }


    private fun Station.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("country", country)
            .put("country_code", countryCode)
            .put("city", city)
            .put("genre", genre)
            .put("language", language)
            .put("streams", JSONArray().apply { streams.forEach(::put) })
            .putOpt("song_title", songTitle)
            .putOpt("artist", artist)
            .putOpt("logo", logo)
            .putOpt("website", website)

    private fun JSONArray.toStations(): List<Station> = buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val streams = item.optJSONArray("streams")?.toStringList().orEmpty()
            if (streams.isEmpty()) continue

            add(
                Station(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    country = item.optString("country"),
                    countryCode = item.optString("country_code"),
                    city = item.optString("city"),
                    genre = item.optString("genre"),
                    language = item.optString("language"),
                    streams = streams,
                    songTitle = item.optString("song_title").takeIf { it.isNotBlank() },
                    artist = item.optString("artist").takeIf { it.isNotBlank() },
                    logo = item.optString("logo").takeIf { it.isNotBlank() },
                    website = item.optString("website").takeIf { it.isNotBlank() }
                )
            )
        }
    }

    private fun JSONArray.toCountries(): List<CountryItem> = buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                CountryItem(
                    name = item.optString("name"),
                    code = item.optString("code"),
                    flag = item.optString("flag", "🌐"),
                    stationCount = item.optInt("station_count")
                )
            )
        }
    }

    private fun JSONArray.toGenres(): List<GenreItem> = buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                GenreItem(
                    name = item.optString("name"),
                    stationCount = item.optInt("station_count")
                )
            )
        }
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) {
            optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}
