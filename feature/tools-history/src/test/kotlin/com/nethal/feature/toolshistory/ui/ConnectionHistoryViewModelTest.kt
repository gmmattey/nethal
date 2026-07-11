package com.nethal.feature.toolshistory.ui

import com.nethal.core.history.ConnectionHistoryAggregator
import com.nethal.core.scheduling.MeasurementSample
import com.nethal.core.scheduling.MeasurementSampleRepository
import com.nethal.core.scheduling.MeasurementSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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

/**
 * Cobre os critérios de aceite da #96 no nível de `ViewModel`: sem amostra real persistida vira
 * [ConnectionHistoryUiState.Empty] explícito (nunca item mockado), e amostra real vira item de
 * lista de verdade.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionHistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sem amostra nenhuma o estado vira Empty explicito`() = runTest {
        val now = 10 * DAY_MS
        val repository = InMemoryMeasurementSampleRepository(emptyList())
        val viewModel = ConnectionHistoryViewModel(ConnectionHistoryAggregator(repository, nowMillis = { now }), nowMillis = { now })

        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ConnectionHistoryUiState.Empty)
    }

    @Test
    fun `com queda resolvida o Content traz Drop e Restored`() = runTest {
        val now = 10 * DAY_MS
        val windowMs = 30 * 60_000L
        // Queda: falha no bloco anterior ao mais recente; bloco mais recente OK -> restaurado.
        val samples = listOf(
            MeasurementSample(source = MeasurementSourceType.SPEEDTEST, timestampEpochMs = now - windowMs - windowMs / 2, success = false),
            MeasurementSample(source = MeasurementSourceType.SPEEDTEST, timestampEpochMs = now - windowMs / 2, success = true, latencyMs = 20.0),
        )
        val repository = InMemoryMeasurementSampleRepository(samples)
        val viewModel = ConnectionHistoryViewModel(ConnectionHistoryAggregator(repository, nowMillis = { now }), nowMillis = { now })

        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ConnectionHistoryUiState.Content)
        state as ConnectionHistoryUiState.Content
        assertEquals(2, state.items.size)
        assertEquals(ConnectionHistoryItemUi.Kind.RESTORED, state.items.first().kind) // mais recente primeiro
        assertEquals(ConnectionHistoryItemUi.Kind.DROP, state.items.last().kind)
    }

    @Test
    fun `rede estavel o periodo todo vira Content com lista vazia, nao Empty`() = runTest {
        val now = 10 * DAY_MS
        val windowMs = 30 * 60_000L
        val samples = listOf(
            MeasurementSample(source = MeasurementSourceType.SPEEDTEST, timestampEpochMs = now - windowMs / 2, success = true, latencyMs = 20.0),
        )
        val repository = InMemoryMeasurementSampleRepository(samples)
        val viewModel = ConnectionHistoryViewModel(ConnectionHistoryAggregator(repository, nowMillis = { now }), nowMillis = { now })

        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ConnectionHistoryUiState.Content)
        assertTrue((state as ConnectionHistoryUiState.Content).items.isEmpty())
    }
}
