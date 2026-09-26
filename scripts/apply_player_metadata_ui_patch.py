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

s = replace_once(s, 'private var playerStatusView: TextView? = null\n    private var playPauseIcon', 'private var playerStatusView: TextView? = null\n    private var playerOffline = false\n    private var playerReconnecting = false\n    private var startupPlaybackRestored = false\n    private val failedStationIds = mutableSetOf<String>()\n    private var playPauseIcon')

s = replace_once(s, 'override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) bufferingSinceMs = null; updatePlayerButton() }', '''override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                bufferingSinceMs = null
                playerOffline = false
                playerReconnecting = false
                currentStation?.let { failedStationIds.remove(it.id) }
            }
            updatePlayerButton()
        }''')

s = replace_once(s, 'Player.STATE_READY, Player.STATE_ENDED, Player.STATE_IDLE -> bufferingSinceMs = null\n            }\n            updatePlayerButton()', '''Player.STATE_READY -> { bufferingSinceMs = null; playerOffline = false; if (controller?.isPlaying != true) playerReconnecting = false }
                Player.STATE_ENDED, Player.STATE_IDLE -> bufferingSinceMs = null
            }
            updatePlayerButton()''')

s = replace_once(s, 'if (player.playbackState == Player.STATE_BUFFERING && currentStation?.id == station.id && currentStreamIndex + 1 < station.streams.size) switchToNextStream("BUFFER TIMEOUT")', 'if (player.playbackState == Player.STATE_BUFFERING && currentStation?.id == station.id) { if (currentStreamIndex + 1 < station.streams.size) switchToNextStream("BUFFER TIMEOUT") else switchToNextStation("BUFFER TIMEOUT") }')

s = replace_once(s, '''val title = mediaMetadata.title?.toString()?.trim().orEmpty()
            val artist = mediaMetadata.artist?.toString()?.trim().orEmpty()
            if (title.isNotBlank() && title != station.name) { playerTrackView?.text = title; updateMarquee(playerTrackView) }
            if (artist.isNotBlank() && artist != station.name) { playerArtistView?.text = artist; updateMarquee(playerArtistView) }''', '''val title = mediaMetadata.title?.toString()?.trim().orEmpty()
            var artist = mediaMetadata.artist?.toString()?.trim().orEmpty()
            var track = title
            if (artist.isBlank() && title.contains(" - ")) {
                val parts = title.split(" - ", limit = 2)
                artist = parts[0].trim()
                track = parts[1].trim()
            }
            if (track.isNotBlank() && track != station.name) {
                updateNowPlayingTitle(if (artist.isNotBlank()) "$artist - $track" else track)
            } else if (artist.isNotBlank() && artist != station.name) {
                updateNowPlayingArtist(artist)
            }''')

s = replace_once(s, '''else if (streamRetryCount < 2) {
                streamRetryCount++
                showPlayerState("RECONNECTING...", "Retry " + streamRetryCount + "/2")''', '''else if (streamRetryCount < 2) {
                streamRetryCount++
                playerReconnecting = true
                playerOffline = false
                showPlayerState("RECONNECTING...", "Retry " + streamRetryCount + "/2")''')

s = replace_once(s, '} else showPlayerState("STREAM UNAVAILABLE", "No working stream")', '} else { playerOffline = true; playerReconnecting = false; switchToNextStation("STREAM UNAVAILABLE") }')

s = replace_once(s, '''val track = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(13), dp(16), dp(13)); setBackgroundResource(R.drawable.bg_surface) }; val trackTitle = marqueeTextView(station.songTitle?.takeIf { it.isNotBlank() } ?: "Live broadcast", 21f, R.color.auto_text_main, true); val trackArtist = marqueeTextView(station.artist?.takeIf { it.isNotBlank() } ?: "Waiting for track metadata", 12f, R.color.auto_text_muted); playerTrackView = trackTitle; playerArtistView = trackArtist; track.addView(trackTitle, LinearLayout.LayoutParams(-1, dp(32))); track.addView(trackArtist, LinearLayout.LayoutParams(-1, dp(22))); info.addView(track, LinearLayout.LayoutParams(-1, dp(72)))
        val status = TextView(this).apply { textSize = 10f; includeFontPadding = false; setPadding(0, dp(10), 0, 0) }; playerStatusView = status; info.addView(status, LinearLayout.LayoutParams(-1, dp(30))); hero.addView(info, LinearLayout.LayoutParams(0, -1, 1f)); root.addView(hero, LinearLayout.LayoutParams(-1, 0, 1f))''', '''val track = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(13), dp(16), dp(13)); setBackgroundResource(R.drawable.bg_surface) }; val trackTitle = marqueeTextView(station.songTitle?.takeIf { it.isNotBlank() } ?: "Live broadcast", 21f, R.color.auto_text_main, true); val trackStatus = marqueeTextView("", 12f, R.color.auto_text_muted); playerTrackView = trackTitle; playerArtistView = null; playerStatusView = trackStatus; track.addView(trackTitle, LinearLayout.LayoutParams(-1, dp(32))); track.addView(trackStatus, LinearLayout.LayoutParams(-1, dp(22))); info.addView(track, LinearLayout.LayoutParams(-1, dp(72))); hero.addView(info, LinearLayout.LayoutParams(0, -1, 1f)); root.addView(hero, LinearLayout.LayoutParams(-1, 0, 1f))''')

