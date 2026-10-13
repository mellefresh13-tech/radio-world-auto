package com.mellefresh13.radio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class PlaybackStateStore(context: Context) {

    private val preferences = context.getSharedPreferences(
        "radio_world_auto_playback",
        Context.MODE_PRIVATE
    )

    fun loadLastStationId(): String? =
        preferences.getString(KEY_LAST_STATION, null)

    fun saveLastStationId(stationId: String) {
        preferences.edit().putString(KEY_LAST_STATION, stationId).apply()
    }

    fun saveLastStation(station: Station) {
        val snapshot = JSONObject()
            .put("id", station.id)
            .put("name", station.name)
            .put("country", station.country)
            .put("country_code", station.countryCode)
            .put("city", station.city)
            .put("genre", station.genre)
            .put("language", station.language)
            .put("streams", JSONArray().apply { station.streams.forEach(::put) })
            .putOpt("song_title", station.songTitle)
            .putOpt("artist", station.artist)
            .putOpt("logo", station.logo)
            .putOpt("website", station.website)
        preferences.edit()
            .putString(KEY_LAST_STATION, station.id)
            .putString(KEY_LAST_STATION_SNAPSHOT, snapshot.toString())
            .apply()
    }

    fun loadLastStationSnapshot(): Station? {
        val raw = preferences.getString(KEY_LAST_STATION_SNAPSHOT, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val streamsJson = json.optJSONArray("streams") ?: JSONArray()
            val streams = buildList {
                for (index in 0 until streamsJson.length()) {
                    streamsJson.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: error("Missing station ID")
            val name = json.optString("name").takeIf { it.isNotBlank() } ?: error("Missing station name")
            if (streams.isEmpty()) error("Missing station streams")
            Station(
                id = id,
                name = name,
                country = json.optString("country"),
                countryCode = json.optString("country_code"),
                city = json.optString("city"),
                genre = json.optString("genre"),
                language = json.optString("language"),
                streams = streams,
                songTitle = json.optString("song_title").takeIf { it.isNotBlank() },
                artist = json.optString("artist").takeIf { it.isNotBlank() },
                logo = json.optString("logo").takeIf { it.isNotBlank() },
                website = json.optString("website").takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }

    fun loadPreviousStationId(): String? =
        preferences.getString(KEY_PREVIOUS_STATION, null)

    fun savePreviousStationId(stationId: String?) {
        preferences.edit().apply {
            if (stationId.isNullOrBlank()) remove(KEY_PREVIOUS_STATION)
            else putString(KEY_PREVIOUS_STATION, stationId)
        }.apply()
    }

    companion object {
        private const val KEY_LAST_STATION = "last_station_id"
        private const val KEY_LAST_STATION_SNAPSHOT = "last_station_snapshot"
        private const val KEY_PREVIOUS_STATION = "previous_station_id"
    }
}
