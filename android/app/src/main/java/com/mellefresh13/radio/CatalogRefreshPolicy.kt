package com.mellefresh13.radio

/** Decides whether the on-disk catalog is usable for a warm start. */
internal object CatalogRefreshPolicy {
    fun shouldRefresh(
        stationCount: Int,
        savedAt: Long,
        now: Long,
        refreshIntervalMillis: Long
    ): Boolean =
        stationCount <= 0 ||
            savedAt <= 0L ||
            now - savedAt >= refreshIntervalMillis
}
