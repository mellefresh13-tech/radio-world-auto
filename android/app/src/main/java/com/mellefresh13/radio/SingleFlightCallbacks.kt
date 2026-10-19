package com.mellefresh13.radio

/**
 * Coalesces callbacks for an operation that is already in progress.
 *
 * Call this coordinator only from the operation's owning thread (the catalog
 * repository uses the main thread). The first enqueue returns true and owns
 * starting the operation; later callers join the same result until complete().
 */
internal class SingleFlightCallbacks<T> {
    private val callbacks = mutableListOf<(T) -> Unit>()
    private var inProgress = false

    fun enqueue(callback: (T) -> Unit): Boolean {
        callbacks += callback
        if (inProgress) return false
        inProgress = true
        return true
    }

    fun complete(result: T) {
        if (!inProgress) return

        val waiting = callbacks.toList()
        callbacks.clear()
        inProgress = false
        waiting.forEach { it(result) }
    }

    fun clear() {
        callbacks.clear()
        inProgress = false
    }
}
