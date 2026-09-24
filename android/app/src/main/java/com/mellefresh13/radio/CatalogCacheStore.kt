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

class CatalogCacheStore(context: Context) {

    private val file = File(context.filesDir, "radio_catalog_cache.json.gz")
    private val maxStations = 500

    data class Snapshot(
        val stations: List<Station>,
        val countries: List<CountryItem>,
        val genres: List<GenreItem>
    )

    @Synchronized
    fun load(maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000): Snapshot? {
        if (!file.exists()) return null
        return runCatching {
            GZIPInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                val root = JSONObject(input.reader(Charsets.UTF_8).use { it.readText() })
                val savedAt = root.optLong("saved_at", 0L)
                if (savedAt > 0L && System.currentTimeMillis() - savedAt > maxAgeMs) {
                    return null
                }

                val stations = root.optJSONArray("stations")?.toStations().orEmpty()
                val countries = root.optJSONArray("countries")?.toCountries().orEmpty()
                val genres = root.optJSONArray("genres")?.toGenres().orEmpty()

                if (stations.isEmpty() && countries.isEmpty() && genres.isEmpty()) {
                    null
                } else {
                    Snapshot(stations, countries, genres)
                }
            }
        }.getOrNull()
    }

    @Synchronized
    fun save(
        stations: Collection<Station>,
        countries: Collection<CountryItem>,
        genres: Collection<GenreItem>
    ) {
        runCatching {
            val uniqueStations = stations
                .filter { it.streams.isNotEmpty() }
                .distinctBy { it.id }
                .take(maxStations)

            val root = JSONObject()
                .put("saved_at", System.currentTimeMillis())
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

            val temp = File(file.parentFile, file.name + ".tmp")
            GZIPOutputStream(BufferedOutputStream(FileOutputStream(temp))).use { output ->
                output.write(root.toString().toByteArray(Charsets.UTF_8))
            }

            if (!temp.renameTo(file)) {
                temp.delete()
                error("Unable to replace catalog cache")
            }
        }
    }

    @Synchronized
    fun findStation(stationId: String): Station? =
        load()?.stations?.firstOrNull { it.id == stationId }

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
