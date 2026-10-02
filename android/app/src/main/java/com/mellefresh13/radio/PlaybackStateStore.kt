package com.mellefresh13.radio

import android.content.Context

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
        private const val KEY_PREVIOUS_STATION = "previous_station_id"
    }
}
