package com.mellefresh13.radio

import org.json.JSONArray
import org.json.JSONObject

object CatalogDeltaApplier {

    fun apply(
        localStations: List<JSONObject>,
        updated: JSONArray,
        removedIds: JSONArray
    ): List<JSONObject> {
        val byId = linkedMapOf<String, JSONObject>()
        localStations.forEach { station ->
            val id = station.optString("id").trim()
            if (id.isNotEmpty()) byId[id] = station
        }

        for (index in 0 until updated.length()) {
            val item = updated.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            if (id.isNotEmpty()) byId[id] = item
        }

        for (index in 0 until removedIds.length()) {
            val id = removedIds.optString(index).trim()
            if (id.isNotEmpty()) byId.remove(id)
        }

        return byId.values.toList()
    }
}
