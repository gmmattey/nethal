package com.nethal.core.history

/**
 * Um bloco de tempo agregado (janela padrão de 30min, ver [ConnectionHistoryAggregator]),
 * classificado por [ConnectionQuality] — equivalente ao `BlocoUptime` do SignallQ.
 *
 * @param startEpochMs início do bloco; o fim é implícito (`startEpochMs + windowMinutes` usado por
 * quem gerou a lista, ver [ConnectionHistoryAggregator.aggregate]).
 * @param avgLatencyMs latência média das amostras bem-sucedidas do bloco; `null` quando [quality]
 * é [ConnectionQuality.SEM_DADO] ou [ConnectionQuality.OFFLINE] sem nenhuma amostra com latência
 * mensurável.
 * @param sampleCount quantidade de [com.nethal.core.scheduling.MeasurementSample] que caíram
 * dentro do bloco (`0` quando [quality] é [ConnectionQuality.SEM_DADO]).
 */
data class ConnectionHistoryBlock(
    val startEpochMs: Long,
    val quality: ConnectionQuality,
    val avgLatencyMs: Double?,
    val sampleCount: Int,
)
