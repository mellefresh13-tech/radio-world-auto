package com.mellefresh13.radio

/**
 * Coalesces cold-start callers while one local-cache read is in progress.
 *
 * Repository entry points and this gate are owned by the main thread. Once the
 * first cache read finishes, an empty result must not trigger another disk read:
 * callers should retry the network sync directly instead.
 */
internal class CatalogLoadRequestGate<T> {

    data class PendingRequest<T>(
        val allowSync: Boolean,
        val callback: (Result<List<T>>) -> Unit
    )

    enum class Action {
        START_LOAD,
        JOIN_LOAD,
        SKIP_CACHE_LOAD
    }

    private val pending = mutableListOf<PendingRequest<T>>()
    private var loadInProgress = false
    private var loadCompleted = false

    fun request(
        allowSync: Boolean,
        callback: (Result<List<T>>) -> Unit
    ): Action {
        if (loadInProgress) {
            pending += PendingRequest(allowSync, callback)
            return Action.JOIN_LOAD
        }

        if (loadCompleted) {
            return Action.SKIP_CACHE_LOAD
        }

        pending += PendingRequest(allowSync, callback)
        loadInProgress = true
        return Action.START_LOAD
    }

    /** Mark the one-time cache read complete and return waiting callers in order. */
    fun completeLoad(): List<PendingRequest<T>> {
        loadInProgress = false
        loadCompleted = true
        val waiting = pending.toList()
        pending.clear()
        return waiting
    }

    fun clear() {
        pending.clear()
        loadInProgress = false
    }
}
