package com.nethal.core.scheduling

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Agenda/cancela a execução periódica de um [PeriodicMeasurementTask] via WorkManager (issue #112).
 *
 * ## Por que WorkManager é seguro aqui (ao contrário da issue #107)
 *
 * A issue #107 (atualização contínua da tela Status) descartou WorkManager de propósito porque
 * aquele mecanismo lê `CapabilityEngine` com uma sessão administrativa autenticada no equipamento —
 * a skill `/seguranca-nethal` proíbe sessão administrativa viva fora do primeiro plano, e
 * WorkManager existe justamente para sobreviver a isso.
 *
 * Speedtest (#98) e latência (#99) são o oposto: medem a conexão de internet do próprio aparelho
 * contra um alvo externo, sem autenticar em nada, sem `CapabilityEngine`/`DriverFamily` envolvidos
 * (ver KDoc de `SpeedtestResult`, `:core:model`, e de [PeriodicMeasurementTask] acima). Não existe
 * credencial para vazar em background — WorkManager é exatamente a ferramenta certa para construir
 * a série de tendência ao estilo SignallQ que #104 precisa (medição precisa continuar acontecendo
 * mesmo com o app fechado, senão a série fica cheia de buracos).
 *
 * ## Intervalo padrão e custo de bateria/dados (critério de aceite #112)
 *
 * [DEFAULT_INTERVAL_MINUTES] = 30min. WorkManager já impõe um piso real de 15min para trabalho
 * periódico (`PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS`) — 30min foi escolhido (não o piso)
 * porque:
 * - Um speedtest custa dados de verdade (download+upload real contra `speed.cloudflare.com`, modo
 *   `FAST` ~poucos MB por rodada, ver `SpeedtestPeriodicMeasurementTask`) — rodar no piso de 15min
 *   soma ~96 rodadas/dia, caro demais em plano de dados móvel para um recurso que hoje não tem opt-in
 *   de usuário nem tela própria.
 * - 30min ainda dá ~48 amostras/dia, granularidade suficiente para os blocos de 30min que #104
 *   citou como inspiração (`TendenciaCalculador`/SignallQ agrega justamente em blocos de 30min).
 * - [requireUnmeteredNetwork] (default `true`) evita rodar em dado móvel de qualquer forma — só
 *   Wi-Fi, a menos que quem chama decida explicitamente que uma capability mais barata (latência,
 *   TCP connect de poucos bytes) pode rodar em qualquer rede.
 *
 * Nenhum destes valores é enviado ao WorkManager como fixo — [schedule] aceita override, para uma
 * futura tela de Configurações (fora de escopo aqui) controlar o intervalo.
 */
class PeriodicMeasurementScheduler(private val workManager: WorkManager) {

    fun schedule(
        taskId: String,
        intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES,
        requireUnmeteredNetwork: Boolean = true,
    ) {
        val request = buildRequest(taskId, intervalMinutes, requireUnmeteredNetwork)
        workManager.enqueueUniquePeriodicWork(uniqueWorkName(taskId), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(taskId: String) {
        workManager.cancelUniqueWork(uniqueWorkName(taskId))
    }

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 30L
        private const val UNIQUE_WORK_PREFIX = "nethal_measurement_"

        fun uniqueWorkName(taskId: String): String = "$UNIQUE_WORK_PREFIX$taskId"

        /**
         * Extraído como função pura (sem depender de uma instância de [WorkManager]) para ser
         * testável em JUnit comum: [PeriodicWorkRequest.Builder] não toca `Context`/binder, só
         * monta um `WorkSpec` em memória.
         */
        fun buildRequest(
            taskId: String,
            intervalMinutes: Long,
            requireUnmeteredNetwork: Boolean,
        ): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<MeasurementWorker>(intervalMinutes, TimeUnit.MINUTES)
                .setInputData(workDataOf(MeasurementWorker.KEY_TASK_ID to taskId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            if (requireUnmeteredNetwork) NetworkType.UNMETERED else NetworkType.CONNECTED,
                        )
                        .build(),
                )
                .build()
    }
}
