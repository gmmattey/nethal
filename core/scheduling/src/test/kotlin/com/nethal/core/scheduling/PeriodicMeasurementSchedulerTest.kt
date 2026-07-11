package com.nethal.core.scheduling

import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * `PeriodicWorkRequest.Builder` não toca `Context`/binder — só monta um `WorkSpec` em memória — por
 * isso dá pra testar [PeriodicMeasurementScheduler.buildRequest] em JUnit comum, sem
 * `TestListenableWorkerBuilder`/instrumentação. O agendamento "de verdade" (via [PeriodicMeasurementScheduler.schedule]
 * chamando `WorkManager.enqueueUniquePeriodicWork`) é coberto pelo teste instrumentado
 * `androidTest/MeasurementSchedulingInstrumentedTest`.
 */
class PeriodicMeasurementSchedulerTest {

    @Test
    fun `intervalo configurado vira repeatInterval do WorkSpec`() {
        val request: PeriodicWorkRequest = PeriodicMeasurementScheduler.buildRequest(
            taskId = "speedtest_periodic",
            intervalMinutes = 45,
            requireUnmeteredNetwork = true,
        )

        assertEquals(TimeUnit.MINUTES.toMillis(45), request.workSpec.intervalDuration)
    }

    @Test
    fun `intervalo abaixo do minimo do WorkManager e sujeito ao piso de 15min`() {
        // PeriodicWorkRequest.Builder já clampa para PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS
        // internamente — este teste documenta esse comportamento em vez de reimplementar o clamp.
        val request = PeriodicMeasurementScheduler.buildRequest(
            taskId = "speedtest_periodic",
            intervalMinutes = 1,
            requireUnmeteredNetwork = true,
        )

        assertEquals(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, request.workSpec.intervalDuration)
    }

    @Test
    fun `requireUnmeteredNetwork controla o tipo de rede exigido`() {
        val onlyWifi = PeriodicMeasurementScheduler.buildRequest("speedtest_periodic", 30, requireUnmeteredNetwork = true)
        val anyNetwork = PeriodicMeasurementScheduler.buildRequest("latency_periodic", 30, requireUnmeteredNetwork = false)

        assertEquals(NetworkType.UNMETERED, onlyWifi.workSpec.constraints.requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, anyNetwork.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun `input data carrega o taskId para o worker resolver a task certa`() {
        val request = PeriodicMeasurementScheduler.buildRequest("speedtest_periodic", 30, requireUnmeteredNetwork = true)

        assertEquals("speedtest_periodic", request.workSpec.input.getString(MeasurementWorker.KEY_TASK_ID))
    }

    @Test
    fun `nome unico de trabalho e estavel por taskId`() {
        assertEquals(
            PeriodicMeasurementScheduler.uniqueWorkName("speedtest_periodic"),
            PeriodicMeasurementScheduler.uniqueWorkName("speedtest_periodic"),
        )
    }
}
