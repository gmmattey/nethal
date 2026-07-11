package com.nethal.feature.toolsspeedtest.scheduling

import com.nethal.core.model.SpeedtestMode
import com.nethal.core.model.SpeedtestRunState
import com.nethal.core.scheduling.MeasurementOutcome
import com.nethal.core.scheduling.MeasurementSourceType
import com.nethal.core.scheduling.PeriodicMeasurementTask
import com.nethal.feature.toolsspeedtest.engine.SpeedtestEngine

/**
 * Adapta o [SpeedtestEngine] real (issue #98, já mergeada) ao contrato genérico de medição
 * periódica da issue #112 — a instância de [engine] injetada aqui é dedicada a rodadas em
 * background (não é a mesma instância que a `SpeedtestViewModel` usa para a tela em primeiro
 * plano, que tem seu próprio ciclo de vida de `ViewModel`).
 *
 * Roda sempre em [SpeedtestMode.FAST] — o modo mais barato em dados/bateria (~6s por direção,
 * poucos streams), adequado a uma rodada recorrente sem o usuário olhando; [SpeedtestMode.TRIPLE]
 * (3 rodadas + mediana) seria ~3x o custo de dados para um ganho de precisão que não compensa numa
 * série de tendência com várias amostras já suavizando ruído.
 */
class SpeedtestPeriodicMeasurementTask(
    private val engine: SpeedtestEngine,
) : PeriodicMeasurementTask {

    override val id: String = TASK_ID
    override val source: MeasurementSourceType = MeasurementSourceType.SPEEDTEST

    override suspend fun measure(): MeasurementOutcome {
        // SpeedtestEngine.run só retorna depois que o snapshotFlow já chegou em DONE/ERROR (ver
        // KDoc de CloudflareSpeedtestEngine.run) — sem necessidade de observar o flow separadamente.
        engine.run(SpeedtestMode.FAST)
        val finalSnapshot = engine.snapshotFlow.value
        val result = finalSnapshot.result

        return if (finalSnapshot.runState == SpeedtestRunState.DONE && result != null) {
            MeasurementOutcome.Success(
                downloadMbps = result.downloadMbps,
                uploadMbps = result.uploadMbps,
                latencyMs = result.latencyMs,
                jitterMs = result.jitterMs,
                packetLossPercent = result.packetLossPercent,
                bufferbloatMs = result.bufferbloatMs,
            )
        } else {
            MeasurementOutcome.Failure(finalSnapshot.errorMessage ?: "Speedtest periódico não completou")
        }
    }

    companion object {
        const val TASK_ID = "speedtest_periodic"
    }
}
