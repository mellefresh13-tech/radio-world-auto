from pathlib import Path

TARGET = Path("android/app/src/main/java/com/mellefresh13/radio/MainActivity.kt")


def replace_once(source: str, old: str, new: str) -> str:
    count = source.count(old)
    if count == 0:
        return source
    if count != 1:
        raise RuntimeError(f"Expected one match, found {count}: {old[:80]!r}")
    return source.replace(old, new, 1)


s = TARGET.read_text(encoding="utf-8")

s = replace_once(
    s,
    'private var playerStatusView: TextView? = null\n    private var playPauseIcon',
    'private var playerStatusView: TextView? = null\n    private var playerOffline = false\n    private var playerReconnecting = false\n    private var playPauseIcon',
)

s = replace_once(
    s,
    'override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) bufferingSinceMs = null; updatePlayerButton() }',
    '''override fun onIsPlayingChanged(isPlaying: Boolean) {\n            if (isPlaying) { bufferingSinceMs = null; playerOffline = false; playerReconnecting = false }\n            updatePlayerButton()\n        }''',
)

s = replace_once(
    s,
    'Player.STATE_READY, Player.STATE_ENDED, Player.STATE_IDLE -> bufferingSinceMs = null\n            }\n            updatePlayerButton()',
    '''Player.STATE_READY -> { bufferingSinceMs = null; playerOffline = false; if (controller?.isPlaying != true) playerReconnecting = false }\n                Player.STATE_ENDED, Player.STATE_IDLE -> bufferingSinceMs = null\n            }\n            updatePlayerButton()''',
)

s = replace_once(
    s,
    '''val title = mediaMetadata.title?.toString()?.trim().orEmpty()\n            val artist = mediaMetadata.artist?.toString()?.trim().orEmpty()\n            if (title.isNotBlank() && title != station.name) { playerTrackView?.text = title; updateMarquee(playerTrackView) }\n            if (artist.isNotBlank() && artist != station.name) { playerArtistView?.text = artist; updateMarquee(playerArtistView) }''',
    '''val title = mediaMetadata.title?.toString()?.trim().orEmpty()\n            var artist = mediaMetadata.artist?.toString()?.trim().orEmpty()\n            var track = title\n            if (artist.isBlank() && title.contains(" - ")) {\n                val parts = title.split(" - ", limit = 2)\n                artist = parts[0].trim()\n                track = parts[1].trim()\n            }\n            if (track.isNotBlank() && track != station.name) {\n                updateNowPlayingTitle(if (artist.isNotBlank()) "$artist - $track" else track)\n            } else if (artist.isNotBlank() && artist != station.name) {\n                updateNowPlayingArtist(artist)\n            }''',
)

s = replace_once(
    s,
    '''else if (streamRetryCount < 2) {\n                streamRetryCount++\n                showPlayerState("RECONNECTING...", "Retry " + streamRetryCount + "/2")''',
    '''else if (streamRetryCount < 2) {\n                streamRetryCount++\n                playerReconnecting = true\n                playerOffline = false\n                showPlayerState("RECONNECTING...", "Retry " + streamRetryCount + "/2")''',
)

s = replace_once(
    s,
    '} else showPlayerState("STREAM UNAVAILABLE", "No working stream")',
    '} else { playerOffline = true; playerReconnecting = false; showPlayerState("STREAM UNAVAILABLE", "No working stream") }',
)

s = replace_once(
    s,
    '''val track = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(13), dp(16), dp(13)); setBackgroundResource(R.drawable.bg_surface) }; val trackTitle = marqueeTextView(station.songTitle?.takeIf { it.isNotBlank() } ?: "Live broadcast", 21f, R.color.auto_text_main, true); val trackArtist = marqueeTextView(station.artist?.takeIf { it.isNotBlank() } ?: "Waiting for track metadata", 12f, R.color.auto_text_muted); playerTrackView = trackTitle; playerArtistView = trackArtist; track.addView(trackTitle, LinearLayout.LayoutParams(-1, dp(32))); track.addView(trackArtist, LinearLayout.LayoutParams(-1, dp(22))); info.addView(track, LinearLayout.LayoutParams(-1, dp(72)))\n        val status = TextView(this).apply { textSize = 10f; includeFontPadding = false; setPadding(0, dp(10), 0, 0) }; playerStatusView = status; info.addView(status, LinearLayout.LayoutParams(-1, dp(30)));''',
    '''val track = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(13), dp(16), dp(13)); setBackgroundResource(R.drawable.bg_surface) }; val trackTitle = marqueeTextView(station.songTitle?.takeIf { it.isNotBlank() } ?: "Live broadcast", 21f, R.color.auto_text_main, true); val trackStatus = marqueeTextView("", 12f, R.color.auto_text_muted); playerTrackView = trackTitle; playerArtistView = null; playerStatusView = trackStatus; track.addView(trackTitle, LinearLayout.LayoutParams(-1, dp(32))); track.addView(trackStatus, LinearLayout.LayoutParams(-1, dp(22))); info.addView(track, LinearLayout.LayoutParams(-1, dp(72)))''',
)