s = replace_once(s, 'val wantedId = restoredStationId ?: controller?.currentMediaItem?.mediaId', 'val wantedId = restoredStationId ?: controller?.currentMediaItem?.mediaId ?: userStateStore.loadRecentIds().firstOrNull()')

s = replace_once(s, 'if (currentStation != null) restoredStationId = currentStation?.id\n    }', '''if (currentStation != null) restoredStationId = currentStation?.id
    }

    private fun restorePlaybackIfNeeded() {
        if (restoringAfterConfig || startupPlaybackRestored) return
        val player = controller ?: return
        val station = currentStation ?: catalog.firstOrNull() ?: return
        if (player.isPlaying || player.playbackState == Player.STATE_READY) { startupPlaybackRestored = true; return }
        ensureStationInPlaylist(station)
        val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == station.id } ?: return
        currentStreamIndex = 0
        streamRetryCount = 0
        failedStationIds.clear()
        showPlayerState("CONNECTING...", "Restoring last station")
        player.seekTo(index, 0L)
        player.prepare()
        player.play()
        startupPlaybackRestored = true
    }''')

s = replace_once(s, 'restoreStationFromState(); syncPlayerPlaylist()\n                    }', 'restoreStationFromState(); syncPlayerPlaylist(); restorePlaybackIfNeeded()\n                    }')
s = replace_once(s, 'catalog = stations.toMutableList(); applyPersistedState(); restoreStationFromState(); syncPlayerPlaylist(); saveCatalogCacheAsync(); renderPlayer()', 'catalog = stations.toMutableList(); applyPersistedState(); restoreStationFromState(); syncPlayerPlaylist(); restorePlaybackIfNeeded(); saveCatalogCacheAsync(); renderPlayer()')
s = replace_once(s, 'resolveCurrentStationFromPlayer()\n            syncPlayerPlaylist()', 'resolveCurrentStationFromPlayer()\n            syncPlayerPlaylist()\n            restoreStationFromState()\n            restorePlaybackIfNeeded()')

s = replace_once(s, 'private fun playStation(station: Station) { currentStation = station; restoredStationId = station.id; currentStreamIndex = 0; streamRetryCount = 0; bufferingSinceMs = null; retryHandler.removeCallbacksAndMessages(null);', 'private fun playStation(station: Station) { startupPlaybackRestored = true; failedStationIds.clear(); currentStation = station; restoredStationId = station.id; currentStreamIndex = 0; streamRetryCount = 0; bufferingSinceMs = null; playerOffline = false; playerReconnecting = false; retryHandler.removeCallbacksAndMessages(null);')

s = replace_once(s, 'private fun updateNowPlayingArtist(artist: String) { val station = currentStation ?: return; val updated = station.copy(artist = artist); currentStation = updated; catalog = catalog.map { if (it.id == updated.id) updated else it }.toMutableList(); playerArtistView?.text = artist; updateMarquee(playerArtistView) }', 'private fun updateNowPlayingArtist(artist: String) { val station = currentStation ?: return; val updated = station.copy(artist = artist); currentStation = updated; catalog = catalog.map { if (it.id == updated.id) updated else it }.toMutableList(); updateCurrentMediaMetadata(updated.artist, updated.songTitle) }\n    private fun updateCurrentMediaMetadata(artist: String?, title: String?) { val player = controller ?: return; val item = player.currentMediaItem ?: return; if (item.mediaId != currentStation?.id) return; val normalizedTitle = title?.trim().takeIf { !it.isNullOrBlank() } ?: currentStation?.name; val normalizedArtist = artist?.trim().takeIf { !it.isNullOrBlank() && !it.equals(currentStation?.name, true) }; val current = item.mediaMetadata; if (current.artist?.toString() == normalizedArtist && current.title?.toString() == normalizedTitle) return; val metadata = current.buildUpon().setArtist(normalizedArtist).setTitle(normalizedTitle).build(); player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon().setMediaMetadata(metadata).build()) }')

