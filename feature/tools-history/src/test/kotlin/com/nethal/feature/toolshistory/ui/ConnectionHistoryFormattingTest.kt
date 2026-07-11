package com.nethal.feature.toolshistory.ui

import com.nethal.core.history.ConnectionHistoryEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY_MS = 24 * 60 * 60_000L

class ConnectionHistoryFormattingTest {

    @Test
    fun `formatDurationMinutes formata minutos, horas e horas com minutos`() {
        assertEquals("3 min", formatDurationMinutes(3))
        assertEquals("1h", formatDurationMinutes(60))
        assertEquals("1h 30min", formatDurationMinutes(90))
    }

    @Test
    fun `evento Drop resolvido mostra relativo mais duracao`() {
        val now = 10 * DAY_MS
        val event = ConnectionHistoryEvent.Drop(atEpochMs = now, durationMinutes = 3)

        val item = event.toUiItem(now)

        assertEquals("Queda de conexão", item.title)
        assertEquals(ConnectionHistoryItemUi.Kind.DROP, item.kind)
        assertTrue(item.subtitle.startsWith("Hoje,"))
        assertTrue(item.subtitle.endsWith("3 min"))
    }

    @Test
    fun `evento Drop em curso mostra em andamento em vez de duracao`() {
        val now = 10 * DAY_MS
        val event = ConnectionHistoryEvent.Drop(atEpochMs = now, durationMinutes = null)

        val item = event.toUiItem(now)

        assertTrue(item.subtitle.endsWith("em andamento"))
    }

    @Test
    fun `evento de ontem usa o rotulo Ontem`() {
        val now = 10 * DAY_MS
        val event = ConnectionHistoryEvent.Restored(atEpochMs = now - DAY_MS)

        val item = event.toUiItem(now)

        assertTrue(item.subtitle.startsWith("Ontem,"))
    }
}
