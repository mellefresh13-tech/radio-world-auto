package com.mellefresh13.radio

internal object ImagePayloadPolicy {
    const val MAX_DOWNLOAD_BYTES = 8L * 1024 * 1024
    const val MAX_DIMENSION_PX = 1_024

    fun isContentLengthAllowed(contentLength: Long): Boolean =
        contentLength == -1L || contentLength in 0L..MAX_DOWNLOAD_BYTES

    fun canAppendChunk(bytesRead: Long, chunkSize: Int): Boolean {
        if (bytesRead !in 0L..MAX_DOWNLOAD_BYTES || chunkSize < 0) return false
        return chunkSize.toLong() <= MAX_DOWNLOAD_BYTES - bytesRead
    }

    fun sampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        val longestEdge = maxOf(width, height).toLong()
        var sample = 1
        while (longestEdge / sample > MAX_DIMENSION_PX) sample *= 2
        return sample
    }

    /** LruCache units are KiB; even a sub-KiB bitmap must count toward the limit. */
    fun memoryCacheSizeKb(byteCount: Int): Int {
        if (byteCount <= 0) return 1
        return ((byteCount.toLong() + 1023L) / 1024L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
            .coerceAtLeast(1)
    }
}
