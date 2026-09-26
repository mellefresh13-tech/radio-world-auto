package com.mellefresh13.radio

import android.content.Intent
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

    private val metadataListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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
        mediaSession = MediaSession.Builder(this, player!!).build()
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
        val position = exoPlayer.currentPosition
        val wasPlaying = exoPlayer.isPlaying
        exoPlayer.replaceMediaItem(index, updated)
        exoPlayer.seekTo(index, position)
        if (wasPlaying) exoPlayer.play()
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
