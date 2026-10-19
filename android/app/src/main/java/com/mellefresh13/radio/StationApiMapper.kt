package com.mellefresh13.radio

/**
 * Converts the app's filtered cached station back into the API client's model
 * without losing locally persisted track metadata during a same-version sync.
 */
internal object StationApiMapper {
    fun fromStation(station: Station): ApiStation = ApiStation(
        id = station.id,
        name = station.name,
        country = station.countryCode,
        city = station.city.takeIf { it.isNotBlank() },
        languages = station.language.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty(),
        genres = station.genre.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty(),
        homepage = station.website,
        logo = station.logo,
        songTitle = station.songTitle,
        artist = station.artist,
        streams = station.streams.map { url ->
            ApiStream(
                url = url,
                codec = null,
                bitrateKbps = null,
                isHls = false,
                status = ""
            )
        }
    )
}
