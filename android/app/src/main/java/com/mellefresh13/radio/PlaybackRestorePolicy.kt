package com.mellefresh13.radio

object PlaybackRestorePolicy {
    fun resolveStationId(
        restoredStationId: String?,
        playerStationId: String?,
        recentStationId: String?,
        availableStationIds: Set<String>
    ): String? {
        if (!restoredStationId.isNullOrBlank()) {
            return restoredStationId.takeIf { it in availableStationIds }
        }
        playerStationId?.takeIf { it.isNotBlank() && it in availableStationIds }?.let { return it }
        return recentStationId?.takeIf { it in availableStationIds }
    }
}
