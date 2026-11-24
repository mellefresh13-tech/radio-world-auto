package com.mellefresh13.radio

/**
 * Guards against publishing a partially parsed on-device catalog snapshot.
 */
internal object CatalogCacheSnapshotPolicy {
    fun isComplete(expectedEntries: Int, parsedEntries: Int, uniqueIds: Int): Boolean =
        expectedEntries > 0 &&
            parsedEntries == expectedEntries &&
            uniqueIds == expectedEntries
}
