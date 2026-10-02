package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackNavigationPolicyTest {

    @Test
    fun inMemoryPreviousStationHasPriority() {
        assertEquals(
            "memory",
            PlaybackNavigationPolicy.resolvePreviousStationId("memory", "persisted")
        )
    }

    @Test
    fun persistedPreviousStationIsFallback() {
        assertEquals(
            "persisted",
            PlaybackNavigationPolicy.resolvePreviousStationId(null, "persisted")
        )
    }

    @Test
    fun blankPreviousStationIdsAreIgnored() {
        assertEquals(
            "persisted",
            PlaybackNavigationPolicy.resolvePreviousStationId("  ", "persisted")
        )
    }
}
