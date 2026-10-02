package com.mellefresh13.radio

data class ParsedTrackMetadata(
    val artist: String?,
    val title: String?
)

object TrackMetadataParser {

    fun parse(raw: String): ParsedTrackMetadata {
        val value = raw.trim()
        if (value.isBlank()) return ParsedTrackMetadata(null, null)

        val parts = value.split(" - ", " – ", " — ", limit = 2)
        return if (parts.size == 2) {
            ParsedTrackMetadata(
                artist = parts[0].trim().takeIf { it.isNotEmpty() },
                title = parts[1].trim().takeIf { it.isNotEmpty() }
            )
        } else {
            ParsedTrackMetadata(
                artist = null,
                title = value
            )
        }
    }
}
