package com.mellefresh13.radio

/**
 * Dimension decisions shared by country and genre catalog cards.
 *
 * The adapters intentionally keep their content/layout composition separate; only
 * the identical responsive measurements live here to prevent profile drift.
 */
internal data class CatalogCardMetrics(
    val isPhone: Boolean,
    val cardHeightDp: Int,
    val titleSizeSp: Float,
    val countSizeSp: Float,
    val sideMarginDp: Int,
    val verticalMarginDp: Int
) {
    companion object {
        fun forProfile(profile: UiProfile, mobileCards: Boolean): CatalogCardMetrics {
            if (mobileCards) {
                val phone = profile.isPhonePortrait || profile.isPhoneLandscape
                return CatalogCardMetrics(
                    isPhone = phone,
                    cardHeightDp = when {
                        profile.isPhonePortrait -> 124
                        profile.isTabletPortrait -> 136
                        profile.isTabletLandscape -> 154
                        else -> 150
                    },
                    titleSizeSp = when {
                        profile.isPhonePortrait -> 12f
                        profile.isTabletPortrait -> 16f
                        profile.isTabletLandscape -> 18f
                        else -> 16f
                    },
                    countSizeSp = if (phone) 9f else if (profile.isTabletPortrait) 11f else 12f,
                    sideMarginDp = if (phone) 4 else 8,
                    verticalMarginDp = 4
                )
            }

            val tablet = profile.isTabletLandscape
            return CatalogCardMetrics(
                isPhone = profile.isPhonePortrait || profile.isPhoneLandscape,
                cardHeightDp = if (tablet) 154 else 150,
                titleSizeSp = if (tablet) 18f else 16f,
                countSizeSp = if (tablet) 12f else 11f,
                sideMarginDp = if (tablet) 12 else 8,
                verticalMarginDp = if (tablet) 10 else 8
            )
        }
    }
}
