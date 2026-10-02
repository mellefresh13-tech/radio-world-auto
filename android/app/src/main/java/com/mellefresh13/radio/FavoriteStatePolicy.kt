package com.mellefresh13.radio

object FavoriteStatePolicy {
    fun toggle(favoriteIds: Set<String>, stationId: String): Boolean =
        stationId !in favoriteIds
}
