package com.mellefresh13.radio

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * Shared station-to-media-item mapping used by the car/mobile controllers and both
 * playback services. Keep metadata fields here in sync with the playback contract.
 */
internal object StationMediaItemFactory {
    fun create(station: Station, stream: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(PlaybackMetadataPolicy.title(station.name, station.songTitle))
            .setArtist(station.artist?.takeIf { it.isNotBlank() && !it.equals(station.name, true) })
            .setAlbumTitle(station.name)
            .setStation(station.name)
            .setGenre(station.genre)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .build()

        return MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(stream)
            .setTag(station.id)
            .setMediaMetadata(metadata)
            .build()
    }
}
