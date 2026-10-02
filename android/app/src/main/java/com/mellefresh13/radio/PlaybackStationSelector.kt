package com.mellefresh13.radio

object PlaybackStationSelector {

    fun nextPlayableStation(
        catalog: List<Station>,
        currentStationId: String?,
        failedStationIds: Set<String>
    ): Station? {
        val playable = catalog.filter { it.streams.isNotEmpty() }.distinctBy { it.id }
        if (playable.isEmpty()) return null

        val currentIndex = playable.indexOfFirst { it.id == currentStationId }
        for (offset in 1..playable.size) {
            val index = if (currentIndex >= 0) {
                (currentIndex + offset) % playable.size
            } else {
                (offset - 1) % playable.size
            }
            val candidate = playable[index]
            if (candidate.id != currentStationId && candidate.id !in failedStationIds) {
                return candidate
            }
        }
        return null
    }
}
