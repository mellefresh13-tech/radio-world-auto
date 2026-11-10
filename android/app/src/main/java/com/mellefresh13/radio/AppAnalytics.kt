package com.mellefresh13.radio

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Privacy-conscious app telemetry.
 *
 * Never pass search text, track metadata, stream URLs, account details, or precise location here.
 */
internal object AppAnalytics {

    fun appOpened(context: Context, profile: UiProfile) {
        val profileName = profileName(profile)
        runCatching {
            val analytics = FirebaseAnalytics.getInstance(context.applicationContext)
            analytics.setUserProperty("ui_profile", profileName)
            analytics.logEvent("radio_app_open", Bundle().apply {
                putString("ui_profile", profileName)
            })
        }
        runCatching {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("ui_profile", profileName)
                setCustomKey("app_version", BuildConfig.VERSION_NAME)
            }
        }
    }

    fun stationPlayed(context: Context, stationId: String, profile: UiProfile) {
        logEvent(context, "station_play", Bundle().apply {
            putString("station_id", stationId.take(100))
            putString("ui_profile", profileName(profile))
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

    fun searchUsed(context: Context, profile: UiProfile) {
        logEvent(context, "search_used", Bundle().apply {
            putString("ui_profile", profileName(profile))
        })
    }

    fun favoriteChanged(context: Context, stationId: String, added: Boolean) {
        logEvent(context, if (added) "favorite_added" else "favorite_removed", Bundle().apply {
            putString("station_id", stationId.take(100))
        })
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
}
