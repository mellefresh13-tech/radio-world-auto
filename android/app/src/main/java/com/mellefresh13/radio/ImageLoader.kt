package com.mellefresh13.radio

import android.app.Activity
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

object ImageLoader {
    private const val MEMORY_CACHE_KB = 16 * 1024
    private const val DISK_CACHE_LIMIT_BYTES = 64L * 1024 * 1024

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(MEMORY_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    @Volatile
    private var diskCacheDir: File? = null

    fun initialize(context: Context) {
        diskCacheDir = File(context.cacheDir, "station-logo-cache").apply { mkdirs() }
        if (context is Activity) ResponsiveUiPatch.install(context)
    }

    fun load(url: String, callback: (Bitmap) -> Unit) {
        cache.get(url)?.let {
            callback(it)
            return
        }
        executor.execute {
            val diskBitmap = diskCacheDir
                ?.let { File(it, cacheFileName(url)) }
                ?.takeIf(File::exists)
                ?.let { file -> runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() }
            if (diskBitmap != null) {
                cache.put(url, diskBitmap)
                diskBitmapFile(url)?.setLastModified(System.currentTimeMillis())
                mainHandler.post { callback(diskBitmap) }
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
            if (bitmap != null) {
                cache.put(url, bitmap)
                diskBitmapFile(url)?.let { file ->
                    runCatching {
                        file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
                        file.setLastModified(System.currentTimeMillis())
                        trimDiskCache()
                    }
                }
                mainHandler.post { callback(bitmap) }
            }
        }
    }

    private fun diskBitmapFile(url: String): File? = diskCacheDir?.let { File(it, cacheFileName(url)) }

    private fun cacheFileName(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) } + ".png"
    }

    private fun trimDiskCache() {
        val files = diskCacheDir?.listFiles().orEmpty()
        var total = files.sumOf { it.length() }
        if (total <= DISK_CACHE_LIMIT_BYTES) return
        files.sortedBy { it.lastModified() }.forEach { file ->
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
