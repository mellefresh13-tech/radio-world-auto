package com.mellefresh13.radio

object PlaybackMetadataPolicy {
    fun title(stationName: String, trackTitle: String?): String =
        trackTitle?.takeIf { it.isNotBlank() } ?: stationName
}
