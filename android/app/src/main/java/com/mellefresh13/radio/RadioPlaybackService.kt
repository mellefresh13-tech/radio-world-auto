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
            // The MediaItem already contains the station metadata. Do not
            // replace the currently playing item here: replacing it forces
            // ExoPlayer to re-prepare the stream and causes an audible seam.
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
                    }
                }
            }
            // Metadata is intentionally observed rather than written back to
            // the current MediaItem. Media3 can receive ICY/ID3 metadata while
            // the same stream keeps playing. Replacing the MediaItem here was
            // the source of the buffer/restart seam on track changes.
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
