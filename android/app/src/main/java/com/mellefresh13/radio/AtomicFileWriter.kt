package com.mellefresh13.radio

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Replaces a cache file only after its complete contents have been written.
 *
 * The temporary file shares the target's parent directory so the final move can
 * be atomic on supported filesystems. If atomic replacement is unavailable, the
 * write fails and the previous target remains in place.
 */
internal object AtomicFileWriter {
    @Throws(IOException::class)
    fun writeAtomically(target: File, writeContents: (File) -> Unit) {
        val parent = target.absoluteFile.parentFile
            ?: throw IOException("Cache target has no parent directory: $target")
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Unable to create cache directory: $parent")
        }

        val temporary = File.createTempFile(target.name + ".", ".tmp", parent)
        try {
            writeContents(temporary)
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (unsupported: AtomicMoveNotSupportedException) {
                throw IOException("Atomic cache replacement is not supported for $target", unsupported)
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
}
