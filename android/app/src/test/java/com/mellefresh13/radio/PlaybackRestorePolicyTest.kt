package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRestorePolicyTest {

    @Test
    fun restoredStationHasHighestPriority() {
        assertEquals(
            "restored",
            PlaybackRestorePolicy.resolveStationId(
                restoredStationId = "restored",
                playerStationId = "player",
                recentStationId = "recent",
                availableStationIds = setOf("restored", "player", "recent")
            )
        )
    }

    @Test
    fun unavailableRestoredStationFallsBackToPlayer() {
        assertEquals(
            "player",
            PlaybackRestorePolicy.resolveStationId(
                restoredStationId = "missing",
                playerStationId = "player",
                recentStationId = "recent",
                availableStationIds = setOf("player", "recent")
            )
        )
    }

    @Test
    fun playerFallsBackToRecentStation() {
        assertEquals(
            "recent",
            PlaybackRestorePolicy.resolveStationId(
                restoredStationId = null,
                playerStationId = "missing",
                recentStationId = "recent",
                availableStationIds = setOf("recent")
            )
        )
    }

    @Test
    fun restoredAndPlayerMissingFallsBackToRecentStation() {
        assertEquals(
            "recent",
            PlaybackRestorePolicy.resolveStationId(
                restoredStationId = "missing",
                playerStationId = "missing-player",
                recentStationId = "recent",
                availableStationIds = setOf("recent")
            )
        )
    }

    @Test
    fun returnsNullWhenNoCandidateIsAvailable() {
        assertNull(
            PlaybackRestorePolicy.resolveStationId(
                restoredStationId = "missing",
                playerStationId = "missing-player",
                recentStationId = "missing-recent",
                availableStationIds = setOf("station")
            )
        )
    }
}
