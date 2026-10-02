package com.mellefresh13.radio

data class PlaybackTransition(
    val currentStationId: String?,
    val previousStationId: String?,
    val returningToPrevious: Boolean
)

object PlaybackTransitionPolicy {
    fun onMediaItemTransition(
        currentStationId: String?,
        previousStationId: String?,
        returningToPrevious: Boolean,
        nextStationId: String
    ): PlaybackTransition {
        if (returningToPrevious) {
            return PlaybackTransition(
                currentStationId = nextStationId,
                previousStationId = previousStationId,
                returningToPrevious = false
            )
        }

        val previous = if (
            !currentStationId.isNullOrBlank() &&
            currentStationId != nextStationId
        ) {
            currentStationId
        } else {
            previousStationId
        }

        return PlaybackTransition(
            currentStationId = nextStationId,
            previousStationId = previous,
            returningToPrevious = false
        )
    }
}
