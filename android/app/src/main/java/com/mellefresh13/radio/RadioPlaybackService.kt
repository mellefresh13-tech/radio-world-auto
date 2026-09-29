package com.mellefresh13.radio

import android.content.Intent
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.util.UnstableApi

class RadioPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var metadataTitle: String? = null
    private var metadataArtist: String? = null
    private var currentStationId: String? = null
    private var previousStationId: String? = null
    private var returningToPrevious = false
    private var cachedCatalog: List<Station> = emptyList()

    private val metadataListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val nextId = mediaItem?.mediaId
            if (!nextId.isNullOrBlank()) {
                if (returningToPrevious) {
                    returningToPrevious = false
                } else if (!currentStationId.isNullOrBlank() && currentStationId != nextId) {
                    previousStationId = currentStationId
                }
                currentStationId = nextId
            }
            metadataTitle = null
            metadataArtist = null
            publishFallbackStationMetadata(mediaItem)
        }

        override fun onMetadata(metadata: Metadata) {
            for (index in 0 until metadata.length()) {
                when (val entry = metadata[index]) {
                    is IcyInfo -> entry.title?.trim()?.takeIf { it.isNotEmpty() }?.let { applyCombinedMetadata(it) }
                    is TextInformationFrame -> {
                        val value = entry.values.firstOrNull()?.trim().orEmpty()
                        when (entry.id) {
                            "TIT2" -> if (value.isNotEmpty()) metadataTitle = value
                            "TPE1" -> if (value.isNotEmpty()) metadataArtist = value
                        }
                        if (entry.id == "TIT2" || entry.id == "TPE1") publishTrackMetadata()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also {
                it.volume = 1f
                it.addListener(metadataListener)
            }
        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(object : MediaSession.Callback {
                @OptIn(UnstableApi::class)
                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (event == null) return false

                    val exoPlayer = session.player
                    if (event.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || event.keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                        if (event.action != KeyEvent.ACTION_DOWN) return true

                        if (event.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                            val currentId = exoPlayer.currentMediaItem?.mediaId
                            val station = loadCatalogStations()
                                .filter { it.id != currentId && it.streams.isNotEmpty() }
                                .randomOrNull()
                            if (station != null) {
                                previousStationId = currentId
                                returningToPrevious = false
                                playStation(station)
                            } else {
                                val candidates = (0 until exoPlayer.mediaItemCount).filter { index ->
                                    exoPlayer.getMediaItemAt(index).mediaId != currentId
                                }
                                val nextIndex = candidates.randomOrNull()
                                if (nextIndex != null) {
                                    previousStationId = currentId
                                    returningToPrevious = false
                                    exoPlayer.seekTo(nextIndex, 0L)
                                    exoPlayer.play()
                                }
                            }
                            return true
                        }

                        val previousId = previousStationId ?: return true
                        previousStationId = null
                        val station = loadCatalogStations().firstOrNull { it.id == previousId }
                        if (station != null) {
                            returningToPrevious = true
                            playStation(station)
                        } else {
                            val previousIndex = (0 until exoPlayer.mediaItemCount).firstOrNull { index ->
                                exoPlayer.getMediaItemAt(index).mediaId == previousId
                            }
                            if (previousIndex != null) {
                                returningToPrevious = true
                                exoPlayer.seekTo(previousIndex, 0L)
                                exoPlayer.play()
                            }
                        }
                        return true
                    }

                    return false
                }
            })
            .build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    private fun applyCombinedMetadata(raw: String) {
        val parts = raw.split(" - ", " – ", " — ", limit = 2)
        if (parts.size == 2) {
            metadataArtist = parts[0].trim().takeIf { it.isNotEmpty() }
            metadataTitle = parts[1].trim().takeIf { it.isNotEmpty() }
        } else {
            metadataArtist = null
            metadataTitle = raw.trim().takeIf { it.isNotEmpty() }
        }
        publishTrackMetadata()
    }

    private fun publishFallbackStationMetadata(mediaItem: MediaItem?) {
        val item = mediaItem ?: return
        val stationName = item.mediaMetadata.station?.toString()?.trim()
            ?: item.mediaMetadata.albumTitle?.toString()?.trim()
            ?: return
        updateCurrentMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(stationName)
                .setStation(stationName)
                .setAlbumTitle(stationName)
                .setGenre(item.mediaMetadata.genre)
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .build()
        )
    }

    private fun publishTrackMetadata() {
        val item = player?.currentMediaItem ?: return
        val stationName = item.mediaMetadata.station?.toString()?.trim()
            ?: item.mediaMetadata.albumTitle?.toString()?.trim()
            ?: return
        val title = metadataTitle?.takeIf { it.isNotBlank() } ?: stationName
        val builder = MediaMetadata.Builder()
            .setTitle(title)
            .setStation(stationName)
            .setAlbumTitle(stationName)
            .setGenre(item.mediaMetadata.genre)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
        metadataArtist?.takeIf { it.isNotBlank() }?.let { builder.setArtist(it) }
        updateCurrentMediaMetadata(builder.build())
    }

    private fun updateCurrentMediaMetadata(metadata: MediaMetadata) {
        val exoPlayer = player ?: return
        val current = exoPlayer.currentMediaItem ?: return
        val updated = current.buildUpon().setMediaMetadata(metadata).build()
        val index = exoPlayer.currentMediaItemIndex
        if (index < 0) return
        exoPlayer.replaceMediaItem(index, updated)
    }

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        player?.removeListener(metadataListener)
        mediaSession?.release()
        player?.release()
        mediaSession = null
        player = null
        super.onDestroy()
    }
}