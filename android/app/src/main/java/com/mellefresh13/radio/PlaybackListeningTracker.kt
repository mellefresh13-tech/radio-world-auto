package com.mellefresh13.radio

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Records actual playing time from the playback service.
 *
 * Buffering and temporary playback suppression pause the active clock but do not
 * split a listening session. A session is finalized only on user pause, station
 * transition, player stop/end, or service cleanup.
 */
internal class PlaybackListeningTracker(
    context: Context,
    private val player: Player,
    private val profile: UiProfile,
    private val stationResolver: (String) -> Station?
) : Player.Listener {

    private val appContext = context.applicationContext
    private var sessionStation: Station? = null
    private var sessionStationId: String? = null
    private var accumulatedPlayingMs = 0L
    private var activeIntervalStartedAtMs: Long? = null
    private var lastPlayedEventStationId: String? = null

    init {
        player.addListener(this)
        syncPlaybackState()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        syncPlaybackState()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        syncPlaybackState()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) {
            finishSession()
        } else {
            syncPlaybackState()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        finishSession()
        val nextStationId = mediaItem?.mediaId?.takeIf { it.isNotBlank() }
        if (nextStationId != lastPlayedEventStationId) {
            lastPlayedEventStationId = null
        }
        syncPlaybackState()
    }

    private fun syncPlaybackState() {
        if (player.isPlaying) {
            startOrResumeSession()
            return
        }

        pauseActiveInterval()

        // IDLE/ENDED means playback was stopped, not merely buffering.
        if (player.playbackState == Player.STATE_IDLE ||
            player.playbackState == Player.STATE_ENDED
        ) {
            finishSession()
        }
    }

    private fun startOrResumeSession() {
        val stationId = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
        if (sessionStationId == null) {
            sessionStationId = stationId
            sessionStation = stationResolver(stationId)
            accumulatedPlayingMs = 0L
        } else if (sessionStationId != stationId) {
            finishSession()
            sessionStationId = stationId
            sessionStation = stationResolver(stationId)
            accumulatedPlayingMs = 0L
        }

        if (lastPlayedEventStationId != stationId) {
            val station = sessionStation
            AppAnalytics.stationPlayed(
                appContext,
                stationId,
                profile,
                station?.name,
                station?.genre,
                station?.country
            )
            lastPlayedEventStationId = stationId
        }

        if (activeIntervalStartedAtMs == null) {
            activeIntervalStartedAtMs = SystemClock.elapsedRealtime()
        }
    }

    private fun pauseActiveInterval() {
        val startedAt = activeIntervalStartedAtMs ?: return
        accumulatedPlayingMs += (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        activeIntervalStartedAtMs = null
    }

    private fun finishSession() {
        pauseActiveInterval()
        val stationId = sessionStationId
        val station = sessionStation
        val durationMs = accumulatedPlayingMs

        sessionStationId = null
        sessionStation = null
        accumulatedPlayingMs = 0L

        if (stationId != null && durationMs >= MIN_TRACKED_SESSION_MS) {
            AppAnalytics.stationListen(
                appContext,
                stationId,
                durationMs,
                profile,
                station?.name,
                station?.genre,
                station?.country
            )
        }
    }

    fun release() {
        finishSession()
        player.removeListener(this)
    }

    private companion object {
        const val MIN_TRACKED_SESSION_MS = 5_000L
    }
}
