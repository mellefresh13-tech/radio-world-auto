package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackMetadataPolicyTest {

    @Test
    fun usesTrackTitleWhenAvailable() {
        assertEquals(
            "Artist - Track",
            PlaybackMetadataPolicy.title("Station", "Artist - Track")
        )
    }

    @Test
    fun fallsBackToStationNameWhenTrackTitleIsBlank() {
        assertEquals(
            "Station",
            PlaybackMetadataPolicy.title("Station", "  ")
        )
    }

    @Test
    fun fallsBackToStationNameWhenTrackTitleIsMissing() {
        assertEquals(
            "Station",
            PlaybackMetadataPolicy.title("Station", null)
        )
    }
}
