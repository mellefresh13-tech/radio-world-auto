package com.mellefresh13.radio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
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
            ImagePayloadPolicy.memoryCacheSizeKb(value.byteCount)
    }

    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

    @Volatile
    private var diskCacheDir: File? = null

    fun initialize(context: Context) {
        diskCacheDir = File(context.cacheDir, "station-logo-cache").apply { mkdirs() }
    }

    fun load(url: String, onFailure: (() -> Unit)? = null, callback: (Bitmap) -> Unit) {
        if (!StationLogoUrlPolicy.isSyntacticallyAllowed(url)) {
            onFailure?.let { failure -> mainHandler.post { failure() } }
            return
        }

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
                try {
                    val diskBitmap = diskBitmapFile(url)
                        ?.takeIf(File::exists)
                        ?.let(::decodeCachedBitmap)

                    if (diskBitmap != null) {
                        // Cache recency is best-effort; a metadata error must not discard a decoded image.
                        runCatching { diskBitmapFile(url)?.setLastModified(System.currentTimeMillis()) }
                        completeLoad(url, Result.success(diskBitmap))
                        return@execute
                    }

                    if (!StationLogoUrlPolicy.isAllowedForNetwork(url)) {
                        completeLoad(url, Result.failure(IOException("Unsafe station logo URL")))
                        return@execute
                    }

                    val bitmap = runCatching { downloadRemoteBitmap(url) }.getOrNull()

                    if (bitmap == null) {
                        completeLoad(url, Result.failure(java.io.IOException("Unable to load station logo")))
                        return@execute
                    }

                    diskBitmapFile(url)?.let { file ->
                        runCatching { writeBitmapToDisk(file, bitmap) }
                    }
                    completeLoad(url, Result.success(bitmap))
                } catch (error: Exception) {
                    // Never strand coalesced callers if cache/file work fails outside HTTP parsing.
                    completeLoad(url, Result.failure(error))
                }
            }
        } catch (_: RejectedExecutionException) {
            completeLoad(url, Result.failure(java.io.IOException("Image loader is closed")))
        }
    }


    private fun downloadRemoteBitmap(startingUrl: String): Bitmap? {
        var currentUrl = startingUrl

        for (redirectCount in 0..StationLogoUrlPolicy.MAX_REDIRECTS) {
            if (!StationLogoUrlPolicy.isAllowedForNetwork(currentUrl)) {
                throw IOException("Unsafe station logo URL or redirect target")
            }

            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "image/*")
            }
            try {
                val responseCode = connection.responseCode
                if (responseCode in REDIRECT_CODES) {
                    if (redirectCount == StationLogoUrlPolicy.MAX_REDIRECTS) {
                        throw IOException("Too many station logo redirects")
                    }
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Station logo redirect has no Location header")
                    currentUrl = StationLogoUrlPolicy.resolveRedirectUrl(currentUrl, location)
                        ?: throw IOException("Unsafe station logo redirect")
                    continue
                }

                if (responseCode !in 200..299) return null
                if (!ImagePayloadPolicy.isContentLengthAllowed(connection.contentLengthLong)) {
                    throw IOException("Station logo exceeds the 8 MiB download limit")
                }
                val bytes = connection.inputStream.use { input -> readBoundedBytes(input) }
                return decodeBitmap(bytes)
            } finally {
                connection.disconnect()
            }
        }

        throw IOException("Too many station logo redirects")
    }

    private fun readBoundedBytes(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var bytesRead = 0L

        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (!ImagePayloadPolicy.canAppendChunk(bytesRead, count)) {
                throw IOException("Station logo exceeds the 8 MiB download limit")
            }
            output.write(buffer, 0, count)
            bytesRead += count.toLong()
        }

        return output.toByteArray()
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = ImagePayloadPolicy.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun decodeCachedBitmap(file: File): Bitmap? {
        if (!file.isFile) return null
        if (file.length() > ImagePayloadPolicy.MAX_DOWNLOAD_BYTES) {
            runCatching { file.delete() }
            return null
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            runCatching { file.delete() }
            return null
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = ImagePayloadPolicy.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeFile(file.absolutePath, options).also { bitmap ->
            if (bitmap == null) runCatching { file.delete() }
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
        requestCoalescer.clear()
        cache.evictAll()
    }
}
