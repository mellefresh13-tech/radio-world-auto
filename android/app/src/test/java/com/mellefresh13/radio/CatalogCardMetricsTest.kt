package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogCardMetricsTest {

    private val phonePortrait = UiProfile(
        widthDp = 390,
        heightDp = 844,
        isLandscape = false,
        isCarReference = false,
        carSafeInsetPx = 0
    )
    private val phoneLandscape = UiProfile(
        widthDp = 844,
        heightDp = 390,
        isLandscape = true,
        isCarReference = false,
        carSafeInsetPx = 0
    )
    private val tabletPortrait = UiProfile(
        widthDp = 800,
        heightDp = 1280,
        isLandscape = false,
        isCarReference = false,
        carSafeInsetPx = 0
    )
    private val tabletLandscape = UiProfile(
        widthDp = 1280,
        heightDp = 800,
        isLandscape = true,
        isCarReference = false,
        carSafeInsetPx = 0
    )

    @Test
    fun mobileCardMetricsKeepPhoneAndTabletDimensions() {
        assertEquals(
            CatalogCardMetrics(
                isPhone = true,
                cardHeightDp = 124,
                titleSizeSp = 12f,
                countSizeSp = 9f,
                sideMarginDp = 4,
                verticalMarginDp = 4
            ),
            CatalogCardMetrics.forProfile(phonePortrait, mobileCards = true)
        )
        assertEquals(
            CatalogCardMetrics(
                isPhone = false,
                cardHeightDp = 136,
                titleSizeSp = 16f,
                countSizeSp = 11f,
                sideMarginDp = 8,
                verticalMarginDp = 4
            ),
            CatalogCardMetrics.forProfile(tabletPortrait, mobileCards = true)
        )
        assertEquals(
            CatalogCardMetrics(
                isPhone = true,
                cardHeightDp = 150,
                titleSizeSp = 16f,
                countSizeSp = 9f,
                sideMarginDp = 4,
                verticalMarginDp = 4
            ),
            CatalogCardMetrics.forProfile(phoneLandscape, mobileCards = true)
        )
        assertEquals(
            CatalogCardMetrics(
                isPhone = false,
                cardHeightDp = 154,
                titleSizeSp = 18f,
                countSizeSp = 12f,
                sideMarginDp = 8,
                verticalMarginDp = 4
            ),
            CatalogCardMetrics.forProfile(tabletLandscape, mobileCards = true)
        )
    }

    @Test
    fun landscapeCardMetricsPreservePhoneAndTabletMeasurements() {
        assertEquals(
            CatalogCardMetrics(
                isPhone = false,
                cardHeightDp = 150,
                titleSizeSp = 16f,
                countSizeSp = 11f,
                sideMarginDp = 8,
                verticalMarginDp = 8
            ),
            CatalogCardMetrics.forProfile(phoneLandscape, mobileCards = false)
        )
        assertEquals(
            CatalogCardMetrics(
                isPhone = false,
                cardHeightDp = 154,
                titleSizeSp = 18f,
                countSizeSp = 12f,
                sideMarginDp = 12,
                verticalMarginDp = 10
            ),
            CatalogCardMetrics.forProfile(tabletLandscape, mobileCards = false)
        )
    }
}
