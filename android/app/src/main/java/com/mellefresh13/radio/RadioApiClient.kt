package com.mellefresh13.radio

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class RadioApiClient(
    private val catalogUrl: String = CATALOG_URL,
    private val onProgress: ((Long, Long) -> Unit)? = null
) {
    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    data class CatalogSyncResult(
        val stations: List<ApiStation>,
        val countries: List<ApiCountry>,
        val genres: List<ApiGenre>,
        val version: String,
        val usedDelta: Boolean
    )

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
                val playableStationCount = manifest.optInt("playable_station_count", -1)
                val expectedUpdatedCount = manifest.optInt("updated_count", -1)
                val expectedRemovedCount = manifest.optInt("removed_count", -1)

                if (localStations.isNotEmpty() && localVersion == version) {
                    // Local stations already contain only playable streams. Do not
                    // serialize and parse them again: that round-trip drops live
                    // song metadata persisted in the cache.
                    if (playableStationCount >= 0) {
                        validateStationCount(localStations.size, playableStationCount)
                    }
                    CatalogSyncResult(
                        stations = localStations.map(StationApiMapper::fromStation),
                        countries = countries,
                        genres = genres,
                        version = version,
                        usedDelta = false
                    )
                } else if (localStations.isNotEmpty() && localVersion != null && localVersion == baseVersion) {
                    val deltaUrl = CatalogDeltaUrlPolicy.canonicalOrFallback(
                        candidate = manifest.optString("delta_url"),
                        canonicalUrl = DELTA_URL
                    )
                    val delta = downloadJsonObject(deltaUrl, reportProgress = false)
                    val deltaVersion = delta.optString("version")
                    val deltaBaseVersion = delta.optString("base_version")
                    if (deltaVersion != version || deltaBaseVersion != localVersion) {
                        downloadFullCatalog(version, countries, genres, stationCount)
                    } else {
                        val updatedArray = delta.optJSONArray("updated")
                        val removedArray = delta.optJSONArray("removed_ids")
                        val updated = updatedArray?.let { array ->
                            buildList(array.length()) {
                                for (index in 0 until array.length()) {
                                    array.optJSONObject(index)?.let(::add)
                                }
                            }
                        }.orEmpty()
                        val removed = removedArray?.let { array ->
                            buildSet {
                                for (index in 0 until array.length()) {
                                    add(array.optString(index))
                                }
                            }
                        }.orEmpty()
                        val deltaCountsMatch = CatalogDeltaValidationPolicy.payloadCountsMatch(
                            expectedUpdatedCount = expectedUpdatedCount,
                            updatedArraySize = updatedArray?.length(),
                            parsedUpdatedCount = updated.size,
                            expectedRemovedCount = expectedRemovedCount,
                            removedArraySize = removedArray?.length(),
                            uniqueRemovedCount = removed.size
                        )
                        val merged = CatalogDeltaApplier.apply(
                            localItems = localStations.map(::stationToJson),
                            updatedItems = updated,
                            removedIds = removed,
                            idOf = { it.optString("id") }
                        )
                        val playableCountMatches = CatalogDeltaValidationPolicy.playableCountMatches(
                            expectedPlayableCount = playableStationCount,
                            actualPlayableCount = countPlayableStations(merged)
                        )
                        if (!deltaCountsMatch || !playableCountMatches) {
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
            }.mapCatching { synced ->
                synced.copy(
                    stations = StationTrackMetadataPolicy.preserve(localStations, synced.stations)
                )
            }
            mainHandler.post { callback(result) }
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun downloadFullCatalog(
        version: String,
        countries: List<ApiCountry>,
        genres: List<ApiGenre>,
        stationCount: Int
    ): CatalogSyncResult {
        val loaded = downloadJsonArray(catalogUrl, reportProgress = true)
        if (stationCount >= 0 && loaded.size != stationCount) {
            error("Catalog station count mismatch: expected=$stationCount actual=${loaded.size}")
        }
        return CatalogSyncResult(
            stations = loaded.map(::parseStation),
            countries = countries,
            genres = genres,
            version = version,
            usedDelta = false
        )
    }

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
            if (total > MAX_JSON_RESPONSE_BYTES) {
                error("Catalog JSON response exceeds the $MAX_JSON_RESPONSE_BYTES-byte limit")
            }
            return ProgressReportingUtf8Reader.read(
                input = connection.inputStream,
                totalBytes = total,
                onProgress = if (reportProgress) onProgress else null,
                maxBytes = MAX_JSON_RESPONSE_BYTES
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun countPlayableStations(stations: List<JSONObject>): Int =
        stations.count { station ->
            val streams = station.optJSONArray("streams") ?: return@count false
            (0 until streams.length()).any { index ->
                val stream = streams.optJSONObject(index) ?: return@any false
                stream.optString("url").isNotBlank() &&
                    StreamAvailabilityPolicy.isPlayable(stream.optString("status"))
            }
        }

    private fun validateStationCount(actualCount: Int, expected: Int) {
        if (expected >= 0 && actualCount != expected) {
            error("Local catalog station count mismatch: expected=$expected actual=$actualCount")
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
        // The full catalog is capped independently of Content-Length because
        // servers may omit it or report an incorrect value.
        internal const val MAX_JSON_RESPONSE_BYTES = 64L * 1024L * 1024L

        private const val CATALOG_URL =
            "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/stations.json"
        private const val MANIFEST_URL =
            "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/catalog-manifest.json"
        private const val DELTA_URL =
            "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/catalog-delta.json"
    }
}
