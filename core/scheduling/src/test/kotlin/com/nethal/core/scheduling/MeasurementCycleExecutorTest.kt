package com.nethal.core.scheduling

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryMeasurementSampleRepository : MeasurementSampleRepository {
    val inserted = mutableListOf<MeasurementSample>()

    override suspend fun insert(sample: MeasurementSample) {
        inserted += sample
    }

    override suspend fun recent(source: MeasurementSourceType, limit: Int): List<MeasurementSample> =
        inserted.filter { it.source == source }.sortedByDescending { it.timestampEpochMs }.take(limit)

    override suspend fun deleteOlderThan(source: MeasurementSourceType, beforeEpochMs: Long) {
        inserted.removeAll { it.source == source && it.timestampEpochMs < beforeEpochMs }
    }
}

/** Task fake cujo comportamento por chamada é controlado pelo teste (sucesso, falha explícita ou exceção). */
private class ScriptedTask(
    override val id: String = "scripted",
    override val source: MeasurementSourceType = MeasurementSourceType.SPEEDTEST,
    private val script: MutableList<() -> MeasurementOutcome>,
) : PeriodicMeasurementTask {
    var callCount = 0
        private set

    override suspend fun measure(): MeasurementOutcome {
        callCount++
        val step = script.removeFirst()
        return step()
    }
}

class MeasurementCycleExecutorTest {

    @Test
    fun `rodada com sucesso persiste amostra com os campos medidos`() = runTest {
        val repository = InMemoryMeasurementSampleRepository()
        val executor = MeasurementCycleExecutor(repository, nowMillis = { 1_000L })
        val task = ScriptedTask(
            script = mutableListOf({
                MeasurementOutcome.Success(downloadMbps = 120.5, uploadMbps = 30.2, latencyMs = 18.0, jitterMs = 2.0)
            }),
        )

        val sample = executor.run(task)

        assertTrue(sample.success)
        assertEquals(120.5, sample.downloadMbps)
        assertEquals(30.2, sample.uploadMbps)
        assertEquals(1_000L, sample.timestampEpochMs)
        assertEquals(MeasurementSourceType.SPEEDTEST, sample.source)
        assertNull(sample.errorMessage)
        assertEquals(1, repository.inserted.size)
    }

    @Test
    fun `outcome de falha explicita persiste amostra sem sucesso`() = runTest {
        val repository = InMemoryMeasurementSampleRepository()
        val executor = MeasurementCycleExecutor(repository, nowMillis = { 2_000L })
        val task = ScriptedTask(script = mutableListOf({ MeasurementOutcome.Failure("timeout ao conectar") }))

        val sample = executor.run(task)

        assertFalse(sample.success)
        assertEquals("timeout ao conectar", sample.errorMessage)
        assertNull(sample.downloadMbps)
    }

    @Test
    fun `excecao de rede numa rodada nao quebra a proxima`() = runTest {
        val repository = InMemoryMeasurementSampleRepository()
        val executor = MeasurementCycleExecutor(repository, nowMillis = { 3_000L })
        val task = ScriptedTask(
            script = mutableListOf(
                { throw java.io.IOException("rede indisponível") },
                { MeasurementOutcome.Success(latencyMs = 22.0) },
            ),
        )

        // Primeira rodada: task lança exceção — executor não deve propagar.
        val firstSample = executor.run(task)
        assertFalse(firstSample.success)
        assertEquals("rede indisponível", firstSample.errorMessage)

        // Segunda rodada (simulando o próximo tick do WorkManager): continua funcionando normalmente.
        val secondSample = executor.run(task)
        assertTrue(secondSample.success)
        assertEquals(22.0, secondSample.latencyMs)

        assertEquals(2, task.callCount)
        assertEquals(2, repository.inserted.size)
    }

    @Test
    fun `recent devolve amostras da origem pedida mais recentes primeiro`() = runTest {
        val repository = InMemoryMeasurementSampleRepository()
        val executor = MeasurementCycleExecutor(repository, nowMillis = { 1L })
        repository.insert(MeasurementSample(source = MeasurementSourceType.LATENCY, timestampEpochMs = 1L, success = true))
        val task = ScriptedTask(script = mutableListOf({ MeasurementOutcome.Success(downloadMbps = 50.0) }))
        executor.run(task) // SPEEDTEST, timestamp 1L (nowMillis fixo neste teste)

        val recentSpeedtest = repository.recent(MeasurementSourceType.SPEEDTEST, limit = 10)

        assertEquals(1, recentSpeedtest.size)
        assertEquals(MeasurementSourceType.SPEEDTEST, recentSpeedtest.first().source)
    }
}
