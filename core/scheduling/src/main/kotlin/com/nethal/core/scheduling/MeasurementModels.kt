package com.nethal.core.scheduling

/**
 * Origem de uma [MeasurementSample] — hoje só [SPEEDTEST] tem [PeriodicMeasurementTask] real
 * registrada (issue #98, já mergeada). [LATENCY] existe desde já no vocabulário porque a issue
 * #99 (latência/TCP connect) está em desenvolvimento em paralelo (branch
 * `feat/tools-ping-port-check-91-99-94-100-136`, não mergeada ainda nesta branch) — o mecanismo
 * desta issue (#112) não pode esperar por ela: quando #99 mergear, basta um novo
 * `PeriodicMeasurementTask` com `source = LATENCY` registrado, nada aqui muda.
 */
enum class MeasurementSourceType {
    SPEEDTEST,
    LATENCY,
}

/**
 * Resultado bruto de uma execução — nenhuma agregação/classificação de tendência acontece aqui
 * (isso é #104). Campos nullable porque nem toda [MeasurementSourceType] preenche todos (latência
 * não tem download/upload/bufferbloat, por exemplo) e porque uma rodada com falha ainda vira uma
 * linha persistida (ver [MeasurementCycleExecutor]), só que com todos os campos de medição nulos.
 */
data class MeasurementSample(
    val id: Long = 0,
    val source: MeasurementSourceType,
    val timestampEpochMs: Long,
    val success: Boolean,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val latencyMs: Double? = null,
    val jitterMs: Double? = null,
    val packetLossPercent: Double? = null,
    val bufferbloatMs: Double? = null,
    val errorMessage: String? = null,
)

/**
 * O que um [PeriodicMeasurementTask] devolve — sem timestamp/id/[MeasurementSourceType], que
 * [MeasurementCycleExecutor] carimba na hora de persistir (a task não precisa saber desses
 * detalhes, só medir).
 */
sealed interface MeasurementOutcome {
    data class Success(
        val downloadMbps: Double? = null,
        val uploadMbps: Double? = null,
        val latencyMs: Double? = null,
        val jitterMs: Double? = null,
        val packetLossPercent: Double? = null,
        val bufferbloatMs: Double? = null,
    ) : MeasurementOutcome

    data class Failure(val reason: String) : MeasurementOutcome
}
