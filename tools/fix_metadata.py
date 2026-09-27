from pathlib import Path
import re

p = Path('android/app/src/main/java/com/mellefresh13/radio/MainActivity.kt')
s = p.read_text()

# This script is also invoked before Gradle's existing UI patch. In that phase
# there is no updateCurrentMediaMetadata() yet, so leave the source untouched.
# The Gradle finalization task invokes it again after the UI patch.
if 'private fun updateCurrentMediaMetadata' not in s:
    raise SystemExit(0)

# Preserve the already parsed artist when the Player screen is rebuilt after tab navigation.
# Metadata is stored in Station, so the initial Player render must combine both fields too.
old_player_title = 'val trackTitle = marqueeTextView(station.songTitle?.takeIf { it.isNotBlank() } ?: "Live broadcast", 21f, R.color.auto_text_main, true)'
new_player_title = '''val storedArtist = station.artist?.trim().takeIf { !it.isNullOrBlank() }
        val storedTitle = station.songTitle?.trim().takeIf { !it.isNullOrBlank() }
        val displayTrack = when {
            storedArtist != null && storedTitle != null -> "$storedArtist — $storedTitle"
            storedTitle != null -> storedTitle
            storedArtist != null -> storedArtist
            else -> "Live broadcast"
        }
        val trackTitle = marqueeTextView(displayTrack, 21f, R.color.auto_text_main, true)'''
if s.count(old_player_title) != 1:
    raise SystemExit(f'Player title render target expected once, found {s.count(old_player_title)}')
s = s.replace(old_player_title, new_player_title, 1)

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
        val combined = when {
            normalizedArtist != null && normalizedTitle != null -> "$normalizedArtist — $normalizedTitle"
            normalizedTitle != null -> normalizedTitle
            else -> normalizedArtist
        }
        playerTrackView?.text = combined ?: "Live broadcast"
        updateMarquee(playerTrackView)
        updateCurrentMediaMetadata(normalizedArtist, normalizedTitle)
    }

'''
    s = s[:pos] + helper + s[pos:]

s = s.replace('artist = station.artist ?: station.name', 'artist = null')
s = s.replace('?: "Waiting for track metadata"', '?: ""')
p.write_text(s)
