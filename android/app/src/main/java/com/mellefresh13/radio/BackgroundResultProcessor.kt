package com.mellefresh13.radio

import java.util.concurrent.Executor

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
        backgroundExecutor.execute {
            val result = runCatching { transform(input) }
            callbackExecutor.execute { callback(result) }
        }
    }
}
