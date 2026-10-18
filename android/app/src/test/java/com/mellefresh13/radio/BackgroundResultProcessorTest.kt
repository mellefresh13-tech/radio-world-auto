package com.mellefresh13.radio

import java.util.ArrayDeque
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundResultProcessorTest {

    private class QueuedExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()

        val pendingCount: Int
            get() = tasks.size

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runNext() {
            tasks.removeFirst().run()
        }
    }

    @Test
    fun transformsOnBackgroundExecutorBeforeDeliveringCallback() {
        val background = QueuedExecutor()
        val callbackExecutor = QueuedExecutor()
        val events = mutableListOf<String>()
        val processor = BackgroundResultProcessor<Int, Int>(
            backgroundExecutor = background,
            callbackExecutor = callbackExecutor
        ) { value ->
            events += "transform"
            value * 2
        }

        processor.submit(21) { result ->
            events += "callback"
            assertEquals(42, result.getOrThrow())
        }

        assertTrue(events.isEmpty())
        assertEquals(1, background.pendingCount)
        assertEquals(0, callbackExecutor.pendingCount)

        background.runNext()
        assertEquals(listOf("transform"), events)
        assertEquals(1, callbackExecutor.pendingCount)

        callbackExecutor.runNext()
        assertEquals(listOf("transform", "callback"), events)
    }

    @Test
    fun transformFailureIsDeliveredThroughCallbackExecutor() {
        val background = QueuedExecutor()
        val callbackExecutor = QueuedExecutor()
        val failure = IllegalStateException("mapping failed")
        var delivered: Result<Int>? = null
        val processor = BackgroundResultProcessor<Int, Int>(
            backgroundExecutor = background,
            callbackExecutor = callbackExecutor
        ) {
            throw failure
        }

        processor.submit(1) { result -> delivered = result }

        background.runNext()
        assertEquals(null, delivered)
        assertEquals(1, callbackExecutor.pendingCount)

        callbackExecutor.runNext()
        assertEquals(failure, delivered?.exceptionOrNull())
    }

    @Test
    fun failedBestEffortCacheWriteDoesNotNeedToFailInMemoryProcessing() {
        val background = QueuedExecutor()
        val callbackExecutor = QueuedExecutor()
        var delivered: Result<String>? = null
        val saveCache: () -> Boolean = { false }
        val processor = BackgroundResultProcessor<Int, String>(
            backgroundExecutor = background,
            callbackExecutor = callbackExecutor
        ) {
            // A cache write is best-effort; the fresh in-memory result is still usable.
            assertEquals(false, saveCache())
            "fresh catalog"
        }

        processor.submit(1) { result -> delivered = result }
        background.runNext()
        callbackExecutor.runNext()

        assertEquals("fresh catalog", delivered?.getOrNull())
    }
}
