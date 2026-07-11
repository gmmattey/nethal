package com.nethal.core.scheduling

import kotlinx.coroutines.CancellationException

/**
 * Uma rodada de medição: chama [PeriodicMeasurementTask.measure], carimba id/timestamp, persiste
 * e devolve a amostra — puro Kotlin (sem `Context`/WorkManager), testável com um repositório fake
 * em teste JVM comum, sem precisar de `TestListenableWorkerBuilder`/instrumentação.
 *
 * ## Falha de rede não quebra a próxima rodada (critério de aceite #112)
 *
 * Qualquer [Exception] lançada por [PeriodicMeasurementTask.measure] é capturada aqui e vira uma
 * [MeasurementSample] com `success = false` — nunca propaga. Quem agenda a próxima rodada é o
 * [PeriodicWorkRequest][androidx.work.PeriodicWorkRequest] do WorkManager, que já está armado
 * independente do resultado desta chamada; [run] nunca lança, então não há como uma falha isolada
 * cancelar/atrasar o próximo ciclo. [CancellationException] é a única exceção repassada adiante —
 * cancelamento é sinal do chamador (job cancelado, worker parado pelo sistema), não falha de rede.
 */
class MeasurementCycleExecutor(
    private val repository: MeasurementSampleRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(task: PeriodicMeasurementTask): MeasurementSample {
        val outcome = try {
            task.measure()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            MeasurementOutcome.Failure(error.message ?: error::class.simpleName ?: "Erro desconhecido na medição")
        }

        val sample = outcome.toSample(source = task.source, timestampEpochMs = nowMillis())
        repository.insert(sample)
        return sample
    }
}

private fun MeasurementOutcome.toSample(source: MeasurementSourceType, timestampEpochMs: Long): MeasurementSample =
    when (this) {
        is MeasurementOutcome.Success -> MeasurementSample(
            source = source,
            timestampEpochMs = timestampEpochMs,
            success = true,
            downloadMbps = downloadMbps,
            uploadMbps = uploadMbps,
            latencyMs = latencyMs,
            jitterMs = jitterMs,
            packetLossPercent = packetLossPercent,
            bufferbloatMs = bufferbloatMs,
        )
        is MeasurementOutcome.Failure -> MeasurementSample(
            source = source,
            timestampEpochMs = timestampEpochMs,
            success = false,
            errorMessage = reason,
        )
    }
