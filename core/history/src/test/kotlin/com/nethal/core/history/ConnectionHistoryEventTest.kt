package com.nethal.core.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val WINDOW_MINUTES = 30L
private const val WINDOW_MS = WINDOW_MINUTES * 60_000L

private fun block(index: Long, quality: ConnectionQuality, avgLatencyMs: Double? = null, sampleCount: Int = 1) =
    ConnectionHistoryBlock(startEpochMs = index * WINDOW_MS, quality = quality, avgLatencyMs = avgLatencyMs, sampleCount = sampleCount)

class ConnectionHistoryEventTest {

    @Test
    fun `serie totalmente OK nao gera eventos`() {
        val blocks = listOf(block(0, ConnectionQuality.OK), block(1, ConnectionQuality.OK), block(2, ConnectionQuality.OK))

        val events = deriveConnectionHistoryEvents(blocks, WINDOW_MINUTES)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `serie so com SEM_DADO nao gera eventos`() {
        val blocks = listOf(block(0, ConnectionQuality.SEM_DADO), block(1, ConnectionQuality.SEM_DADO))

        val events = deriveConnectionHistoryEvents(blocks, WINDOW_MINUTES)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `queda resolvida gera Drop com duracao e Restored`() {
        val blocks = listOf(
            block(0, ConnectionQuality.OK),
            block(1, ConnectionQuality.OFFLINE),
            block(2, ConnectionQuality.OFFLINE),
            block(3, ConnectionQuality.OK),
        )

        val events = deriveConnectionHistoryEvents(blocks, WINDOW_MINUTES)

        assertEquals(2, events.size)
        val drop = events.filterIsInstance<ConnectionHistoryEvent.Drop>().single()
        val restored = events.filterIsInstance<ConnectionHistoryEvent.Restored>().single()
        assertEquals(1 * WINDOW_MS, drop.atEpochMs)
        assertEquals(60, drop.durationMinutes) // 2 blocos de 30min
        assertEquals(3 * WINDOW_MS, restored.atEpochMs)
        // Restored é o item mais recente (maior atEpochMs) — ordenado primeiro.
        assertEquals(restored, events.first())
    }

    @Test
    fun `queda em curso gera Drop sem duracao e sem Restored`() {
        val blocks = listOf(block(0, ConnectionQuality.OK), block(1, ConnectionQuality.OFFLINE))

        val events = deriveConnectionHistoryEvents(blocks, WINDOW_MINUTES)

        assertEquals(1, events.size)
        val drop = events.single() as ConnectionHistoryEvent.Drop
        assertNull(drop.durationMinutes)
    }

    @Test
    fun `instabilidade resolvida gera um unico item com duracao`() {
        val blocks = listOf(
            block(0, ConnectionQuality.OK),
            block(1, ConnectionQuality.LENTO),
            block(2, ConnectionQuality.LENTO),
            block(3, ConnectionQuality.OK),
        )

        val events = deriveConnectionHistoryEvents(blocks, WINDOW_MINUTES)

        val instability = events.single() as ConnectionHistoryEvent.Instability
        assertEquals(1 * WINDOW_MS, instability.atEpochMs)
        assertEquals(60, instability.durationMinutes)
    }

    @Test
    fun `instabilidade em curso nao gera evento`() {
        val blocks = listOf(block(0, ConnectionQuality.OK), block(1, ConnectionQuality.LENTO))

        val events = deriveConnectionHistoryEvents(blocks, WINDOW_MINUTES)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `gap de SEM_DADO entre dois blocos OFFLINE funde numa unica queda`() {
        val blocks = listOf(
            block(0, ConnectionQuality.OK),
            block(1, ConnectionQuality.OFFLINE),
            block(2, ConnectionQuality.SEM_DADO),
            block(3, ConnectionQuality.OFFLINE),
            block(4, ConnectionQuality.OK),
        )

        val events = deriveConnectionHistoryEvents(blocks, WINDOW_MINUTES)

        val drop = events.filterIsInstance<ConnectionHistoryEvent.Drop>().single()
        assertEquals(1 * WINDOW_MS, drop.atEpochMs)
        // A sequência medida (ignorando SEM_DADO) é [OFFLINE@1, OFFLINE@3] — dura do início do
        // bloco 1 até o fim do bloco 3 (bloco 3 + 1 janela) = 3 blocos de 30min.
        assertEquals(90, drop.durationMinutes)
    }

    @Test
    fun `eventos ficam ordenados do mais recente para o mais antigo`() {
        val blocks = listOf(
            block(0, ConnectionQuality.OK),
            block(1, ConnectionQuality.OFFLINE),
            block(2, ConnectionQuality.OK),
            block(3, ConnectionQuality.LENTO),
            block(4, ConnectionQuality.OK),
        )

        val events = deriveConnectionHistoryEvents(blocks, WINDOW_MINUTES)

        val timestamps = events.map { it.atEpochMs }
        assertEquals(timestamps.sortedDescending(), timestamps)
    }
}
