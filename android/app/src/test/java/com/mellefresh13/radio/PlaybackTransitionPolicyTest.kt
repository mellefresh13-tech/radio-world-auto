package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTransitionPolicyTest {

    @Test
    fun transitionStoresCurrentStationAsPrevious() {
        assertEquals(
            PlaybackTransition(
                currentStationId = "next",
                previousStationId = "current",
                returningToPrevious = false
            ),
            PlaybackTransitionPolicy.onMediaItemTransition(
                currentStationId = "current",
                previousStationId = null,
                returningToPrevious = false,
                nextStationId = "next"
            )
        )
    }

    @Test
    fun returningToPreviousDoesNotOverwritePreviousStation() {
        assertEquals(
            PlaybackTransition(
                currentStationId = "previous",
                previousStationId = "older",
                returningToPrevious = false
            ),
            PlaybackTransitionPolicy.onMediaItemTransition(
                currentStationId = "current",
                previousStationId = "older",
                returningToPrevious = true,
                nextStationId = "previous"
            )
        )
    }

    @Test
    fun blankCurrentStationKeepsExistingPreviousStation() {
        assertEquals(
            PlaybackTransition(
                currentStationId = "next",
                previousStationId = "older",
                returningToPrevious = false
            ),
            PlaybackTransitionPolicy.onMediaItemTransition(
                currentStationId = "  ",
                previousStationId = "older",
                returningToPrevious = false,
                nextStationId = "next"
            )
        )
    }

    @Test
    fun sameStationKeepsExistingPreviousStation() {
        assertEquals(
            PlaybackTransition(
                currentStationId = "same",
                previousStationId = "older",
                returningToPrevious = false
            ),
            PlaybackTransitionPolicy.onMediaItemTransition(
                currentStationId = "same",
                previousStationId = "older",
                returningToPrevious = false,
                nextStationId = "same"
            )
        )
    }
}
