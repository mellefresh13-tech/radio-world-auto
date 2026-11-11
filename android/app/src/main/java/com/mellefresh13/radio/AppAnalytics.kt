package com.mellefresh13.radio

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Privacy-conscious app telemetry.
 *
 * Search terms are restricted to short radio/catalog queries; contact details,
 * URLs and other punctuation commonly used in personal data are discarded.
 */
internal object AppAnalytics {

    fun appOpened(context: Context, profile: UiProfile) {
        val profileName = profileName(profile)
        val language = AppCompatDelegate.getApplicationLocales()[0]?.language
            ?: context.resources.configuration.locales[0]?.language
            ?: "en"
        updateUserProperties(context, profile)
        runCatching {
            val analytics = FirebaseAnalytics.getInstance(context.applicationContext)
            analytics.logEvent("radio_app_open", Bundle().apply {
                putString("ui_profile", profileName)
                putString("app_language", language.take(8))
                putString("app_theme", themeName(SettingsPreferences.getThemeMode(context)))
            })
        }
        screenOpened(context, "player")
        runCatching {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("ui_profile", profileName)
                setCustomKey("app_version", BuildConfig.VERSION_NAME)
            }
        }
    }

    fun updateUserProperties(context: Context, profile: UiProfile) {
        val language = AppCompatDelegate.getApplicationLocales()[0]?.language
            ?: context.resources.configuration.locales[0]?.language
            ?: "en"
        val theme = themeName(SettingsPreferences.getThemeMode(context))
        runCatching {
            FirebaseAnalytics.getInstance(context.applicationContext).apply {
                setUserProperty("ui_profile", profileName(profile))
                setUserProperty("app_language", language.take(8))
                setUserProperty("app_theme", theme)
                setUserProperty("app_version", BuildConfig.VERSION_NAME)
            }
        }
    }

    fun stationPlayed(
        context: Context,
        stationId: String,
        profile: UiProfile,
        stationName: String? = null,
        genre: String? = null,
        country: String? = null
    ) {
        logEvent(context, "station_play", Bundle().apply {
            putString("station_id", stationId.take(100))
            putString("ui_profile", profileName(profile))
            putStationDetails(this, stationName, genre, country)
        })
    }

    fun stationListen(
        context: Context,
        stationId: String,
        durationMs: Long,
        profile: UiProfile,
        stationName: String? = null,
        genre: String? = null,
        country: String? = null
    ) {
        val seconds = (durationMs / 1_000L).coerceAtLeast(0L)
        if (seconds < MIN_LISTEN_SECONDS) return
        logEvent(context, "station_listen", Bundle().apply {
            putString("station_id", stationId.take(100))
            putLong("listen_seconds", seconds.coerceAtMost(MAX_LISTEN_SECONDS))
            putString("ui_profile", profileName(profile))
            putStationDetails(this, stationName, genre, country)
        })
    }

    fun playbackError(
        context: Context,
        stationId: String?,
        errorCode: String,
        profile: UiProfile
    ) {
        logEvent(context, "playback_error", Bundle().apply {
            stationId?.takeIf { it.isNotBlank() }?.let { putString("station_id", it.take(100)) }
            putString("error_code", errorCode.take(64))
            putString("ui_profile", profileName(profile))
        })
    }

    fun searchUsed(context: Context, query: String, resultCount: Int, profile: UiProfile) {
        val rawQuery = query.trim()
        val containsPersonalPattern = rawQuery.contains("@") ||
            rawQuery.contains("://") ||
            rawQuery.count { it.isDigit() } >= 7
        val safeQuery = if (containsPersonalPattern) "" else rawQuery
            .replace(Regex("[^\\p{L}\\p{N} .&+'_-]"), "")
            .replace(Regex("\\s+"), " ")
            .take(MAX_SEARCH_QUERY_LENGTH)
            .trim()
        logEvent(context, "search_used", Bundle().apply {
            if (safeQuery.length >= 2) putString("search_query", safeQuery)
            putLong("result_count", resultCount.coerceAtLeast(0).toLong())
            putString("ui_profile", profileName(profile))
        })
    }

    fun favoriteChanged(
        context: Context,
        stationId: String,
        added: Boolean,
        stationName: String? = null,
        genre: String? = null,
        country: String? = null
    ) {
        logEvent(context, if (added) "favorite_added" else "favorite_removed", Bundle().apply {
            putString("station_id", stationId.take(100))
            putStationDetails(this, stationName, genre, country)
        })
    }

    fun screenOpened(context: Context, screen: String) {
        logEvent(context, "screen_opened", Bundle().apply {
            putString("screen_name", screen.take(32))
        })
    }

    private fun putStationDetails(
        params: Bundle,
        stationName: String?,
        genre: String?,
        country: String?
    ) {
        stationName?.takeIf { it.isNotBlank() }?.let { params.putString("station_name", it.take(100)) }
        genre?.takeIf { it.isNotBlank() }?.let { params.putString("station_genre", it.take(64)) }
        country?.takeIf { it.isNotBlank() }?.let { params.putString("station_country", it.take(64)) }
    }

    private fun logEvent(context: Context, name: String, params: Bundle) {
        runCatching {
            FirebaseAnalytics.getInstance(context.applicationContext).logEvent(name, params)
        }
    }

    private fun profileName(profile: UiProfile): String = when {
        profile.isCarReference -> "car"
        profile.isPhonePortrait || profile.isPhoneLandscape -> "phone"
        else -> "tablet"
    }

    private fun themeName(mode: Int): String = when (mode) {
        AppCompatDelegate.MODE_NIGHT_YES -> "dark"
        AppCompatDelegate.MODE_NIGHT_NO -> "light"
        else -> "system"
    }

    private const val MIN_LISTEN_SECONDS = 5L
    private const val MAX_LISTEN_SECONDS = 4L * 60L * 60L
    private const val MAX_SEARCH_QUERY_LENGTH = 40
}
