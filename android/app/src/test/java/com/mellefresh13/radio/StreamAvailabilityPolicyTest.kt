package com.mellefresh13.radio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamAvailabilityPolicyTest {

    @Test
    fun blankStatusIsPlayable() {
        assertTrue(StreamAvailabilityPolicy.isPlayable(""))
        assertTrue(StreamAvailabilityPolicy.isPlayable("  "))
    }

    @Test
    fun onlineStatusIsPlayableIgnoringCase() {
        assertTrue(StreamAvailabilityPolicy.isPlayable("online"))
        assertTrue(StreamAvailabilityPolicy.isPlayable("ONLINE"))
    }

    @Test
    fun offlineStatusIsNotPlayable() {
        assertFalse(StreamAvailabilityPolicy.isPlayable("offline"))
    }
}
