package com.mellefresh13.radio

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads a UTF-8 response while reporting bytes read, not decoded UTF-16 chars.
 * HTTP Content-Length is expressed in bytes, so both progress values now use
 * the same unit even when the catalog contains non-ASCII station metadata.
 */
internal object ProgressReportingUtf8Reader {
    fun read(
        input: InputStream,
        totalBytes: Long,
        onProgress: ((loadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var loadedBytes = 0L

        input.use { stream ->
            var count = stream.read(buffer)
            while (count >= 0) {
                if (count > 0) {
                    output.write(buffer, 0, count)
                    loadedBytes += count.toLong()
                    onProgress?.invoke(loadedBytes, totalBytes)
                }
                count = stream.read(buffer)
            }
        }

        return String(output.toByteArray(), Charsets.UTF_8)
    }
}
