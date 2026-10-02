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

class CarRadioPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var metadataTitle: String? = null
    private var metadataArtist: String? = null
    private var currentStationId: String? = null
    private var previousStationId: String? = null
    private var returningToPrevious = false
    private var cachedCatalog: List<Station> = emptyList()
    private lateinit var playbackStateStore: PlaybackStateStore

    private val metadataListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val nextId = mediaItem?.mediaId
            if (!nextId.isNullOrBlank()) {
                val transition = PlaybackTransitionPolicy.onMediaItemTransition(
                    currentStationId = currentStationId,
                    previousStationId = previousStationId,
                    returningToPrevious = returningToPrevious,
                    nextStationId = nextId
                )
                currentStationId = transition.currentStationId
                previousStationId = transition.previousStationId
                returningToPrevious = transition.returningToPrevious
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
        playbackStateStore = PlaybackStateStore(this)
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

                        val delta = if (event.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) 1 else -1
                        PlaybackAdjacentStationPolicy.resolve(
                            catalog = loadCatalogStations(),
                            currentStationId = exoPlayer.currentMediaItem?.mediaId,
                            delta = delta
                        )?.let(::playStation)
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
        playbackStateStore.saveLastStationId(stationId)
        store.saveRecentIds(RecentStationsPolicy.add(store.loadRecentIds(), stationId))
    }

    private fun restoreLastStationIntoPlayer() {
        val stationId = playbackStateStore.loadLastStationId()
            ?: UserStateStore(this).loadRecentIds().firstOrNull()
            ?: return
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
            .setTitle(PlaybackMetadataPolicy.title(station.name, station.songTitle))
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
}