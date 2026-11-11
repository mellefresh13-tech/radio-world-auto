package com.mellefresh13.radio

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Records listening segments from the playback service, so tracking continues
 * while the UI Activity is stopped or destroyed.
 */
internal class PlaybackListeningTracker(
    context: Context,
    private val player: Player,
    private val profile: UiProfile,
    private val stationResolver: (String) -> Station?
) : Player.Listener {

    private val appContext = context.applicationContext
    private var startedAtMs: Long? = null
    private var activeStation: Station? = null
    private var activeStationId: String? = null

    init {
        player.addListener(this)
        startIfPlaying()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) startIfPlaying() else finishSegment()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        finishSegment()
        if (player.isPlaying) startIfPlaying()
    }

    private fun startIfPlaying() {
        if (!player.isPlaying || startedAtMs != null) return
        val stationId = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
        activeStationId = stationId
        activeStation = stationResolver(stationId)
        startedAtMs = SystemClock.elapsedRealtime()
    }

    private fun finishSegment() {
        val started = startedAtMs ?: return
        val stationId = activeStationId
        val station = activeStation
        startedAtMs = null
        activeStationId = null
        activeStation = null

        if (stationId != null) {
            AppAnalytics.stationListen(
                appContext,
                stationId,
                SystemClock.elapsedRealtime() - started,
                station?.name,
                station?.genre,
                station?.country
            )
        }
    }

    fun release() {
        finishSegment()
        player.removeListener(this)
    }
}
