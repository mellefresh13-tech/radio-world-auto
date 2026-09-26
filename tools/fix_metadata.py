from pathlib import Path
import re

p = Path('android/app/src/main/java/com/mellefresh13/radio/MainActivity.kt')
s = p.read_text()

listener = re.compile(r'        override fun onMetadata\(metadata: Metadata\) \{.*?        override fun onPlayerError', re.S)
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
if listener.search(s):
    s, n = listener.subn(listener_replacement, s, count=1)
    if n != 1:
        raise SystemExit('metadata listener patch failed')

if 'private fun parseNowPlaying' not in s:
    marker = '    private fun togglePlayPause'
    pos = s.find(marker)
    if pos < 0:
        raise SystemExit('toggle marker not found')
    helper = '''    private fun parseNowPlaying(raw: String): Pair<String?, String?> {
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
        updateMarquee(playerTrackView)
        updateCurrentMediaMetadata(normalizedArtist, normalizedTitle)
    }

'''
    s = s[:pos] + helper + s[pos:]

s = s.replace('artist = station.artist ?: station.name', 'artist = null')
s = s.replace('?: "Waiting for track metadata"', '?: ""')

if 'private fun updateCurrentMediaMetadata' not in s:
    marker = '    private fun updateNowPlayingArtist'
    pos = s.find(marker)
    if pos < 0:
        raise SystemExit('artist function marker not found')
    helper2 = '''    private fun updateCurrentMediaMetadata(artist: String?, title: String?) {
        val player = controller ?: return
        val item = player.currentMediaItem ?: return
        if (item.mediaId != currentStation?.id) return
        val index = player.currentMediaItemIndex
        if (index < 0) return
        val metadata = item.mediaMetadata.buildUpon().setArtist(artist).setTitle(title).build()
        player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(metadata).build())
    }

'''
    s = s[:pos] + helper2 + s[pos:]

p.write_text(s)
