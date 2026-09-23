package com.mellefresh13.radio

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class RadioApiClient(
    private val baseUrl: String = BuildConfig.API_BASE_URL
) {
    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadStations(
        query: String? = null,
        country: String? = null,
        genre: String? = null,
        limit: Int = 50,
        callback: (Result<List<ApiStation>>) -> Unit
    ) {
        executor.execute {
            runCatching {
                val url = StringBuilder(baseUrl.trimEnd('/') + "/stations")
                val params = mutableListOf<String>()

                query?.takeIf { it.isNotBlank() }?.let {
                    params += "q=" + java.net.URLEncoder.encode(it, "UTF-8")
                }
                country?.takeIf { it.isNotBlank() }?.let {
                    params += "country=" + java.net.URLEncoder.encode(it, "UTF-8")
                }
                genre?.takeIf { it.isNotBlank() }?.let {
                    params += "genre=" + java.net.URLEncoder.encode(it, "UTF-8")
                }

                if (params.isNotEmpty()) {
                    url.append("?").append(params.joinToString("&"))
                }

                getJson(url.toString()).getJSONArrayFromRoot().map(::parseStation)
            }.also { result ->
                mainHandler.post { callback(result) }
            }
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun getJson(urlString: String): JSONObject {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty(
                "Accept",
                "application/json"
            )
        }

        try {
            if (connection.responseCode !in 200..299) {
                error("HTTP " + connection.responseCode)
            }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.getJSONArrayFromRoot(): List<JSONObject> {
        val array = getJSONArray("stations")
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(array.getJSONObject(index))
            }
        }
    }

    private fun parseStation(json: JSONObject): ApiStation {
        val streamArray = json.optJSONArray("streams") ?: JSONArray()
        val streams = buildList(streamArray.length()) {
            for (index in 0 until streamArray.length()) {
                val stream = streamArray.getJSONObject(index)
                add(
                    ApiStream(
                        url = stream.getString("url"),
                        codec = stream.optString("codec").takeIf { it.isNotBlank() },
                        bitrateKbps = if (stream.isNull("bitrate_kbps")) null
                        else stream.optInt("bitrate_kbps"),
                        isHls = stream.optBoolean("is_hls"),
                        status = stream.optString("status")
                    )
                )
            }
        }

        return ApiStation(
            id = json.getString("id"),
            name = json.getString("name"),
            country = json.getString("country"),
            city = json.optString("city").takeIf { it.isNotBlank() },
            languages = json.optJSONArray("languages").toStringList(),
            genres = json.optJSONArray("genres").toStringList(),
            homepage = json.optString("homepage").takeIf { it.isNotBlank() },
            logo = json.optString("logo").takeIf { it.isNotBlank() },
            streams = streams
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                add(optString(index))
            }
        }
    }
}
