package com.mellefresh13.radio

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Small persistent store for appearance preferences shared by mobile and automotive activities.
 * Language selection intentionally remains unavailable until the app has complete translations.
 */
internal object SettingsPreferences {
    private const val PREFS_NAME = "radio_world_auto_settings"
    private const val KEY_THEME_MODE = "theme_mode"

    fun getThemeMode(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun setThemeMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME_MODE, mode)
            .apply()
    }
}
