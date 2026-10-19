package com.mellefresh13.radio

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AtomicFileWriterTest {

    @Test
    fun replacesTargetAfterCompleteWrite() {
        val directory = Files.createTempDirectory("radio-cache-atomic").toFile()
        try {
            val target = File(directory, "station.png")
            target.writeText("old-complete-image")

            AtomicFileWriter.writeAtomically(target) { temporary ->
                temporary.writeText("new-complete-image")
            }

            assertEquals("new-complete-image", target.readText())
            assertEquals(listOf(target.name), directory.listFiles()!!.map { it.name })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedWritePreservesPreviousTargetAndRemovesTemporaryFile() {
        val directory = Files.createTempDirectory("radio-cache-failure").toFile()
        try {
            val target = File(directory, "station.png")
            target.writeText("known-good-image")

            val failure = runCatching {
                AtomicFileWriter.writeAtomically(target) { temporary ->
                    temporary.writeText("partial-corrupt-image")
                    throw IOException("simulated disk failure")
                }
            }.exceptionOrNull()

            assertNotNull(failure)
            assertEquals("known-good-image", target.readText())
            assertEquals(listOf(target.name), directory.listFiles()!!.map { it.name })
        } finally {
            directory.deleteRecursively()
        }
    }
}
