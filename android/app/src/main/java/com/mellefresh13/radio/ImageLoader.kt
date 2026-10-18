package com.mellefresh13.radio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

object ImageLoader {
    private const val MEMORY_CACHE_KB = 16 * 1024
    private const val DISK_CACHE_LIMIT_BYTES = 64L * 1024 * 1024

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestCoalescer = KeyedRequestCoalescer<String, Bitmap>()
    private val cache = object : LruCache<String, Bitmap>(MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            value.byteCount / 1024
    }

    @Volatile
    private var diskCacheDir: File? = null

    fun initialize(context: Context) {
        diskCacheDir = File(context.cacheDir, "station-logo-cache").apply { mkdirs() }
    }

    fun load(url: String, onFailure: (() -> Unit)? = null, callback: (Bitmap) -> Unit) {
        val registration = requestCoalescer.register(
            key = url,
            callbacks = KeyedRequestCoalescer.Callbacks(callback, onFailure),
            cachedValue = { cache.get(url) }
        )
        when (registration) {
            is KeyedRequestCoalescer.Registration.Cached -> {
                callback(registration.value)
                return
            }
            KeyedRequestCoalescer.Registration.Joined -> return
            KeyedRequestCoalescer.Registration.Started -> Unit
        }

        try {
            executor.execute {
                val diskBitmap = diskBitmapFile(url)
                    ?.takeIf(File::exists)
                    ?.let { file ->
                        runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                    }

                if (diskBitmap != null) {
                    diskBitmapFile(url)?.setLastModified(System.currentTimeMillis())
                    completeLoad(url, Result.success(diskBitmap))
                    return@execute
                }

                val bitmap = runCatching {
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8_000
                        readTimeout = 10_000
                        instanceFollowRedirects = true
                        setRequestProperty("Accept", "image/*")
                    }
                    try {
                        if (connection.responseCode !in 200..299) return@runCatching null
                        connection.inputStream.use { BitmapFactory.decodeStream(it) }
                    } finally {
                        connection.disconnect()
                    }
                }.getOrNull()

                if (bitmap == null) {
                    completeLoad(url, Result.failure(java.io.IOException("Unable to load station logo")))
                    return@execute
                }

                diskBitmapFile(url)?.let { file ->
                    runCatching { writeBitmapToDisk(file, bitmap) }
                }
                completeLoad(url, Result.success(bitmap))
            }
        } catch (_: RejectedExecutionException) {
            completeLoad(url, Result.failure(java.io.IOException("Image loader is closed")))
        }
    }

    private fun completeLoad(url: String, result: Result<Bitmap>) {
        val waiting = requestCoalescer.complete(url, result) { bitmap ->
            cache.put(url, bitmap)
        }
        if (waiting.isEmpty()) return

        result.fold(
            onSuccess = { bitmap ->
                waiting.forEach { request ->
                    mainHandler.post { request.onSuccess(bitmap) }
                }
            },
            onFailure = {
                waiting.forEach { request ->
                    request.onFailure?.let { onFailure ->
                        mainHandler.post { onFailure() }
                    }
                }
            }
        )
    }

    private fun diskBitmapFile(url: String): File? =
        diskCacheDir?.let { File(it, cacheFileName(url)) }

    private fun cacheFileName(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) } + ".png"
    }

    @Synchronized
    private fun writeBitmapToDisk(target: File, bitmap: Bitmap) {
        AtomicFileWriter.writeAtomically(target) { temporary ->
            temporary.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw java.io.IOException("Unable to encode station image")
                }
            }
        }
        target.setLastModified(System.currentTimeMillis())
        trimDiskCache()
    }

    @Synchronized
    private fun trimDiskCache() {
        val files = diskCacheDir?.listFiles().orEmpty()
        var total = files.sumOf { it.length() }
        if (total <= DISK_CACHE_LIMIT_BYTES) return

        files.sortedBy { it.lastModified() }
            .forEach { file ->
                if (total <= DISK_CACHE_LIMIT_BYTES) return@forEach
                val length = file.length()
                if (file.delete()) total -= length
            }
    }

    fun close() {
        executor.shutdownNow()
        cache.evictAll()
    }
}
