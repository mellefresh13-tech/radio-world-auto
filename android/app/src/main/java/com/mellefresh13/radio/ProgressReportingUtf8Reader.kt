package com.mellefresh13.radio

import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Reads UTF-8 response text while reporting the underlying number of bytes read,
 * not decoded UTF-16 characters. The reader avoids buffering a second full copy
 * of a potentially large catalog response just to count its encoded bytes.
 */
internal object ProgressReportingUtf8Reader {
    fun read(
        input: InputStream,
        totalBytes: Long,
        onProgress: ((loadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): String {
        val counted = CountingInputStream(input)
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

        return output.toString()
    }

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) bytesRead += 1L
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) bytesRead += count.toLong()
            return count
        }
    }
}
