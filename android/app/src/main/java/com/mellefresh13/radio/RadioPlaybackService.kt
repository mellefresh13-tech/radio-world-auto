package com.mellefresh13.radio

import android.content.Intent
import android.os.Bundle
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
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.common.util.UnstableApi

class RadioPlaybackService : MediaSessionService() {

    private val closeCommand = SessionCommand(ACTION_CLOSE_APP, Bundle.EMPTY)

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var metadataTitle: String? = null
    private var metadataArtist: String? = null
    private var currentStationId: String? = null
    private var previousStationId: String? = null
    private var returningToPrevious = false
    private var cachedCatalog: List<Station> = emptyList()
    private val failedStreamsByStation = mutableMapOf<String, MutableSet<String>>()

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
                failedStreamsByStation.remove(nextId)
                persistLastStation(nextId)
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

        restoreLastStationIntoPlayer()

        val closeButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.ic_close)
            .setDisplayName("Close Radio")
            .setSessionCommand(closeCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        mediaSession = MediaSession.Builder(this, player!!)
            .setMediaButtonPreferences(ImmutableList.of(closeButton))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(
                            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                                .add(closeCommand)
                                .build()
                        )
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == ACTION_CLOSE_APP) {
                        session.player.stop()
                        session.player.clearMediaItems()
                        stopSelf()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }

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

    private fun persistLastStation(stationId: String) {
        val store = UserStateStore(this)
        val recent = store.loadRecentIds().toMutableList()
        recent.remove(stationId)
        recent.add(0, stationId)
        if (recent.size > 10) recent.subList(10, recent.size).clear()
        store.saveRecentIds(recent)
    }

    private fun restoreLastStationIntoPlayer() {
        val stationId = UserStateStore(this).loadRecentIds().firstOrNull() ?: return
        val cache = CatalogCacheStore(this).load() ?: return
        cachedCatalog = cache.stations
        val station = cache.stations.firstOrNull { it.id == stationId } ?: return
        val stream = station.streams.firstOrNull() ?: return
        player?.setMediaItem(stationToMediaItem(station, stream))
        currentStationId = station.id
    }

    private fun applyCombinedMetadata(raw: String) {
        val parsed = TrackMetadataParser.parse(raw)
        metadataArtist = parsed.artist
        metadataTitle = parsed.title
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

    private fun loadCatalogStations(): List<Station> {
        if (cachedCatalog.isEmpty()) {
            cachedCatalog = CatalogCacheStore(this).load()?.stations.orEmpty()
        }
        return cachedCatalog
    }

    private fun playStation(station: Station) {
        val exoPlayer = player ?: return
        val stream = station.streams.firstOrNull() ?: return
        val index = (0 until exoPlayer.mediaItemCount)
            .firstOrNull { exoPlayer.getMediaItemAt(it).mediaId == station.id }

        if (index != null) {
            exoPlayer.replaceMediaItem(index, stationToMediaItem(station, stream))
            exoPlayer.seekTo(index, 0L)
        } else {
            exoPlayer.addMediaItem(stationToMediaItem(station, stream))
            exoPlayer.seekTo(exoPlayer.mediaItemCount - 1, 0L)
        }
        exoPlayer.prepare()
        exoPlayer.play()
    }

    private fun stationToMediaItem(station: Station, stream: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(station.songTitle?.takeIf { it.isNotBlank() } ?: station.name)
            .setArtist(station.artist?.takeIf { it.isNotBlank() && !it.equals(station.name, true) })
            .setAlbumTitle(station.name)
            .setStation(station.name)
            .setGenre(station.genre)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .build()

        return MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(stream)
            .setTag(station.id)
            .setMediaMetadata(metadata)
            .build()
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
    
    companion object {
        private const val ACTION_CLOSE_APP = "com.mellefresh13.radio.CLOSE_APP"
    }

}