package com.nethal.feature.toolshistory.ui

import com.nethal.core.history.ConnectionHistoryEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale("pt", "BR"))

/** Traduz um [ConnectionHistoryEvent] para o item de lista visto no protótipo `4i`. */
internal fun ConnectionHistoryEvent.toUiItem(nowEpochMs: Long): ConnectionHistoryItemUi {
    val relative = formatRelativeDayAndTime(atEpochMs, nowEpochMs)
    return when (this) {
        is ConnectionHistoryEvent.Drop -> ConnectionHistoryItemUi(
            kind = ConnectionHistoryItemUi.Kind.DROP,
            title = "Queda de conexão",
            subtitle = durationMinutes?.let { "$relative · ${formatDurationMinutes(it)}" } ?: "$relative · em andamento",
        )
        is ConnectionHistoryEvent.Restored -> ConnectionHistoryItemUi(
            kind = ConnectionHistoryItemUi.Kind.RESTORED,
            title = "Conexão restaurada",
            subtitle = relative,
        )
        is ConnectionHistoryEvent.Instability -> ConnectionHistoryItemUi(
            kind = ConnectionHistoryItemUi.Kind.INSTABILITY,
            title = "Instabilidade detectada",
            subtitle = "$relative · ${formatDurationMinutes(durationMinutes)}",
        )
    }
}

/** "Hoje, 14:32" / "Ontem, 09:12" / "3 dias atrás" — mesmo vocabulário relativo do protótipo `4i`. */
internal fun formatRelativeDayAndTime(epochMs: Long, nowEpochMs: Long): String {
    val zone = ZoneId.systemDefault()
    val eventDateTime = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(eventDateTime.toLocalDate(), today)
    val time = eventDateTime.format(TIME_FORMATTER)
    return when {
        daysAgo <= 0L -> "Hoje, $time"
        daysAgo == 1L -> "Ontem, $time"
        else -> "$daysAgo dias atrás"
    }
}

/** "3 min" / "1h" / "1h 30min" — mesmo padrão de duração usado em `UptimeNarrativaEngine` (SignallQ). */
internal fun formatDurationMinutes(minutes: Int): String {
    val hours = minutes / 60
    val remainder = minutes % 60
    return when {
        hours <= 0 -> "$remainder min"
        remainder == 0 -> "${hours}h"
        else -> "${hours}h ${remainder}min"
    }
}
