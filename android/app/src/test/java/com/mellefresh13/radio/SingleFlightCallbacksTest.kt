package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleFlightCallbacksTest {

    @Test
    fun concurrentCallersJoinOneOperationAndReceiveItsResultInOrder() {
        val callbacks = SingleFlightCallbacks<String>()
        val delivered = mutableListOf<String>()

        assertTrue(callbacks.enqueue { delivered += "first:$it" })
        assertFalse(callbacks.enqueue { delivered += "second:$it" })
        assertFalse(callbacks.enqueue { delivered += "third:$it" })

        assertTrue(delivered.isEmpty())
        callbacks.complete("catalog-v2")

        assertEquals(
            listOf("first:catalog-v2", "second:catalog-v2", "third:catalog-v2"),
            delivered
        )

        // After completion, a later refresh can start a new operation.
        assertTrue(callbacks.enqueue { delivered += "fourth:$it" })
        callbacks.complete("catalog-v3")
        assertEquals("fourth:catalog-v3", delivered.last())
    }

    @Test
    fun clearDiscardsWaitingCallbacksAndAllowsFutureOperation() {
        val callbacks = SingleFlightCallbacks<Int>()
        var calls = 0

        assertTrue(callbacks.enqueue { calls += it })
        assertFalse(callbacks.enqueue { calls += it })
        callbacks.clear()
        callbacks.complete(7)

        assertEquals(0, calls)
        assertTrue(callbacks.enqueue { calls += it })
        callbacks.complete(3)
        assertEquals(3, calls)
    }

    @Test
    fun duplicateCompletionDoesNotRedeliverCallbacks() {
        val callbacks = SingleFlightCallbacks<Int>()
        var calls = 0

        assertTrue(callbacks.enqueue { calls += it })
        callbacks.complete(2)
        callbacks.complete(5)

        assertEquals(2, calls)
    }
}
