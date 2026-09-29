package com.mellefresh13.radio

import android.content.res.Resources

// Responsive profile. The 1920x720 HMI profile is intentionally kept immutable.
data class UiProfile(
    val widthDp: Int,
    val heightDp: Int,
    val isLandscape: Boolean,
    val isCarReference: Boolean,
    val carSafeInsetPx: Int
) {
    val isPhonePortrait: Boolean
        get() = !isLandscape && widthDp <= 600

    val isPhoneLandscape: Boolean
        get() = isLandscape && widthDp < 1000

    val contentPaddingDp: Int
        get() = when {
            isCarReference -> 14
            isPhonePortrait && widthDp <= 360 -> 8
            isPhonePortrait && widthDp <= 390 -> 10
            isPhonePortrait && widthDp <= 414 -> 12
            isPhonePortrait -> 14
            isPhoneLandscape -> 10
            else -> 14
        }

    val playerLogoDp: Int
        get() = when {
            isCarReference -> 250
            isPhonePortrait && widthDp <= 360 -> 108
            isPhonePortrait && widthDp <= 390 -> 118
            isPhonePortrait -> 128
            isPhoneLandscape && widthDp <= 800 -> 132
            isPhoneLandscape -> 150
            else -> 220
        }

    val playerTitleSizeSp: Float
        get() = when {
            isCarReference -> 34f
            isPhonePortrait && widthDp <= 360 -> 23f
            isPhonePortrait && widthDp <= 390 -> 25f
            isPhonePortrait -> 27f
            isPhoneLandscape -> 26f
            else -> 34f
        }

    val playerTrackSizeSp: Float
        get() = when {
            isCarReference -> 25f
            isPhonePortrait && widthDp <= 360 -> 17f
            isPhonePortrait && widthDp <= 390 -> 19f
            isPhonePortrait -> 21f
            isPhoneLandscape -> 20f
            else -> 25f
        }

    val controlHeightDp: Int
        get() = when {
            isCarReference -> 80
            isPhonePortrait && widthDp <= 360 -> 58
            isPhonePortrait && widthDp <= 390 -> 60
            isPhonePortrait -> 62
            isPhoneLandscape -> 64
            else -> 80
        }

    val playerFavoriteDp: Int
        get() = if (isCarReference) 60 else if (isPhonePortrait) 48 else if (isPhoneLandscape) 52 else 60

    val playerHeroPaddingDp: Int
        get() = when {
            isCarReference -> 22
            isPhonePortrait && widthDp <= 360 -> 12
            isPhonePortrait -> 16
            isPhoneLandscape -> 18
            else -> 22
        }

    val playerControlIconDp: Int
        get() = when {
            isCarReference -> 74
            isPhonePortrait && widthDp <= 360 -> 50
            isPhonePortrait && widthDp <= 390 -> 54
            isPhonePortrait -> 58
            isPhoneLandscape -> 58
            else -> 74
        }

    val playerPlayWidthDp: Int
        get() = when {
            isCarReference -> 246
            isPhonePortrait && widthDp <= 360 -> 104
            isPhonePortrait && widthDp <= 390 -> 116
            isPhonePortrait -> 128
            isPhoneLandscape -> 150
            else -> 246
        }

    val playerPlayHeightDp: Int
        get() = when {
            isCarReference -> 90
            isPhonePortrait -> controlHeightDp
            isPhoneLandscape -> 64
            else -> 80
        }

    val playerStatusWidthDp: Int
        get() = when {
            isCarReference -> 230
            isPhonePortrait && widthDp <= 360 -> 170
            isPhonePortrait -> 185
            isPhoneLandscape -> 190
            else -> 230
        }

    val sectionTitleSizeSp: Float
        get() = when {
            isCarReference -> 29f
            isPhonePortrait && widthDp <= 360 -> 23f
            isPhonePortrait -> 25f
            isPhoneLandscape -> 25f
            else -> 29f
        }

    val sectionHeaderHeightDp: Int
        get() = when {
            isCarReference -> 94
            isPhonePortrait && widthDp <= 360 -> 76
            isPhonePortrait -> 82
            isPhoneLandscape -> 82
            else -> 94
        }

    val sectionSubtitleSizeSp: Float
        get() = if (isCarReference || !isPhonePortrait) 14f else 12f

    val bottomNavHeightDp: Int
        get() = when {
            isPhonePortrait && widthDp <= 360 -> 78
            isPhonePortrait && widthDp <= 390 -> 82
            isPhonePortrait -> 86
            else -> 96
        }

    val navLabelSizeSp: Float
        get() = when {
            isPhonePortrait && widthDp <= 360 -> 10f
            isPhonePortrait && widthDp <= 390 -> 11f
            isPhonePortrait -> 12f
            isPhoneLandscape -> 13f
            else -> 15f
        }

    val sidebarWidthDp: Int
        get() = when {
            isCarReference -> 188
            isPhoneLandscape -> 190
            else -> 250
        }

    val sidebarButtonHeightDp: Int
        get() = if (isPhoneLandscape) 46 else 68

    val miniPlayerHeightDp: Int
        get() = when {
            isPhonePortrait && widthDp <= 360 -> 82
            isPhonePortrait && widthDp <= 390 -> 86
            isPhonePortrait -> 90
            isPhoneLandscape -> 86
            else -> 116
        }

    val countryColumns: Int
        get() = when {
            isPhonePortrait && widthDp <= 390 -> 1
            isPhonePortrait -> 2
            isPhoneLandscape && widthDp < 700 -> 2
            isPhoneLandscape -> 3
            isCarReference -> 3
            widthDp < 1250 -> 4
            else -> 5
        }

    val genreColumns: Int
        get() = when {
            isPhonePortrait && widthDp <= 400 -> 1
            isPhonePortrait -> 2
            isPhoneLandscape && widthDp < 700 -> 2
            isPhoneLandscape -> 3
            isCarReference -> 3
            widthDp < 1250 -> 5
            else -> 6
        }

    val stationColumns: Int
        get() = when {
            isPhonePortrait -> 1
            isPhoneLandscape && widthDp < 700 -> 2
            isPhoneLandscape -> 3
            else -> 3
        }

    val playerHeroWeight: Float
        get() = when {
            isCarReference -> 1f
            isPhonePortrait -> 1f
            isPhoneLandscape -> 1f
            else -> 1f
        }

    val playerLogoMarginDp: Int
        get() = when {
            isCarReference -> 34
            isPhonePortrait -> 12
            isPhoneLandscape -> 18
            else -> 24
        }

    val useOnScreenKeypad: Boolean
        get() = isCarReference

    companion object {
        fun from(resources: Resources): UiProfile {
            val metrics = resources.displayMetrics
            val widthPixels = metrics.widthPixels
            val heightPixels = metrics.heightPixels
            val landscape = widthPixels >= heightPixels
            val carReference = landscape &&
                minOf(widthPixels, heightPixels) == 720 &&
                maxOf(widthPixels, heightPixels) == 1920

            return UiProfile(
                widthDp = resources.configuration.screenWidthDp,
                heightDp = resources.configuration.screenHeightDp,
                isLandscape = landscape,
                isCarReference = carReference,
                carSafeInsetPx = if (carReference) 96 else 0
            )
        }
    }
}
