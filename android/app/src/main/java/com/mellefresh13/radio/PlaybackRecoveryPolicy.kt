package com.mellefresh13.radio

sealed interface PlaybackRecoveryAction {
    data class NextStream(val index: Int) : PlaybackRecoveryAction
    data class Retry(val attempt: Int) : PlaybackRecoveryAction
    data object SwitchStation : PlaybackRecoveryAction
}

object PlaybackRecoveryPolicy {
    const val MAX_RETRIES = 2

    fun onFailure(
        currentStreamIndex: Int,
        streamCount: Int,
        retryCount: Int
    ): PlaybackRecoveryAction {
        if (currentStreamIndex + 1 < streamCount) {
            return PlaybackRecoveryAction.NextStream(currentStreamIndex + 1)
        }
        if (retryCount < MAX_RETRIES) {
            return PlaybackRecoveryAction.Retry(retryCount + 1)
        }
        return PlaybackRecoveryAction.SwitchStation
    }
}
