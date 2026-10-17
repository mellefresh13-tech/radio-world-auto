package com.mellefresh13.radio

import android.content.Intent
import android.os.Bundle
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
    private var cachedCatalogStamp: Long = -1L
    private lateinit var playbackStateStore: PlaybackStateStore

    private val metadataListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val nextId = mediaItem?.mediaId
            if (!nextId.isNullOrBlank() && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                val transition = PlaybackTransitionPolicy.onMediaItemTransition(
                    currentStationId = currentStationId,
                    previousStationId = previousStationId,
                    returningToPrevious = returningToPrevious,
                    nextStationId = nextId
                )
                currentStationId = transition.currentStationId
                previousStationId = transition.previousStationId
                returningToPrevious = transition.returningToPrevious
                persistLastStation(nextId, mediaItem)
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

        playbackStateStore = PlaybackStateStore(this)
        restoreLastStationIntoPlayer()

        val closeButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.ic_close)
            .setDisplayName("Close Radio")
            .setSessionCommand(closeCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        mediaSession = MediaSession.Builder(this, CatalogNavigationPlayer(player!!, ::navigateCatalog))
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
            })
            .build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    private fun persistLastStation(stationId: String, mediaItem: MediaItem?) {
        val store = UserStateStore(this)
        val cacheStore = CatalogCacheStore(this)
        if (cachedCatalog.isEmpty()) cachedCatalog = cacheStore.load()?.stations.orEmpty()
        val currentStream = mediaItem?.localConfiguration?.uri?.toString()
        val cachedStation = playbackStateStore.loadLastStationSnapshot()?.takeIf { it.id == stationId }
            ?: cachedCatalog.firstOrNull { it.id == stationId }
        val station = cachedStation?.let { cached ->
            if (currentStream.isNullOrBlank()) cached
            else cached.copy(streams = listOf(currentStream) + cached.streams.filterNot { it == currentStream })
        } ?: run {
            val stream = currentStream ?: return
            val metadata = mediaItem?.mediaMetadata ?: return
            val name = metadata.station?.toString()?.takeIf { it.isNotBlank() }
                ?: metadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
                ?: metadata.title?.toString()?.takeIf { it.isNotBlank() }
                ?: stationId
            Station(
                id = stationId,
                name = name,
                country = "",
                countryCode = "",
                city = "",
                genre = metadata.genre?.toString().orEmpty(),
                language = "",
                streams = listOf(stream),
                logo = metadata.artworkUri?.toString()
            )
        }
        playbackStateStore.saveLastStation(station)
        store.saveRecentIds(RecentStationsPolicy.add(store.loadRecentIds(), stationId))
    }

    private fun restoreLastStationIntoPlayer() {
        val stationId = playbackStateStore.loadLastStationId()
            ?: UserStateStore(this).loadRecentIds().firstOrNull()
            ?: return
        val cache = CatalogCacheStore(this).load()
        cachedCatalog = cache?.stations.orEmpty()
        val station = cachedCatalog.firstOrNull { it.id == stationId }
            ?: playbackStateStore.loadLastStationSnapshot()?.takeIf { it.id == stationId }
            ?: return
        val stream = station.streams.firstOrNull() ?: return
        playbackStateStore.saveLastStation(station)
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
        // The activity re-syncs/saves the catalog cache while this service is alive,
        // so reload whenever the cache file changed (fresh store = no stale memory snapshot).
        val stamp = CatalogCacheStore(this).fileStamp()
        if (cachedCatalog.isEmpty() || stamp != cachedCatalogStamp) {
            cachedCatalog = CatalogCacheStore(this).load()?.stations.orEmpty()
            cachedCatalogStamp = stamp
        }
        return cachedCatalog
    }

    private fun navigateCatalog(delta: Int) {
        PlaybackAdjacentStationPolicy.resolve(
            catalog = loadCatalogStations(),
            currentStationId = player?.currentMediaItem?.mediaId ?: currentStationId,
            delta = delta
        )?.let(::playStation)
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

    private fun stationToMediaItem(station: Station, stream: String): MediaItem =
        StationMediaItemFactory.create(station, stream)

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