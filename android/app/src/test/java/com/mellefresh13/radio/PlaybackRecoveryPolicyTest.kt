package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecoveryPolicyTest {

    @Test
    fun failureUsesNextStreamBeforeRetry() {
        assertEquals(
            PlaybackRecoveryAction.NextStream(1),
            PlaybackRecoveryPolicy.onFailure(currentStreamIndex = 0, streamCount = 3, retryCount = 0)
        )
    }

    @Test
    fun failureRetriesAfterLastStream() {
        assertEquals(
            PlaybackRecoveryAction.Retry(1),
            PlaybackRecoveryPolicy.onFailure(currentStreamIndex = 2, streamCount = 3, retryCount = 0)
        )
    }

    @Test
    fun failureSwitchesStationAfterRetriesExhausted() {
        assertEquals(
            PlaybackRecoveryAction.SwitchStation,
            PlaybackRecoveryPolicy.onFailure(currentStreamIndex = 0, streamCount = 1, retryCount = PlaybackRecoveryPolicy.MAX_RETRIES)
        )
    }
}
