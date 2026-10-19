package com.mellefresh13.radio

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * Runs potentially expensive result processing off the caller thread and
 * delivers the result through the supplied callback executor.
 *
 * Keeping both executors injectable makes processing order and callback
 * delivery testable without Android instrumentation.
 */
internal class BackgroundResultProcessor<I, O>(
    private val backgroundExecutor: Executor,
    private val callbackExecutor: Executor,
    private val transform: (I) -> O
) {
    fun submit(input: I, callback: (Result<O>) -> Unit) {
        try {
            backgroundExecutor.execute {
                val result = runCatching { transform(input) }
                try {
                    callbackExecutor.execute { callback(result) }
                } catch (_: RejectedExecutionException) {
                    // The callback owner may already be shutting down. There is
                    // no live executor on which a completion can safely be delivered.
                }
            }
        } catch (error: RejectedExecutionException) {
            // A late request after repository shutdown must not escape onto the
            // caller (often the main thread) as an uncaught exception.
            try {
                callbackExecutor.execute { callback(Result.failure(error)) }
            } catch (_: RejectedExecutionException) {
                // Both owners are shutting down; dropping the callback is safe.
            }
        }
    }
}
