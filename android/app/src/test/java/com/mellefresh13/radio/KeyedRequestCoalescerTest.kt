package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyedRequestCoalescerTest {

    @Test
    fun sameKeyJoinsOneRequestAndDeliversCallbacksInRegistrationOrder() {
        val coalescer = KeyedRequestCoalescer<String, String>()
        val delivered = mutableListOf<String>()
        var cached: String? = null

        val first = coalescer.register(
            "logo-a",
            KeyedRequestCoalescer.Callbacks({ delivered += "first:$it" }, null)
        ) { cached }
        val second = coalescer.register(
            "logo-a",
            KeyedRequestCoalescer.Callbacks({ delivered += "second:$it" }, null)
        ) { cached }
        val otherKey = coalescer.register(
            "logo-b",
            KeyedRequestCoalescer.Callbacks({ delivered += "other:$it" }, null)
        ) { cached }

        assertTrue(first is KeyedRequestCoalescer.Registration.Started)
        assertTrue(second is KeyedRequestCoalescer.Registration.Joined)
        assertTrue(otherKey is KeyedRequestCoalescer.Registration.Started)

        val waiting = coalescer.complete("logo-a", Result.success("bitmap-a")) { cached = it }
        assertEquals(2, waiting.size)
        waiting.forEach { it.onSuccess("bitmap-a") }
        assertEquals(listOf("first:bitmap-a", "second:bitmap-a"), delivered)
        assertEquals("bitmap-a", cached)
    }

    @Test
    fun cachedValueIsReturnedWithoutStartingAnotherRequest() {
        val coalescer = KeyedRequestCoalescer<String, String>()
        val cachedCallbacks = mutableListOf<String>()
        val first = coalescer.register(
            "logo",
            KeyedRequestCoalescer.Callbacks({ cachedCallbacks += it }, null)
        ) { null }
        coalescer.complete("logo", Result.success("saved")) { }

        val second = coalescer.register(
            "logo",
            KeyedRequestCoalescer.Callbacks({ cachedCallbacks += it }, null)
        ) { "saved" }

        assertTrue(first is KeyedRequestCoalescer.Registration.Started)
        assertTrue(second is KeyedRequestCoalescer.Registration.Cached)
        assertEquals("saved", (second as KeyedRequestCoalescer.Registration.Cached).value)
        assertTrue(cachedCallbacks.isEmpty())
    }

    @Test
    fun failureReleasesAllWaitersAndNextCallCanStartANewRequest() {
        val coalescer = KeyedRequestCoalescer<String, String>()
        val failures = mutableListOf<String>()

        coalescer.register(
            "logo",
            KeyedRequestCoalescer.Callbacks<String>({}, { failures += "first" })
        ) { null }
        coalescer.register(
            "logo",
            KeyedRequestCoalescer.Callbacks<String>({}, { failures += "second" })
        ) { null }

        val waiting = coalescer.complete("logo", Result.failure(IllegalStateException("offline"))) { }
        waiting.forEach { it.onFailure?.invoke() }

        val retry = coalescer.register("logo", KeyedRequestCoalescer.Callbacks<String>({}, null)) { null }

        assertEquals(listOf("first", "second"), failures)
        assertTrue(retry is KeyedRequestCoalescer.Registration.Started)
        assertFalse(coalescer.complete("missing", Result.success("unused")) { }.isNotEmpty())
    }

    @Test
    fun clearDropsAllPendingCallbacks() {
        val coalescer = KeyedRequestCoalescer<String, String>()
        coalescer.register("logo", KeyedRequestCoalescer.Callbacks<String>({}, null)) { null }
        coalescer.clear()

        assertTrue(coalescer.complete("logo", Result.success("unused")) { }.isEmpty())
        assertTrue(
            coalescer.register("logo", KeyedRequestCoalescer.Callbacks<String>({}, null)) { null }
                is KeyedRequestCoalescer.Registration.Started
        )
    }
}
