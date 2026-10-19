package com.mellefresh13.radio

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressReportingUtf8ReaderTest {

    @Test
    fun reportsUtf8PayloadLengthInBytesNotCharacters() {
        val text = "Радио 🌍 café 日本語 — ".repeat(1_000)
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val progress = mutableListOf<Pair<Long, Long>>()

        val decoded = ProgressReportingUtf8Reader.read(
            input = ByteArrayInputStream(bytes),
            totalBytes = bytes.size.toLong()
        ) { loaded, total ->
            progress += loaded to total
        }

        assertEquals(text, decoded)
        assertTrue(progress.size > 1)
        assertEquals(bytes.size.toLong(), progress.last().first)
        assertEquals(bytes.size.toLong(), progress.last().second)
        assertTrue(progress.zipWithNext().all { (before, after) -> after.first > before.first })
        assertTrue(progress.all { (loaded, total) -> loaded <= total && total == bytes.size.toLong() })
    }

    @Test
    fun preservesUnknownContentLengthWhileStillReportingBytesRead() {
        val bytes = "Каталог радио".toByteArray(StandardCharsets.UTF_8)
        val progress = mutableListOf<Pair<Long, Long>>()

        val decoded = ProgressReportingUtf8Reader.read(
            input = ByteArrayInputStream(bytes),
            totalBytes = -1L
        ) { loaded, total ->
            progress += loaded to total
        }

        assertEquals("Каталог радио", decoded)
        assertFalse(progress.isEmpty())
        assertEquals(bytes.size.toLong(), progress.last().first)
        assertEquals(-1L, progress.last().second)
    }
    @Test
    fun acceptsPayloadExactlyAtByteLimitIncludingMultibyteUtf8() {
        val text = "🌍éРадио"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)

        val decoded = ProgressReportingUtf8Reader.read(
            input = ByteArrayInputStream(bytes),
            totalBytes = -1L,
            maxBytes = bytes.size.toLong()
        )

        assertEquals(text, decoded)
    }

    @Test
    fun rejectsPayloadThatExceedsByteLimitEvenWhenLengthIsUnknown() {
        val bytes = "🌍éРадио".toByteArray(StandardCharsets.UTF_8)
        val limit = bytes.size - 1L

        val error = assertThrows(
            ProgressReportingUtf8Reader.PayloadTooLargeException::class.java
        ) {
            ProgressReportingUtf8Reader.read(
                input = ByteArrayInputStream(bytes),
                totalBytes = -1L,
                maxBytes = limit
            )
        }

        assertEquals(limit, error.maxBytes)
    }

}
