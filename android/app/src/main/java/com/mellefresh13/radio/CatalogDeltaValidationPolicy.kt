package com.mellefresh13.radio

/**
 * Validates manifest/delta metadata without confusing the raw catalog size with
 * the Android projection that removes stations lacking playable stream URLs.
 */
internal object CatalogDeltaValidationPolicy {

    fun payloadCountsMatch(
        expectedUpdatedCount: Int,
        updatedArraySize: Int?,
        parsedUpdatedCount: Int,
        expectedRemovedCount: Int,
        removedArraySize: Int?,
        uniqueRemovedCount: Int
    ): Boolean {
        val updatedMatch = expectedUpdatedCount < 0 ||
            (updatedArraySize == expectedUpdatedCount &&
                parsedUpdatedCount == expectedUpdatedCount)
        val removedMatch = expectedRemovedCount < 0 ||
            (removedArraySize == expectedRemovedCount &&
                uniqueRemovedCount == expectedRemovedCount)
        return updatedMatch && removedMatch
    }

    /**
     * A negative count means the manifest predates playable_station_count.
     * In that case no raw station_count comparison is valid for the filtered
     * local catalog; delta payload version and item counts still get checked.
     */
    fun playableCountMatches(expectedPlayableCount: Int, actualPlayableCount: Int): Boolean =
        expectedPlayableCount < 0 || actualPlayableCount == expectedPlayableCount
}
