package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentStationsPolicyTest {

    @Test
    fun addsStationToFrontAndRemovesDuplicate() {
        assertEquals(
            listOf("c", "a", "b"),
            RecentStationsPolicy.add(listOf("a", "b", "c"), "c")
        )
    }

    @Test
    fun limitsRecentStationsToTen() {
        val current = (1..10).map { it.toString() }
        val result = RecentStationsPolicy.add(current, "11")
        assertEquals(10, result.size)
        assertEquals("11", result.first())
    }
}
