package com.mellefresh13.radio

object PlaybackRestorePolicy {
    fun resolveStationId(
        restoredStationId: String?,
        playerStationId: String?,
        recentStationId: String?,
        availableStationIds: Set<String>
    ): String? =
        listOf(restoredStationId, playerStationId, recentStationId)
            .firstOrNull { !it.isNullOrBlank() && it in availableStationIds }
}
