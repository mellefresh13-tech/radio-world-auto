package com.mellefresh13.radio

object PlaybackAdjacentStationPolicy {
    fun resolve(
        catalog: List<Station>,
        currentStationId: String?,
        delta: Int
    ): Station? {
        val playable = catalog
            .filter { it.streams.isNotEmpty() }
            .distinctBy { it.id }
        if (playable.isEmpty()) return null

        val currentIndex = playable.indexOfFirst { it.id == currentStationId }
        val base = if (currentIndex >= 0) currentIndex else 0
        val targetIndex = (base + delta + playable.size) % playable.size
        return playable[targetIndex].takeIf { it.id != currentStationId }
    }
}
