package com.mellefresh13.radio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogDeltaApplierTest {

    private fun station(id: String, name: String = id) =
        JSONObject().put("id", id).put("name", name)

    @Test
    fun delta_updates_adds_and_removesStations() {
        val result = CatalogDeltaApplier.apply(
            localStations = listOf(station("a"), station("b")),
            updated = JSONArray()
                .put(station("b", "B updated"))
                .put(station("c")),
            removedIds = JSONArray().put("a")
        )

        assertEquals(listOf("B updated", "c"), result.map { it.getString("name") })
        assertTrue(result.none { it.getString("id") == "a" })
        assertFalse(result.any { it.getString("name") == "B" })
    }

    @Test
    fun delta_keepsUnchangedStations() {
        val result = CatalogDeltaApplier.apply(
            localStations = listOf(station("a"), station("b")),
            updated = JSONArray(),
            removedIds = JSONArray()
        )

        assertEquals(listOf("a", "b"), result.map { it.getString("id") })
    }
}
