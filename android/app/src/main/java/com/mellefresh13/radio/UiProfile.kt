package com.mellefresh13.radio

import android.content.res.Resources

// HTML reference HMI profile

data class UiProfile(
    val widthDp: Int,
    val heightDp: Int,
    val isLandscape: Boolean,
    val isCarReference: Boolean,
    val carSafeInsetPx: Int
) {
    val contentPaddingDp: Int
        get() = when {
            isCarReference -> 14
            widthDp <= 360 -> 8
            widthDp <= 390 -> 10
            widthDp <= 414 -> 12
            isLandscape && widthDp < 700 -> 10
            else -> 14
        }

    val playerLogoDp: Int
        get() = when {
            isCarReference -> 250
            isLandscape && widthDp >= 1000 -> 220
            isLandscape -> 150
            widthDp <= 360 -> 108
            widthDp <= 390 -> 118
            else -> 128
        }

    val controlHeightDp: Int
        get() = when {
            isCarReference -> 80
            isLandscape -> 64
            widthDp <= 360 -> 58
            widthDp <= 390 -> 60
            else -> 62
        }

    val countryColumns: Int
        get() = when {
            !isLandscape && widthDp < 400 -> 1
            widthDp < 640 -> 2
            widthDp < 900 -> 3
            widthDp < 1250 -> 4
            else -> 5
        }

    val genreColumns: Int
        get() = when {
            !isLandscape && widthDp < 400 -> 2
            widthDp < 640 -> 3
            widthDp < 900 -> 4
            widthDp < 1250 -> 5
            else -> 6
        }

    val useOnScreenKeypad: Boolean
        get() = isCarReference

    companion object {
        fun from(resources: Resources): UiProfile {
            val metrics = resources.displayMetrics
            val widthPixels = metrics.widthPixels
            val heightPixels = metrics.heightPixels
            val landscape = widthPixels >= heightPixels

            return UiProfile(
                widthDp = resources.configuration.screenWidthDp,
                heightDp = resources.configuration.screenHeightDp,
                isLandscape = landscape,
                isCarReference = landscape &&
                    minOf(widthPixels, heightPixels) == 720 &&
                    maxOf(widthPixels, heightPixels) == 1920,
                carSafeInsetPx = if (
                    landscape &&
                    minOf(widthPixels, heightPixels) == 720 &&
                    maxOf(widthPixels, heightPixels) == 1920
                ) 96 else 0
            )
        }
    }
}
