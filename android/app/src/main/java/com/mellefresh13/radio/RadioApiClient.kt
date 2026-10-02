package com.mellefresh13.radio

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class RadioApiClient(
    private val catalogUrl: String = CATALOG_URL,
    private val onProgress: ((Long, Long) -> Unit)? = null
) {
    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var catalogCache: List<JSONObject>? = null

    data class CatalogSyncResult(
        val stations: List<ApiStation>,
        val countries: List<ApiCountry>,
        val genres: List<ApiGenre>,
        val version: String,
        val usedDelta: Boolean
    )

    fun forceRefresh() {
        synchronized(this) {
            catalogCache = null
        }
    }

    fun syncCatalog(
        localStations: List<Station>,
        localVersion: String?,
        callback: (Result<CatalogSyncResult>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                val manifest = downloadJsonObject(MANIFEST_URL, reportProgress = false)
                val version = manifest.optString("version").takeIf { it.isNotBlank() }
                    ?: error("Catalog manifest has no version")
                val baseVersion = manifest.optString("base_version").takeIf { it.isNotBlank() }
                val countries = parseCountries(manifest.optJSONArray("countries"))
                val genres = parseGenres(manifest.optJSONArray("genres"))
                val stationCount = manifest.optInt("station_count", -1)

                if (localStations.isNotEmpty() && localVersion == version) {
                    val local = localStations.map(::stationToJson)
                    validateStationCount(local, stationCount)
                    CatalogSyncResult(
                        stations = local.map(::parseStation),
                        countries = countries,
                        genres = genres,
                        version = version,
                        usedDelta = false
                    )
                } else if (localStations.isNotEmpty() && localVersion != null && localVersion == baseVersion) {
                    val deltaUrl = manifest.optString("delta_url").ifBlank { DELTA_URL }
                    val delta = downloadJsonObject(deltaUrl, reportProgress = false)
                    val deltaVersion = delta.optString("version")
                    val deltaBaseVersion = delta.optString("base_version")
                    if (deltaVersion != version || deltaBaseVersion != localVersion) {
                        downloadFullCatalog(version, countries, genres, stationCount)
                    } else {
                        val updated = delta.optJSONArray("updated")?.let { array ->
                            buildList(array.length()) {
                                for (index in 0 until array.length()) {
                                    array.optJSONObject(index)?.let(::add)
                                }
                            }
                        }.orEmpty()
                        val removed = delta.optJSONArray("removed_ids")?.let { array ->
                            buildSet {
                                for (index in 0 until array.length()) {
                                    add(array.optString(index))
                                }
                            }
                        }.orEmpty()
                        val merged = CatalogDeltaApplier.apply(
                            localItems = localStations.map(::stationToJson),
                            updatedItems = updated,
                            removedIds = removed,
                            idOf = { it.optString("id") }
                        )
                        if (stationCount >= 0 && merged.size != stationCount) {
                            downloadFullCatalog(version, countries, genres, stationCount)
                        } else {
                            CatalogSyncResult(
                                stations = merged.map(::parseStation),
                                countries = countries,
                                genres = genres,
                                version = version,
                                usedDelta = true
                            )
                        }
                    }
                } else {
                    downloadFullCatalog(version, countries, genres, stationCount)
                }
            }
            mainHandler.post { callback(result) }
        }
    }

    fun loadStations(
        query: String? = null,
        country: String? = null,
        genre: String? = null,
        limit: Int = 50,
        offset: Int = 0,
        callback: (Result<List<ApiStation>>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                val all = loadCatalog()
                filterStations(all, query, country, genre, limit, offset)
            }
            mainHandler.post { callback(result) }
        }
    }

    fun loadCountries(callback: (Result<List<ApiCountry>>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val all = loadCatalog()
                all.groupBy { it.optString("country") }
                    .filterKeys { it.isNotBlank() }
                    .map { (code, stations) -> ApiCountry(code, stations.size) }
                    .sortedBy { it.code }
            }
            mainHandler.post { callback(result) }
        }
    }

    fun loadGenres(callback: (Result<List<ApiGenre>>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val counts = linkedMapOf<String, Int>()
                loadCatalog().forEach { station ->
                    station.optJSONArray("genres").toStringList()
                        .filter { it.isNotBlank() }
                        .forEach { genre -> counts[genre] = (counts[genre] ?: 0) + 1 }
                }
                counts.map { (name, count) -> ApiGenre(name, count) }
                    .sortedByDescending { it.stationCount }
            }
            mainHandler.post { callback(result) }
        }
    }

    fun loadStation(
        stationId: String,
        callback: (Result<ApiStation>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                val station = loadCatalog().firstOrNull { it.optString("id") == stationId }
                    ?: error("Station not found: $stationId")
                parseStation(station)
            }
            mainHandler.post { callback(result) }
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun loadCatalog(): List<JSONObject> {
        catalogCache?.let { return it }
        synchronized(this) {
            catalogCache?.let { return it }
            val loaded = downloadJsonCatalog()
            catalogCache = loaded
            return loaded
        }
    }

    private fun downloadFullCatalog(
        version: String,
        countries: List<ApiCountry>,
        genres: List<ApiGenre>,
        stationCount: Int
    ): CatalogSyncResult {
        val loaded = downloadJsonCatalog()
        if (stationCount >= 0 && loaded.size != stationCount) {
            error("Catalog station count mismatch: expected=$stationCount actual=${loaded.size}")
        }
        synchronized(this) {
            catalogCache = loaded
        }
        return CatalogSyncResult(
            stations = loaded.map(::parseStation),
            countries = countries,
            genres = genres,
            version = version,
            usedDelta = false
        )
    }

    private fun filterStations(
        all: List<JSONObject>,
        query: String?,
        country: String?,
        genre: String?,
        limit: Int,
        offset: Int
    ): List<ApiStation> {
        val normalizedQuery = query?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val normalizedCountry = country?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val normalizedGenre = genre?.trim()?.lowercase(Locale.ROOT).orEmpty()

        return all.asSequence()
            .filter { station ->
                normalizedCountry.isBlank() || station.optString("country").lowercase(Locale.ROOT) == normalizedCountry
            }
            .filter { station ->
                normalizedGenre.isBlank() || station.optJSONArray("genres").toStringList()
                    .any { it.lowercase(Locale.ROOT) == normalizedGenre }
            }
            .filter { station ->
                normalizedQuery.isBlank() || searchableText(station).contains(normalizedQuery)
            }
            .drop(offset)
            .take(limit)
            .map(::parseStation)
            .toList()
    }

    private fun downloadJsonCatalog(): List<JSONObject> =
        downloadJsonArray(catalogUrl, reportProgress = true)

    private fun downloadJsonArray(url: String, reportProgress: Boolean): List<JSONObject> {
        val array = JSONArray(downloadText(url, reportProgress))
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(::add)
            }
        }
    }

    private fun downloadJsonObject(url: String, reportProgress: Boolean): JSONObject =
        JSONObject(downloadText(url, reportProgress))

    private fun downloadText(url: String, reportProgress: Boolean): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "RadioWorldAuto/1.0")
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP " + connection.responseCode)
            val total = connection.contentLengthLong
            var loadedBytes = 0L
            val text = connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(8192)
                val out = StringBuilder()
                var count: Int
                while (reader.read(buffer).also { count = it } >= 0) {
                    if (count == 0) continue
                    out.append(buffer, 0, count)
                    loadedBytes += count.toLong()
                    if (reportProgress) onProgress?.invoke(loadedBytes, total)
                }
                out.toString()
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun validateStationCount(stations: List<JSONObject>, expected: Int) {
        if (expected >= 0 && stations.size != expected) {
            error("Local catalog station count mismatch: expected=$expected actual=${stations.size}")
        }
    }

    private fun stationToJson(station: Station): JSONObject =
        JSONObject()
            .put("id", station.id)
            .put("name", station.name)
            .put("country", station.countryCode)
            .put("city", station.city)
            .put("languages", JSONArray().apply { station.language.takeIf { it.isNotBlank() }?.let(::put) })
            .put("genres", JSONArray().apply { station.genre.takeIf { it.isNotBlank() }?.let(::put) })
            .put("homepage", station.website ?: "")
            .put("logo", station.logo ?: "")
            .put("streams", JSONArray().apply {
                station.streams.forEach { stream ->
                    put(JSONObject().put("url", stream))
                }
            })

    private fun parseCountries(array: JSONArray?): List<ApiCountry> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val code = item.optString("code").trim()
            if (code.isNotEmpty()) add(ApiCountry(code, item.optInt("station_count")))
        }
    }

    private fun parseGenres(array: JSONArray?): List<ApiGenre> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            if (name.isNotEmpty()) add(ApiGenre(name, item.optInt("station_count")))
        }
    }

    private fun searchableText(json: JSONObject): String = buildString {
        append(json.optString("name")).append(' ')
        append(json.optString("country")).append(' ')
        append(json.optString("city")).append(' ')
        append(json.optString("homepage")).append(' ')
        json.optJSONArray("languages").toStringList().forEach { append(it).append(' ') }
        json.optJSONArray("genres").toStringList().forEach { append(it).append(' ') }
        json.optJSONArray("aliases").toStringList().forEach { append(it).append(' ') }
    }.lowercase(Locale.ROOT)

    private fun parseStation(json: JSONObject): ApiStation {
        val streamArray = json.optJSONArray("streams") ?: JSONArray()
        val streams = buildList(streamArray.length()) {
            for (index in 0 until streamArray.length()) {
                val stream = streamArray.getJSONObject(index)
                add(
                    ApiStream(
                        url = stream.optString("url"),
                        codec = stream.optString("codec").takeIf { it.isNotBlank() },
                        bitrateKbps = if (stream.isNull("bitrate_kbps")) null else stream.optInt("bitrate_kbps"),
                        isHls = stream.optBoolean("is_hls"),
                        status = stream.optString("status")
                    )
                )
            }
        }.filter { it.url.isNotBlank() && StreamAvailabilityPolicy.isPlayable(it.status) }

        return ApiStation(
            id = json.getString("id"),
            name = json.getString("name"),
            country = json.optString("country"),
            city = json.optString("city").takeIf { it.isNotBlank() },
            languages = json.optJSONArray("languages").toStringList(),
            genres = json.optJSONArray("genres").toStringList(),
            homepage = json.optString("homepage").takeIf { it.isNotBlank() },
            logo = json.optString("logo").takeIf { it.isNotBlank() },
            songTitle = null,
            artist = null,
            streams = streams
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    companion object {
        private const val CATALOG_URL =
            "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/stations.json"
        private const val MANIFEST_URL =
            "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/catalog-manifest.json"
        private const val DELTA_URL =
            "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/catalog-delta.json"
    }
}
