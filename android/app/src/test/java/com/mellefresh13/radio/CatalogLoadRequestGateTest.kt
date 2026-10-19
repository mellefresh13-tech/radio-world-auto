package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogLoadRequestGateTest {

    @Test
    fun concurrentColdStartRequestsShareOneCacheLoadAndKeepCallbackOrder() {
        val gate = CatalogLoadRequestGate<String>()
        val delivered = mutableListOf<String>()

        assertEquals(
            CatalogLoadRequestGate.Action.START_LOAD,
            gate.request(allowSync = true) { delivered += "first:${it.getOrThrow().single()}" }
        )
        assertEquals(
            CatalogLoadRequestGate.Action.JOIN_LOAD,
            gate.request(allowSync = false) { delivered += "second:${it.getOrThrow().single()}" }
        )
        assertEquals(
            CatalogLoadRequestGate.Action.JOIN_LOAD,
            gate.request(allowSync = true) { delivered += "third:${it.getOrThrow().single()}" }
        )

        assertTrue(delivered.isEmpty())
        val waiting = gate.completeLoad()
        assertEquals(listOf(true, false, true), waiting.map { it.allowSync })
        waiting.forEach { it.callback(Result.success(listOf("station"))) }

        assertEquals(
            listOf("first:station", "second:station", "third:station"),
            delivered
        )
        assertTrue(gate.completeLoad().isEmpty())
    }

    @Test
    fun completedMissingCacheSkipsAnotherDiskLoad() {
        val gate = CatalogLoadRequestGate<String>()
        var callbackCalled = false

        assertEquals(
            CatalogLoadRequestGate.Action.START_LOAD,
            gate.request(allowSync = false) { callbackCalled = true }
        )
        assertEquals(1, gate.completeLoad().size)

        assertEquals(
            CatalogLoadRequestGate.Action.SKIP_CACHE_LOAD,
            gate.request(allowSync = false) { callbackCalled = true }
        )
        assertFalse(callbackCalled)
    }

    @Test
    fun clearDiscardsPendingRequestsWhenRepositoryCloses() {
        val gate = CatalogLoadRequestGate<String>()
        var callbackCalled = false

        gate.request(allowSync = false) { callbackCalled = true }
        gate.clear()

        assertTrue(gate.completeLoad().isEmpty())
        assertFalse(callbackCalled)
    }
}
