package com.mellefresh13.radio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogDeltaValidationPolicyTest {

    @Test
    fun acceptsCompleteDeltaWhenManifestCountsMatchPayload() {
        assertTrue(
            CatalogDeltaValidationPolicy.payloadCountsMatch(
                expectedUpdatedCount = 2,
                updatedArraySize = 2,
                parsedUpdatedCount = 2,
                expectedRemovedCount = 3,
                removedArraySize = 3,
                uniqueRemovedCount = 3
            )
        )
    }

    @Test
    fun rejectsTruncatedOrMalformedUpdatedPayload() {
        assertFalse(
            CatalogDeltaValidationPolicy.payloadCountsMatch(
                expectedUpdatedCount = 2,
                updatedArraySize = 2,
                parsedUpdatedCount = 1,
                expectedRemovedCount = 0,
                removedArraySize = 0,
                uniqueRemovedCount = 0
            )
        )
        assertFalse(
            CatalogDeltaValidationPolicy.payloadCountsMatch(
                expectedUpdatedCount = 2,
                updatedArraySize = null,
                parsedUpdatedCount = 0,
                expectedRemovedCount = 0,
                removedArraySize = 0,
                uniqueRemovedCount = 0
            )
        )
    }

    @Test
    fun rejectsDuplicateRemovedIdsThatShrinkTheParsedSet() {
        assertFalse(
            CatalogDeltaValidationPolicy.payloadCountsMatch(
                expectedUpdatedCount = 0,
                updatedArraySize = 0,
                parsedUpdatedCount = 0,
                expectedRemovedCount = 2,
                removedArraySize = 2,
                uniqueRemovedCount = 1
            )
        )
    }

    @Test
    fun legacyManifestDoesNotCompareRawStationCountToPlayableProjection() {
        assertTrue(CatalogDeltaValidationPolicy.playableCountMatches(-1, 120))
        assertTrue(CatalogDeltaValidationPolicy.playableCountMatches(120, 120))
        assertFalse(CatalogDeltaValidationPolicy.playableCountMatches(121, 120))
    }

    @Test
    fun unknownManifestItemCountsRemainBackwardCompatible() {
        assertTrue(
            CatalogDeltaValidationPolicy.payloadCountsMatch(
                expectedUpdatedCount = -1,
                updatedArraySize = 12,
                parsedUpdatedCount = 12,
                expectedRemovedCount = -1,
                removedArraySize = 4,
                uniqueRemovedCount = 4
            )
        )
    }
}