s = replace_once(s, 'private fun stationToMediaItem(station: Station, streamIndex: Int): MediaItem { val stream = station.streams.getOrNull(streamIndex) ?: station.streams.first(); return MediaItem.Builder().setMediaId(station.id).setUri(stream).setTag(station.id).setMediaMetadata(MediaMetadata.Builder().setAlbumTitle(station.name).setStation(station.name).setGenre(station.genre).setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION).build()).build() }', 'private fun stationToMediaItem(station: Station, streamIndex: Int): MediaItem { val stream = station.streams.getOrNull(streamIndex) ?: station.streams.first(); val metadata = MediaMetadata.Builder().setTitle(station.songTitle?.takeIf { it.isNotBlank() } ?: station.name).setArtist(station.artist?.takeIf { it.isNotBlank() && !it.equals(station.name, true) }).setAlbumTitle(station.name).setStation(station.name).setGenre(station.genre).setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION).build(); return MediaItem.Builder().setMediaId(station.id).setUri(stream).setTag(station.id).setMediaMetadata(metadata).build() }')

s = replace_once(s, '''private fun switchToNextStream(reason: String) { val station = currentStation ?: return; val player = controller ?: return; if (currentStreamIndex + 1 >= station.streams.size) return; currentStreamIndex++; showPlayerState(reason, "Opening stream " + (currentStreamIndex + 1)); val index = player.currentMediaItemIndex; player.replaceMediaItem(index, stationToMediaItem(station, currentStreamIndex)); player.prepare(); player.play() }''', '''private fun switchToNextStream(reason: String) {
        val station = currentStation ?: return
        val player = controller ?: return
        if (currentStreamIndex + 1 >= station.streams.size) {
            switchToNextStation(reason)
            return
        }
        currentStreamIndex++
        showPlayerState(reason, "Opening stream " + (currentStreamIndex + 1))
        val index = player.currentMediaItemIndex
        player.replaceMediaItem(index, stationToMediaItem(station, currentStreamIndex))
        player.prepare()
        player.play()
    }''')

s = replace_once(s, '''private fun togglePlayPause()''', '''private fun switchToNextStation(reason: String) {
        val player = controller ?: return
        val currentIndex = player.currentMediaItemIndex
        if (player.mediaItemCount <= 1) { playerOffline = true; playerReconnecting = false; player.pause(); updatePlayerButton(); showPlayerState("STREAM UNAVAILABLE", "No working station"); return }
        val currentId = currentStation?.id
        if (currentId != null) failedStationIds.add(currentId)
        var nextIndex = -1
        for (offset in 1 until player.mediaItemCount) {
            val index = (currentIndex + offset) % player.mediaItemCount
            val id = player.getMediaItemAt(index).mediaId
            if (!failedStationIds.contains(id)) { nextIndex = index; break }
        }
        if (nextIndex < 0) { playerOffline = true; playerReconnecting = false; player.pause(); updatePlayerButton(); showPlayerState("STREAM UNAVAILABLE", "No working station"); return }
        playerReconnecting = true
        playerOffline = false
        streamRetryCount = 0
        currentStreamIndex = 0
        showPlayerState("RECONNECTING...", "Switching station")
        player.seekTo(nextIndex, 0L)
        player.prepare()
        player.play()
    }

    private fun togglePlayPause()''')

s = replace_once(s, 'private fun updatePlayerButton() { if (!::binding.isInitialized) return; val playing = controller?.isPlaying == true; playPauseIcon?.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play); playPauseLabel?.text = if (playing) "PAUSE" else "PLAY"; playerStatusView?.apply { text = when { controller?.playbackState == Player.STATE_BUFFERING -> "●  CONNECTING"; playing -> "●  PLAYING"; else -> "○  READY" }; setTextColor(getColor(if (playing) R.color.auto_success else R.color.auto_text_muted)) } }', 'private fun updatePlayerButton() { if (!::binding.isInitialized) return; val player = controller; val playing = player?.isPlaying == true; playPauseIcon?.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play); playPauseLabel?.text = if (playing) "PAUSE" else "PLAY"; val status = when { playerOffline -> "●  OFFLINE"; playerReconnecting -> "●  RECONNECTING"; player?.playbackState == Player.STATE_BUFFERING -> "●  BUFFERING"; player?.playbackState == Player.STATE_IDLE -> "●  CONNECTING"; playing -> "●  PLAYING"; else -> "●  PAUSED" }; val color = when { playerOffline -> R.color.auto_danger; playerReconnecting || player?.playbackState == Player.STATE_BUFFERING || player?.playbackState == Player.STATE_IDLE -> R.color.auto_warning; playing -> R.color.auto_success; else -> R.color.auto_text_muted }; playerStatusView?.apply { text = status; setTextColor(getColor(color)); updateMarquee(this) } }')

TARGET.write_text(s, encoding="utf-8")
print("Player metadata/status/startup/failover patch applied.")
