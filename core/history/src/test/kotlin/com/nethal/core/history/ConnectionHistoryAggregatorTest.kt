package com.nethal.core.history

import com.nethal.core.scheduling.MeasurementSample
import com.nethal.core.scheduling.MeasurementSampleRepository
import com.nethal.core.scheduling.MeasurementSourceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryMeasurementSampleRepository(
    private val samples: List<MeasurementSample>,
) : MeasurementSampleRepository {
    override suspend fun insert(sample: MeasurementSample) = error("não usado neste teste")

    override suspend fun recent(source: MeasurementSourceType, limit: Int): List<MeasurementSample> =
        samples.filter { it.source == source }.sortedByDescending { it.timestampEpochMs }.take(limit)

    override suspend fun deleteOlderThan(source: MeasurementSourceType, beforeEpochMs: Long) =
        error("não usado neste teste")
}

private const val DAY_MS = 24 * 60 * 60_000L
private const val WINDOW_MS = 30 * 60_000L

class ConnectionHistoryAggregatorTest {

    @Test
    fun `sem amostras todos os blocos ficam SEM_DADO`() = runTest {
        val now = 10 * DAY_MS
        val aggregator = ConnectionHistoryAggregator(InMemoryMeasurementSampleRepository(emptyList()), nowMillis = { now })

        val blocks = aggregator.aggregate()

        assertEquals(336, blocks.size)
        assertTrue(blocks.all { it.quality == ConnectionQuality.SEM_DADO })
    }

    @Test
    fun `bloco com latencia baixa classifica OK`() = runTest {
        val now = 10 * DAY_MS
        val sampleTime = now - WINDOW_MS / 2 // cai dentro do último bloco
        val repository = InMemoryMeasurementSampleRepository(
            listOf(MeasurementSample(source = MeasurementSourceType.SPEEDTEST, timestampEpochMs = sampleTime, success = true, latencyMs = 20.0)),
        )
        val aggregator = ConnectionHistoryAggregator(repository, nowMillis = { now })

        val blocks = aggregator.aggregate()

        assertEquals(ConnectionQuality.OK, blocks.last().quality)
        assertEquals(20.0, blocks.last().avgLatencyMs)
        assertEquals(1, blocks.last().sampleCount)
    }

    @Test
    fun `bloco com latencia alta classifica LENTO`() = runTest {
        val now = 10 * DAY_MS
        val sampleTime = now - WINDOW_MS / 2
        val repository = InMemoryMeasurementSampleRepository(
            listOf(MeasurementSample(source = MeasurementSourceType.SPEEDTEST, timestampEpochMs = sampleTime, success = true, latencyMs = 500.0)),
        )
        val aggregator = ConnectionHistoryAggregator(repository, nowMillis = { now })

        val blocks = aggregator.aggregate()

        assertEquals(ConnectionQuality.LENTO, blocks.last().quality)
    }

    @Test
    fun `bloco com falha de amostra classifica OFFLINE`() = runTest {
        val now = 10 * DAY_MS
        val sampleTime = now - WINDOW_MS / 2
        val repository = InMemoryMeasurementSampleRepository(
            listOf(
                MeasurementSample(
                    source = MeasurementSourceType.SPEEDTEST,
                    timestampEpochMs = sampleTime,
                    success = false,
                    errorMessage = "timeout",
                ),
            ),
        )
        val aggregator = ConnectionHistoryAggregator(repository, nowMillis = { now })

        val blocks = aggregator.aggregate()

        assertEquals(ConnectionQuality.OFFLINE, blocks.last().quality)
        assertNull(blocks.last().avgLatencyMs)
    }

    @Test
    fun `amostra fora da janela de 7 dias nao entra em nenhum bloco`() = runTest {
        val now = 10 * DAY_MS
        val sampleTime = now - 8 * DAY_MS
        val repository = InMemoryMeasurementSampleRepository(
            listOf(MeasurementSample(source = MeasurementSourceType.SPEEDTEST, timestampEpochMs = sampleTime, success = true, latencyMs = 20.0)),
        )
        val aggregator = ConnectionHistoryAggregator(repository, nowMillis = { now })

        val blocks = aggregator.aggregate()

        assertTrue(blocks.all { it.quality == ConnectionQuality.SEM_DADO })
    }

    @Test
    fun `amostra de outra fonte nao contamina o bloco`() = runTest {
        val now = 10 * DAY_MS
        val sampleTime = now - WINDOW_MS / 2
        val repository = InMemoryMeasurementSampleRepository(
            listOf(MeasurementSample(source = MeasurementSourceType.LATENCY, timestampEpochMs = sampleTime, success = true, latencyMs = 20.0)),
        )
        val aggregator = ConnectionHistoryAggregator(repository, nowMillis = { now })

        val blocks = aggregator.aggregate(source = MeasurementSourceType.SPEEDTEST)

        assertTrue(blocks.all { it.quality == ConnectionQuality.SEM_DADO })
    }
}
