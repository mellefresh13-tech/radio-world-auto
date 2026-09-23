package com.mellefresh13.radio

import android.content.Context

class UserStateStore(context: Context) {

    private val preferences = context.getSharedPreferences(
        "radio_world_auto_state",
        Context.MODE_PRIVATE
    )

    fun loadFavoriteIds(): Set<String> =
        preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()

    fun saveFavoriteIds(ids: Set<String>) {
        preferences.edit()
            .putStringSet(KEY_FAVORITES, ids.toSet())
            .apply()
    }

    fun loadRecentIds(): List<String> =
        preferences.getString(KEY_RECENTS, "")
            .orEmpty()
            .split('|')
            .filter { it.isNotBlank() }

    fun saveRecentIds(ids: Collection<String>) {
        preferences.edit()
            .putString(KEY_RECENTS, ids.joinToString("|"))
            .apply()
    }

    companion object {
        private const val KEY_FAVORITES = "favorite_ids"
        private const val KEY_RECENTS = "recent_ids"
    }
}
