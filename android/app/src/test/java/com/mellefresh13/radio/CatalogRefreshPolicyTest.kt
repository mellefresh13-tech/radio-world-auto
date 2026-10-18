package com.mellefresh13.radio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRefreshPolicyTest {
    private val interval = 60_000L
    private val now = 1_000_000L

    @Test
    fun emptyCacheIsRefreshedEvenWhenItsTimestampIsRecent() {
        assertTrue(
            CatalogRefreshPolicy.shouldRefresh(
                stationCount = 0,
                savedAt = now - 1_000L,
                now = now,
                refreshIntervalMillis = interval
            )
        )
    }

    @Test
    fun nullOrInvalidCacheTimestampRequiresRefresh() {
        assertTrue(CatalogRefreshPolicy.shouldRefresh(10, 0L, now, interval))
        assertTrue(CatalogRefreshPolicy.shouldRefresh(10, -1L, now, interval))
    }

    @Test
    fun staleCatalogIsRefreshed() {
        assertTrue(
            CatalogRefreshPolicy.shouldRefresh(
                stationCount = 10,
                savedAt = now - interval,
                now = now,
                refreshIntervalMillis = interval
            )
        )
    }

    @Test
    fun populatedFreshCacheDoesNotRequireRefresh() {
        assertFalse(
            CatalogRefreshPolicy.shouldRefresh(
                stationCount = 10,
                savedAt = now - interval + 1L,
                now = now,
                refreshIntervalMillis = interval
            )
        )
    }
}
