package com.mellefresh13.radio

object PlaybackNavigationPolicy {
    fun resolvePreviousStationId(
        inMemoryStationId: String?,
        persistedStationId: String?
    ): String? =
        inMemoryStationId?.takeIf { it.isNotBlank() }
            ?: persistedStationId?.takeIf { it.isNotBlank() }
}
