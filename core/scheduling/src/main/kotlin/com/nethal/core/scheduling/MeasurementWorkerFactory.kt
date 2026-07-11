package com.nethal.core.scheduling

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/**
 * Fábrica de [MeasurementWorker] com injeção manual de [repository]/[registry] — padrão oficial do
 * WorkManager para evitar reflection com construtor sem argumentos quando o worker precisa de
 * dependências reais (aqui, sem framework de DI, ADR 0002).
 *
 * O composition root (`:app`, `NetHalApplication`) precisa: (1) implementar
 * `Configuration.Provider` devolvendo `Configuration.Builder().setWorkerFactory(...)`, e (2)
 * desabilitar o `WorkManagerInitializer` automático via `tools:node="remove"` no manifest — passo
 * de wiring que não faz parte desta issue (#112 é só o mecanismo; nenhum `:feature:tools-*`
 * mergeado até agora está de fato pendurado no `:app`, ver `BottomNavHost.kt` — a ativação real do
 * agendamento fica para quando #104/uma tela de Configurações decidir *quando* ligar isso).
 */
class MeasurementWorkerFactory(
    private val repository: MeasurementSampleRepository,
    private val registry: MeasurementTaskRegistry,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        if (workerClassName != MeasurementWorker::class.java.name) return null
        return MeasurementWorker(appContext, workerParameters, repository, registry)
    }
}
