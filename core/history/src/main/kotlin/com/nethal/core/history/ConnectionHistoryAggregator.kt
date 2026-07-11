package com.nethal.core.history

import com.nethal.core.scheduling.MeasurementSample
import com.nethal.core.scheduling.MeasurementSampleRepository
import com.nethal.core.scheduling.MeasurementSourceType

/**
 * Agrupa [MeasurementSample] persistidas por [MeasurementSampleRepository] (issue #112, já
 * mergeada) em blocos de tempo classificados (issue #104) — adaptação de
 * `UptimeChartUseCase`/`TendenciaCalculador` (SignallQ) para o modelo de dados do NetHAL. Kotlin
 * puro, sem `Context`/Room — só a interface de repositório, mesmo padrão dos outros `core:*`.
 *
 * ## Fonte de dado (por que [MeasurementSourceType.SPEEDTEST] é o default)
 *
 * Hoje só existe um [com.nethal.core.scheduling.PeriodicMeasurementTask] real registrado
 * (`SpeedtestPeriodicMeasurementTask`, `:feature:tools-speedtest`) — `LATENCY` está no vocabulário
 * do módulo de scheduling desde a issue #112, mas sem task associada ainda (issue #99, não
 * mergeada nesta branch; ver KDoc de `MeasurementSourceType`). Quando #99 mergear, quem chamar
 * [aggregate] pode passar `source = MeasurementSourceType.LATENCY` sem precisar mudar nada aqui.
 *
 * ## Blocos e thresholds (mesmos valores do SignallQ, mesma justificativa)
 *
 * Janela de 30min / 7 dias = 336 blocos, thresholds de latência 300ms (OK) / 800ms (LENTO) — ver
 * `UptimeChartUseCase.kt` no SignallQ. Repetidos aqui como constantes porque a única mudança de
 * fonte é o repositório, não a lógica de classificação.
 */
class ConnectionHistoryAggregator(
    private val repository: MeasurementSampleRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    /**
     * Gera [DEFAULT_DAYS] * (24h / [windowMinutes]) blocos, do mais antigo para o mais recente —
     * o último bloco é o que inclui `nowMillis()`.
     *
     * [sampleLimit] limita quantas amostras recentes são lidas do repositório antes de agrupar em
     * blocos: [MeasurementSampleRepository.recent] não tem consulta por intervalo de tempo (só
     * `limit`), então pedimos uma margem generosa (default cobre 7 dias mesmo no piso de 15min do
     * WorkManager, que é o dobro do intervalo padrão de 30min) e filtramos pelo início da janela em
     * seguida.
     */
    suspend fun aggregate(
        source: MeasurementSourceType = MeasurementSourceType.SPEEDTEST,
        windowMinutes: Long = DEFAULT_WINDOW_MINUTES,
        days: Int = DEFAULT_DAYS,
        sampleLimit: Int = DEFAULT_SAMPLE_LIMIT,
    ): List<ConnectionHistoryBlock> {
        val now = nowMillis()
        val windowMs = windowMinutes * 60_000L
        val totalBlocks = (days * 24L * 60L / windowMinutes).toInt()
        val historyStartEpochMs = now - days * 24L * 60L * 60_000L

        val samples = repository.recent(source, sampleLimit)
            .filter { it.timestampEpochMs >= historyStartEpochMs }

        return (totalBlocks - 1 downTo 0).map { blocksAgo ->
            val blockEndEpochMs = now - blocksAgo * windowMs
            val blockStartEpochMs = blockEndEpochMs - windowMs
            val samplesInBlock = samples.filter { it.timestampEpochMs in blockStartEpochMs until blockEndEpochMs }
            classifyBlock(blockStartEpochMs, samplesInBlock)
        }
    }

    private fun classifyBlock(startEpochMs: Long, samples: List<MeasurementSample>): ConnectionHistoryBlock {
        if (samples.isEmpty()) {
            return ConnectionHistoryBlock(startEpochMs, ConnectionQuality.SEM_DADO, avgLatencyMs = null, sampleCount = 0)
        }

        val measuredLatencies = samples.filter { it.success }.mapNotNull { it.latencyMs }
        if (measuredLatencies.isEmpty()) {
            // Amostras existem no bloco mas nenhuma tem latência mensurável (falha de rede ou
            // rodada sem sucesso) — mesmo critério do SignallQ: sem latência = OFFLINE, não SEM_DADO
            // (SEM_DADO é reservado para "nenhuma medição rodou nessa janela").
            return ConnectionHistoryBlock(startEpochMs, ConnectionQuality.OFFLINE, avgLatencyMs = null, sampleCount = samples.size)
        }

        val avgLatency = measuredLatencies.average()
        val quality = when {
            avgLatency <= LATENCY_OK_MAX_MS -> ConnectionQuality.OK
            avgLatency <= LATENCY_LENTO_MAX_MS -> ConnectionQuality.LENTO
            else -> ConnectionQuality.OFFLINE
        }
        return ConnectionHistoryBlock(startEpochMs, quality, avgLatency, samples.size)
    }

    companion object {
        const val DEFAULT_WINDOW_MINUTES = 30L
        const val DEFAULT_DAYS = 7
        const val DEFAULT_SAMPLE_LIMIT = 1_000

        private const val LATENCY_OK_MAX_MS = 300.0
        private const val LATENCY_LENTO_MAX_MS = 800.0
    }
}
