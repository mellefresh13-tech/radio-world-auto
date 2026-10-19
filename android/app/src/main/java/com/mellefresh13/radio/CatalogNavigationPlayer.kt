package com.mellefresh13.radio

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * Wraps the real player so that NEXT/PREVIOUS (steering wheel, head unit, notification,
 * AVRCP) are always available and always resolved against the full station catalog,
 * regardless of what is in the ExoPlayer queue (which may contain just one item).
 */
class CatalogNavigationPlayer(
    player: Player,
    private val navigate: (delta: Int) -> Unit
) : ForwardingPlayer(player) {

    private val navCommands = intArrayOf(
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
    )

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(*navCommands).build()

    override fun isCommandAvailable(command: Int): Boolean =
        command in navCommands || super.isCommandAvailable(command)

    override fun hasNextMediaItem(): Boolean = true
    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() = navigate(1)
    override fun seekToNextMediaItem() = navigate(1)
    override fun seekToPrevious() = navigate(-1)
    override fun seekToPreviousMediaItem() = navigate(-1)
}
