package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePayloadPolicyTest {

    @Test
    fun contentLengthAllowsUnknownAndBoundedResponsesOnly() {
        assertTrue(ImagePayloadPolicy.isContentLengthAllowed(-1L))
        assertTrue(ImagePayloadPolicy.isContentLengthAllowed(0L))
        assertTrue(ImagePayloadPolicy.isContentLengthAllowed(ImagePayloadPolicy.MAX_DOWNLOAD_BYTES))
        assertFalse(ImagePayloadPolicy.isContentLengthAllowed(-2L))
        assertFalse(ImagePayloadPolicy.isContentLengthAllowed(ImagePayloadPolicy.MAX_DOWNLOAD_BYTES + 1L))
    }

    @Test
    fun byteBudgetAllowsExactLimitAndRejectsOversizedChunks() {
        val maxBytes = ImagePayloadPolicy.MAX_DOWNLOAD_BYTES

        assertTrue(ImagePayloadPolicy.canAppendChunk(0L, 8 * 1024))
        assertTrue(ImagePayloadPolicy.canAppendChunk(maxBytes - 3L, 3))
        assertFalse(ImagePayloadPolicy.canAppendChunk(maxBytes - 3L, 4))
        assertFalse(ImagePayloadPolicy.canAppendChunk(maxBytes, 1))
        assertFalse(ImagePayloadPolicy.canAppendChunk(-1L, 1))
        assertFalse(ImagePayloadPolicy.canAppendChunk(0L, -1))
    }

    @Test
    fun sampleSizeKeepsLandscapeAndPortraitImagesWithinTargetEdge() {
        val landscapeSample = ImagePayloadPolicy.sampleSize(4_096, 2_048)
        val portraitSample = ImagePayloadPolicy.sampleSize(6_000, 4_000)

        assertEquals(4, landscapeSample)
        assertEquals(8, portraitSample)
        assertTrue(4_096 / landscapeSample <= ImagePayloadPolicy.MAX_DIMENSION_PX)
        assertTrue(2_048 / landscapeSample <= ImagePayloadPolicy.MAX_DIMENSION_PX)
        assertTrue(6_000 / portraitSample <= ImagePayloadPolicy.MAX_DIMENSION_PX)
        assertTrue(4_000 / portraitSample <= ImagePayloadPolicy.MAX_DIMENSION_PX)
    }

    @Test
    fun smallOrInvalidDimensionsUseSafeDefaultSampling() {
        assertEquals(1, ImagePayloadPolicy.sampleSize(256, 128))
        assertEquals(1, ImagePayloadPolicy.sampleSize(0, 1))
        assertEquals(1, ImagePayloadPolicy.sampleSize(-100, 2_000))
    }

    @Test
    fun memoryCacheSizeRoundsUpAndNeverCountsABitmapAsZero() {
        assertEquals(1, ImagePayloadPolicy.memoryCacheSizeKb(1))
        assertEquals(1, ImagePayloadPolicy.memoryCacheSizeKb(4))
        assertEquals(1, ImagePayloadPolicy.memoryCacheSizeKb(1_024))
        assertEquals(2, ImagePayloadPolicy.memoryCacheSizeKb(1_025))
        assertEquals(16, ImagePayloadPolicy.memoryCacheSizeKb(16 * 1_024))
        assertEquals(1, ImagePayloadPolicy.memoryCacheSizeKb(0))
        assertEquals(1, ImagePayloadPolicy.memoryCacheSizeKb(-4))
    }
}