s = replace_once(
    s,
    '''private fun updateNowPlayingTitle(rawTitle: String) { val station = currentStation ?: return; val parts = rawTitle.split(" - ", limit = 2); val updated = if (parts.size == 2) station.copy(songTitle = parts[1].trim(), artist = parts[0].trim()) else station.copy(songTitle = rawTitle, artist = station.artist ?: station.name); currentStation = updated; restoredStationId = updated.id; catalog = catalog.map { if (it.id == updated.id) updated else it }.toMutableList(); playerTrackView?.text = updated.songTitle?.takeIf { it.isNotBlank() } ?: "Live broadcast"; playerArtistView?.text = updated.artist?.takeIf { it.isNotBlank() } ?: "Waiting for track metadata"; updateMarquee(playerTrackView); updateMarquee(playerArtistView) }''',
    '''private fun updateNowPlayingTitle(rawTitle: String) { val station = currentStation ?: return; val parts = rawTitle.split(" - ", limit = 2); val updated = if (parts.size == 2) station.copy(songTitle = parts[1].trim(), artist = parts[0].trim()) else station.copy(songTitle = rawTitle); currentStation = updated; restoredStationId = updated.id; catalog = catalog.map { if (it.id == updated.id) updated else it }.toMutableList(); playerTrackView?.text = updated.songTitle?.takeIf { it.isNotBlank() } ?: "Live broadcast"; updateMarquee(playerTrackView); updateCurrentMediaMetadata(updated.artist, updated.songTitle) }''',
)

s = replace_once(
    s,
    '''private fun updateNowPlayingArtist(artist: String) { val station = currentStation ?: return; val updated = station.copy(artist = artist); currentStation = updated; catalog = catalog.map { if (it.id == updated.id) updated else it }.toMutableList(); playerArtistView?.text = artist; updateMarquee(playerArtistView) }''',
    '''private fun updateNowPlayingArtist(artist: String) { val station = currentStation ?: return; val updated = station.copy(artist = artist); currentStation = updated; catalog = catalog.map { if (it.id == updated.id) updated else it }.toMutableList(); updateCurrentMediaMetadata(updated.artist, updated.songTitle) }\n    private fun updateCurrentMediaMetadata(artist: String?, title: String?) { val player = controller ?: return; val item = player.currentMediaItem ?: return; if (item.mediaId != currentStation?.id) return; val current = item.mediaMetadata; if (current.artist?.toString() == artist && current.title?.toString() == title) return; val metadata = current.buildUpon().setArtist(artist).setTitle(title).build(); player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon().setMediaMetadata(metadata).build()) }''',
)

s = replace_once(
    s,
    '''private fun updatePlayerButton() { if (!::binding.isInitialized) return; val playing = controller?.isPlaying == true; playPauseIcon?.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play); playPauseLabel?.text = if (playing) "PAUSE" else "PLAY"; playerStatusView?.apply { text = when { controller?.playbackState == Player.STATE_BUFFERING -> "●  CONNECTING"; playing -> "●  PLAYING"; else -> "○  READY" }; setTextColor(getColor(if (playing) R.color.auto_success else R.color.auto_text_muted)) } }''',
    '''private fun updatePlayerButton() { if (!::binding.isInitialized) return; val player = controller; val playing = player?.isPlaying == true; playPauseIcon?.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play); playPauseLabel?.text = if (playing) "PAUSE" else "PLAY"; val status = when { playerOffline -> "●  OFFLINE"; playerReconnecting -> "●  RECONNECTING"; player?.playbackState == Player.STATE_BUFFERING -> "●  BUFFERING"; player?.playbackState == Player.STATE_IDLE -> "●  CONNECTING"; playing -> "●  PLAYING"; else -> "●  PAUSED" }; val color = when { playerOffline -> R.color.auto_danger; playerReconnecting || player?.playbackState == Player.STATE_BUFFERING || player?.playbackState == Player.STATE_IDLE -> R.color.auto_warning; playing -> R.color.auto_success; else -> R.color.auto_text_muted }; playerStatusView?.apply { text = status; setTextColor(getColor(color)); updateMarquee(this) } }''',
)

TARGET.write_text(s, encoding="utf-8")
print("Player metadata/status UI patch applied.")
