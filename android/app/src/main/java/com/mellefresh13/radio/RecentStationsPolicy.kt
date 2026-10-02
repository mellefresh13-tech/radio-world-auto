package com.mellefresh13.radio

object RecentStationsPolicy {
    const val MAX_RECENTS = 10

    fun add(current: Collection<String>, stationId: String): List<String> =
        buildList {
            add(stationId)
            current.filter { it != stationId }.take(MAX_RECENTS - 1).forEach(::add)
        }
}
