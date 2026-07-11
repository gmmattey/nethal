package com.nethal.core.history

/**
 * Classificação de qualidade de conexão por bloco de tempo (issue #104).
 *
 * Vocabulário fixado pelo Rafael (decisão de escopo registrada em 2026-07-11 nas issues #96/#104):
 * série agregada estilo SignallQ, não trilha de eventos discretos. Os quatro estados e os
 * thresholds de latência em [ConnectionHistoryAggregator] são adaptados de `StatusUptime`
 * (`feature/history/UptimeChartUseCase.kt`, SignallQ) — mesma semântica, fonte de dado diferente
 * ([com.nethal.core.scheduling.MeasurementSample] em vez de `MedicaoEntity`/Room).
 */
enum class ConnectionQuality {
    OK,
    LENTO,
    OFFLINE,
    /** Nenhuma [com.nethal.core.scheduling.MeasurementSample] caiu dentro do bloco. */
    SEM_DADO,
}
