package com.mellefresh13.radio

/**
 * Catalog snapshots usually omit the live track currently playing on a station.
 * Keep locally observed metadata unless a sync explicitly supplies a nonblank
 * replacement for that same station ID.
 */
internal object StationTrackMetadataPolicy {
    fun preserve(
        existingStations: Collection<Station>,
        incomingStations: List<ApiStation>
    ): List<ApiStation> {
        if (existingStations.isEmpty() || incomingStations.isEmpty()) {
            return incomingStations
        }

        val existingById = existingStations.associateBy { it.id }
        return incomingStations.map { incoming ->
            val existing = existingById[incoming.id] ?: return@map incoming
            incoming.copy(
                songTitle = incoming.songTitle?.takeIf { it.isNotBlank() }
                    ?: existing.songTitle?.takeIf { it.isNotBlank() },
                artist = incoming.artist?.takeIf { it.isNotBlank() }
                    ?: existing.artist?.takeIf { it.isNotBlank() }
            )
        }
    }
}
