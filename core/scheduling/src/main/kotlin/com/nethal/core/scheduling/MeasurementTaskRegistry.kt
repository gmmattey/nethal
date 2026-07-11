package com.nethal.core.scheduling

/**
 * Mapa `taskId -> PeriodicMeasurementTask`, montado pelo composition root (`:app`) do mesmo jeito
 * que `DriverFamilyRegistry` (ver `DriverFamilies.kt`) — sem framework de DI (ADR 0002 não
 * introduz um), sem `ServiceLoader`/classpath scanning.
 *
 * Existe como classe comum (não `object` global) por dois motivos: (1) evita estado global mutável
 * compartilhado entre testes, (2) [MeasurementWorkerFactory] recebe uma instância explícita — a
 * mesma que o composition root populou — em vez de ler um singleton escondido.
 */
class MeasurementTaskRegistry(tasks: List<PeriodicMeasurementTask> = emptyList()) {
    private val byId: Map<String, PeriodicMeasurementTask> = tasks.associateBy { it.id }

    fun get(taskId: String): PeriodicMeasurementTask? = byId[taskId]
}
