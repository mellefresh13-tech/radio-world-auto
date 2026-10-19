package com.mellefresh13.radio

import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.IOException

/**
 * Reads UTF-8 response text while reporting the underlying number of bytes read,
 * not decoded UTF-16 characters. The reader avoids buffering a second full copy
 * of a potentially large catalog response just to count its encoded bytes.
 */
internal object ProgressReportingUtf8Reader {
    fun read(
        input: InputStream,
        totalBytes: Long,
        maxBytes: Long = Long.MAX_VALUE,
        onProgress: ((loadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): String {
        require(maxBytes >= 0L) { "maxBytes must not be negative" }
        val counted = CountingInputStream(input, maxBytes)
        val output = StringBuilder()
        val buffer = CharArray(8 * 1024)
        var lastReported = -1L

        InputStreamReader(counted, Charsets.UTF_8).use { reader ->
            var count = reader.read(buffer)
            while (count >= 0) {
                if (count > 0) {
                    output.append(buffer, 0, count)
                    val loadedBytes = counted.bytesRead
                    if (loadedBytes != lastReported) {
                        onProgress?.invoke(loadedBytes, totalBytes)
                        lastReported = loadedBytes
                    }
                }
                count = reader.read(buffer)
            }
        }

        // A decoder can consume a final byte sequence without adding visible
        // characters (for example, a trailing byte-order marker); report it too.
        if (counted.bytesRead > 0L && counted.bytesRead != lastReported) {
            onProgress?.invoke(counted.bytesRead, totalBytes)
        }
        return output.toString()
    }

    internal class PayloadTooLargeException(val maxBytes: Long) :
        IOException("Response exceeds the $maxBytes-byte limit")

    private class CountingInputStream(
        input: InputStream,
        private val maxBytes: Long
    ) : FilterInputStream(input) {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                bytesRead += 1L
                checkLimit()
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val remaining = maxBytes - bytesRead
            // Read at most one byte beyond the limit so an exact-size stream can
            // still reach EOF, while an oversized stream is rejected promptly.
            val allowed = if (remaining >= length.toLong()) {
                length
            } else {
                (remaining + 1L).coerceAtLeast(1L).coerceAtMost(length.toLong()).toInt()
            }
            val count = super.read(buffer, offset, allowed)
            if (count > 0) {
                bytesRead += count.toLong()
                checkLimit()
            }
            return count
        }

        private fun checkLimit() {
            if (bytesRead > maxBytes) throw PayloadTooLargeException(maxBytes)
        }
    }
}
