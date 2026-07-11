package com.nethal.core.scheduling

/**
 * Contrato genérico de uma capability de medição que pode ser rodada em intervalo (issue #112).
 *
 * Deliberadamente **não** conhece [com.nethal.core.capability.CapabilityEngine]/`DriverFamily`:
 * speedtest (#98) e latência (#99) medem a conexão de internet do próprio aparelho contra um
 * alvo externo (Cloudflare/gateway), não autenticam em equipamento pareado — mesmo raciocínio já
 * registrado no KDoc de `SpeedtestResult` (`:core:model`). Por isso este módulo não depende de
 * `:core:capability`/`:core:auth`: não há sessão administrativa para gerenciar aqui, e é por isso
 * que WorkManager é seguro neste caso específico (ver KDoc de [PeriodicMeasurementScheduler] e o
 * precedente/contraste com a issue #107, `StatusViewModel`, que rejeitou WorkManager exatamente
 * porque aquele caso *toca* sessão administrativa).
 *
 * Implementações concretas vivem no módulo de feature dono da capability (ex.:
 * `SpeedtestPeriodicMeasurementTask` em `:feature:tools-speedtest`, envolvendo o `SpeedtestEngine`
 * já existente) — nunca dentro de `:core:scheduling`, que não sabe nada sobre motores de medição
 * específicos. Isso respeita a ADR 0002 (`:core:*` nunca depende de `:feature:*`).
 */
interface PeriodicMeasurementTask {
    /** Identificador estável usado como nome do trabalho único no WorkManager e como chave de busca no [MeasurementTaskRegistry]. */
    val id: String

    val source: MeasurementSourceType

    /**
     * Roda a medição real. Implementações não precisam capturar exceções — [MeasurementCycleExecutor]
     * já trata qualquer [Exception] lançada daqui como [MeasurementOutcome.Failure], garantindo que
     * uma rodada com falha de rede nunca derruba o agendamento periódico (critério de aceite #112).
     */
    suspend fun measure(): MeasurementOutcome
}
