package com.mellefresh13.radio

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogCacheSnapshotTest {

    @Test
    fun concurrentFirstAccessPublishesOneCompleteStationIndex() {
        val stations = (0 until 2_000).map { index ->
            Station(
                id = "station-$index",
                name = "Station $index",
                country = "Germany",
                countryCode = "DE",
                city = "Berlin",
                genre = "Rock",
                language = "German",
                streams = listOf("https://stream.example/$index")
            )
        }
        val snapshot = CatalogCacheStore.Snapshot(
            savedAt = 1L,
            catalogVersion = "test",
            stations = stations,
            countries = emptyList(),
            genres = emptyList()
        )

        val workerCount = 8
        val ready = CountDownLatch(workerCount)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(workerCount)

        try {
            val futures = (0 until workerCount).map {
                executor.submit<Map<String, Station>> {
                    ready.countDown()
                    assertTrue("workers should start together", start.await(5, TimeUnit.SECONDS))
                    snapshot.stationsById
                }
            }

            assertTrue("all workers should be ready", ready.await(5, TimeUnit.SECONDS))
            start.countDown()

            val indexes = futures.map { it.get(10, TimeUnit.SECONDS) }
            val firstIndex = indexes.first()

            assertEquals(stations.size, firstIndex.size)
            stations.forEach { station ->
                assertEquals(station, firstIndex[station.id])
            }
            indexes.drop(1).forEach { index ->
                assertSame("all readers should see the safely published lazy value", firstIndex, index)
            }
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }
}
