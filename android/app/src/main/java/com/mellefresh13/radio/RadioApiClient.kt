package com.mellefresh13.radio

import android.os.Handler
import android.os.Looper
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class RadioApiClient(
    private val context: Context,
    private val catalogUrl: String = CATALOG_URL,
    private val dbUrl: String = DB_URL,
    private val onProgress: ((Long, Long) -> Unit)? = null
) {
    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var catalogCache: List<JSONObject>? = null

    fun loadStations(
        query: String? = null,
        country: String? = null,
        genre: String? = null,
        limit: Int = 50,
        offset: Int = 0,
        callback: (Result<List<ApiStation>>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                val all = loadCatalog()
                val normalizedQuery = query?.trim()?.lowercase(Locale.ROOT).orEmpty()
                val normalizedCountry = country?.trim()?.lowercase(Locale.ROOT).orEmpty()
                val normalizedGenre = genre?.trim()?.lowercase(Locale.ROOT).orEmpty()

                all.asSequence()
                    .filter { station ->
                        normalizedCountry.isBlank() || station.optString("country").lowercase(Locale.ROOT) == normalizedCountry
                    }
                    .filter { station ->
                        normalizedGenre.isBlank() || station.optJSONArray("genres").toStringList()
                            .any { it.lowercase(Locale.ROOT) == normalizedGenre }
                    }
                    .filter { station ->
                        normalizedQuery.isBlank() || searchableText(station).contains(normalizedQuery)
                    }
                    .drop(offset)
                    .take(limit)
                    .map(::parseStation)
                    .toList()
            }
            mainHandler.post { callback(result) }
        }
    }

    fun loadCountries(callback: (Result<List<ApiCountry>>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val all = loadCatalog()
                all.groupBy { it.optString("country") }
                    .filterKeys { it.isNotBlank() }
                    .map { (code, stations) -> ApiCountry(code, stations.size) }
                    .sortedBy { it.code }
            }
            mainHandler.post { callback(result) }
        }
    }

    fun loadGenres(callback: (Result<List<ApiGenre>>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val counts = linkedMapOf<String, Int>()
                loadCatalog().forEach { station ->
                    station.optJSONArray("genres").toStringList()
                        .filter { it.isNotBlank() }
                        .forEach { genre -> counts[genre] = (counts[genre] ?: 0) + 1 }
                }
                counts.map { (name, count) -> ApiGenre(name, count) }
                    .sortedByDescending { it.stationCount }
            }
            mainHandler.post { callback(result) }
        }
    }

    fun loadStation(
        stationId: String,
        callback: (Result<ApiStation>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                val station = loadCatalog().firstOrNull { it.optString("id") == stationId }
                    ?: error("Station not found: $stationId")
                parseStation(station)
            }
            mainHandler.post { callback(result) }
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun loadCatalog(): List<JSONObject> {
        catalogCache?.let { return it }
        synchronized(this) {
            catalogCache?.let { return it }
            val loaded = runCatching { downloadJsonCatalog() }.getOrElse { downloadDbCatalog() }
            catalogCache = loaded
            return loaded
        }
    }
    private fun downloadJsonCatalog(): List<JSONObject> {
        val connection = (URL(catalogUrl).openConnection() as HttpURLConnection).apply { requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 60_000; setRequestProperty("Accept", "application/json"); setRequestProperty("User-Agent", "RadioWorldAuto/1.0") }
        try {
            if (connection.responseCode !in 200..299) error("HTTP " + connection.responseCode)
            val total = connection.contentLengthLong; var loadedBytes = 0L
            val text = connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(8192); val out = StringBuilder(); var count: Int
                while (reader.read(buffer).also { count = it } >= 0) { if (count == 0) continue; out.append(buffer, 0, count); loadedBytes += count.toLong(); onProgress?.invoke(loadedBytes, total) }
                out.toString()
            }
            val array = JSONArray(text)
            return buildList(array.length()) { for (index in 0 until array.length()) add(array.getJSONObject(index)) }
        } finally { connection.disconnect() }
    }
    private fun downloadDbCatalog(): List<JSONObject> {
        val target = File(context.cacheDir, "radio-world-catalog.db")
        val temp = File(context.cacheDir, "radio-world-catalog.db.part")
        val connection = (URL(dbUrl).openConnection() as HttpURLConnection).apply { requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 60_000; setRequestProperty("Accept", "application/octet-stream"); setRequestProperty("User-Agent", "RadioWorldAuto/1.0") }
        try {
            if (connection.responseCode !in 200..299) error("DB HTTP " + connection.responseCode)
            val total = connection.contentLengthLong; var loadedBytes = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) { val count = input.read(buffer); if (count < 0) break; if (count == 0) continue; output.write(buffer, 0, count); loadedBytes += count.toLong(); onProgress?.invoke(loadedBytes, total) }
                }
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) error("Unable to store catalog database")
        } finally { connection.disconnect() }
        val db = SQLiteDatabase.openDatabase(target.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val stationMap = linkedMapOf<String, JSONObject>()
            db.rawQuery("SELECT id, name, country, city, languages_json, genres_json, homepage, logo FROM stations WHERE status != 'duplicate' ORDER BY name COLLATE NOCASE", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val obj = JSONObject(); obj.put("id", cursor.getString(0)); obj.put("name", cursor.getString(1)); obj.put("country", cursor.getString(2)); obj.put("city", cursor.getString(3) ?: ""); obj.put("languages", JSONArray(cursor.getString(4))); obj.put("genres", JSONArray(cursor.getString(5))); obj.put("homepage", cursor.getString(6) ?: ""); obj.put("logo", cursor.getString(7) ?: ""); obj.put("streams", JSONArray()); stationMap[cursor.getString(0)] = obj
                }
            }
            db.rawQuery("SELECT station_id, url, codec, bitrate_kbps, is_hls, status FROM streams WHERE status = 'online' ORDER BY station_id, COALESCE(bitrate_kbps, 0) DESC", null).use { cursor ->
                while (cursor.moveToNext()) {
                    stationMap[cursor.getString(0)]?.optJSONArray("streams")?.put(JSONObject().apply { put("url", cursor.getString(1)); put("codec", cursor.getString(2) ?: ""); if (cursor.isNull(3)) put("bitrate_kbps", JSONObject.NULL) else put("bitrate_kbps", cursor.getInt(3)); put("is_hls", cursor.getInt(4) != 0); put("status", cursor.getString(5)) })
                }
            }
            return stationMap.values.filter { (it.optJSONArray("streams")?.length() ?: 0) > 0 }
        } finally { db.close() }
    }
    private fun searchableText(json: JSONObject): String = buildString {
        append(json.optString("name")).append(' ')
        append(json.optString("country")).append(' ')
        append(json.optString("city")).append(' ')
        append(json.optString("homepage")).append(' ')
        json.optJSONArray("languages").toStringList().forEach { append(it).append(' ') }
        json.optJSONArray("genres").toStringList().forEach { append(it).append(' ') }
        json.optJSONArray("aliases").toStringList().forEach { append(it).append(' ') }
    }.lowercase(Locale.ROOT)

    private fun parseStation(json: JSONObject): ApiStation {
        val streamArray = json.optJSONArray("streams") ?: JSONArray()
        val streams = buildList(streamArray.length()) {
            for (index in 0 until streamArray.length()) {
                val stream = streamArray.getJSONObject(index)
                add(
                    ApiStream(
                        url = stream.optString("url"),
                        codec = stream.optString("codec").takeIf { it.isNotBlank() },
                        bitrateKbps = if (stream.isNull("bitrate_kbps")) null else stream.optInt("bitrate_kbps"),
                        isHls = stream.optBoolean("is_hls"),
                        status = stream.optString("status")
                    )
                )
            }
        }.filter { it.url.isNotBlank() }

        return ApiStation(
            id = json.getString("id"),
            name = json.getString("name"),
            country = json.optString("country"),
            city = json.optString("city").takeIf { it.isNotBlank() },
            languages = json.optJSONArray("languages").toStringList(),
            genres = json.optJSONArray("genres").toStringList(),
            homepage = json.optString("homepage").takeIf { it.isNotBlank() },
            logo = json.optString("logo").takeIf { it.isNotBlank() },
            songTitle = null,
            artist = null,
            streams = streams
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    companion object {
        private const val CATALOG_URL =
            "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/stations.json"
        private const val DB_URL =
            "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/radio.db"
    }
}
