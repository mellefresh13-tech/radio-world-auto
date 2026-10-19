package com.mellefresh13.radio

/**
 * Coalesces concurrent work for the same key while allowing different keys to
 * proceed independently. Registration, cached-value lookup, and completion are
 * atomic with respect to one another.
 */
internal class KeyedRequestCoalescer<K, V> {

    data class Callbacks<V>(
        val onSuccess: (V) -> Unit,
        val onFailure: (() -> Unit)?
    )

    sealed class Registration<out V> {
        data class Cached<V>(val value: V) : Registration<V>()
        object Joined : Registration<Nothing>()
        object Started : Registration<Nothing>()
    }

    private val lock = Any()
    private val waiting = mutableMapOf<K, MutableList<Callbacks<V>>>()

    fun register(
        key: K,
        callbacks: Callbacks<V>,
        cachedValue: () -> V?
    ): Registration<V> = synchronized(lock) {
        cachedValue()?.let { return@synchronized Registration.Cached(it) }

        val existing = waiting[key]
        if (existing != null) {
            existing += callbacks
            Registration.Joined
        } else {
            waiting[key] = mutableListOf(callbacks)
            Registration.Started
        }
    }

    /**
     * Update the cache under the same lock used by register(), then release all
     * waiters. This closes the race where a caller misses the cache just before
     * a request finishes and accidentally starts a second download.
     */
    fun complete(
        key: K,
        result: Result<V>,
        cacheSuccess: (V) -> Unit
    ): List<Callbacks<V>> = synchronized(lock) {
        result.onSuccess(cacheSuccess)
        waiting.remove(key).orEmpty()
    }

    fun clear() {
        synchronized(lock) {
            waiting.clear()
        }
    }
}
