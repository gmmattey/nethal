package com.nethal.core.scheduling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Glue de WorkManager (issue #112) — a lógica de verdade fica em [MeasurementCycleExecutor], puro
 * Kotlin e testável sem `Context`. Este worker só resolve a [PeriodicMeasurementTask] pelo id
 * recebido em [inputData] e delega.
 *
 * Sempre devolve [Result.success], mesmo quando a medição falha: a falha já foi persistida como
 * [MeasurementSample] com `success = false` por [MeasurementCycleExecutor] — devolver
 * `Result.retry()`/`Result.failure()` aqui faria o WorkManager reagendar/desistir do trabalho
 * único, o que é o oposto do que #112 pede ("falha de rede numa rodada não quebra a próxima"): a
 * próxima rodada já está garantida pelo `PeriodicWorkRequest`, não precisa de retry.
 *
 * Instanciado via [MeasurementWorkerFactory] — nunca pelo construtor default do WorkManager
 * (precisa de [repository]/[registry] injetados manualmente, sem DI framework).
 */
class MeasurementWorker(
    appContext: Context,
    params: WorkerParameters,
    private val repository: MeasurementSampleRepository,
    private val registry: MeasurementTaskRegistry,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val task = registry.get(taskId) ?: return Result.failure()

        MeasurementCycleExecutor(repository).run(task)
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
    }
}
