from pathlib import Path
import re

p = Path("android/app/src/main/java/com/mellefresh13/radio/MainActivity.kt")
s = p.read_text()

listener = re.compile(r"        override fun onMetadata\(metadata: Metadata\) \{.*?        override fun onPlayerError", re.S)
listener_replacement = '''        override fun onMetadata(metadata: Metadata) {
            var artist: String? = null
            var title: String? = null
            for (index in 0 until metadata.length()) {
                when (val entry = metadata[index]) {
                    is IcyInfo -> entry.title?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        val parsed = parseNowPlaying(it)
                        artist = parsed.first
                        title = parsed.second
                    }
                    is TextInformationFrame -> {
                        val value = entry.values.firstOrNull()?.trim().orEmpty()
                        if (value.isNotEmpty()) {
                            when (entry.id) {
                                "TIT2" -> title = value
                                "TPE1" -> artist = value
                            }
                        }
                    }
                }
            }
            updateNowPlayingMetadata(artist, title)
        }
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val station = currentStation ?: return
            val title = mediaMetadata.title?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() && it != station.name }
            val artist = mediaMetadata.artist?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() && it != station.name }
            if (title != null || artist != null) updateNowPlayingMetadata(artist, title)
        }
        override fun onPlayerError'''
s, n = listener.subn(listener_replacement, s, count=1)
if n != 1:
    raise SystemExit("metadata listener block not found")

functions = re.compile(r"    private fun updateNowPlayingArtist\(artist: String\) \{.*?    private fun togglePlayPause", re.S)
functions_replacement = '''    private fun parseNowPlaying(raw: String): Pair<String?, String?> {
        val value = raw.trim()
        if (value.isBlank()) return null to null
        val separator = listOf(" - ", " – ", " — ").firstOrNull { value.contains(it) }
        if (separator == null) return null to value
        val parts = value.split(separator, limit = 2)
        return parts.getOrNull(0)?.trim().takeIf { !it.isNullOrBlank() } to
            parts.getOrNull(1)?.trim().takeIf { !it.isNullOrBlank() }
    }

    private fun updateNowPlayingMetadata(artist: String?, title: String?) {
        val station = currentStation ?: return
        val normalizedArtist = artist?.trim().takeIf { !it.isNullOrBlank() && !it.equals(station.name, true) }
        val normalizedTitle = title?.trim().takeIf { !it.isNullOrBlank() && !it.equals(station.name, true) }
        if (normalizedArtist == null && normalizedTitle == null) return
        val updated = station.copy(songTitle = normalizedTitle, artist = normalizedArtist)
        currentStation = updated
        restoredStationId = updated.id
        catalog = catalog.map { if (it.id == updated.id) updated else it }.toMutableList()
        playerTrackView?.text = normalizedTitle ?: "Live broadcast"
        playerArtistView?.text = normalizedArtist ?: ""
        updateMarquee(playerTrackView)
        updateMarquee(playerArtistView)

        val player = controller ?: return
        val index = player.currentMediaItemIndex
        if (index >= 0 && index < player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            val metadata = item.mediaMetadata.buildUpon()
                .setTitle(normalizedTitle)
                .setArtist(normalizedArtist)
                .build()
            player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(metadata).build())
        }
    }

    private fun togglePlayPause'''
s, n = functions.subn(functions_replacement, s, count=1)
if n != 1:
    raise SystemExit("metadata functions not found")

old = 'station.artist?.takeIf { it.isNotBlank() } ?: "Waiting for track metadata"'
if old not in s:
    raise SystemExit("old artist placeholder not found")
s = s.replace(old, 'station.artist?.takeIf { it.isNotBlank() } ?: ""', 1)
p.write_text(s)
